package com.myra.assistant.whatsapp

object WhatsAppInboxCommandParser {

    sealed class Action {
        /** User wants message read aloud */
        object ReadAloud : Action()
        /** User declined — do nothing */
        object DeclineRead : Action()
        /** Read message then send reply */
        data class ReadAndReply(val replyText: String) : Action()
        /** Reply only (no read) */
        data class Reply(val text: String) : Action()
        object Dismiss : Action()
    }

    fun parse(spoken: String): Action? {
        val lower = spoken.lowercase().trim()
        if (lower.isBlank()) return null

        val dismissWords = listOf("band", "close", "dismiss", "chhod", "baad mein", "ruk", "theek")
        if (dismissWords.any { lower == it || lower.startsWith("$it ") }) return Action.Dismiss

        val declineReadPhrases = listOf(
            "read na", "mat parho", "nahi parho", "na parho", "mat sunao", "nahi sunao",
            "skip", "rehne do", "chor do", "nahi chahiye", "mat batao"
        )
        if (declineReadPhrases.any { lower.contains(it) }) return Action.DeclineRead

        parseReadAndReply(spoken, lower)?.let { return it }

        val replyOnly = parseReplyText(spoken, lower)
        if (replyOnly != null && looksLikeReplyOnly(lower)) {
            return Action.Reply(replyOnly)
        }

        if (isReadCommand(lower)) return Action.ReadAloud

        replyOnly?.let { return Action.Reply(it) }

        return null
    }

    private fun parseReadAndReply(spoken: String, lower: String): Action.ReadAndReply? {
        val patterns = listOf(
            Regex("""(?:read|parh|parho)\s+(?:kro|kar|ke|kr)\s*(?:aur|or|ky|ke)?\s*(?:reply|jawab|bhej|send)\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:parh ke|read kr ke|read kar ke)\s+(?:reply|jawab|bhej)\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:reply|jawab)\s+(.+)\s+(?:bhej do|bhejo)\s*(?:aur)?\s*(?:read|parh)?""", RegexOption.IGNORE_CASE),
            Regex("""(?:read|parh)\s+(?:kro|kar)\s+(.+)\s+(?:reply|jawab|bhej)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(spoken) ?: continue
            val text = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (text.length >= 1) return Action.ReadAndReply(text)
        }
        if (lower.contains("read") && lower.contains("reply")) {
            parseReplyText(spoken, lower)?.let { return Action.ReadAndReply(it) }
        }
        if ((lower.contains("parh") || lower.contains("read")) &&
            (lower.contains("reply") || lower.contains("jawab") || lower.contains("bhej"))
        ) {
            parseReplyText(spoken, lower)?.let { return Action.ReadAndReply(it) }
        }
        return null
    }

    private fun parseReplyText(spoken: String, lower: String): String? {
        val patterns = listOf(
            Regex("""(?:reply|jawab|jawab do|reply do|bhej do|bhejo|likho|keh do|bolo|send)\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(.+)\s+(?:bhej do|bhejo|reply karo|jawab do)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(spoken) ?: continue
            val msg = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (msg.length >= 1) return msg
        }
        if (lower.startsWith("reply ") || lower.startsWith("jawab ")) {
            return spoken.substringAfter(' ').trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun looksLikeReplyOnly(lower: String): Boolean {
        return lower.startsWith("reply") || lower.startsWith("jawab") ||
            lower.contains("bhej do") || lower.contains("bhejo")
    }

    private fun isReadCommand(lower: String): Boolean {
        if (declineReadPhrases().any { lower.contains(it) }) return false
        val readPhrases = listOf(
            "read kro", "read kar", "read kr", "parho", "parh ke", "parh do",
            "sunao", "suna do", "read", "batao", "message sunao"
        )
        return readPhrases.any { lower.contains(it) }
    }

    private fun declineReadPhrases() = listOf("read na", "mat parho", "nahi parho")
}
