package com.example.cinefiles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The phone's own storage for the user's movies: one table, `movies` (see data/Movie.kt).
 * The app has ONE of these, made in CineFilesApp. Nothing else should build it.
 *
 * This is version 1, so there is nothing to migrate yet. If the `movies` table ever changes, raise the version AND add
 * a Migration to the builder below. Without one, Room starts over with an empty table (fallbackToDestructiveMigration):
 * the user's saved movies would be lost, but the app never crashes on a phone that has an older copy of the database.
 */
@Database(entities = [Movie::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao

    companion object {
        private const val FILE_NAME = "cinefiles.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .fallbackToDestructiveMigration() // the safety net described above
                .build()
    }
}
