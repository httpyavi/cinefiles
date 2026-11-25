package com.example.cinefiles.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import kotlin.math.max

/*
 * Copying a picture from the phone's gallery into the app's own private storage, shared by everything that lets the user pick one
 * (the diary's covers: DiaryImageStore, and the profile photo: AvatarStore). The copy is a small JPEG turned upright, so it never
 * breaks when the original is moved or deleted and no permission to the gallery has to be kept.
 *
 * These touch the disk: call them from a background thread (both stores do, on Dispatchers.IO).
 */

private const val JPEG_QUALITY = 85

/**
 * Reads the picture at [uri] (at most [maxSide] pixels on its longer side, turned the way the photo was taken) and writes it to [target]
 * as a JPEG, replacing any file already there. Returns false when the picture cannot be read or written (not a picture, out of space...).
 */
internal fun Context.copyPictureAsJpeg(uri: Uri, target: File, maxSide: Int): Boolean {
    return try {
        val bitmap = decodeUpright(uri, maxSide) ?: return false
        target.parentFile?.mkdirs()
        target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        bitmap.recycle()
        true
    } catch (e: Exception) {
        false
    }
}

private fun Context.decodeUpright(uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

    val rotation = contentResolver.openInputStream(uri)?.use { stream ->
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
