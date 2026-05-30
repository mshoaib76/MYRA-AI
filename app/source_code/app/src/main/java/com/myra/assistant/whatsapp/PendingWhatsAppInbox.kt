package com.myra.assistant.whatsapp

import com.myra.assistant.model.PrimeContact

object PendingWhatsAppInbox {

    data class Message(
        val prime: PrimeContact,
        val senderLabel: String,
        val body: String,
        val receivedAt: Long = System.currentTimeMillis()
    )

    @Volatile
    var current: Message? = null

    fun set(message: Message) {
        current = message
    }

    fun clear() {
        current = null
    }
}
