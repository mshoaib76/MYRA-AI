package com.myra.assistant.util

import android.content.Context
import android.net.Uri

/**
 * Tracks which image the user means by "ye wali photo" / "this picture".
 */
object MediaContext {

    private var pinnedUri: Uri? = null
    private var pinnedLabel: String? = null

    fun pin(uri: Uri?, label: String? = null) {
        pinnedUri = uri
        pinnedLabel = label
    }

    fun getPinned(): Uri? = pinnedUri

    fun resolve(context: Context, hint: String): Uri? {
        val h = hint.trim().lowercase()
        if (h.isNotBlank() && !isGenericHint(h)) {
            GalleryHelper.findByName(context, hint)?.let { return it }
        }
        pinnedUri?.let { return it }
        return GalleryHelper.getLatestImageUri(context)
    }

    private fun isGenericHint(h: String): Boolean {
        return h.isEmpty() ||
            h == "last" || h == "latest" || h == "recent" ||
            h.contains("ye wali") || h.contains("yeh wali") || h.contains("this photo") ||
            h.contains("ye pic") || h.contains("same photo") || h.contains("wahi photo")
    }
}
