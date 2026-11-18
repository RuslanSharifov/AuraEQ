package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.security.MessageDigest

internal class ArtworkCache(context: Context) {
    private val directory = File(context.cacheDir, "album_art")
    private val lock = Any()

    fun contains(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        return parsed.scheme == "file" && parsed.path?.let { File(it).length() > 0 } == true
    }

    fun store(source: String, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 768) {
            options.inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        try {
            val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return synchronized(lock) {
                directory.mkdirs()
                val file = File(directory, "$hash.jpg")
                val atomic = AtomicFile(file)
                val output = atomic.startWrite()
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
                    atomic.finishWrite(output)
                } catch (e: Exception) {
                    atomic.failWrite(output)
                    throw e
                }
                Uri.fromFile(file).toString()
            }
        } catch (e: Exception) {
            Log.w("AuraArtwork", "Could not cache embedded artwork", e)
            return null
        } finally {
            bitmap.recycle()
        }
    }

    fun clear() = synchronized(lock) {
        directory.deleteRecursively()
    }
}
