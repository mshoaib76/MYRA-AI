package com.myra.assistant.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.myra.assistant.util.PrefsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveClient(private val context: Context) {

    companion object {
        private const val TAG = "GeminiLive"
        private const val WS_BASE =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val SESSION_RENEW_AFTER_MS = 540_000L
        private const val KEEPALIVE_INTERVAL_MS = 8_000L
        private const val RECONNECT_DELAY_MS = 3_000L
        private const val SILENT_PCM_CHUNK_SIZE = 1024
        private const val DEFAULT_OUTPUT_SAMPLE_RATE = 24000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var keepaliveJob: Job? = null
    private var sessionRenewJob: Job? = null
    private var reconnectJob: Job? = null

    private val shouldRun = AtomicBoolean(false)
    private val isSetupComplete = AtomicBoolean(false)
    private val isRenewing = AtomicBoolean(false)
    private var sessionStartMs = 0L
    private var useMinimalSetup = false

    var suppressMicSend = false
    var onConnected: (() -> Unit)? = null
    var onSetupComplete: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onAudioReceived: ((ByteArray, Int) -> Unit)? = null
    var onInputTranscript: ((String) -> Unit)? = null
    var onOutputTranscript: ((String) -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onThinking: ((Boolean) -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null

    fun connect() {
        val apiKey = PrefsHelper.getApiKey(context).trim()
        if (apiKey.isBlank()) {
            onError?.invoke("API key missing — open Settings")
            return
        }
        shouldRun.set(true)
        useMinimalSetup = false
        openSocket(apiKey)
    }

    fun disconnect() {
        shouldRun.set(false)
        keepaliveJob?.cancel()
        sessionRenewJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isSetupComplete.set(false)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    fun sendAudioPcm(pcmBytes: ByteArray) {
        if (!isSetupComplete.get() || suppressMicSend) return
        val ws = webSocket ?: return
        val b64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        // Match official cookbook: realtime_input + media_chunks + mime_type "audio/pcm"
        val msg = JSONObject().apply {
            put("realtime_input", JSONObject().apply {
                put("media_chunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mime_type", "audio/pcm")
                        put("data", b64)
                    })
                })
            })
        }
        ws.send(msg.toString())
    }

    fun sendText(text: String) {
        if (!isSetupComplete.get()) {
            onError?.invoke("Not connected yet — wait for setup")
            return
        }
        val msg = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", text))
                        })
                    })
                })
                put("turn_complete", true)
            })
        }
        webSocket?.send(msg.toString())
        onThinking?.invoke(true)
    }

    fun sendInterrupt() {
        if (!isSetupComplete.get()) return
        val msg = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray())
                put("turn_complete", true)
            })
        }
        webSocket?.send(msg.toString())
    }

    private fun openSocket(apiKey: String) {
        val url = "$WS_BASE?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened HTTP ${response.code}")
                sessionStartMs = System.currentTimeMillis()
                isSetupComplete.set(false)
                onStatus?.invoke(if (useMinimalSetup) "Simple setup..." else "Sending setup...")
                sendSetupMessage(webSocket)
                onConnected?.invoke()
                startKeepalive()
                startSessionRenewal(apiKey)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text, apiKey)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8(), apiKey)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code $reason")
                isSetupComplete.set(false)
                onDisconnected?.invoke()
                if (isInvalidArgument(reason) && !useMinimalSetup) {
                    useMinimalSetup = true
                    onError?.invoke("Retrying with simple setup...")
                    scope.launch {
                        delay(500)
                        if (shouldRun.get()) openSocket(apiKey)
                    }
                    return
                }
                if (code != 1000 && reason.isNotBlank()) {
                    onError?.invoke(friendlyError(reason))
                }
                scheduleReconnect(apiKey)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val body = response?.body?.string()
                Log.e(TAG, "Failure HTTP ${response?.code} body=$body", t)
                isSetupComplete.set(false)
                val raw = body ?: t.message ?: ""
                if (isInvalidArgument(raw) && !useMinimalSetup) {
                    useMinimalSetup = true
                    onError?.invoke("Setup error — retrying simple mode...")
                    scope.launch {
                        delay(500)
                        if (shouldRun.get()) openSocket(apiKey)
                    }
                    return
                }
                val msg = when {
                    response?.code == 401 || response?.code == 403 ->
                        "Invalid API key — check Settings"
                    response?.code == 404 -> "Model not found — use MYRA Voice model in Settings"
                    else -> friendlyError(parseErrorBody(body) ?: raw)
                }
                onError?.invoke(msg)
                onDisconnected?.invoke()
                scheduleReconnect(apiKey)
            }
        })
    }

    private fun isInvalidArgument(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("invalid argument") ||
            lower.contains("invalid_argument") ||
            lower.contains("invalid frame")
    }

    private fun friendlyError(raw: String): String {
        return when {
            isInvalidArgument(raw) ->
                "Invalid setup — Settings save karo, app restart karo"
            raw.contains("API key", ignoreCase = true) -> raw
            raw.length > 120 -> raw.take(120) + "..."
            else -> raw.ifBlank { "Connection failed" }
        }
    }

    private fun parseErrorBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            body
        }
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val model = normalizeModel(PrefsHelper.getModel(context))
        val voice = PrefsHelper.getVoice(context)
        val systemPrompt = PrefsHelper.buildSystemPrompt(context)

        val setupInner = JSONObject().apply {
            put("model", model)
            if (!useMinimalSetup) {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    })
                })
            }
            if (!useMinimalSetup && supportsNativeAudioConfig(model)) {
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().put("AUDIO"))
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", voice)
                            })
                        })
                    })
                })
                // Transcription: only for native-audio models (empty object enables it per API docs)
                put("input_audio_transcription", JSONObject())
                put("output_audio_transcription", JSONObject())
            } else if (!useMinimalSetup) {
                // gemini-2.0-flash-live-001 — AUDIO only, no voice_name (not supported)
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().put("AUDIO"))
                })
            }
        }

        val setup = JSONObject().put("setup", setupInner)
        Log.d(TAG, "Setup minimal=$useMinimalSetup model=$model payload=${setup.toString().take(300)}")
        ws.send(setup.toString())
    }

    private fun normalizeModel(model: String): String {
        var m = model.trim()
        if (!m.startsWith("models/")) m = "models/$m"
        // Map deprecated/invalid IDs to working cookbook model
        val remap = setOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.5-flash-preview-native-audio-dialog",
            PrefsHelper.MODEL_FLASH_LIVE,
            "models/gemini-flash-2.0-live-001"
        )
        if (m in remap) m = PrefsHelper.MODEL_NATIVE_AUDIO_LATEST
        return m
    }

    private fun supportsNativeAudioConfig(model: String): Boolean {
        return model.contains("native-audio", ignoreCase = true) &&
            !model.contains("live-001", ignoreCase = true)
    }

    private fun handleMessage(text: String, apiKey: String) {
        if (text.isBlank()) return
        try {
            val json = JSONObject(text)
            Log.d(TAG, "RX: ${text.take(280)}")

            json.optJSONObject("error")?.let { err ->
                val msg = err.optString("message", text)
                if (isInvalidArgument(msg) && !useMinimalSetup) {
                    useMinimalSetup = true
                    webSocket?.close(1000, "Retry minimal")
                    scope.launch {
                        delay(400)
                        if (shouldRun.get()) openSocket(apiKey)
                    }
                    return
                }
                onError?.invoke(friendlyError(msg))
                return
            }

            if (json.has("setupComplete") || json.has("setup_complete")) {
                isSetupComplete.set(true)
                onStatus?.invoke("Ready — speak now")
                onSetupComplete?.invoke()
                onThinking?.invoke(false)
                return
            }

            val serverContent = json.optJSONObject("serverContent")
                ?: json.optJSONObject("server_content")
                ?: return

            if (serverContent.optBoolean("interrupted", false)) {
                onThinking?.invoke(false)
            }

            val modelTurn = serverContent.optJSONObject("modelTurn")
                ?: serverContent.optJSONObject("model_turn")
            modelTurn?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val inline = part.optJSONObject("inlineData")
                        ?: part.optJSONObject("inline_data")
                        ?: continue
                    val mime = inline.optString("mimeType", inline.optString("mime_type", ""))
                    val data = inline.optString("data", "")
                    if (data.isNotEmpty() && (mime.contains("audio", ignoreCase = true) || mime.isEmpty())) {
                        val audio = Base64.decode(data, Base64.DEFAULT)
                        if (audio.isNotEmpty()) {
                            onAudioReceived?.invoke(audio, parseAudioSampleRate(mime))
                            onThinking?.invoke(false)
                        }
                    }
                }
            }

            val outTrans = serverContent.optJSONObject("outputTranscription")
                ?: serverContent.optJSONObject("output_transcription")
            outTrans?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
                onOutputTranscript?.invoke(it)
            }

            val inTrans = serverContent.optJSONObject("inputTranscription")
                ?: serverContent.optJSONObject("input_transcription")
            inTrans?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
                onInputTranscript?.invoke(it)
            }

            if (serverContent.optBoolean("turnComplete", false) ||
                serverContent.optBoolean("turn_complete", false)
            ) {
                onTurnComplete?.invoke()
                onThinking?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${text.take(500)}", e)
        }
    }

    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            val silent = ByteArray(SILENT_PCM_CHUNK_SIZE)
            while (isActive && shouldRun.get()) {
                delay(KEEPALIVE_INTERVAL_MS)
                if (isSetupComplete.get()) {
                    sendAudioPcm(silent)
                }
            }
        }
    }

    private fun startSessionRenewal(apiKey: String) {
        sessionRenewJob?.cancel()
        sessionRenewJob = scope.launch {
            while (isActive && shouldRun.get()) {
                delay(SESSION_RENEW_AFTER_MS)
                if (System.currentTimeMillis() - sessionStartMs >= SESSION_RENEW_AFTER_MS) {
                    renewSession(apiKey)
                    break
                }
            }
        }
    }

    private fun scheduleReconnect(apiKey: String) {
        if (!shouldRun.get() || isRenewing.get()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (shouldRun.get() && !isRenewing.get()) {
                onStatus?.invoke("Reconnecting...")
                openSocket(apiKey)
            }
        }
    }

    private fun renewSession(apiKey: String) {
        isRenewing.set(true)
        webSocket?.close(1000, "Session renewal")
        scope.launch {
            delay(500)
            isRenewing.set(false)
            if (shouldRun.get()) openSocket(apiKey)
        }
    }

    private fun parseAudioSampleRate(mimeType: String): Int {
        val normalized = mimeType.lowercase()
        val marker = "rate="
        val idx = normalized.indexOf(marker)
        if (idx >= 0) {
            val value = normalized.substring(idx + marker.length)
                .takeWhile { it.isDigit() }
                .toIntOrNull()
            if (value != null && value in 8_000..48_000) return value
        }
        return DEFAULT_OUTPUT_SAMPLE_RATE
    }
}
