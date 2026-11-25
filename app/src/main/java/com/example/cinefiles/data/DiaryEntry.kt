package com.example.cinefiles.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One page of the user's private Movie Diary (table `diary_entries`). CineFiles is not a social app: diary pages are
 * strictly personal and live only on the phone.
 *
 * A page is ONE piece of writing, like a post on Substack: an optional [headline], a [bodyHtml] and an optional cover picture.
 * The body is the editor's HTML (bold, italic, underline, headings and pull-quotes: ui/diary/RichText.kt). The cover is only an
 * ADDRESS ([coverImageUri], read by logic/DiaryText.kt: "tmdb:/abc.jpg" for a movie still, "local:name.jpg" for a small copy
 * of a gallery picture that the app keeps in its own storage, data/DiaryImageStore.kt). Never the picture bytes.
 *
 * movieId / movieTitle / posterPath  the movie the page is about (the title and poster are copied, so the page still
 *                                    reads well when the movie row is gone)
 * createdAt / updatedAt              milliseconds
 * ratingSnapshot                     the user's popcorn rating of the movie when the page was last saved (0.5 to 5.0),
 *                                    or null. A snapshot: rating the movie again later does not rewrite old pages.
 * headline                           the page's own headline; blank = the page has none (the movie's name stands alone)
 * fontStyle / textSize /             the page's look: the name of a [com.example.cinefiles.logic.DiaryFont],
 * textAlignment / canvasTheme        [com.example.cinefiles.logic.DiaryTextSize], [com.example.cinefiles.logic.TextAlign]
 *                                    and [com.example.cinefiles.logic.DiaryTheme]
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
    val headline: String = "",
    val bodyHtml: String = "",
    val coverImageUri: String? = null,
    val fontStyle: String = "SANS",
    val textAlignment: String = "LEFT",
    val textSize: String = "STANDARD",
    val canvasTheme: String = "MIDNIGHT"
)
