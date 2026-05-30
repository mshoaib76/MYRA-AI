package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.myra.assistant.R
import com.myra.assistant.ui.whatsapp.WhatsAppMessageActivity
import com.myra.assistant.util.NotificationListenerHelper
import com.myra.assistant.util.PrefsHelper
import com.myra.assistant.util.PrimeContactMatcher
import com.myra.assistant.whatsapp.PendingWhatsAppInbox

class MyraNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val CHANNEL_ALERTS = "myra_wa_prime_alerts"
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        private var lastAlertKey = ""
        private var lastAlertAt = 0L

        fun requestRebind(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, MyraNotificationListenerService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        if (!PrefsHelper.isWhatsAppPrimeAlertsEnabled(this)) return
        if (!PrefsHelper.isLoggedIn(this)) return

        val extras = sbn.notification.extras ?: return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val sender = extractSender(extras) ?: return
        val body = extractMessageBody(extras) ?: return
        if (body.equals("Checking for new messages", ignoreCase = true)) return
        if (body.equals("Messages", ignoreCase = true)) return

        val prime = PrimeContactMatcher.match(this, sender) ?: return

        val dedupeKey = "${prime.number}|$body"
        val now = System.currentTimeMillis()
        if (dedupeKey == lastAlertKey && now - lastAlertAt < 4000) return
        lastAlertKey = dedupeKey
        lastAlertAt = now

        val inbox = PendingWhatsAppInbox.Message(
            prime = prime,
            senderLabel = sender,
            body = body
        )
        PendingWhatsAppInbox.set(inbox)

        showMessageUi(inbox)
    }

    private fun extractSender(extras: Bundle): String? {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        if (!title.isNullOrBlank()) {
            return if (!sub.isNullOrBlank() && sub.length < title.length) sub else title
        }
        return sub
    }

    private fun extractMessageBody(extras: Bundle): String? {
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        extras.getCharSequence("android.text")?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun showMessageUi(inbox: PendingWhatsAppInbox.Message) {
        val activityIntent = WhatsAppMessageActivity.intent(this, inbox)
        try {
            startActivity(activityIntent)
        } catch (_: Exception) {
            showAlertNotification(inbox, activityIntent)
        }
    }

    private fun showAlertNotification(
        inbox: PendingWhatsAppInbox.Message,
        activityIntent: Intent
    ) {
        createChannel()
        val pending = PendingIntent.getActivity(
            this, inbox.receivedAt.toInt(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_myra_notif)
            .setContentTitle(getString(R.string.wa_prime_notif_title, inbox.prime.name))
            .setContentText(getString(R.string.wa_prime_notif_tap))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(inbox.receivedAt.toInt(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            getString(R.string.wa_prime_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
