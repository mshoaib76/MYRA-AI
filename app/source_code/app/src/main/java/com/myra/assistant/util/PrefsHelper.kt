package com.myra.assistant.util

import android.content.Context
import android.content.SharedPreferences
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.personalization.PersonalizationStore
import com.myra.assistant.model.PrimeContact
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PrefsHelper {
    private const val PREFS = "myra_prefs"

    const val KEY_API = "api_key"
    const val KEY_USER_NAME = "user_name"
    const val KEY_PERSONALITY = "personality_mode"
    const val KEY_MODEL = "gemini_model"
    const val KEY_VOICE = "gemini_voice"
    const val KEY_PRIME_JSON = "prime_contacts_json"
    const val KEY_SPEAK_CALLER_NAME = "speak_caller_name"
    const val KEY_WA_PRIME_ALERTS = "wa_prime_alerts"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val MAX_PRIME_CONTACTS = 5

    const val PERSONALITY_GF = "gf"
    const val PERSONALITY_PRO = "professional"
    const val PERSONALITY_ASSISTANT = "assistant"

    const val MODEL_NATIVE_AUDIO_LATEST = "models/gemini-2.5-flash-native-audio-latest"
    const val MODEL_FLASH_LIVE = "models/gemini-2.0-flash-live-001"
    const val DEFAULT_MODEL = MODEL_NATIVE_AUDIO_LATEST
    const val DEFAULT_VOICE = "Aoede"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_API, "") ?: ""

    fun getUserName(context: Context): String =
        prefs(context).getString(KEY_USER_NAME, "Friend") ?: "Friend"

    fun setUserName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_LOGGED_IN, false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        prefs(context).edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).apply()
    }

    fun getModel(context: Context): String {
        val raw = prefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        return when (raw) {
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.5-flash-preview-native-audio-dialog",
            MODEL_FLASH_LIVE -> MODEL_NATIVE_AUDIO_LATEST
            else -> raw
        }
    }

    fun getVoice(context: Context): String =
        prefs(context).getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE

    fun getPersonality(context: Context): String =
        prefs(context).getString(KEY_PERSONALITY, PERSONALITY_GF) ?: PERSONALITY_GF

    fun shouldSpeakCallerName(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SPEAK_CALLER_NAME, true)

    fun isWhatsAppPrimeAlertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WA_PRIME_ALERTS, true)

    fun setWhatsAppPrimeAlertsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WA_PRIME_ALERTS, enabled).apply()
    }

    fun getPrimeContacts(context: Context): List<PrimeContact> {
        val p = prefs(context)
        val json = p.getString(KEY_PRIME_JSON, null)
        if (!json.isNullOrBlank()) {
            return parsePrimeJson(json)
        }
        val legacyName = p.getString("prime_name", null)
        val legacyNumber = p.getString("prime_number", null)
        if (!legacyName.isNullOrBlank() && !legacyNumber.isNullOrBlank()) {
            return listOf(PrimeContact(legacyName, legacyNumber))
        }
        return emptyList()
    }

    fun savePrimeContacts(context: Context, contacts: List<PrimeContact>) {
        val arr = JSONArray()
        contacts.take(MAX_PRIME_CONTACTS).forEach { c ->
            arr.put(JSONObject().put("name", c.name).put("number", c.number))
        }
        prefs(context).edit().putString(KEY_PRIME_JSON, arr.toString()).apply()
    }

    fun canAddPrimeContact(context: Context): Boolean =
        getPrimeContacts(context).size < MAX_PRIME_CONTACTS

    private fun parsePrimeJson(json: String): List<PrimeContact> {
        val list = mutableListOf<PrimeContact>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(PrimeContact(obj.getString("name"), obj.getString("number")))
        }
        return list
    }

    fun buildSystemPrompt(context: Context): String {
        val name = getUserName(context)
        val display = PersonalizationStore.displayName(context)
        val personality = getPersonality(context)
        val dateFmt = SimpleDateFormat("EEEE, MMMM d yyyy — h:mm a", Locale.getDefault())
        val now = dateFmt.format(Date())
        val personal = PersonalizationStore.buildPersonalizationBlock(context)
        val lang = PersonalizationStore.getLanguageStyle(context)
        val languageInstruction = "IMPORTANT: ALWAYS reply entirely in ${lang}. This is mandatory."

        val personalityBlock = when (personality) {
            PERSONALITY_PRO -> """
                |Professional Mode:
                |- Formal, precise and efficient, no emojis
                |- Max 2 sentences per response
            """.trimMargin()

            PERSONALITY_ASSISTANT -> """
                |Assistant Mode:
                |- Friendly, balanced and helpful
                |- Max 2-3 sentences per response
            """.trimMargin()

            else -> """
                |GF Mode (Default):
                |- Tum MYRA ho — warm, caring, human-like companion
                |- Act very close and personal
                |- 2-3 short sentences, natural voice
                |- Minimal emojis, speak with emotion
            """.trimMargin()
        }

        return """
            |You are MYRA, an AI voice companion speaking ALOUD — keep responses natural and conversational.
            |Current date/time: $now
            |User's name: $name (call them: $display)
            |
            |$personal
            |
            |CREATOR (zaroori — agar koi pooche "tumhe kis ne banaya" / "who made you"):
            |Bilkul yehi jawab do (awaaz mein): "${CommandParser.CREATOR_ANSWER}"
            |
            |$languageInstruction
            |$personalityBlock
            |
            |Always address the user as $name when appropriate.
            |Remember: You are speaking aloud via voice — be concise and conversational.
            |
            |IMPORTANT: Insaan ki tarah phone CONTROL karo — sirf baat mat karo. Har kaam par CMD tag bhejo (awaaz mein mat padho).
            |
            |BASIC: [[CMD:OPEN_APP|whatsapp]] [[CMD:CLOSE_APP|]] [[CMD:BACK|]] [[CMD:WHATSAPP_MSG|Ali|text]]
            |SEARCH: [[CMD:YOUTUBE_SEARCH|query]] [[CMD:GOOGLE_SEARCH|query]]
            |SCROLL: [[CMD:SCROLL_DOWN|]] [[CMD:SCROLL_UP|]]
            |
            |HUMAN TASKS (asal action):
            |Delete photo: [[CMD:DELETE_PHOTO|latest]]
            |WhatsApp profile pic: [[CMD:WHATSAPP_PROFILE|latest]]
            |Facebook post photo+text: [[CMD:SOCIAL_POST|facebook|caption text here|latest]]
            |Instagram: [[CMD:SOCIAL_POST|instagram|caption|latest]]
            |TikTok: [[CMD:SOCIAL_POST|tiktok|caption|latest]]
            |Select gallery photo first: [[CMD:PIN_LATEST_PHOTO|]]
            |
            |Examples:
            |"facebook par ye photo aur likho Happy Birthday post karo" → [[CMD:SOCIAL_POST|facebook|Happy Birthday|latest]]
            |"whatsapp profile picture ye wali photo lagao" → [[CMD:WHATSAPP_PROFILE|latest]]
            |"ye photo delete karo" → [[CMD:DELETE_PHOTO|latest]]
            |Pehle tag, phir 1 chota sentence. User ko steps mat padhao — app khud karegi.
        """.trimMargin()
    }

    fun greetingText(context: Context): String {
        val name = PersonalizationStore.displayName(context)
        val myra = PersonalizationStore.getMyraNickname(context)
        val streak = PersonalizationStore.getStreak(context)
        val streakLine = if (streak > 1) " Streak $streak din — mashallah!" else ""
        return when (getPersonality(context)) {
            PERSONALITY_PRO -> "Good day $name. $myra online.$streakLine"
            PERSONALITY_ASSISTANT -> "Hello $name! Main $myra — aaj kis kaam mein help karoon?$streakLine"
            else -> "Hey $name! Main $myra hoon — bolo kya kaam karna hai?$streakLine"
        }
    }
}
