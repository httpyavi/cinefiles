package com.example.cinefiles.logic

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie

/*
 * ACHIEVEMENTS: small, quiet milestones for discovering movies.
 *
 * No points, no coins, no streaks: just a handful of badges, each with a goal and a progress bar.
 * Everything here is plain Kotlin and works from one small summary of what the user has done
 * ([AchievementStats]), so it is easy to test and does not care where that summary is stored.
 * (Today it is kept in the phone's small key-value storage, see data/AchievementTracker.kt.
 * Batch 6 can move it into Room without touching this file.)
 *
 * Not here yet: "Double Feature" (add two movies to your watchlist). There is no watchlist until Batch 6/7.
 */

enum class AchievementId {
    FIRST_FRAME, SCENE_EXPLORER, MOVIE_BUFF, GENRE_COLLECTOR, ACROSS_THE_GENRES,
    HEARTBREAKER, DIRECTORS_CUT, NIGHT_OWL, EMOTIONAL_DAMAGE
}

/** One badge: its name, what to do to earn it, its picture, and the number to reach (the goal). */
data class Achievement(
    val id: AchievementId,
    @StringRes val name: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
    val goal: Int
)

/**
 * Everything the achievements need to know about what the user has done, over all sessions.
 *
 * @param swiped          cards swiped (liked or passed)
 * @param liked           movies liked
 * @param passed          movies passed
 * @param likedGenres     genre name -> how many liked movies had it (a count, so Undo can take a like back)
 * @param seenGenres      every genre the user has had a card of
 * @param nightSwipes     cards swiped late at night (11 p.m. to 5 a.m.)
 * @param bestSession     the most cards swiped in one sitting
 * @param emotionalSwipes cards swiped while the Emotional mood was part of the mix
 */
data class AchievementStats(
    val swiped: Int = 0,
    val liked: Int = 0,
    val passed: Int = 0,
    val likedGenres: Map<String, Int> = emptyMap(),
    val seenGenres: Set<String> = emptySet(),
    val nightSwipes: Int = 0,
    val bestSession: Int = 0,
    val emotionalSwipes: Int = 0
) {
    /** How far along this achievement is: the number to compare with its goal. */
    fun progressOf(id: AchievementId): Int = when (id) {
        AchievementId.FIRST_FRAME, AchievementId.MOVIE_BUFF -> liked
        AchievementId.SCENE_EXPLORER -> swiped
        AchievementId.GENRE_COLLECTOR -> likedGenres.count { it.value > 0 }
        AchievementId.ACROSS_THE_GENRES -> seenGenres.size
        AchievementId.HEARTBREAKER -> passed
        AchievementId.DIRECTORS_CUT -> bestSession
        AchievementId.NIGHT_OWL -> nightSwipes
        AchievementId.EMOTIONAL_DAMAGE -> emotionalSwipes
    }
}

object Achievements {

    /** All the badges, in the order the Profile screen shows them (easy ones first). */
    val all: List<Achievement> = listOf(
        Achievement(AchievementId.FIRST_FRAME, R.string.ach_first_frame, R.string.ach_first_frame_desc, R.drawable.ic_film, 1),
        Achievement(AchievementId.SCENE_EXPLORER, R.string.ach_scene_explorer, R.string.ach_scene_explorer_desc, R.drawable.ic_discover, 10),
        Achievement(AchievementId.HEARTBREAKER, R.string.ach_heartbreaker, R.string.ach_heartbreaker_desc, R.drawable.ic_heart_broken, 20),
        Achievement(AchievementId.DIRECTORS_CUT, R.string.ach_directors_cut, R.string.ach_directors_cut_desc, R.drawable.ic_play, 20),
        Achievement(AchievementId.GENRE_COLLECTOR, R.string.ach_genre_collector, R.string.ach_genre_collector_desc, R.drawable.ic_layers, 5),
        Achievement(AchievementId.ACROSS_THE_GENRES, R.string.ach_across_the_genres, R.string.ach_across_the_genres_desc, R.drawable.ic_sparkle, 8),
        Achievement(AchievementId.EMOTIONAL_DAMAGE, R.string.ach_emotional_damage, R.string.ach_emotional_damage_desc, R.drawable.ic_drop, 5),
        Achievement(AchievementId.NIGHT_OWL, R.string.ach_night_owl, R.string.ach_night_owl_desc, R.drawable.ic_moon, 5),
        Achievement(AchievementId.MOVIE_BUFF, R.string.ach_movie_buff, R.string.ach_movie_buff_desc, R.drawable.ic_star_filled, 25)
    )

    /** The number shown in "3 / 10": progress, but never more than the goal. */
    fun progress(achievement: Achievement, stats: AchievementStats): Int =
        minOf(achievement.goal, stats.progressOf(achievement.id))

    fun isUnlocked(achievement: Achievement, stats: AchievementStats): Boolean =
        stats.progressOf(achievement.id) >= achievement.goal

    /** Between 11 p.m. and 5 a.m. */
    fun isNight(hourOfDay: Int): Boolean = hourOfDay >= 23 || hourOfDay < 5

    /**
     * The summary after one more card was swiped.
     * @param sessionSwipes how many cards this sitting has swiped, including this one
     * @param emotionalSession true when the Emotional mood is part of the mix
     */
    fun afterSwipe(
        stats: AchievementStats,
        movie: Movie,
        direction: SwipeDirection,
        hourOfDay: Int,
        sessionSwipes: Int,
        emotionalSession: Boolean
    ): AchievementStats {
        val liked = direction == SwipeDirection.LIKE
        return stats.copy(
            swiped = stats.swiped + 1,
            liked = stats.liked + if (liked) 1 else 0,
            passed = stats.passed + if (liked) 0 else 1,
            likedGenres = if (liked) {
                stats.likedGenres + movie.genreNames.associateWith { (stats.likedGenres[it] ?: 0) + 1 }
            } else {
                stats.likedGenres
            },
            seenGenres = stats.seenGenres + movie.genreNames,
            nightSwipes = stats.nightSwipes + if (isNight(hourOfDay)) 1 else 0,
            bestSession = maxOf(stats.bestSession, sessionSwipes),
            emotionalSwipes = stats.emotionalSwipes + if (emotionalSession) 1 else 0
        )
    }

    /**
     * The summary after a swipe was taken back with Undo, so liking, undoing and liking again does not count twice.
     * Badges that were already earned are never taken away (see the tracker); and genres that were SEEN stay seen.
     */
    fun afterUndo(
        stats: AchievementStats,
        movie: Movie,
        direction: SwipeDirection,
        hourOfDay: Int,
        emotionalSession: Boolean
    ): AchievementStats {
        val liked = direction == SwipeDirection.LIKE
        return stats.copy(
            swiped = (stats.swiped - 1).coerceAtLeast(0),
            liked = (stats.liked - if (liked) 1 else 0).coerceAtLeast(0),
            passed = (stats.passed - if (liked) 0 else 1).coerceAtLeast(0),
            likedGenres = if (liked) {
                stats.likedGenres.mapValues { (genre, count) ->
                    if (genre in movie.genreNames) (count - 1).coerceAtLeast(0) else count
                }
            } else {
                stats.likedGenres
            },
            nightSwipes = (stats.nightSwipes - if (isNight(hourOfDay)) 1 else 0).coerceAtLeast(0),
            emotionalSwipes = (stats.emotionalSwipes - if (emotionalSession) 1 else 0).coerceAtLeast(0)
        )
    }
}
