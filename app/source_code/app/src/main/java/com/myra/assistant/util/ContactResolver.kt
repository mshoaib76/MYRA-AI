package com.myra.assistant.util

import android.content.Context
import android.provider.ContactsContract

object ContactResolver {

    fun findPhone(context: Context, nameOrNumber: String): String? {
        val raw = nameOrNumber.trim()
        if (raw.isBlank()) return null
        if (raw.any { it.isDigit() }) {
            return normalizeDigits(raw)
        }
        val query = raw.lowercase().replace(Regex("\\s+"), " ").trim()
        
        // Priority: Check Prime Contacts first
        val primeContacts = PrefsHelper.getPrimeContacts(context)
        val primeMatch = primeContacts.firstOrNull { 
            it.name.lowercase().replace(Regex("\\s+"), " ").trim() == query ||
            it.name.lowercase().contains(query) || query.contains(it.name.lowercase())
        }
        if (primeMatch != null) return primeMatch.number
        
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        ) ?: return null

        val matches = mutableListOf<Pair<String, String>>()
        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(0) ?: continue
                val displayName = it.getString(1) ?: continue
                matches.add(displayName to number)
            }
        }

        matches.firstOrNull { it.first.equals(raw, ignoreCase = true) }?.let { return it.second }

        matches.firstOrNull { it.first.lowercase().replace(Regex("\\s+"), " ").trim() == query }
            ?.let { return it.second }

        val words = query.split(" ").filter { it.length > 1 }
        val wordMatches = matches.filter { (name, _) ->
            val n = name.lowercase().replace(Regex("\\s+"), " ").trim()
            words.isNotEmpty() && words.all { w -> n.contains(w) }
        }
        if (wordMatches.size == 1) return wordMatches.first().second
        // If multiple contacts match, don't guess — avoid wrong person.
        if (wordMatches.size > 1) return null

        val loose = matches.filter { (name, _) ->
            val n = name.lowercase().replace(Regex("\\s+"), " ").trim()
            n.contains(query) || query.contains(n)
        }
        if (loose.size == 1) return loose.first().second
        if (loose.size > 1) return null

        return null
    }

    fun normalizeDigits(number: String): String {
        return number.filter { it.isDigit() || it == '+' }
    }

    /** WhatsApp wa.me needs country code without + or leading 0 */
    fun forWhatsApp(number: String): String {
        var d = number.filter { it.isDigit() }
        if (d.startsWith("0") && d.length >= 10) {
            d = "92" + d.drop(1)
        }
        if (d.startsWith("92") && d.length == 12) return d
        return d
    }
}
