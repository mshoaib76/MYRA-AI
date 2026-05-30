package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val DOUBLE_PRESS_MS = 600L
        private var lastPressTime = 0L
        private var pressCount = 0
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SCREEN_OFF && action != Intent.ACTION_SCREEN_ON) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPressTime < DOUBLE_PRESS_MS) {
            pressCount++
        } else {
            pressCount = 1
        }
        lastPressTime = now

        if (pressCount >= 2) {
            pressCount = 0
            val serviceIntent = Intent(context, MyraOverlayService::class.java).apply {
                this.action = MyraOverlayService.ACTION_SHOW
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
