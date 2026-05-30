package com.myra.assistant.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.myra.assistant.service.MyraNotificationListenerService

object NotificationListenerHelper {

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        if (flat.isEmpty()) return false
        val cn = ComponentName(context, MyraNotificationListenerService::class.java).flattenToString()
        return flat.split(':').any { part ->
            part.equals(cn, ignoreCase = true) || part.contains(context.packageName, ignoreCase = true)
        }
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestRebind(context: Context) {
        if (!isEnabled(context)) return
        try {
            MyraNotificationListenerService.requestRebind(context)
        } catch (_: Exception) {
        }
    }
}
