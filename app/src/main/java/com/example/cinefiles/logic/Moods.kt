package com.example.cinefiles.logic

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.cinefiles.R

/**
 * One mood the user can pick on the "What are you feeling today?" screen.
 *
 * @param id          a short fixed name, used to remember the choice (never shown)
 * @param label       the full name (string resource)
 * @param shortLabel  a one-word name for big or small places
 * @param hint        a short hint
 * @param line        exact description required
 * @param genreIds    the main TMDB genre ids for this mood. Empty means "no filter" (Surprise Me).
 * @param tint        the mood's own accent color
 * @param illustrationRes the exact XML drawable illustration resource (null if missing)
 * @param avoidGenreIds genres that clash with this mood (a horror film is no Happy pick, even when it is also a comedy).
 *                      They are left out unless another chosen mood asks for them (Happy + Scared keeps horror).
 */
data class Mood(
    val id: String,
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
    @StringRes val hint: Int,
    @StringRes val line: Int,
    val genreIds: List<Int>,
    @ColorRes val tint: Int,
    @DrawableRes val illustrationRes: Int? = null,
    val avoidGenreIds: List<Int> = emptyList()
) {
    /** "Surprise Me" is special: it cannot be combined with other moods. */
    val isSurprise: Boolean get() = id == Moods.SURPRISE_ID
}

/**
 * THE one place where the 10 exact moods and their TMDB genre ids are written down.
 */
object Moods {

    const val SURPRISE_ID = "surprise"

    // TMDB genre ids that some moods avoid
    private const val HORROR = 27
    private const val THRILLER = 53
    private const val WAR = 10752

    // EXACT 10 MOODS ONLY:
    // 1. HAPPY
    // 2. ROMANTIC
    // 3. RELAXED
    // 4. EMOTIONAL
    // 5. EXCITED
    // 6. CURIOUS
    // 7. NOSTALGIC
    // 8. SCARED
    // 9. ANGRY
    // 10. SURPRISE ME
    val all: List<Mood> = listOf(
        Mood("happy", R.string.mood_happy, R.string.mood_happy_short, R.string.mood_happy_hint,
            R.string.mood_happy_line, listOf(35), R.color.mood_tint_happy, R.drawable.happy,
            avoidGenreIds = listOf(HORROR)),

        Mood("romantic", R.string.mood_romantic, R.string.mood_romantic_short, R.string.mood_romantic_hint,
            R.string.mood_romantic_line, listOf(10749), R.color.mood_tint_romantic, R.drawable.romantic,
            avoidGenreIds = listOf(HORROR)),

        Mood("relaxed", R.string.mood_relaxed, R.string.mood_relaxed_short, R.string.mood_relaxed_hint,
            R.string.mood_relaxed_line, listOf(16, 10751), R.color.mood_tint_relaxed, R.drawable.relaxed,
            avoidGenreIds = listOf(HORROR, THRILLER, WAR)),

        Mood("emotional", R.string.mood_emotional, R.string.mood_emotional_short, R.string.mood_emotional_hint,
            R.string.mood_emotional_line, listOf(18), R.color.mood_tint_emotional, R.drawable.emotional),

        Mood("excited", R.string.mood_excited, R.string.mood_excited_short, R.string.mood_excited_hint,
            R.string.mood_excited_line, listOf(28, 12), R.color.mood_tint_excited, R.drawable.excited),

        Mood("curious", R.string.mood_curious, R.string.mood_curious_short, R.string.mood_curious_hint,
            R.string.mood_curious_line, listOf(9648, 878), R.color.mood_tint_curious, R.drawable.curious),

        Mood("nostalgic", R.string.mood_nostalgic, R.string.mood_nostalgic_short, R.string.mood_nostalgic_hint,
            R.string.mood_nostalgic_line, listOf(36, 10751), R.color.mood_tint_nostalgic, R.drawable.nostalgic,
            avoidGenreIds = listOf(HORROR)),

        Mood("scared", R.string.mood_scared, R.string.mood_scared_short, R.string.mood_scared_hint,
            R.string.mood_scared_line, listOf(27, 53), R.color.mood_tint_scared, R.drawable.scared),

        Mood("angry", R.string.mood_angry, R.string.mood_angry_short, R.string.mood_angry_hint,
            R.string.mood_angry_line, listOf(28, 53), R.color.mood_tint_angry, R.drawable.angry),

        // Surprise Me is always the last mood and special
        Mood(SURPRISE_ID, R.string.mood_surprise, R.string.mood_surprise_short, R.string.mood_surprise_hint,
            R.string.mood_surprise_line, emptyList(), R.color.mood_tint_surprise, R.drawable.surprise)
    )

    /** 2-column grid of all 10 moods in 5 rows */
    val grid: List<Mood> get() = all

    /** The genre ids of all the given moods together, each id once. Empty = any genre. */
    fun genreIdsFor(moodIds: List<String>): List<Int> =
        all.filter { it.id in moodIds }.flatMap { it.genreIds }.distinct()

    /** The genres to leave out for these moods: what one of them avoids and none of them asks for. */
    fun avoidedGenreIds(moods: List<Mood>): List<Int> {
        val wanted = moods.flatMap { it.genreIds }.toSet()
        return moods.flatMap { it.avoidGenreIds }.filter { it !in wanted }.distinct()
    }

    fun avoidedGenreIdsFor(moodIds: List<String>): List<Int> = avoidedGenreIds(all.filter { it.id in moodIds })
}
