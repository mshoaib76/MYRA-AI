package com.myra.assistant.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

object GalleryHelper {

    private const val TAG = "GalleryHelper"

    fun getLatestImageUri(context: Context): Uri? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val name = c.getString(1)
                Log.d(TAG, "Latest image: $name id=$id")
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    fun findByName(context: Context, query: String): Uri? {
        val q = query.lowercase().trim()
        if (q.isBlank()) return null
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            var best: Pair<Uri, Int>? = null
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)?.lowercase() ?: continue
                val score = when {
                    name == q -> 100
                    name.contains(q) -> 80
                    q.split(" ").all { name.contains(it) } -> 60
                    else -> 0
                }
                if (score > 0) {
                    val uri = ContentUris.withAppendedId(collection, id)
                    if (best == null || score > best!!.second) best = uri to score
                }
            }
            return best?.first
        }
        return null
    }

    fun deleteImage(context: Context, uri: Uri): Boolean {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: SecurityException) {
            Log.e(TAG, "Delete permission", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Delete error", e)
            false
        }
    }
}
