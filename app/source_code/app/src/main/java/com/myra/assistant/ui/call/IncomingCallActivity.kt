package com.myra.assistant.ui.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.myra.assistant.R
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.ui.main.MainActivity
import com.myra.assistant.util.CallerInfo

/**
 * Visual dialog only — MYRA agent (MainActivity + Gemini) handles voice, listen, accept/reject.
 */
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var callerNameText: TextView
    private lateinit var callerNumberText: TextView
    private lateinit var listeningHint: TextView
    private var callerInfo = CallerInfo("Unknown", "", false)

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowFlags()
        setContentView(R.layout.activity_incoming_call)

        callerInfo = readCallerFromIntent(intent)
        callerNameText = findViewById(R.id.callerNameText)
        callerNumberText = findViewById(R.id.callerNumberText)
        listeningHint = findViewById(R.id.listeningHint)
        bindCallerToUi()

        listeningHint.visibility = View.VISIBLE
        listeningHint.text = getString(R.string.incoming_call_myra_listening)

        findViewById<MaterialButton>(R.id.btnAcceptCall).setOnClickListener {
            MainActivity.notifyCallDecision(this, "ACCEPT")
        }
        findViewById<MaterialButton>(R.id.btnRejectCall).setOnClickListener {
            MainActivity.notifyCallDecision(this, "REJECT")
        }

        val filter = IntentFilter().apply {
            addAction(CallMonitorService.ACTION_CALL_ENDED)
            addAction(MainActivity.ACTION_CALL_HANDLED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(dismissReceiver, filter)
        }
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

    private fun readCallerFromIntent(intent: Intent): CallerInfo {
        return CallerInfo(
            displayName = intent.getStringExtra(EXTRA_CALLER_NAME)?.trim().orEmpty().ifBlank { "Unknown" },
            phoneNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER)?.trim().orEmpty(),
            isSavedContact = intent.getBooleanExtra(EXTRA_IS_CONTACT, false)
        )
    }

    private fun bindCallerToUi() {
        if (callerInfo.isSavedContact) {
            callerNameText.text = callerInfo.displayName
            if (callerInfo.phoneNumber.isNotBlank()) {
                callerNumberText.text = callerInfo.phoneNumber
                callerNumberText.visibility = View.VISIBLE
            } else {
                callerNumberText.visibility = View.GONE
            }
        } else {
            callerNameText.text = getString(R.string.unknown_caller)
            if (callerInfo.phoneNumber.isNotBlank()) {
                callerNumberText.text = callerInfo.phoneNumber
                callerNumberText.visibility = View.VISIBLE
            } else {
                callerNumberText.visibility = View.GONE
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callerInfo = readCallerFromIntent(intent)
        bindCallerToUi()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(dismissReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CALLER_NAME = "caller_name"
        private const val EXTRA_CALLER_NUMBER = "caller_number"
        private const val EXTRA_IS_CONTACT = "is_contact"

        fun intent(context: Context, caller: CallerInfo): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_CALLER_NAME, caller.displayName)
                putExtra(EXTRA_CALLER_NUMBER, caller.phoneNumber)
                putExtra(EXTRA_IS_CONTACT, caller.isSavedContact)
            }
        }
    }
}
