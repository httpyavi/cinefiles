package com.example.cinefiles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The phone's own storage for the user's movies, ratings, favorites and diary entries.
 */
@Database(entities = [Movie::class, DiaryEntry::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        private const val FILE_NAME = "cinefiles.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
