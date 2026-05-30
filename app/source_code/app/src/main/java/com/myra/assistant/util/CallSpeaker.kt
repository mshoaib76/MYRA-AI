package com.myra.assistant.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CallSpeaker(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(onReady: (() -> Unit)? = null) {
        if (tts != null) {
            onReady?.invoke()
            return
        }
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag("ur-PK").takeIf {
                    tts?.isLanguageAvailable(it) == TextToSpeech.LANG_AVAILABLE
                } ?: Locale("hi", "IN").takeIf {
                    tts?.isLanguageAvailable(it) == TextToSpeech.LANG_AVAILABLE
                } ?: Locale.getDefault()
                ready.set(true)
                onReady?.let { mainHandler.post(it) }
            }
        }
    }

    fun speak(message: String, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (!ready.get() || engine == null) {
            onDone?.let { mainHandler.postDelayed(it, 300) }
            return
        }
        val utteranceId = "myra_call_${System.currentTimeMillis()}"
        if (onDone != null) {
            val doneCallback = onDone
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) mainHandler.post(doneCallback)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post(doneCallback)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post(doneCallback)
                }
            })
        }
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }
}
