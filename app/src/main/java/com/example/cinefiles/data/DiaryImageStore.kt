package com.example.cinefiles.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the pictures from the phone's gallery live after the user adds them to a diary page.
 *
 * A gallery picture is COPIED here, into the app's own private storage (files/diary_images), as a small JPEG (at most
 * [MAX_SIDE] pixels on its longer side, turned upright). So the page never breaks when the original is moved or deleted,
 * and no permission to the gallery has to be kept. The diary page only remembers the file NAME (see logic/DiaryBlocks.kt);
 * the picture bytes are never put in the database.
 *
 * Every function that touches the disk is a `suspend` function that runs on the IO threads (StrictMode would complain
 * about the main thread). The one exception is [file], which only builds a path.
 */
class DiaryImageStore(private val context: Context) {

    // The first look at the app's files folder touches the disk, so it is made once (CineFilesApp does it on a background thread)
    private val folder: File by lazy { File(context.filesDir, FOLDER_NAME) }

    /** Looks the folder up now. Called once at app start on a background thread, so no screen has to wait for the disk. */
    fun prepare() {
        folder
    }

    /** The file for a stored picture's [name] (never a path outside the folder, whatever the name says). */
    fun file(name: String): File = File(folder, File(name).name)

    /** Copies the picture at [uri] into the app's storage and returns its new file name, or null when it cannot be read. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decode(uri) ?: return@withContext null
            folder.mkdirs()
            val name = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            file(name).outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            bitmap.recycle()
            name
        } catch (e: Exception) { // unreadable, not a picture, out of space...
            null
        }
    }

    /** Deletes the stored pictures called [names] (a name that does not exist is ignored). */
    suspend fun delete(names: Collection<String>) = withContext(Dispatchers.IO) {
        for (name in names) file(name).delete()
    }

    /** Deletes every stored picture (Clear my data). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        folder.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** Reads the picture, smaller than [MAX_SIDE], and turned the way the photo was taken. */
    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return bitmap

        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    private companion object {
        const val FOLDER_NAME = "diary_images"
        const val MAX_SIDE = 1600
        const val JPEG_QUALITY = 85
    }
}
