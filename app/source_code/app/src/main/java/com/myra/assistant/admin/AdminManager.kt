package com.myra.assistant.admin

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Admin PIN/pattern + saved unlock secrets (app lock passwords user stores in MYRA).
 */
object AdminManager {

    private const val ADMIN_PREFS = "myra_admin_secure"
    private const val KEY_PIN_HASH = "admin_pin_hash"
    private const val KEY_PATTERN = "admin_pattern"
    private const val KEY_VAULT = "vault_json"
    private const val KEY_ADMIN_ENABLED = "admin_enabled"

    private var sessionUntilMs = 0L
    private const val SESSION_MS = 10 * 60 * 1000L

    data class VaultEntry(
        val label: String,
        val secret: String,
        val usePattern: Boolean
    )

    private fun securePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ADMIN_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences(ADMIN_PREFS, Context.MODE_PRIVATE)
        }
    }

    fun isAdminEnabled(context: Context): Boolean =
        securePrefs(context).getBoolean(KEY_ADMIN_ENABLED, false)

    fun setAdminEnabled(context: Context, enabled: Boolean) {
        securePrefs(context).edit().putBoolean(KEY_ADMIN_ENABLED, enabled).apply()
    }

    fun setAdminPin(context: Context, pin: String) {
        if (pin.length < 4) return
        securePrefs(context).edit()
            .putString(KEY_PIN_HASH, hash(pin))
            .putBoolean(KEY_ADMIN_ENABLED, true)
            .apply()
    }

    fun setAdminPattern(context: Context, pattern: String) {
        val normalized = pattern.filter { it.isDigit() || it == '-' || it == ',' }
            .replace(",", "-")
        securePrefs(context).edit().putString(KEY_PATTERN, normalized).apply()
    }

    fun getAdminPattern(context: Context): String =
        securePrefs(context).getString(KEY_PATTERN, "") ?: ""

    fun hasAdminPin(context: Context): Boolean {
        val hash = securePrefs(context).getString(KEY_PIN_HASH, null)
        return !hash.isNullOrBlank()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        if (!isAdminEnabled(context)) return true
        val stored = securePrefs(context).getString(KEY_PIN_HASH, null) ?: return false
        if (hash(pin) == stored) {
            sessionUntilMs = System.currentTimeMillis() + SESSION_MS
            return true
        }
        return false
    }

    fun verifyPattern(context: Context, pattern: String): Boolean {
        if (!isAdminEnabled(context)) return true
        val stored = getAdminPattern(context)
        if (stored.isBlank()) return false
        val a = normalizePattern(pattern)
        val b = normalizePattern(stored)
        if (a == b) {
            sessionUntilMs = System.currentTimeMillis() + SESSION_MS
            return true
        }
        return false
    }

    fun isSessionActive(): Boolean =
        System.currentTimeMillis() < sessionUntilMs

    fun clearSession() {
        sessionUntilMs = 0L
    }

    fun extendSession() {
        if (isSessionActive()) {
            sessionUntilMs = System.currentTimeMillis() + SESSION_MS
        }
    }

    fun saveVault(context: Context, entries: List<VaultEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("label", e.label)
                    .put("secret", e.secret)
                    .put("usePattern", e.usePattern)
            )
        }
        securePrefs(context).edit().putString(KEY_VAULT, arr.toString()).apply()
    }

    fun getVault(context: Context): List<VaultEntry> {
        val json = securePrefs(context).getString(KEY_VAULT, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<VaultEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    VaultEntry(
                        o.getString("label"),
                        o.getString("secret"),
                        o.optBoolean("usePattern", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findVaultSecret(context: Context, labelQuery: String): VaultEntry? {
        val q = labelQuery.lowercase().trim()
        return getVault(context).firstOrNull {
            it.label.lowercase().contains(q) || q.contains(it.label.lowercase())
        }
    }

    private fun normalizePattern(p: String): String =
        p.filter { it.isDigit() }.chunked(1).joinToString("-")

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
