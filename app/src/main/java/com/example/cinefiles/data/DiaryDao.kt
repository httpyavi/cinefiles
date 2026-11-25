package com.example.cinefiles.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Every question we ask the `diary_entries` table, and every change we make to it. */
@Dao
interface DiaryDao {

    /** Saves a NEW page (id 0) and returns its id. Saved pages are changed with [update]. */
    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun get(id: Long): DiaryEntry?

    /** Newest page first (by the time it was last changed). */
    @Query("SELECT * FROM diary_entries ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE movieId = :movieId ORDER BY updatedAt DESC, id DESC")
    fun observeForMovie(movieId: Int): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries ORDER BY updatedAt DESC, id DESC")
    suspend fun getAll(): List<DiaryEntry>

    /** The cover of every page that has one (to find the pictures in the app's storage that no page uses any more). */
    @Query("SELECT coverImageUri FROM diary_entries WHERE coverImageUri IS NOT NULL")
    suspend fun coverAddresses(): List<String>

    @Query("SELECT COUNT(*) FROM diary_entries")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM diary_entries")
    suspend fun count(): Int

    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()
}
