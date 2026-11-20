package com.example.cinefiles

import android.app.Application
import android.util.Log

/**
 * The Application class. Android creates it once when the app process starts, before any screen.
 *
 * Later batches create the Room database and the TMDB API here, so there is only ever
 * one of each in the whole app.
 */
class CineFilesApp : Application() {

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
