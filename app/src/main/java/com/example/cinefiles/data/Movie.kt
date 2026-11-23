package com.example.cinefiles.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What the user did with a movie on the swipe screen (or in the Collection). NONE = nothing. */
enum class Reaction { NONE, LIKED, PASSED }

/**
 * A movie as the screens need it: THE one Movie class used by every screen, adapter and the database.
 * It is built from TMDB's answer in exactly one place: `MovieDto.toMovie()` in data/dto/MovieDto.kt.
 *
 * It is also the one Room table, `movies`: one row per movie, so the movie's information is stored once and
 * never copied. The user's own state (liked, watchlist, watched) lives in the same row, in the last columns.
 * genreIds and genreNames are stored as comma-separated text (see data/Converters.kt).
 *
 * posterPath / backdropPath are TMDB image paths such as "/abc123.jpg" (not full web addresses).
 * Turn them into addresses with ImageUrls.poster(...) / ImageUrls.backdrop(...).
 *
 * overview         the short story text ("" when TMDB has none)
 * releaseDate      TMDB's date text, "2014-11-05" (null when unknown)
 * releaseYear      the year of releaseDate, for example 2014 (null when unknown)
 * voteAverage      TMDB's rating from 0 to 10, or null when unknown (nobody has rated it yet)
 * genreNames       the genre names to show ("Drama", "Science Fiction"), in TMDB's order
 *
 * The fields below are what the swipe deck needs to choose and mix movies (see logic/Discovery.kt).
 * TMDB's list answers contain all of them except the runtime, which only the details answer has.
 * 0 or null always means "we do not know".
 *
 * genreIds         TMDB genre ids, the numbers behind genreNames (28 = Action, 35 = Comedy ...)
 * voteCount        how many people rated it. A well rated movie with few votes is a "hidden gem".
 * originalLanguage TMDB's language code: "en", "ko", "ja", "tl" ...
 * runtimeMinutes   the length in minutes
 *
 * The user's state. TMDB knows nothing about it. The "...At" fields are times in milliseconds (0 = never);
 * they decide the order in the Collection lists (newest first).
 *
 * reaction         liked, passed or none
 * inWatchlist      saved to watch later. A movie is on the watchlist OR watched, never both.
 * isWatched        already seen. Marking a movie watched takes it off the watchlist.
 */
@Entity(tableName = "movies")
data class Movie(
    @PrimaryKey val id: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double? = null,
    val releaseYear: Int? = null,
    val genreNames: List<String> = emptyList(),
    val genreIds: List<Int> = emptyList(),
    val voteCount: Int = 0,
    val originalLanguage: String? = null,
    val runtimeMinutes: Int? = null,
    val overview: String = "",
    val releaseDate: String? = null,
    val reaction: Reaction = Reaction.NONE,
    val reactedAt: Long = 0L,
    val inWatchlist: Boolean = false,
    val watchlistAddedAt: Long = 0L,
    val isWatched: Boolean = false,
    val watchedAt: Long = 0L
)
