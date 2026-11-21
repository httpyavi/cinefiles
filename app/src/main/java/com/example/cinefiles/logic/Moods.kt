package com.example.cinefiles.logic

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.cinefiles.R

/**
 * One mood the user can pick on the "What are you feeling today?" screen.
 *
 * @param id          a short fixed name, used to remember the choice (never shown)
 * @param label       the name on the card (string resource)
 * @param shortLabel  a one-word name for small places, like the caption on the swipe screen
 * @param hint        the one-line hint on the card (string resource)
 * @param genreIds    the main TMDB genre ids for this mood. Empty means "no filter" (Surprise Me).
 * @param icon        the icon of the fallback card, shown when there is no photo
 * @param tint        the faint color of the fallback card
 */
data class Mood(
    val id: String,
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
    @StringRes val hint: Int,
    val genreIds: List<Int>,
    @DrawableRes val icon: Int,
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
            listOf(35), R.drawable.ic_mood_happy, R.color.mood_tint_happy),
        Mood("excited", R.string.mood_excited, R.string.mood_excited_short, R.string.mood_excited_hint,
            listOf(28, 12), R.drawable.ic_mood_excited, R.color.mood_tint_excited),
        Mood("romantic", R.string.mood_romantic, R.string.mood_romantic_short, R.string.mood_romantic_hint,
            listOf(10749), R.drawable.ic_heart, R.color.mood_tint_romantic),
        Mood("scared", R.string.mood_scared, R.string.mood_scared_short, R.string.mood_scared_hint,
            listOf(27, 53), R.drawable.ic_mood_scared, R.color.mood_tint_scared),
        Mood("emotional", R.string.mood_emotional, R.string.mood_emotional_short, R.string.mood_emotional_hint,
            listOf(18), R.drawable.ic_mood_emotional, R.color.mood_tint_emotional),
        Mood("relaxed", R.string.mood_relaxed, R.string.mood_relaxed_short, R.string.mood_relaxed_hint,
            listOf(16, 10751), R.drawable.ic_mood_relaxed, R.color.mood_tint_relaxed),
        Mood("escape", R.string.mood_escape, R.string.mood_escape_short, R.string.mood_escape_hint,
            listOf(14, 878), R.drawable.ic_mood_escape, R.color.mood_tint_escape),
        Mood("mind_bending", R.string.mood_mind_bending, R.string.mood_mind_bending_short,
            R.string.mood_mind_bending_hint,
            listOf(9648), R.drawable.ic_mood_mind_bending, R.color.mood_tint_mind_bending),
        // Surprise Me is always the last card, and it is shown full width
        Mood(SURPRISE_ID, R.string.mood_surprise, R.string.mood_surprise_short, R.string.mood_surprise_hint,
            emptyList(), R.drawable.ic_mood_surprise, R.color.mood_tint_surprise)
    )

    /** The genre ids of all the given moods together, each id once. Empty = any genre. */
    fun genreIdsFor(moodIds: List<String>): List<Int> =
        all.filter { it.id in moodIds }.flatMap { it.genreIds }.distinct()
}
