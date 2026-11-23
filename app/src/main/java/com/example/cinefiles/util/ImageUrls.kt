package com.example.cinefiles.util

/**
 * Builds TMDB image web addresses from the short paths TMDB gives us ("/abc123.jpg").
 *
 * The size is part of the address. We pick a size close to what the screen needs,
 * not the biggest one, so images download fast and use little memory.
 * The "original" size is never used: it can be several megabytes.
 */
object ImageUrls {
    private const val BASE = "https://image.tmdb.org/t/p/"

    private const val POSTER_SIZE = "w342"       // rows and grids: posters up to about 130 dp wide
    private const val BIG_POSTER_SIZE = "w500"   // the swipe card (a poster as wide as a phone screen)
    private const val BACKDROP_SIZE = "w780"     // the Home hero and the Details backdrop

    fun poster(path: String?): String? = path?.let { BASE + POSTER_SIZE + it }

    fun bigPoster(path: String?): String? = path?.let { BASE + BIG_POSTER_SIZE + it }

    fun backdrop(path: String?): String? = path?.let { BASE + BACKDROP_SIZE + it }
}
