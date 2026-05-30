package com.myra.assistant.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ProfileHelper {

    private const val FILE_NAME = "profile_photo.jpg"

    fun photoFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasPhoto(context: Context): Boolean = photoFile(context).exists()

    fun savePhotoFromUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return false
                val scaled = scaleBitmap(bitmap, 512)
                FileOutputStream(photoFile(context)).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                if (scaled !== bitmap) bitmap.recycle()
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun loadPhoto(context: Context): Bitmap? {
        val file = photoFile(context)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun clearPhoto(context: Context) {
        photoFile(context).delete()
    }

    private fun scaleBitmap(source: Bitmap, maxSize: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxSize && h <= maxSize) return source
        val ratio = minOf(maxSize.toFloat() / w, maxSize.toFloat() / h)
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }
}
