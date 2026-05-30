package com.myra.assistant.util

import android.content.Context
import com.myra.assistant.model.PrimeContact

object PrimeContactMatcher {

    fun match(context: Context, senderTitle: String): PrimeContact? {
        val title = cleanSenderTitle(senderTitle)
        if (title.isBlank()) return null
        val titleNorm = normalizeName(title)
        val titleDigits = digitsLast10(title)

        for (prime in PrefsHelper.getPrimeContacts(context)) {
            val primeNorm = normalizeName(prime.name)
            if (primeNorm == titleNorm) return prime
            if (primeNorm.isNotBlank() && (titleNorm.contains(primeNorm) || primeNorm.contains(titleNorm))) {
                return prime
            }
            val primeDigits = digitsLast10(prime.number)
            if (primeDigits.isNotBlank() && titleDigits.isNotBlank() &&
                (primeDigits == titleDigits || titleDigits.endsWith(primeDigits) || primeDigits.endsWith(titleDigits))
            ) {
                return prime
            }
        }
        return null
    }

    fun cleanSenderTitle(raw: String): String {
        var t = raw.trim()
        t = t.replace(Regex("""\s*\(\d+\s*(new )?messages?\)""", RegexOption.IGNORE_CASE), "").trim()
        t = t.replace(Regex("""\s*:\s*\d+\s*(new )?messages?""", RegexOption.IGNORE_CASE), "").trim()
        return t
    }

    private fun normalizeName(name: String): String =
        name.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun digitsLast10(value: String): String =
        value.filter { it.isDigit() }.takeLast(10)
}
