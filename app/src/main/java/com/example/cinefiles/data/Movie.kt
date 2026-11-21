package com.example.cinefiles.data

/**
 * A movie as the screens need it. Kept small on purpose: more fields are added by the batches that need them.
 *
 * posterPath / backdropPath are TMDB image paths such as "/abc123.jpg" (not full web addresses).
 * Turn them into addresses with ImageUrls.poster(...) / ImageUrls.backdrop(...).
 *
 * voteAverage      TMDB's rating from 0 to 10, or null when unknown
 * releaseYear      for example 2014, or null when unknown
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
 */
data class Movie(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double? = null,
    val releaseYear: Int? = null,
    val genreNames: List<String> = emptyList(),
    val genreIds: List<Int> = emptyList(),
    val voteCount: Int = 0,
    val originalLanguage: String? = null,
    val runtimeMinutes: Int? = null
)
