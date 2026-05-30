package com.myra.assistant.personalization

import android.content.Context
import com.myra.assistant.util.PrefsHelper
import org.json.JSONArray
import java.util.Calendar

/**
 * Advanced user personalization — feeds MYRA's voice personality and memory.
 */
object PersonalizationStore {

    private const val PREFS = "myra_personalization"

    const val KEY_NICKNAME = "nickname"
    const val KEY_MYRA_NICKNAME = "myra_nickname"
    const val KEY_CITY = "city"
    const val KEY_HOBBIES = "hobbies"
    const val KEY_BIRTHDAY = "birthday"
    const val KEY_LANGUAGE = "language_style"
    const val KEY_HUMOR = "humor_level"
    const val KEY_ENERGY = "energy_level"
    const val KEY_LENGTH = "response_length"
    const val KEY_MEMORIES = "memories_json"
    const val KEY_STREAK = "chat_streak"
    const val KEY_LAST_CHAT_DAY = "last_chat_day"
    const val KEY_TOTAL_CHATS = "total_sessions"
    const val LANG_ROMAN_URDU = "roman_urdu"
    const val LANG_HINGLISH = "hinglish"
    const val LANG_ENGLISH = "english"

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getNickname(context: Context): String =
        prefs(context).getString(KEY_NICKNAME, "")?.trim().orEmpty()

    fun getMyraNickname(context: Context): String =
        prefs(context).getString(KEY_MYRA_NICKNAME, "MYRA")?.trim().orEmpty().ifBlank { "MYRA" }

    fun getCity(context: Context): String =
        prefs(context).getString(KEY_CITY, "") ?: ""

    fun getHobbies(context: Context): String =
        prefs(context).getString(KEY_HOBBIES, "") ?: ""

    fun getBirthday(context: Context): String =
        prefs(context).getString(KEY_BIRTHDAY, "") ?: ""

    fun getLanguageStyle(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, LANG_ROMAN_URDU) ?: LANG_ROMAN_URDU

    fun getHumorLevel(context: Context): Int =
        prefs(context).getInt(KEY_HUMOR, 2).coerceIn(0, 3)

    fun getEnergyLevel(context: Context): Int =
        prefs(context).getInt(KEY_ENERGY, 2).coerceIn(0, 3)

    fun getResponseLength(context: Context): Int =
        prefs(context).getInt(KEY_LENGTH, 1).coerceIn(0, 2)

    fun getMemories(context: Context): List<String> {
        val json = prefs(context).getString(KEY_MEMORIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMemories(context: Context, lines: List<String>) {
        val arr = JSONArray()
        lines.filter { it.isNotBlank() }.take(20).forEach { arr.put(it.trim()) }
        prefs(context).edit().putString(KEY_MEMORIES, arr.toString()).apply()
    }

    fun getStreak(context: Context): Int = prefs(context).getInt(KEY_STREAK, 0)

    fun getTotalSessions(context: Context): Int = prefs(context).getInt(KEY_TOTAL_CHATS, 0)

    fun recordSession(context: Context) {
        val p = prefs(context)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val key = year * 1000 + today
        val last = p.getInt(KEY_LAST_CHAT_DAY, -1)
        var streak = p.getInt(KEY_STREAK, 0)
        streak = when {
            last == key -> streak
            last == key - 1 || last == key + 364 -> streak + 1
            else -> 1
        }
        p.edit()
            .putInt(KEY_LAST_CHAT_DAY, key)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_TOTAL_CHATS, p.getInt(KEY_TOTAL_CHATS, 0) + 1)
            .apply()
    }

    fun displayName(context: Context): String {
        val nick = getNickname(context)
        val name = PrefsHelper.getUserName(context)
        return nick.ifBlank { name }
    }

    fun buildPersonalizationBlock(context: Context): String {
        val name = PrefsHelper.getUserName(context)
        val nick = getNickname(context)
        val myraNick = getMyraNickname(context)
        val city = getCity(context)
        val hobbies = getHobbies(context)
        val bday = getBirthday(context)
        val memories = getMemories(context)
        val streak = getStreak(context)
        val humor = humorLabel(getHumorLevel(context))
        val energy = energyLabel(getEnergyLevel(context))
        val length = lengthLabel(getResponseLength(context))
        val lang = languageLabel(getLanguageStyle(context))

        val sb = StringBuilder()
        sb.appendLine("PERSONALIZATION (follow closely):")
        sb.appendLine("- User legal name: $name")
        if (nick.isNotBlank()) sb.appendLine("- Call user: $nick (preferred)")
        sb.appendLine("- User may call you: $myraNick")
        if (city.isNotBlank()) sb.appendLine("- City: $city")
        if (hobbies.isNotBlank()) sb.appendLine("- Hobbies: $hobbies")
        if (bday.isNotBlank()) sb.appendLine("- Birthday: $bday (wish on that date)")
        sb.appendLine("- Language style: $lang")
        sb.appendLine("- Humor: $humor | Energy: $energy | Reply length: $length")
        sb.appendLine("- Chat streak: $streak days — encourage warmly")
        if (memories.isNotEmpty()) {
            sb.appendLine("- Remember about user:")
            memories.forEach { sb.appendLine("  • $it") }
        }
        return sb.toString().trimEnd()
    }

    private fun humorLabel(l: Int) = when (l) {
        0 -> "serious, minimal jokes"
        1 -> "light jokes sometimes"
        2 -> "funny, playful"
        else -> "very funny, roast gently with love"
    }

    private fun energyLabel(l: Int) = when (l) {
        0 -> "calm, soft"
        1 -> "balanced"
        2 -> "energetic, excited"
        else -> "hyper enthusiastic"
    }

    private fun lengthLabel(l: Int) = when (l) {
        0 -> "1 short sentence"
        1 -> "2-3 sentences"
        else -> "3-4 sentences max"
    }

    private fun languageLabel(l: String) = when (l) {
        LANG_ENGLISH -> "English mainly"
        LANG_HINGLISH -> "Hinglish mix"
        else -> "Roman Urdu (Latin script) default"
    }
}
