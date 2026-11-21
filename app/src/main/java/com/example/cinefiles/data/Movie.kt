package com.example.cinefiles.data

/**
 * A movie as the screens need it. Kept small on purpose: more fields are added by the batches that need them.
 *
 * posterPath / backdropPath are TMDB image paths such as "/abc123.jpg" (not full web addresses).
 * Turn them into addresses with ImageUrls.poster(...) / ImageUrls.backdrop(...).
 *
 * voteAverage  TMDB's rating from 0 to 10, or null when unknown
 * releaseYear  for example 2014, or null when unknown
 * genreNames   the genre names to show ("Drama", "Science Fiction"), in TMDB's order
 */
data class Movie(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double? = null,
    val releaseYear: Int? = null,
    val genreNames: List<String> = emptyList()
)
