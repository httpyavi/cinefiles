package com.example.cinefiles.data

/**
 * A movie as the screens need it. Kept small on purpose: more fields are added by the batches that need them.
 *
 * posterPath / backdropPath are TMDB image paths such as "/abc123.jpg" (not full web addresses).
 * Turn them into addresses with ImageUrls.poster(...) / ImageUrls.backdrop(...).
 */
data class Movie(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?
)
