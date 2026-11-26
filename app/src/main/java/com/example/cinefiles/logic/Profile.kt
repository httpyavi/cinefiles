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

const val MIN_MOVIES_FOR_TASTE = 2

/** Personality archetypes derived from the user's dominant genres across liked movies. */
enum class TastePersonality {
    ROMANTIC,
    DRAMA_ROMANCE,
    MIND_BENDING,
    THRILL_SEEKER,
    DEEP_FEELER,
    COMEDY_LOVER,
    ROMCOM_FAN,
    SUSPENSE_FAN,
    HORROR_FAN,
    DREAMER,
    CURIOUS_MIND,
    MUSIC_SOUL,
    FRONTIER_SPIRIT,
    ECLECTIC
}

/**
 * Summary of the user's movie taste derived from their liked movies.
 * [hasEnoughData] is false when there are fewer than [MIN_MOVIES_FOR_TASTE] liked movies
 * or no genre data is available yet.
 */
data class Taste(
    val topGenre: String?,
    val secondaryGenre: String? = null,
    val topGenreCount: Int = 0,
    val likedCount: Int = 0,
    val personality: TastePersonality? = null
) {
    val hasEnoughData: Boolean
        get() = likedCount >= MIN_MOVIES_FOR_TASTE && topGenre != null && personality != null
}

/**
 * Derives the user's movie personality from their [liked] movies and dominant genres.
 * Each movie counts once per distinct genre; ties are broken alphabetically for determinism.
 */
fun tasteOf(liked: List<Movie>): Taste {
    val counts = HashMap<String, Int>()
    for (movie in liked) {
        for (genre in movie.genreNames.distinct()) counts[genre] = (counts[genre] ?: 0) + 1
    }
    val sorted = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    val top = sorted.getOrNull(0)
    val second = sorted.getOrNull(1)?.takeIf { it.value >= 2 || liked.size <= 3 }
    val personality = if (liked.size >= MIN_MOVIES_FOR_TASTE && top != null) {
        personalityFor(top.key, second?.key)
    } else {
        null
    }
    return Taste(
        topGenre = top?.key,
        secondaryGenre = second?.key,
        topGenreCount = top?.value ?: 0,
        likedCount = liked.size,
        personality = personality
    )
}

private fun personalityFor(top: String, second: String?): TastePersonality {
    val pair = setOfNotNull(top, second)
    return when {
        "Drama" in pair && "Romance" in pair -> TastePersonality.DRAMA_ROMANCE
        "Comedy" in pair && "Romance" in pair -> TastePersonality.ROMCOM_FAN
        "Thriller" in pair && "Mystery" in pair -> TastePersonality.SUSPENSE_FAN
        "Horror" in pair && "Thriller" in pair -> TastePersonality.HORROR_FAN
        "Drama" in pair && "Crime" in pair -> TastePersonality.SUSPENSE_FAN
        top == "Romance" -> TastePersonality.ROMANTIC
        top == "Science Fiction" || top == "Mystery" -> TastePersonality.MIND_BENDING
        top == "Action" || top == "Adventure" -> TastePersonality.THRILL_SEEKER
        top == "Drama" -> TastePersonality.DEEP_FEELER
        top == "Comedy" -> TastePersonality.COMEDY_LOVER
        top == "Thriller" || top == "Crime" -> TastePersonality.SUSPENSE_FAN
        top == "Horror" -> TastePersonality.HORROR_FAN
        top == "Fantasy" || top == "Animation" || top == "Family" -> TastePersonality.DREAMER
        top == "Documentary" || top == "History" || top == "War" -> TastePersonality.CURIOUS_MIND
        top == "Music" -> TastePersonality.MUSIC_SOUL
        top == "Western" -> TastePersonality.FRONTIER_SPIRIT
        else -> TastePersonality.ECLECTIC
    }
}
