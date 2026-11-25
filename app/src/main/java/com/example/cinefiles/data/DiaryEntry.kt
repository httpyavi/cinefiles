package com.example.cinefiles.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One page of the user's private Movie Diary (table `diary_entries`). CineFiles is not a social app: diary pages are
 * strictly personal and live only on the phone.
 *
 * A page is a list of BLOCKS (text and pictures, like the cells of a notebook). The blocks are kept as one piece of
 * text, [blocksJson] (made and read by logic/DiaryBlocks.kt with Gson). It only ever holds words and picture
 * ADDRESSES: a TMDB picture is its TMDB path, a picture from the phone's gallery is the NAME of a small copy that the
 * app keeps in its own storage (data/DiaryImageStore.kt). Never the picture bytes.
 *
 * movieId / movieTitle / posterPath  the movie the page is about (the title and poster are copied, so the page still
 *                                    reads well when the movie row is gone)
 * createdAt / updatedAt              milliseconds
 * ratingSnapshot                     the user's popcorn rating of the movie when the page was last saved (0.5 to 5.0),
 *                                    or null. A snapshot: rating the movie again later does not rewrite old pages.
 * fontStyle / backgroundTheme        the page's look: the name of a [com.example.cinefiles.logic.DiaryFont] and of a
 *                                    [com.example.cinefiles.logic.DiaryTheme]
 */
@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movieId: Int,
    val movieTitle: String,
    val posterPath: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val ratingSnapshot: Float? = null,
    val fontStyle: String = "SANS",
    val backgroundTheme: String = "MIDNIGHT",
    val blocksJson: String = "[]"
)
