package com.example.cinefiles.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A private diary entry for a movie in the user's personal movie journal.
 * CineFiles is not a social app; diary entries are strictly personal and stored locally.
 */
@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movieId: Int,
    val movieTitle: String,
    val posterPath: String? = null,
    val releaseYear: Int? = null,
    val rating: Float? = null,           // 0.5 to 5.0 stars in half-star increments
    val notes: String = "",              // personal thoughts, review, or experience
    val dateWatched: Long = System.currentTimeMillis(), // timestamp of viewing date
    val isRewatch: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
