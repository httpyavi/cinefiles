package com.example.cinefiles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The phone's own storage for the user's movies, ratings, favorites and diary pages.
 *
 * Version 3 added the order of the favorites and the first diary page table; version 4 turned a diary page from a list of blocks
 * into one headline, one body and one cover. MIGRATION_2_3 and MIGRATION_3_4 (data/Migrations.kt) carry everything the user saved
 * over. Only a database from some other, unknown version is started over (fallbackToDestructiveMigration), so a schema change can
 * never crash the app.
 */
@Database(entities = [Movie::class, DiaryEntry::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        private const val FILE_NAME = "cinefiles.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
