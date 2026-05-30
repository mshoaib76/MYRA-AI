package com.myra.assistant.util

import android.content.Context
import android.provider.ContactsContract

data class CallerInfo(
    val displayName: String,
    val phoneNumber: String,
    val isSavedContact: Boolean
)

object CallerInfoResolver {

    fun resolve(context: Context, rawNumber: String?): CallerInfo {
        val number = rawNumber?.trim().orEmpty()
        if (number.isBlank()) {
            return CallerInfo(
                displayName = "Unknown",
                phoneNumber = "",
                isSavedContact = false
            )
        }

        val digits = number.filter { it.isDigit() }.takeLast(10)
        if (digits.length < 7) {
            return CallerInfo(displayName = number, phoneNumber = number, isSavedContact = false)
        }

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val phone = it.getString(1)?.filter { c -> c.isDigit() }?.takeLast(10) ?: continue
                if (phone == digits || phone.endsWith(digits) || digits.endsWith(phone)) {
                    val name = it.getString(0)?.trim().orEmpty()
                    if (name.isNotBlank()) {
                        return CallerInfo(
                            displayName = name,
                            phoneNumber = number,
                            isSavedContact = true
                        )
                    }
                }
            }
        }

        return CallerInfo(
            displayName = number,
            phoneNumber = number,
            isSavedContact = false
        )
    }
}
