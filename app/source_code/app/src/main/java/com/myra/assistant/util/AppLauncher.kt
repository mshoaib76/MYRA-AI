package com.myra.assistant.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Opens installed apps — manifest &lt;queries&gt; + launch-intent checks (Android 11+ safe).
 */
object AppLauncher {

    private const val TAG = "AppLauncher"

    data class LaunchableApp(val label: String, val packageName: String)

    private var installedCache: List<LaunchableApp>? = null

    private val aliasPackages: Map<String, List<String>> = mapOf(
        "youtube" to listOf("com.google.android.youtube"),
        "youtub" to listOf("com.google.android.youtube"),
        "yt" to listOf("com.google.android.youtube"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "whats app" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "watsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "wahtsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "wa" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
        "insta" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "fb" to listOf("com.facebook.katana"),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome", "org.mozilla.firefox"),
        "internet" to listOf("com.android.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"),
        "spotify" to listOf("com.spotify.music"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "telegram" to listOf("org.telegram.messenger"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "snapchat" to listOf("com.snapchat.android"),
        "twitter" to listOf("com.twitter.android", "com.twitter.android.lite"),
        "x" to listOf("com.twitter.android"),
        "settings" to listOf("com.android.settings"),
        "setting" to listOf("com.android.settings"),
        "tanzimat" to listOf("com.android.settings"),
        "calculator" to listOf(
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.sec.android.app.popupcalculator",
            "com.miui.calculator"
        ),
        "phone" to listOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer"
        ),
        "dialer" to listOf("com.google.android.dialer", "com.android.dialer"),
        "contacts" to listOf("com.google.android.contacts", "com.android.contacts"),
        "messages" to listOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging"
        ),
        "sms" to listOf("com.google.android.apps.messaging"),
        "camera" to listOf(
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.sec.android.app.camera",
            "com.miui.camera"
        ),
        "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "photos" to listOf("com.google.android.apps.photos"),
        "play store" to listOf("com.android.vending"),
        "playstore" to listOf("com.android.vending"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "easypaisa" to listOf("pk.com.telenor.peasypaisa"),
        "jazzcash" to listOf("com.techlogix.mobilinkcustomer"),
        "paytm" to listOf("net.one97.paytm"),
        "phonepe" to listOf("com.phonepe.app")
    )

    private val fillerWords = setOf(
        "karo", "kholo", "khol", "open", "chalao", "chala", "start", "launch", "app",
        "please", "plz", "mujhe", "mujhay", "mara", "mera", "meri", "mere",
        "ko", "ka", "ki", "ke", "bhai", "yar", "abhi", "hai", "ho", "se", "de", "do",
        "lagao", "laga", "karna", "the", "a", "an", "par", "pe", "mein", "main"
    )

    fun open(context: Context, rawQuery: String): String {
        val query = normalizeQuery(rawQuery)
        if (query.isBlank()) return "App name clear nahi — dubara bolo"

        Log.d(TAG, "OPEN raw='$rawQuery' norm='$query'")

        // 1) Exact alias key
        aliasPackages[query]?.let { pkgs ->
            launchFirst(context, pkgs, query)?.let { return it }
        }

        // 2) Longest alias contained in query (whatsapp in "whatsapp kholo")
        aliasPackages.keys
            .filter { query.contains(it) || it.contains(query) }
            .maxByOrNull { it.length }
            ?.let { hit ->
                aliasPackages[hit]?.let { pkgs ->
                    launchFirst(context, pkgs, hit)?.let { return it }
                }
            }

        return continueSearch(context, query, rawQuery)
    }

    private fun continueSearch(context: Context, query: String, rawQuery: String): String {
        refreshInstalledApps(context)
        val visible = installedCache?.size ?: 0
        Log.d(TAG, "Installed launchers visible: $visible")

        findBestInstalledMatch(query)?.let { app ->
            if (launchPackage(context, app.packageName)) {
                return "Khola: ${app.label} ✓"
            }
        }

        query.split(" ").filter { it.length > 1 }.forEach { word ->
            aliasPackages[word]?.let { pkgs ->
                launchFirst(context, pkgs, word)?.let { return it }
            }
            findBestInstalledMatch(word)?.let { app ->
                if (launchPackage(context, app.packageName)) {
                    return "Khola: ${app.label} ✓"
                }
            }
        }

        // Try raw label search without normalize stripping
        val rawLower = rawQuery.lowercase()
        aliasPackages.keys.filter { rawLower.contains(it) }.maxByOrNull { it.length }?.let { hit ->
            aliasPackages[hit]?.let { pkgs ->
                launchFirst(context, pkgs, hit)?.let { return it }
            }
        }

        val suggestions = suggestApps(query)
        return if (suggestions.isEmpty()) {
            "App launch nahi hui: $rawQuery. Settings → Accessibility ON karo, phir dubara bolo."
        } else {
            "Shayad ye app ho: ${suggestions.joinToString(", ")} — exact naam bolo."
        }
    }

    private fun launchFirst(context: Context, packages: List<String>, displayName: String): String? {
        for (pkg in packages) {
            if (launchPackage(context, pkg)) {
                val label = getAppLabel(context, pkg) ?: displayName
                return "Khola: $label ✓"
            }
            Log.d(TAG, "Launch failed for $pkg")
        }
        return null
    }

    fun extractAppNameFromSpeech(text: String): String? {
        val lower = text.lowercase()
        aliasPackages.keys.filter { lower.contains(it) }.maxByOrNull { it.length }?.let { return it }
        val patterns = listOf(
            Regex("""(?:open|kholo|khol|chalao|chala|launch|start)\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(.+?)\s+(?:kholo|khol|open|chalao|karo)""", RegexOption.IGNORE_CASE),
            Regex("""(?:app|application)\s+(.+?)\s+(?:kholo|open)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            p.find(text)?.groupValues?.getOrNull(1)?.let {
                val n = normalizeQuery(it)
                if (n.length >= 2) return n
            }
        }
        return null
    }

    fun normalizeQuery(raw: String): String {
        var s = raw.lowercase().trim()
        s = s.replace(Regex("""\[\[CMD:[^]]+]]"""), "")
        s = s.replace(Regex("""[^a-z0-9\s]"""), " ")
        fillerWords.forEach { w ->
            s = s.replace(Regex("""\b${Regex.escape(w)}\b"""), " ")
        }
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    private fun refreshInstalledApps(context: Context) {
        val pm = context.packageManager
        val list = mutableListOf<LaunchableApp>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        val seen = mutableSetOf<String>()
        for (resolve in resolves) {
            val pkg = resolve.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            try {
                val label = resolve.loadLabel(pm).toString()
                list.add(LaunchableApp(label, pkg))
            } catch (_: Exception) {
            }
        }
        installedCache = list.sortedBy { it.label.lowercase() }
        Log.d(TAG, "Cached ${list.size} launchable apps")
    }

    fun invalidateCache() {
        installedCache = null
    }

    fun warmCache(context: Context) {
        refreshInstalledApps(context)
    }

    /** True if we can get a launch intent (works when getPackageInfo is blocked). */
    fun canLaunch(context: Context, packageName: String): Boolean {
        return context.packageManager.getLaunchIntentForPackage(packageName) != null
    }

    private fun getAppLabel(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun launchPackage(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        try {
            var intent = pm.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                val main = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                }
                val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        main,
                        PackageManager.ResolveInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(main, 0)
                }
                val first = resolves.firstOrNull() ?: return false
                intent = Intent(main).setClassName(
                    first.activityInfo.packageName,
                    first.activityInfo.name
                )
            }
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            context.startActivity(intent)
            Log.d(TAG, "Launched $packageName OK")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Launch error $packageName", e)
            return false
        }
    }

    private fun findBestInstalledMatch(query: String): LaunchableApp? {
        val apps = installedCache ?: return null
        val q = query.lowercase()
        apps.firstOrNull { it.label.lowercase() == q }?.let { return it }
        apps.firstOrNull { it.label.lowercase().contains(q) || q.contains(it.label.lowercase()) }
            ?.let { return it }
        apps.firstOrNull { it.packageName.lowercase().contains(q.replace(" ", "")) }?.let { return it }
        return apps.map { it to similarity(q, it.label.lowercase()) }
            .filter { it.second > 0.45 }
            .maxByOrNull { it.second }?.first
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.9
        val common = a.toSet().intersect(b.toSet()).size
        return common.toDouble() / maxOf(a.length, b.length, 1)
    }

    private fun suggestApps(query: String): List<String> =
        installedCache?.map { it.label to similarity(query, it.label.lowercase()) }
            ?.filter { it.second > 0.25 }
            ?.sortedByDescending { it.second }
            ?.take(3)
            ?.map { it.first } ?: emptyList()

    fun testOpenYoutube(context: Context): String = open(context, "youtube")

    fun testOpenWhatsapp(context: Context): String = open(context, "whatsapp")
}
