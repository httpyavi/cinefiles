package com.example.cinefiles.logic

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.example.cinefiles.R

/**
 * One mood the user can pick on the "What are you feeling today?" screen.
 *
 * @param id          a short fixed name, used to remember the choice (never shown)
 * @param label       the full name (string resource), for example "Happy / Feel-Good"
 * @param shortLabel  a one-word name for big or small places: the Mood screen's headline and the swipe caption
 * @param hint        a two-word hint (string resource)
 * @param line        one friendly sentence under the mood's name on the Mood screen
 * @param genreIds    the main TMDB genre ids for this mood. Empty means "no filter" (Surprise Me).
 * @param tint        the mood's own color: it tints the Mood screen's glow, the face and the slider
 */
data class Mood(
    val id: String,
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
    @StringRes val hint: Int,
    @StringRes val line: Int,
    val genreIds: List<Int>,
    @ColorRes val tint: Int
) {
    /** "Surprise Me" is special: it cannot be combined with other moods. */
    val isSurprise: Boolean get() = id == Moods.SURPRISE_ID
}

/**
 * THE one place where the moods and their TMDB genre ids are written down.
 * Do not copy these numbers anywhere else: ask this file instead.
 */
object Moods {

    const val SURPRISE_ID = "surprise"

    // TMDB genre ids used below:
    // 35 Comedy, 28 Action, 12 Adventure, 10749 Romance, 27 Horror, 53 Thriller, 18 Drama,
    // 16 Animation, 10751 Family, 14 Fantasy, 878 Science Fiction, 9648 Mystery
    val all: List<Mood> = listOf(
        Mood("happy", R.string.mood_happy, R.string.mood_happy_short, R.string.mood_happy_hint,
            R.string.mood_happy_line, listOf(35), R.color.mood_tint_happy),
        Mood("excited", R.string.mood_excited, R.string.mood_excited_short, R.string.mood_excited_hint,
            R.string.mood_excited_line, listOf(28, 12), R.color.mood_tint_excited),
        Mood("romantic", R.string.mood_romantic, R.string.mood_romantic_short, R.string.mood_romantic_hint,
            R.string.mood_romantic_line, listOf(10749), R.color.mood_tint_romantic),
        Mood("scared", R.string.mood_scared, R.string.mood_scared_short, R.string.mood_scared_hint,
            R.string.mood_scared_line, listOf(27, 53), R.color.mood_tint_scared),
        Mood("emotional", R.string.mood_emotional, R.string.mood_emotional_short, R.string.mood_emotional_hint,
            R.string.mood_emotional_line, listOf(18), R.color.mood_tint_emotional),
        Mood("relaxed", R.string.mood_relaxed, R.string.mood_relaxed_short, R.string.mood_relaxed_hint,
            R.string.mood_relaxed_line, listOf(16, 10751), R.color.mood_tint_relaxed),
        Mood("escape", R.string.mood_escape, R.string.mood_escape_short, R.string.mood_escape_hint,
            R.string.mood_escape_line, listOf(14, 878), R.color.mood_tint_escape),
        Mood("mind_bending", R.string.mood_mind_bending, R.string.mood_mind_bending_short,
            R.string.mood_mind_bending_hint, R.string.mood_mind_bending_line,
            listOf(9648), R.color.mood_tint_mind_bending),
        // Surprise Me is always the last mood
        Mood(SURPRISE_ID, R.string.mood_surprise, R.string.mood_surprise_short, R.string.mood_surprise_hint,
            R.string.mood_surprise_line, emptyList(), R.color.mood_tint_surprise)
    )

    /**
     * The order of the moods on the Mood screen's slider: from cozy to wild, so neighbours feel alike
     * (and their colors sit next to each other on the color wheel). The same nine moods as [all].
     * The animated face has one expression per place in this list, in this order.
     */
    val spectrum: List<Mood> = listOf(
        "relaxed", "happy", "romantic", "emotional", "scared",
        "mind_bending", "escape", "excited", SURPRISE_ID
    ).map { id -> all.first { it.id == id } }

    /** The genre ids of all the given moods together, each id once. Empty = any genre. */
    fun genreIdsFor(moodIds: List<String>): List<Int> =
        all.filter { it.id in moodIds }.flatMap { it.genreIds }.distinct()
}
