package com.myra.assistant.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.service.PendingActionRunner
import com.myra.assistant.util.AppLauncher
import com.myra.assistant.util.GalleryHelper
import com.myra.assistant.util.MediaContext

/**
 * High-level "human like" tasks: gallery, social post, profile photo.
 * Uses Share intents + Accessibility automation chains.
 */
object HumanAgent {

    private val platforms = mapOf(
        "facebook" to "com.facebook.katana",
        "fb" to "com.facebook.katana",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "whatsapp" to "com.whatsapp"
    )

    fun deletePhoto(context: Context, hint: String): String {
        val uri = MediaContext.resolve(context, hint)
            ?: return "Photo nahi mili — Photos permission ON karo"
        val name = hint.ifBlank { "photo" }
        return if (GalleryHelper.deleteImage(context, uri)) {
            MediaContext.pin(null)
            "Photo delete ho gayi ✓ ($name)"
        } else {
            "Delete nahi hui — Android 11+ par kabhi confirm dialog aata hai, manually delete karo"
        }
    }

    fun setWhatsAppProfilePhoto(context: Context, imageHint: String): String {
        if (!requireAccessibility(context)) return accessibilityMsg()
        val uri = MediaContext.resolve(context, imageHint)
            ?: return "Photo nahi mili — gallery mein pic honi chahiye"
        MediaContext.pin(uri)
        AppLauncher.open(context, "whatsapp")
        PendingActionRunner.scheduleWhatsAppProfilePhoto()
        return "WhatsApp profile picture change kar rahi hoon ✓ screen dekhte raho"
    }

    fun postOnSocial(context: Context, platform: String, caption: String, imageHint: String): String {
        if (!requireAccessibility(context)) return accessibilityMsg()
        val pkg = platforms[platform.lowercase().trim()]
            ?: return "Platform support: facebook, instagram, tiktok"
        if (!AppLauncher.canLaunch(context, pkg)) {
            return "${platform.replaceFirstChar { it.uppercase() }} install nahi hai"
        }
        val uri = if (imageHint.isNotBlank() && imageHint != "none") {
            MediaContext.resolve(context, imageHint)
        } else {
            null
        }
        if (uri == null && imageHint.isNotBlank() && imageHint != "none") {
            return "Photo nahi mili — latest ya naam se bolo"
        }
        MediaContext.pin(uri)

        val launched = shareToApp(context, pkg, caption, uri)
        if (!launched) return "App open nahi hui"

        PendingActionRunner.scheduleSocialPost(platform.lowercase(), caption)
        val hasImg = uri != null
        return buildString {
            append("${platform.replaceFirstChar { it.uppercase() }} par post ")
            if (hasImg) append("photo + ")
            append("text bhej rahi hoon ✓")
            append(" — Post/Share button screen par tap ho sakta hai")
        }
    }

    fun pinLatestPhoto(context: Context): String {
        val uri = GalleryHelper.getLatestImageUri(context)
            ?: return "Gallery mein koi photo nahi mili"
        MediaContext.pin(uri, "latest")
        return "Latest gallery photo select ho gayi ✓ — ab bolo kahan lagani hai"
    }

    private fun shareToApp(context: Context, packageName: String, text: String, imageUri: Uri?): Boolean {
        return try {
            val intent = if (imageUri != null) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }
            intent.setPackage(packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    if (imageUri != null) {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        type = "text/plain"
                    }
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallback, "Share via"))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun requireAccessibility(context: Context): Boolean =
        AccessibilityHelperService.isEnabled(context)

    private fun accessibilityMsg(): String =
        "Ye kaam ke liye Settings → MYRA Accessibility ON karo (human jaisa screen control)"
}
