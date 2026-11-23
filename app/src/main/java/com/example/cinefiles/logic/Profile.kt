package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie

/*
 * THE RULES OF THE PROFILE SCREEN (plain Kotlin, no screens: easy to test).
 * Two small things: what a valid display name is, and what "your taste" says.
 */

/**
 * The name typed in the Edit dialog, tidied up: no spaces at the ends, one space between words, cut to [maxLength]
 * letters. Returns null when nothing is left (a blank name is not allowed).
 */
fun cleanDisplayName(raw: String, maxLength: Int): String? {
    val tidy = raw.trim().replace(WHITE_SPACE, " ").take(maxLength).trim()
    return tidy.ifEmpty { null }
}

/** The letter shown in the avatar: the first letter of the name, as a capital. Empty for an empty name. */
fun initialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    // codePointAt keeps a letter made of two Java characters (an emoji, for example) in one piece
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}

private val WHITE_SPACE = Regex("\\s+")

/**
 * What the "Your taste" line says: the genre you like most, and how many movies you like in all.
 * [topGenre] is null while nothing is liked.
 */
data class Taste(val topGenre: String?, val likedCount: Int)

/**
 * The genre that most of the [liked] movies have. A movie counts once for each of its genres. When two genres are
 * equally common, the one first in the alphabet wins, so the answer never changes by chance.
 */
fun tasteOf(liked: List<Movie>): Taste {
    val counts = HashMap<String, Int>()
    for (movie in liked) {
        for (genre in movie.genreNames.distinct()) counts[genre] = (counts[genre] ?: 0) + 1
    }
    val top = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()?.key
    return Taste(topGenre = top, likedCount = liked.size)
}
