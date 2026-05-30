package com.myra.assistant.util

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import com.myra.assistant.service.AccessibilityHelperService

object CallActionHelper {

    fun accept(context: Context): Boolean {
        if (tryTelecomAccept(context)) return true
        return AccessibilityHelperService.instance?.answerIncomingCall() == true
    }

    fun reject(context: Context): Boolean {
        if (tryTelecomReject(context)) return true
        return AccessibilityHelperService.instance?.declineIncomingCall() == true
    }

    private fun tryTelecomAccept(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
            telecom.acceptRingingCall()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun tryTelecomReject(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
            telecom.endCall()
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
