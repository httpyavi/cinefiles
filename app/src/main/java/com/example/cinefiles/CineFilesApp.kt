package com.example.cinefiles

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.example.cinefiles.data.AppDatabase
import com.example.cinefiles.data.DiaryImageStore
import com.example.cinefiles.data.MovieRepository
import com.example.cinefiles.data.TmdbApi
import com.example.cinefiles.data.UserSettings
import kotlin.concurrent.thread

/**
 * The Application class. Android creates it once when the app process starts, before any screen.
 *
 * Things there must be only ONE of live here: the database, the TMDB API (inside the repository), the achievements
 * and the user's settings.
 */
class CineFilesApp : Application() {

    /** The user's display name (the Profile screen edits it, Home shows it). */
    val userSettings: UserSettings by lazy { UserSettings(this) }

    /** The phone's own storage for the user's movies (Room). It is only opened when something first asks it a question. */
    val database: AppDatabase by lazy { AppDatabase.create(this) }

    /** The private folder with the copies of the gallery pictures that diary pages use (see data/DiaryImageStore.kt). */
    val diaryImages: DiaryImageStore by lazy { DiaryImageStore(this) }

    /**
     * The only way to get movies: from TMDB and from the database. It makes the TMDB API (one network client for
     * the whole app) the first time a screen asks. With no token it still exists: its TMDB calls fail with a
     * friendly "TMDB key missing" error, and the saved movies still work.
     */
    val movieRepository: MovieRepository by lazy {
        val token = BuildConfig.TMDB_READ_TOKEN
        MovieRepository(
            api = TmdbApi.create(token),
            hasToken = token.isNotBlank(),
            dao = database.movieDao(),
            diaryDao = database.diaryDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            watchForSlowCallsOnTheMainThread()
            // A missing token must never crash the app. In debug builds we just leave a hint in Logcat.
            if (BuildConfig.TMDB_READ_TOKEN.isEmpty()) {
                Log.w(TAG, "TMDB_READ_TOKEN is empty. Add it to local.properties to load real movie data.")
            }
        }
        warmUp()
    }

    /**
     * Makes the shared objects on a background thread right away. Reading the small settings files and setting
     * up the network client touch the disk, and that must not happen on the main thread while a screen is drawing.
     * A screen that needs one of them before it is ready simply waits for it (a lazy value is made only once).
     */
    private fun warmUp() {
        thread(name = "cinefiles-warm-up") {
            userSettings
            movieRepository
            diaryImages.prepare()
        }
    }

    /**
     * Debug builds only: StrictMode writes a line to Logcat (tag "StrictMode") whenever the main thread reads or writes
     * the disk or uses the network, and whenever a database cursor or an Activity is leaked. Release builds are not
     * affected. Search Logcat for "StrictMode" while using the app: no line should come from our own code.
     */
    private fun watchForSlowCallsOnTheMainThread() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    private companion object {
        const val TAG = "CineFiles"
    }
}
