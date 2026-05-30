package com.myra.assistant.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

object OverlayPermissionHelper {

    const val REQUEST_OVERLAY = 101
    private const val PREFS = "myra_prefs"
    private const val KEY_PROMPTED = "overlay_permission_prompted"

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun shouldPrompt(context: Context): Boolean {
        if (canDrawOverlays(context)) return false
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMPTED, false)
    }

    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROMPTED, true)
            .apply()
    }

    fun showRationaleAndRequest(activity: AppCompatActivity) {
        if (canDrawOverlays(activity)) return
        AlertDialog.Builder(activity)
            .setTitle("Overlay permission")
            .setMessage(
                "MYRA needs \"Display over other apps\" so the floating orb appears " +
                    "when you double-press the power button."
            )
            .setPositiveButton("Allow") { _, _ ->
                markPrompted(activity)
                openOverlaySettings(activity)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun requestIfNeeded(activity: AppCompatActivity) {
        if (canDrawOverlays(activity)) return
        if (shouldPrompt(activity)) {
            showRationaleAndRequest(activity)
        }
    }
}
