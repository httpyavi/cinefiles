package com.example.cinefiles.data

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the user's profile photo lives. The photo the user picks is COPIED into the app's own private storage
 * (files/avatar/profile_avatar.jpg, a small square-ish JPEG: data/ImageImport.kt), replacing the one before it, so it survives
 * a restart and does not depend on the gallery or on any permission. UserSettings remembers that there is one.
 *
 * Every function that touches the disk is a `suspend` function that runs on the IO threads (StrictMode would complain about the
 * main thread). [file] only builds the path.
 */
class AvatarStore(private val context: Context) {

    // The first look at the app's files folder touches the disk, so it is made once on first use
    private val folder: File by lazy { File(context.filesDir, FOLDER_NAME) }

    /** The photo's file (it may not exist: UserSettings knows whether there is a photo). */
    fun file(): File = File(folder, FILE_NAME)

    /** Copies the picture at [uri] over the current profile photo. Returns false (and keeps the old photo) when it cannot be read. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        // Written beside the real file first, so a picture that fails halfway never leaves the user without a photo
        val temp = File(folder, "$FILE_NAME.tmp")
        if (!context.copyPictureAsJpeg(uri, temp, MAX_SIDE)) {
            temp.delete()
            return@withContext false
        }
        val target = file()
        target.delete()
        temp.renameTo(target)
    }

    /** Deletes the profile photo (Remove photo, Clear my data). */
    suspend fun delete() = withContext(Dispatchers.IO) {
        folder.listFiles()?.forEach { it.delete() }
        Unit
    }

    private companion object {
        const val FOLDER_NAME = "avatar"
        const val FILE_NAME = "profile_avatar.jpg"
        const val MAX_SIDE = 512
    }
}
