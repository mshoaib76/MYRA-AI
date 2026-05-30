package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand
import com.myra.assistant.util.AppLauncher

object CommandParser {

    private val CMD_TAG = Regex("""\[\[CMD:([A-Z_]+)\|([^]]*)]]""", RegexOption.IGNORE_CASE)

    fun parse(text: String, primeAliases: List<String> = emptyList()): AppCommand? {
        parseCmdTags(text).firstOrNull()?.let { return it }
        return parseNatural(text, primeAliases)
    }

    fun parseAll(text: String, primeAliases: List<String> = emptyList()): List<AppCommand> {
        val fromTags = parseCmdTags(text)
        if (fromTags.isNotEmpty()) return fromTags
        return listOfNotNull(parseNatural(text, primeAliases))
    }

    fun parseCmdTags(text: String): List<AppCommand> {
        val list = mutableListOf<AppCommand>()
        CMD_TAG.findAll(text).forEach { m ->
            val type = m.groupValues[1].uppercase()
            val rawParam = m.groupValues[2].trim()
            val param = AppLauncher.normalizeQuery(rawParam).ifBlank { rawParam }
            val cmd = when (type) {
                "OPEN_APP" -> AppCommand("OPEN_APP", mapOf("app_name" to param.ifBlank { rawParam }))
                "CLOSE_APP" -> AppCommand("CLOSE_APP")
                "CALL" -> AppCommand("CALL", mapOf("name" to rawParam))
                "SMS" -> {
                    val parts = rawParam.split("|", limit = 2)
                    AppCommand(
                        "SMS",
                        mapOf("name" to parts[0].trim(), "message" to parts.getOrNull(1).orEmpty())
                    )
                }
                "WHATSAPP_MSG" -> {
                    val parts = rawParam.split("|", limit = 2)
                    AppCommand(
                        "WHATSAPP_MSG",
                        mapOf(
                            "name" to parts[0].trim(),
                            "message" to parts.getOrNull(1).orEmpty()
                        )
                    )
                }
                "WHATSAPP_SEND" -> AppCommand("WHATSAPP_SEND")
                "WHATSAPP_NUMBER_MSG" -> {
                    val parts = rawParam.split("|", limit = 2)
                    AppCommand(
                        "WHATSAPP_NUMBER_MSG",
                        mapOf(
                            "number" to parts[0].trim(),
                            "message" to parts.getOrNull(1).orEmpty()
                        )
                    )
                }
                "WHATSAPP_CALL" -> AppCommand("WHATSAPP_CALL", mapOf("name" to rawParam))
                "YOUTUBE_SEARCH" -> AppCommand("YOUTUBE_SEARCH", mapOf("query" to rawParam))
                "GOOGLE_SEARCH" -> AppCommand("GOOGLE_SEARCH", mapOf("query" to rawParam))
                "CHROME_SEARCH" -> AppCommand("CHROME_SEARCH", mapOf("query" to rawParam))
                "MAPS_SEARCH" -> AppCommand("MAPS_SEARCH", mapOf("query" to rawParam))
                "PRIME_CALL" -> AppCommand("PRIME_CALL", mapOf("index" to param.ifBlank { "0" }))
                "PRIME_MSG" -> AppCommand("PRIME_MSG", mapOf("index" to param.ifBlank { "0" }))
                "VOLUME_UP" -> AppCommand("VOLUME_UP")
                "VOLUME_DOWN" -> AppCommand("VOLUME_DOWN")
                "FLASHLIGHT_ON" -> AppCommand("FLASHLIGHT_ON")
                "FLASHLIGHT_OFF" -> AppCommand("FLASHLIGHT_OFF")
                "WIFI_ON" -> AppCommand("WIFI_ON")
                "WIFI_OFF" -> AppCommand("WIFI_OFF")
                "BLUETOOTH_ON" -> AppCommand("BLUETOOTH_ON")
                "BLUETOOTH_OFF" -> AppCommand("BLUETOOTH_OFF")
                "HOME" -> AppCommand("HOME")
                "BACK" -> AppCommand("BACK")
                "ADMIN_PIN" -> AppCommand("ADMIN_PIN", mapOf("pin" to rawParam))
                "ADMIN_PATTERN" -> AppCommand("ADMIN_PATTERN", mapOf("pattern" to rawParam))
                "UNLOCK" -> AppCommand("UNLOCK", mapOf("target" to rawParam))
                "DELETE_PHOTO" -> AppCommand("DELETE_PHOTO", mapOf("hint" to rawParam))
                "WHATSAPP_PROFILE" -> AppCommand("WHATSAPP_PROFILE", mapOf("image" to rawParam))
                "SOCIAL_POST" -> {
                    val parts = rawParam.split("|", limit = 3)
                    AppCommand(
                        "SOCIAL_POST",
                        mapOf(
                            "platform" to parts.getOrNull(0).orEmpty(),
                            "caption" to parts.getOrNull(1).orEmpty(),
                            "image" to parts.getOrNull(2).orEmpty().ifBlank { "latest" }
                        )
                    )
                }
                "PIN_LATEST_PHOTO" -> AppCommand("PIN_LATEST_PHOTO")
                else -> null
            }
            if (cmd != null) list.add(cmd)
        }
        return list
    }

    private fun parseNatural(text: String, primeAliases: List<String>): AppCommand? {
        val t = text.trim()
        if (t.isBlank()) return null
        val lower = t.lowercase()

        parseSimple(lower)?.let { return it }
        parseAdmin(t)?.let { return it }
        parseUnlock(lower, t)?.let { return it }

        if (lower.contains("home") || lower.contains("home screen")) return AppCommand("HOME")
        if (lower.contains("back") || lower.contains("wapis") || lower.contains("peeche") ||
            lower.contains("piche")
        ) return AppCommand("BACK")
        if (looksLikeCloseCommand(lower)) return AppCommand("CLOSE_APP")

        parseSearch(lower, t)?.let { return it }
        parseHumanActions(lower, t)?.let { return it }
        parseWhatsApp(lower, t)?.let { return it }

        if (looksLikeOpenCommand(lower)) {
            val appName = AppLauncher.extractAppNameFromSpeech(t) ?: AppLauncher.normalizeQuery(t)
            if (appName.isNotBlank() && appName.length >= 2) {
                return AppCommand("OPEN_APP", mapOf("app_name" to appName))
            }
        }

        Regex("""(?:call|phone|dial)\s+(.+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("CALL", mapOf("name" to it.groupValues[1].trim()))
        }
        Regex("""(.+?)\s+ko\s+call\s+karo""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("CALL", mapOf("name" to it.groupValues[1].trim()))
        }

        Regex("""(.+?)\s+ko\s+(?:msg|message|sms)\s+(?:bhejo|karo|likho)\s+(.+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return if (lower.contains("whatsapp") || lower.contains("wa ")) {
                AppCommand("WHATSAPP_MSG", mapOf("name" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim()))
            } else {
                AppCommand("SMS", mapOf("name" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim()))
            }
        }

        primeAliases.forEachIndexed { index, alias ->
            if (alias.isNotBlank() && lower.contains(alias.lowercase())) {
                return when {
                    lower.contains("msg") || lower.contains("message") ->
                        AppCommand("PRIME_MSG", mapOf("index" to index.toString()))
                    lower.contains("call") ->
                        AppCommand("PRIME_CALL", mapOf("index" to index.toString()))
                    else -> null
                }
            }
        }

        return null
    }

    private fun parseSearch(lower: String, t: String): AppCommand? {
        if (!lower.contains("search") && !lower.contains("dhundo") && !lower.contains("dhoondo") &&
            !lower.contains("khojo") && !lower.contains("talash")
        ) return null

        val patterns = listOf(
            Regex("""youtube\s+(?:par|pe|py|per|mein|me)\s+(.+?)\s+(?:search|dhundo|dhoondo|khojo)""", RegexOption.IGNORE_CASE),
            Regex("""youtube\s+(?:par|pe|py)\s+(?:search|dhundo)\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(.+?)\s+youtube\s+par\s+(?:search|dhundo)""", RegexOption.IGNORE_CASE),
            Regex("""search\s+(.+?)\s+on\s+youtube""", RegexOption.IGNORE_CASE),
            Regex("""youtube\s+search\s+(.+)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            p.find(t)?.groupValues?.getOrNull(1)?.trim()?.let { q ->
                if (q.length >= 2) return AppCommand("YOUTUBE_SEARCH", mapOf("query" to q))
            }
        }

        if (lower.contains("youtube") && (lower.contains("search") || lower.contains("dhundo"))) {
            val cleaned = t.replace(Regex("""(?i)youtube|search|par|pe|py|karo|khojo|dhundo"""), " ").trim()
            if (cleaned.length >= 2) return AppCommand("YOUTUBE_SEARCH", mapOf("query" to cleaned))
        }

        Regex("""google\s+(?:par\s+)?(?:search\s+)?(.+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("GOOGLE_SEARCH", mapOf("query" to it.groupValues[1].trim()))
        }
        Regex("""chrome\s+(?:par\s+)?(?:search\s+)?(.+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("CHROME_SEARCH", mapOf("query" to it.groupValues[1].trim()))
        }
        Regex("""maps\s+(?:par\s+)?(.+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("MAPS_SEARCH", mapOf("query" to it.groupValues[1].trim()))
        }

        return null
    }

    private fun parseHumanActions(lower: String, t: String): AppCommand? {
        if (lower.contains("latest photo") || lower.contains("ye photo select") ||
            lower.contains("photo select karo")
        ) {
            return AppCommand("PIN_LATEST_PHOTO")
        }

        if ((lower.contains("delete") || lower.contains("remove")) &&
            (lower.contains("photo") || lower.contains("pic") || lower.contains("image"))
        ) {
            val hint = extractPhotoHint(t)
            return AppCommand("DELETE_PHOTO", mapOf("hint" to hint))
        }

        if (lower.contains("whatsapp") && lower.contains("profile") &&
            (lower.contains("photo") || lower.contains("pic") || lower.contains("picture"))
        ) {
            return AppCommand("WHATSAPP_PROFILE", mapOf("image" to extractPhotoHint(t)))
        }

        val socialPlatforms = listOf("facebook", "fb", "instagram", "insta", "tiktok")
        val platform = socialPlatforms.firstOrNull { lower.contains(it) } ?: return null
        val wantsPost = lower.contains("post") || lower.contains("upload") || lower.contains("share") ||
            lower.contains("lagao") || lower.contains("dal") || lower.contains("dalo") ||
            lower.contains("likh") || lower.contains("publish")
        if (!wantsPost) return null

        val caption = extractSocialCaption(t, platform) ?: ""
        val image = extractPhotoHint(t)
        return AppCommand(
            "SOCIAL_POST",
            mapOf("platform" to platform, "caption" to caption, "image" to image)
        )
    }

    private fun extractPhotoHint(text: String): String {
        val lower = text.lowercase()
        if (lower.contains("ye wali") || lower.contains("yeh wali") || lower.contains("this photo")) {
            return "latest"
        }
        Regex("""(?:photo|pic|image)\s+(.+?)(?:\s+(?:delete|lagao|post|profile)|$)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()?.let {
                if (it.length in 2..40) return it
            }
        return "latest"
    }

    private fun extractSocialCaption(text: String, platform: String): String? {
        Regex(
            """(?:likh|text|caption|message)\s+(?:karo|kar|ke|hai)?\s*[:\-]?\s*(.+)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)?.trim()?.let {
            return it.replace(Regex("""(?i)$platform|post|upload|share|lagao|dal"""), "").trim()
        }
        Regex("""post\s+(.+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()?.let {
            return it
        }
        Regex("""['"](.+?)['"]""").find(text)?.groupValues?.get(1)?.trim()?.let { return it }
        return null
    }

    private fun parseWhatsApp(lower: String, t: String): AppCommand? {
        if (!lower.contains("whatsapp") && !lower.contains("watsapp") &&
            !(lower.contains("message") && lower.contains("ko"))
        ) {
            return null
        }

        Regex(
            """whatsapp\s+(?:par\s+)?(?:number\s+)?([+\d][\d\s-]{8,})\s+(?:ko\s+)?(?:message|msg)\s+(.+)""",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return AppCommand(
                "WHATSAPP_NUMBER_MSG",
                mapOf("number" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim())
            )
        }

        Regex(
            """(.+?)\s+ko\s+whatsapp\s+(?:par\s+)?(?:message|msg)\s+(?:karo|bhejo|likho)\s+(.+)""",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return AppCommand("WHATSAPP_MSG", mapOf("name" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim()))
        }

        Regex(
            """whatsapp\s+(?:par\s+)?(.+?)\s+ko\s+(?:message|msg)\s+(.+)""",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return AppCommand("WHATSAPP_MSG", mapOf("name" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim()))
        }

        Regex(
            """(.+?)\s+ko\s+(?:message|msg)\s+(?:karo|bhejo|likho)\s+(.+)""",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            if (lower.contains("whatsapp") || lower.contains("wa")) {
                return AppCommand("WHATSAPP_MSG", mapOf("name" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim()))
            }
        }

        Regex(
            """whatsapp\s+(?:par\s+)?(.+?)\s+ko\s+(.+)\s+bhej""",
            RegexOption.IGNORE_CASE
        ).find(t)?.let {
            return AppCommand("WHATSAPP_MSG", mapOf("name" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim()))
        }

        Regex("""whatsapp\s+(?:karo|kholo|open|chalao)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("OPEN_APP", mapOf("app_name" to "whatsapp"))
        }

        if (lower.contains("whatsapp") && (lower.contains("band") || lower.contains("close"))) {
            return AppCommand("CLOSE_APP")
        }

        return null
    }

    private fun parseAdmin(t: String): AppCommand? {
        Regex("""admin\s+(?:pin\s+)?(\d{4,8})""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("ADMIN_PIN", mapOf("pin" to it.groupValues[1]))
        }
        Regex("""pattern\s+(\d[\d\-,\s]+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("ADMIN_PATTERN", mapOf("pattern" to it.groupValues[1].trim()))
        }
        return null
    }

    private fun parseUnlock(lower: String, t: String): AppCommand? {
        if (!lower.contains("unlock") && !lower.contains("password")) return null
        Regex("""unlock\s+(.+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return AppCommand("UNLOCK", mapOf("target" to it.groupValues[1].trim()))
        }
        return null
    }

    private fun looksLikeOpenCommand(lower: String): Boolean {
        if ((lower.contains("message") || lower.contains("msg")) &&
            (lower.contains("whatsapp") || lower.contains("ko"))
        ) return false
        if (lower.contains("search") || lower.contains("dhundo")) return false
        val verbs = listOf(
            "kholo", "khol", "open", "chalao", "chala", "launch", "start",
            "lagao", "chalu", "on karo", "show", "dekhao"
        )
        return verbs.any { lower.contains(it) }
    }

    private fun looksLikeCloseCommand(lower: String): Boolean {
        return (lower.contains("close") || lower.contains("band")) &&
            (lower.contains("app") || lower.contains("whatsapp") || lower.contains("youtube") ||
                lower.contains("chrome") || lower.contains("current"))
    }

    private fun parseSimple(lower: String): AppCommand? = when {
        lower.contains("volume up") || lower.contains("awaz badhao") -> AppCommand("VOLUME_UP")
        lower.contains("volume down") || lower.contains("awaz kam") -> AppCommand("VOLUME_DOWN")
        (lower.contains("scroll") || lower.contains("scrolling")) &&
            (lower.contains("down") || lower.contains("neeche")) -> AppCommand("SCROLL_DOWN")
        (lower.contains("scroll") || lower.contains("scrolling")) &&
            (lower.contains("up") || lower.contains("upar")) -> AppCommand("SCROLL_UP")
        (lower.contains("torch") || lower.contains("flashlight")) && lower.contains("on") -> AppCommand("FLASHLIGHT_ON")
        (lower.contains("torch") || lower.contains("flashlight")) && lower.contains("off") -> AppCommand("FLASHLIGHT_OFF")
        lower.contains("wifi on") -> AppCommand("WIFI_ON")
        lower.contains("wifi off") -> AppCommand("WIFI_OFF")
        lower.contains("bluetooth on") -> AppCommand("BLUETOOTH_ON")
        lower.contains("bluetooth off") -> AppCommand("BLUETOOTH_OFF")
        else -> null
    }

    fun parseCallDecision(text: String): String? {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) return null
        val acceptWords = listOf(
            "accept", "uthao", "pick up", "pickup", "answer", "yes", "haan", "han",
            "ji", "theek", "ok", "okay", "receive", "lelo", "connect"
        )
        val rejectWords = listOf(
            "reject", "decline", "cut", "cancel", "dismiss", "ignore", "no", "nahi", "na",
            "mat", "ruk", "band", "kaat", "kaat do", "decline karo", "reject karo"
        )
        if (rejectWords.any { lower.contains(it) }) return "REJECT"
        if (acceptWords.any { lower.contains(it) }) return "ACCEPT"
        return null
    }

    fun isCreatorQuestion(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("kis ne banaya") || lower.contains("kisne banaya") ||
            lower.contains("who made you") || lower.contains("banane wala")
    }

    const val CREATOR_ANSWER =
        "Mujhe Muhammad Shoaib ne banaya jo University of Layyah mein Computer Science ka student hain."
}
