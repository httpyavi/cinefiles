package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * HOW "YOUR PICKS" ARE CHOSEN (no AI, no server: three small rules you can read and change).
 *
 * 1. A GENRE PROFILE says how much the user likes each genre, from 0 (not at all) to 1 (the most):
 *      every genre of a chosen mood        +1
 *      every genre of a movie you liked    +1.5
 *      every genre of a movie you passed   -0.5
 *    Negative totals count as 0, and everything is divided by the biggest total.
 *
 * 2. A SCORE for a candidate movie:   0.6 x genre match  +  0.3 x (rating / 10)  +  0.1 x era match
 *      genre match = the average profile value of the candidate's genres
 *      era match   = how close the candidate's year is to the average year of the movies you liked
 *                    (0.5 when you liked nothing, so mood and rating decide)
 *
 * 3. THE LIST: the best scores first, the top 10, but at most 4 movies whose first genre is the same,
 *    so the picks are not all one kind. The match percent is the score x 100.
 *
 * This file is plain Kotlin and knows nothing about TMDB, the database or screens.
 * Where the candidates come from is MovieRepository.picksCandidates.
 */

/** Why a movie was picked. The screen turns it into a sentence ("Similar to ...", "Matches your ... mood"). */
sealed class PickReason {
    /** It shares genres with a movie you liked. */
    data class SimilarTo(val likedTitle: String) : PickReason()

    /** It fits one of the moods you chose. */
    data class MoodMatch(val moodId: String) : PickReason()

    /** No mood was chosen and nothing was liked. */
    object Generic : PickReason()
}

/** One pick: the movie, its score (0 to 1) and why it is there. */
data class Recommendation(val movie: Movie, val score: Double, val reason: PickReason) {
    /** The "92% match" shown on the screen. */
    val matchPercent: Int get() = (score * 100).roundToInt().coerceIn(0, 100)
}

class RecommendationEngine(
    private val maxPicks: Int = MAX_PICKS,
    private val maxPerFirstGenre: Int = MAX_PER_FIRST_GENRE
) {

    /** Rule 1: genre id -> how much the user likes it (0 to 1). Genres with no liking at all are left out. */
    fun genreProfile(moods: List<Mood>, liked: List<Movie>, passed: List<Movie>): Map<Int, Double> {
        val totals = HashMap<Int, Double>()
        for (genre in moods.flatMap { it.genreIds }.distinct()) totals.add(genre, MOOD_GENRE_POINTS)
        for (movie in liked) for (genre in movie.genreIds) totals.add(genre, LIKED_GENRE_POINTS)
        for (movie in passed) for (genre in movie.genreIds) totals.add(genre, PASSED_GENRE_POINTS)

        val biggest = totals.values.maxOfOrNull { max(0.0, it) } ?: 0.0
        if (biggest <= 0.0) return emptyMap()
        return totals
            .mapValues { max(0.0, it.value) / biggest }
            .filterValues { it > 0.0 }
    }

    /** The genres to ask TMDB for: the [count] highest in the profile (equal values: the smaller genre id first). */
    fun topGenres(profile: Map<Int, Double>, count: Int = TOP_GENRE_COUNT): List<Int> =
        profile.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
            .take(count)
            .map { it.key }

    /** Rule 2: the score of one candidate, from 0 to 1. [averageLikedYear] is null when nothing was liked. */
    fun score(candidate: Movie, profile: Map<Int, Double>, averageLikedYear: Double?): Double {
        val genreMatch = if (candidate.genreIds.isEmpty()) {
            0.0
        } else {
            candidate.genreIds.sumOf { profile[it] ?: 0.0 } / candidate.genreIds.size
        }
        val rating = (candidate.voteAverage ?: 0.0) / 10.0
        return GENRE_WEIGHT * genreMatch + RATING_WEIGHT * rating + ERA_WEIGHT * eraMatch(candidate.releaseYear, averageLikedYear)
    }

    /**
     * Rule 3: scores the [candidates] and returns the picks, best first.
     * A movie is never picked when its id is in [excludeIds], when it was liked or passed in this sitting,
     * when it has no poster, or when it is in the list twice. The order is the same every time for the same input.
     */
    fun recommend(
        candidates: List<Movie>,
        moods: List<Mood>,
        liked: List<Movie>,
        passed: List<Movie>,
        excludeIds: Set<Int> = emptySet()
    ): List<Recommendation> {
        val profile = genreProfile(moods, liked, passed)
        val likedYears = liked.mapNotNull { it.releaseYear }
        val averageLikedYear = if (likedYears.isEmpty()) null else likedYears.average()
        val skip = excludeIds + liked.map { it.id } + passed.map { it.id }

        val ranked = candidates
            .filter { it.id !in skip && it.posterPath != null }
            .distinctBy { it.id }
            .map { Recommendation(it, score(it, profile, averageLikedYear), reasonFor(it, moods, liked)) }
            .sortedWith(
                // Same score: the better rated first, then the smaller id, so the order never changes by chance
                compareByDescending<Recommendation> { it.score }
                    .thenByDescending { it.movie.voteAverage ?: 0.0 }
                    .thenBy { it.movie.id }
            )

        val picks = ArrayList<Recommendation>()
        val perFirstGenre = HashMap<Int, Int>()
        for (pick in ranked) {
            if (picks.size >= maxPicks) break
            val firstGenre = pick.movie.genreIds.firstOrNull() ?: NO_GENRE
            val used = perFirstGenre[firstGenre] ?: 0
            if (used >= maxPerFirstGenre) continue
            perFirstGenre[firstGenre] = used + 1
            picks.add(pick)
        }
        return picks
    }

    /** "Similar to <the liked movie it shares the most genres with>", else "Matches your <best fitting mood> mood". */
    private fun reasonFor(candidate: Movie, moods: List<Mood>, liked: List<Movie>): PickReason {
        val genres = candidate.genreIds.toSet()

        val mostSimilar = liked
            .map { it to it.genreIds.count { genre -> genre in genres } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second } // the first one wins a tie
        if (mostSimilar != null) return PickReason.SimilarTo(mostSimilar.first.title)

        val bestMood = moods
            .map { it to it.genreIds.count { genre -> genre in genres } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
            ?: moods.firstOrNull() // a mood with no genres (Surprise Me) still names the mood
        return if (bestMood != null) PickReason.MoodMatch(bestMood.id) else PickReason.Generic
    }

    /** 1 = the same year as the average liked movie, falling to 0 at [ERA_SPREAD_YEARS] years apart. */
    private fun eraMatch(year: Int?, averageLikedYear: Double?): Double {
        if (year == null || averageLikedYear == null) return NEUTRAL_ERA
        return max(0.0, 1.0 - abs(year - averageLikedYear) / ERA_SPREAD_YEARS)
    }

    private fun HashMap<Int, Double>.add(genre: Int, points: Double) {
        this[genre] = (this[genre] ?: 0.0) + points
    }

    companion object {
        const val MAX_PICKS = 10
        const val MAX_PER_FIRST_GENRE = 4
        const val TOP_GENRE_COUNT = 3

        private const val MOOD_GENRE_POINTS = 1.0
        private const val LIKED_GENRE_POINTS = 1.5
        private const val PASSED_GENRE_POINTS = -0.5

        private const val GENRE_WEIGHT = 0.6
        private const val RATING_WEIGHT = 0.3
        private const val ERA_WEIGHT = 0.1

        private const val ERA_SPREAD_YEARS = 30.0
        private const val NEUTRAL_ERA = 0.5
        private const val NO_GENRE = -1
    }
}
