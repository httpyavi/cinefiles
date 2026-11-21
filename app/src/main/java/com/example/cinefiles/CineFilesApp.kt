package com.example.cinefiles

import android.app.Application
import android.util.Log
import com.example.cinefiles.data.AchievementTracker

/**
 * The Application class. Android creates it once when the app process starts, before any screen.
 *
 * Things there must be only ONE of live here. Later batches add the Room database and the TMDB API.
 */
class CineFilesApp : Application() {

    /** The user's achievements. The swipe screen feeds it, the Profile screen shows it. */
    val achievements: AchievementTracker by lazy { AchievementTracker(this) }

    override fun onCreate() {
        super.onCreate()

        // A missing token must never crash the app. In debug builds we just leave a hint in Logcat.
        if (BuildConfig.DEBUG && BuildConfig.TMDB_READ_TOKEN.isEmpty()) {
            Log.w(TAG, "TMDB_READ_TOKEN is empty. Add it to local.properties to load real movie data.")
        }
    }

    private companion object {
        const val TAG = "CineFiles"
    }
}
