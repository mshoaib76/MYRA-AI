package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.myra.assistant.R
import com.myra.assistant.ui.call.IncomingCallActivity
import com.myra.assistant.ui.main.MainActivity
import com.myra.assistant.util.CallerInfoResolver

class CallMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "myra_call_monitor"
        const val ACTION_CALL_ENDED = "com.myra.CALL_ENDED"
        const val ACTION_INCOMING_CALL = "com.myra.INCOMING_CALL"
        const val EXTRA_INCOMING = "INCOMING_CALL"
        const val EXTRA_CALLER_NAME = "CALLER_NAME"
        const val EXTRA_CALLER_NUMBER = "CALLER_NUMBER"
        const val EXTRA_IS_CONTACT = "IS_CONTACT"
    }

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var lastIncomingUiAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, buildNotification())
        setupPhoneListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        super.onDestroy()
    }

    private fun setupPhoneListener() {
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            @Suppress("DEPRECATION")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        val now = System.currentTimeMillis()
                        if (now - lastIncomingUiAt < 2500) return
                        lastIncomingUiAt = now
                        val number = phoneNumber ?: ""
                        val caller = CallerInfoResolver.resolve(this@CallMonitorService, number)
                        val broadcast = Intent(ACTION_INCOMING_CALL).apply {
                            setPackage(packageName)
                            putExtra(EXTRA_CALLER_NAME, caller.displayName)
                            putExtra(EXTRA_CALLER_NUMBER, caller.phoneNumber)
                            putExtra(EXTRA_IS_CONTACT, caller.isSavedContact)
                        }
                        sendBroadcast(broadcast)
                        startActivity(MainActivity.incomingCallIntent(this@CallMonitorService, caller))
                        startActivity(IncomingCallActivity.intent(this@CallMonitorService, caller))
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        lastIncomingUiAt = 0L
                        sendBroadcast(Intent(ACTION_CALL_ENDED).setPackage(packageName))
                    }
                }
            }
        }
        @Suppress("DEPRECATION")
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.call_monitor_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA Call Monitor")
            .setContentText("Watching for incoming calls")
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
