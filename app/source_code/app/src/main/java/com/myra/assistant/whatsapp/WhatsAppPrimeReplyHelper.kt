package com.myra.assistant.whatsapp

import android.content.Context
import com.myra.assistant.model.PrimeContact
import com.myra.assistant.util.AppActions
import com.myra.assistant.util.ContactResolver

object WhatsAppPrimeReplyHelper {

    fun sendReply(context: Context, prime: PrimeContact, message: String): String {
        val phone = ContactResolver.forWhatsApp(
            ContactResolver.normalizeDigits(prime.number)
        )
        if (phone.length < 10) return "Prime contact number sahi nahi"
        return AppActions.openWhatsAppChat(
            context = context,
            phoneWa = phone,
            message = message.trim(),
            autoSend = true
        )
    }
}
