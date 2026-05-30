package com.myra.assistant.ui.whatsapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.myra.assistant.R
import com.myra.assistant.util.CallSpeaker
import com.myra.assistant.whatsapp.PendingWhatsAppInbox
import com.myra.assistant.whatsapp.WhatsAppInboxCommandParser
import com.myra.assistant.whatsapp.WhatsAppPrimeReplyHelper

/**
 * Voice-only alert for prime WhatsApp messages — message text is never shown on screen.
 */
class WhatsAppMessageActivity : AppCompatActivity() {

    private lateinit var inbox: PendingWhatsAppInbox.Message
    private lateinit var callSpeaker: CallSpeaker

    private var speechRecognizer: SpeechRecognizer? = null
    private var finished = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowFlags()

        inbox = readInboxFromIntent() ?: run {
            finish()
            return
        }
        PendingWhatsAppInbox.set(inbox)

        callSpeaker = CallSpeaker(this)
        callSpeaker.initialize {
            announceNameAndAskPermission()
        }
    }

    private fun readInboxFromIntent(): PendingWhatsAppInbox.Message? {
        val name = intent.getStringExtra(EXTRA_PRIME_NAME)?.trim().orEmpty()
        val number = intent.getStringExtra(EXTRA_PRIME_NUMBER)?.trim().orEmpty()
        val body = intent.getStringExtra(EXTRA_MESSAGE)?.trim().orEmpty()
        val sender = intent.getStringExtra(EXTRA_SENDER_LABEL)?.trim().orEmpty()
        if (name.isBlank() || body.isBlank()) return PendingWhatsAppInbox.current
        return PendingWhatsAppInbox.Message(
            prime = com.myra.assistant.model.PrimeContact(name, number),
            senderLabel = sender.ifBlank { name },
            body = body
        )
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    /** Only the sender name — never the message body. */
    private fun announceNameAndAskPermission() {
        val intro = getString(R.string.wa_prime_ask_read, inbox.prime.name)
        callSpeaker.speak(intro) {
            if (!finished) startVoiceListening()
        }
    }

    private fun readMessageAloud(onDone: (() -> Unit)? = null) {
        val spoken = getString(R.string.wa_prime_reading, inbox.prime.name, inbox.body)
        callSpeaker.speak(spoken) {
            onDone?.invoke() ?: run {
                if (!finished) startVoiceListening()
            }
        }
    }

    private fun startVoiceListening() {
        if (finished) return
        if (!hasMicPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            handler.postDelayed({ finishUi() }, 2500)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            handler.postDelayed({ finishUi() }, 2000)
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                processSpeech(
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                )
            }

            override fun onPartialResults(partialResults: Bundle?) {
                processSpeech(
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                )
            }

            override fun onError(error: Int) {
                if (!finished && error != SpeechRecognizer.ERROR_CLIENT) {
                    handler.postDelayed({ startVoiceListening() }, 900)
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(listenIntent)
    }

    private fun processSpeech(phrases: List<String>) {
        for (phrase in phrases) {
            when (val action = WhatsAppInboxCommandParser.parse(phrase)) {
                WhatsAppInboxCommandParser.Action.DeclineRead -> {
                    callSpeaker.speak(getString(R.string.wa_prime_declined)) {
                        finishUi()
                    }
                    return
                }
                WhatsAppInboxCommandParser.Action.ReadAloud -> {
                    readMessageAloud()
                    return
                }
                is WhatsAppInboxCommandParser.Action.ReadAndReply -> {
                    readMessageAloud {
                        sendReply(action.replyText)
                    }
                    return
                }
                is WhatsAppInboxCommandParser.Action.Reply -> {
                    sendReply(action.text)
                    return
                }
                WhatsAppInboxCommandParser.Action.Dismiss -> {
                    finishUi()
                    return
                }
                null -> continue
            }
        }
    }

    private fun sendReply(text: String) {
        if (finished) return
        finished = true
        speechRecognizer?.destroy()
        val result = WhatsAppPrimeReplyHelper.sendReply(this, inbox.prime, text)
        Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
        callSpeaker.speak(getString(R.string.wa_reply_sent, inbox.prime.name)) {
            handler.postDelayed({ finishUi() }, 400)
        }
    }

    private fun finishUi() {
        if (isFinishing) return
        finished = true
        speechRecognizer?.destroy()
        PendingWhatsAppInbox.clear()
        finish()
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceListening()
        } else if (requestCode == REQ_MIC) {
            finishUi()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        callSpeaker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PRIME_NAME = "prime_name"
        private const val EXTRA_PRIME_NUMBER = "prime_number"
        private const val EXTRA_SENDER_LABEL = "sender_label"
        private const val EXTRA_MESSAGE = "message"
        private const val REQ_MIC = 301

        fun intent(context: Context, inbox: PendingWhatsAppInbox.Message): Intent {
            return Intent(context, WhatsAppMessageActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_PRIME_NAME, inbox.prime.name)
                putExtra(EXTRA_PRIME_NUMBER, inbox.prime.number)
                putExtra(EXTRA_SENDER_LABEL, inbox.senderLabel)
                putExtra(EXTRA_MESSAGE, inbox.body)
            }
        }
    }
}
