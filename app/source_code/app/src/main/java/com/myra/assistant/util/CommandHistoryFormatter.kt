package com.myra.assistant.util

import com.myra.assistant.model.AppCommand

object CommandHistoryFormatter {

    fun format(cmd: AppCommand): String {
        val p = cmd.params
        return when (cmd.type) {
            "OPEN_APP" -> "Opened app: ${p["app_name"] ?: p.values.firstOrNull() ?: "—"}"
            "CLOSE_APP" -> "Closed current app"
            "BACK" -> "Navigated back"
            "HOME" -> "Went to home screen"
            "WHATSAPP_MSG" -> "WhatsApp to ${p["name"] ?: "?"}: ${p["message"] ?: ""}"
            "WHATSAPP_NUMBER_MSG" -> "WhatsApp message sent"
            "YOUTUBE_SEARCH" -> "YouTube search: ${p["query"] ?: ""}"
            "GOOGLE_SEARCH" -> "Google search: ${p["query"] ?: ""}"
            "CHROME_SEARCH" -> "Chrome search: ${p["query"] ?: ""}"
            "MAPS_SEARCH" -> "Maps search: ${p["query"] ?: ""}"
            "CALL" -> "Call: ${p["number"] ?: p.values.firstOrNull() ?: ""}"
            "SMS" -> "SMS: ${p["number"] ?: ""}"
            "VOLUME_UP" -> "Volume up"
            "VOLUME_DOWN" -> "Volume down"
            "FLASHLIGHT_ON" -> "Flashlight on"
            "FLASHLIGHT_OFF" -> "Flashlight off"
            "SCROLL_UP" -> "Scrolled up"
            "SCROLL_DOWN" -> "Scrolled down"
            "DELETE_PHOTO" -> "Deleted photo"
            "WHATSAPP_PROFILE" -> "Changed WhatsApp profile photo"
            "SOCIAL_POST" -> "Social post on ${p["platform"] ?: "app"}"
            "PIN_LATEST_PHOTO" -> "Selected latest photo"
            else -> "${cmd.type.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} ${p.values.joinToString(" ")}".trim()
        }
    }
}
