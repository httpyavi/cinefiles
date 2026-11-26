package com.example.cinefiles.data

import android.content.Context
import android.net.Uri
import com.example.cinefiles.logic.DiaryCover
import com.example.cinefiles.logic.ImageSource
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the cover pictures from the phone's gallery live after the user adds them to a diary page.
 *
 * A gallery picture is COPIED here, into the app's own private storage (files/diary_images), as a small JPEG (at most
 * [MAX_SIDE] pixels on its longer side, turned upright: data/ImageImport.kt). So the page never breaks when the original is moved
 * or deleted, and no permission to the gallery has to be kept. The diary page only remembers the file NAME (see logic/DiaryText.kt:
 * "local:name.jpg"); the picture bytes are never put in the database.
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
        val name = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        if (context.copyPictureAsJpeg(uri, file(name), MAX_SIDE)) name else null
    }

    /** Deletes the stored pictures called [names] (a name that does not exist is ignored). */
    suspend fun delete(names: Collection<String>) = withContext(Dispatchers.IO) {
        for (name in names) file(name).delete()
    }

    /**
     * Deletes every stored picture that no page uses as its cover. [covers] are the `coverImageUri` texts of all pages. This tidies up after
     * a page was deleted while the app was closed, a crash, and the extra pictures of pages written before a page had just one cover.
     */
    suspend fun deleteUnused(covers: Collection<String>) = withContext(Dispatchers.IO) {
        val used = covers.flatMap { DiaryCover.parseList(it) }.filter { it.source == ImageSource.LOCAL }.map { it.ref }.toSet()
        folder.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
        Unit
    }

    /** Deletes every stored picture (Clear my data). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        folder.listFiles()?.forEach { it.delete() }
        Unit
    }

    private companion object {
        const val FOLDER_NAME = "diary_images"
        const val MAX_SIDE = 1600
    }
}
