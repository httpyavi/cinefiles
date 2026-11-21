package com.example.cinefiles.util

/**
 * Builds TMDB image web addresses from the short paths TMDB gives us ("/abc123.jpg").
 *
 * The size is part of the address. We pick a size close to what the screen needs,
 * not the biggest one, so images download fast and use little memory.
 */
object ImageUrls {
    private const val BASE = "https://image.tmdb.org/t/p/"

    private const val POSTER_SIZE = "w342"       // good for posters up to about 130 dp wide
    private const val BIG_POSTER_SIZE = "w780"   // good for a poster as wide as a phone screen (swipe cards)
    private const val BACKDROP_SIZE = "w780"     // good for a card as wide as a phone screen

    fun poster(path: String?): String? = path?.let { BASE + POSTER_SIZE + it }

    fun bigPoster(path: String?): String? = path?.let { BASE + BIG_POSTER_SIZE + it }

    fun backdrop(path: String?): String? = path?.let { BASE + BACKDROP_SIZE + it }
}
