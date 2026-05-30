package com.myra.assistant.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myra.assistant.service.AccessibilityHelperService
import com.myra.assistant.service.PendingActionRunner

/**
 * Performs real device actions (search, WhatsApp send, etc.) — not just voice replies.
 */
object AppActions {

    fun youtubeSearch(context: Context, query: String): String {
        if (query.isBlank()) return "Kya search karun? naam bolo"
        val encoded = Uri.encode(query.trim())
        val uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        val pkg = "com.google.android.youtube"
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (AppLauncher.canLaunch(context, pkg)) setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "YouTube par search ho rahi: $query ✓"
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "YouTube search browser mein ✓"
            } catch (e2: Exception) {
                "YouTube search fail: ${e2.message}"
            }
        }
    }

    fun googleSearch(context: Context, query: String): String {
        if (query.isBlank()) return "Search text bolo"
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query.trim())}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Google search: $query ✓"
        } catch (e: Exception) {
            "Search fail: ${e.message}"
        }
    }

    fun chromeSearch(context: Context, query: String): String {
        if (query.isBlank()) return "Kya search karun?"
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query.trim())}")
        val pkg = "com.android.chrome"
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (AppLauncher.canLaunch(context, pkg)) setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Chrome search: $query ✓"
        } catch (e: Exception) {
            googleSearch(context, query)
        }
    }

    fun openWhatsAppChat(context: Context, phoneWa: String, message: String, autoSend: Boolean): String {
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
            AppLauncher.canLaunch(context, it)
        } ?: return "WhatsApp install nahi hai"

        if (phoneWa.length < 8) return "Sahi number nahi mila"

        val textEncoded = Uri.encode(message)
        val url = if (message.isBlank()) {
            "https://wa.me/$phoneWa"
        } else {
            "https://wa.me/$phoneWa?text=$textEncoded"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            if (message.isNotBlank() && autoSend) {
                if (!AccessibilityHelperService.isEnabled(context)) {
                    return "Chat khuli — message likha hai. Send ke liye Accessibility ON karo, phir dubara bolo 'send karo'"
                }
                PendingActionRunner.scheduleWhatsAppSend(attempts = 5)
                "WhatsApp message bhej rahi hoon ✓"
            } else if (message.isNotBlank()) {
                "WhatsApp chat khuli, message ready ✓"
            } else {
                "WhatsApp khola ✓"
            }
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            if (message.isNotBlank() && autoSend) {
                PendingActionRunner.scheduleWhatsAppSend(attempts = 5)
            }
            "WhatsApp khola ✓"
        }
    }

    fun mapsSearch(context: Context, query: String): String {
        if (query.isBlank()) return "Location bolo"
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (!AppLauncher.canLaunch(context, "com.google.android.apps.maps")) {
                intent.setPackage(null)
            }
            context.startActivity(intent)
            "Maps: $query ✓"
        } catch (e: Exception) {
            "Maps fail: ${e.message}"
        }
    }
}
