package com.example.cinefiles.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM diary_entries WHERE movieId = :movieId ORDER BY dateWatched DESC, id DESC")
    suspend fun getForMovie(movieId: Int): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE movieId = :movieId ORDER BY dateWatched DESC, id DESC")
    fun observeForMovie(movieId: Int): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries ORDER BY dateWatched DESC, id DESC")
    fun observeAll(): Flow<List<DiaryEntry>>

    @Query("SELECT COUNT(*) FROM diary_entries")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM diary_entries")
    suspend fun count(): Int

    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()
}
