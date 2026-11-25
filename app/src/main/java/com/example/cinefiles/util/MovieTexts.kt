package com.example.cinefiles.util

import com.example.cinefiles.data.Movie
import java.text.NumberFormat
import java.util.Locale

/*
 * The small pieces of text every screen writes about a movie, made in ONE place so that a poster card, the Home
 * hero, Details and Your Picks all say the same thing the same way.
 */

/** A whole number written the way the phone's language does it (1,234 or 1.234). */
fun Int.formatted(): String = NumberFormat.getIntegerInstance().format(this)

/** The rating with one decimal, in the phone's language (8.4, or 8,4), or null when the movie has none. */
fun Movie.ratingText(): String? = voteAverage?.let {
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(it)
}

/** The first [max] genres, like "Drama, Crime". Empty when the movie has no genres. */
fun Movie.genresText(max: Int = Int.MAX_VALUE): String = genreNames.take(max).joinToString(", ")

/** "2017 · Science Fiction, Drama": the year and up to [maxGenres] genres. Anything unknown is left out. */
fun Movie.yearAndGenres(maxGenres: Int = 2): String =
    listOfNotNull(releaseYear?.toString(), genresText(maxGenres).ifEmpty { null }).joinToString(" · ")

/**
 * The English name of one of TMDB's language codes: "ko" gives "Korean". TMDB's texts are English, so this is too.
 * Null when there is no code, or when it is not a language ("xx" is TMDB's "no spoken language").
 */
fun languageName(code: String?): String? {
    val clean = code?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    if (clean == "cn") return "Cantonese" // TMDB's own code: it is not a real language code
    val name = Locale(clean).getDisplayLanguage(Locale.ENGLISH)
    return name.takeIf { it.isNotEmpty() && !it.equals(clean, ignoreCase = true) }
}
