package com.hoshino.wenku8reader.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves downloaded books to the public Downloads/Wenku8 folder. */
object FileSaver {

    /** Returns a human readable location, or null on failure. */
    fun saveDownload(context: Context, displayName: String, mime: String,
                     bytes: ByteArray): String? {
        val safeName = sanitize(displayName)
        return if (Build.VERSION.SDK_INT >= 29) {
            saveMediaStore(context, safeName, mime, bytes)
        } else {
            saveAppDir(context, safeName, bytes)
        }
    }

    private fun saveMediaStore(context: Context, name: String, mime: String,
                               bytes: ByteArray): String? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Wenku8"
            )
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        val ok = resolver.openOutputStream(uri)?.use { out -> out.write(bytes) } != null
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return "Downloads/Wenku8/$name"
    }

    private fun saveAppDir(context: Context, name: String, bytes: ByteArray): String? {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "Wenku8"
        )
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, name)
        return try {
            f.writeBytes(bytes)
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitize(name: String): String {
        val s = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_', '.')
        return s.ifEmpty { "novel" }
    }
}
