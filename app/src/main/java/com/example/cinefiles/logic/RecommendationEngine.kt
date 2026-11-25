package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * HOW "YOUR PICKS" ARE CHOSEN (no AI, no server: a few small rules you can read and change).
 *
 * The user's TASTE comes from three things: the moods they chose, the movies they swiped right (a "popcorn"), and the
 * movies they passed. A candidate movie then gets a SCORE from 0 to 1, made of four parts and one penalty:
 *
 *   score = 0.40 x genre match  +  0.35 x quality  +  0.15 x credibility  +  0.10 x era and language   -  pass penalty
 *
 * 1. GENRE MATCH. A genre profile says how much the user likes each genre: every genre of a chosen mood +1, of a
 *    swiped-right movie +2, of a passed movie -1. Negative totals count as 0 and the rest is divided by the biggest.
 *    The match is the COSINE similarity between the candidate's genres and that profile (1 = the same direction,
 *    0 = nothing in common). With no profile at all (Surprise Me, nothing swiped) it is a neutral 0.5.
 * 2. QUALITY. IMDb's Bayesian weighted rating, (v / (v + m)) x R + (m / (v + m)) x C, with R the movie's rating, v its
 *    number of votes, m = 500 and C = 6.8 (the average movie). A rating with few votes is pulled toward C, so a 9.4
 *    from 12 voters is worth less than an 8.2 from 12,000. It is put on 0..1 by mapping 5.0 to 9.0 onto 0 to 1.
 * 3. CREDIBILITY. How many people voted, on a log scale (20,000 votes = 1): a movie everyone has seen is a safer pick.
 * 4. ERA AND LANGUAGE. How close the candidate's decade is to the decades of the swiped-right movies (same decade 1,
 *    next decade 0.5) and how many of them share its original language. 0.5 when nothing was swiped.
 * 5. PASS PENALTY. When the user passed a genre clearly more often than they liked it (2 or more net passes), a
 *    candidate whose FIRST genre is that one loses up to 0.20.
 *
 * THE LIST: best scores first, top 10, but at most 3 movies whose first genre is the same, so the picks are not all one
 * kind. The match percent is the score x 100. Equal scores are settled by rating, then movie id, so the order never
 * changes by chance. Each pick says why ([PickReason]).
 *
 * This file is plain Kotlin and knows nothing about TMDB, the database or screens.
 * Where the candidates come from is MovieRepository.picksCandidates.
 */

/** Why a movie was picked. The screen turns it into a sentence ("Similar to ...", "Top-rated Happy pick"). */
sealed class PickReason {
    /** It is like a movie you swiped right: TMDB listed it as similar to it, or they share most of their genres. */
    data class SimilarTo(val likedTitle: String) : PickReason()

    /** It is very well rated and fits one of the moods you chose ([moodId] is null when no mood was chosen). */
    data class TopRated(val moodId: String?) : PickReason()

    /** It fits one of the moods you chose. */
    data class MoodMatch(val moodId: String) : PickReason()

    /** No mood was chosen and nothing was swiped right. */
    object Generic : PickReason()
}

/** One pick: the movie, its score (0 to 1) and why it is there. */
data class Recommendation(val movie: Movie, val score: Double, val reason: PickReason) {
    /** The "92% match" shown on the screen. */
    val matchPercent: Int get() = (score * 100).roundToInt().coerceIn(0, 100)
}

/** The parts of one score, each from 0 to 1 ([passPenalty] is taken OFF the total). Handy for tests and for tuning. */
data class ScoreBreakdown(
    val genreMatch: Double,
    val quality: Double,
    val credibility: Double,
    val eraAndLanguage: Double,
    val passPenalty: Double
) {
    val total: Double
        get() = (GENRE_WEIGHT * genreMatch + QUALITY_WEIGHT * quality + CREDIBILITY_WEIGHT * credibility +
            ERA_LANGUAGE_WEIGHT * eraAndLanguage - passPenalty).coerceIn(0.0, 1.0)

    private companion object {
        const val GENRE_WEIGHT = 0.40
        const val QUALITY_WEIGHT = 0.35
        const val CREDIBILITY_WEIGHT = 0.15
        const val ERA_LANGUAGE_WEIGHT = 0.10
    }
}

/**
 * What we know about the user's taste, worked out ONCE for a whole list of candidates by [RecommendationEngine.tasteOf].
 * [genres] is the genre profile (genre id -> 0..1); the rest is what the era, language and penalty rules look at.
 */
class PicksTaste internal constructor(
    val genres: Map<Int, Double>,
    internal val liked: List<Movie>,
    internal val likedByGenre: Map<Int, Int>,
    internal val passedByGenre: Map<Int, Int>
) {
    internal val genreLength: Double = sqrt(genres.values.sumOf { it * it })
}

class RecommendationEngine(
    private val maxPicks: Int = MAX_PICKS,
    private val maxPerFirstGenre: Int = MAX_PER_FIRST_GENRE
) {

    // =================================================================================================================
    //  The taste
    // =================================================================================================================

    /** Genre id -> how much the user likes it (0 to 1). Genres with no liking at all are left out. */
    fun genreProfile(moods: List<Mood>, liked: List<Movie>, passed: List<Movie>): Map<Int, Double> {
        val totals = HashMap<Int, Double>()
        for (genre in moods.flatMap { it.genreIds }.distinct()) totals.add(genre, MOOD_GENRE_POINTS)
        for (movie in liked) for (genre in movie.genreIds.distinct()) totals.add(genre, LIKED_GENRE_POINTS)
        for (movie in passed) for (genre in movie.genreIds.distinct()) totals.add(genre, PASSED_GENRE_POINTS)

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

    /** The swiped-right movies to ask TMDB's "similar" list for: the [count] latest ones, newest first. */
    fun similarSeeds(liked: List<Movie>, count: Int = SIMILAR_SEED_COUNT): List<Movie> =
        liked.distinctBy { it.id }.takeLast(count).asReversed()

    fun tasteOf(moods: List<Mood>, liked: List<Movie>, passed: List<Movie>): PicksTaste = PicksTaste(
        genres = genreProfile(moods, liked, passed),
        liked = liked,
        likedByGenre = countByGenre(liked),
        passedByGenre = countByGenre(passed)
    )

    private fun countByGenre(movies: List<Movie>): Map<Int, Int> =
        movies.flatMap { it.genreIds.distinct() }.groupingBy { it }.eachCount()

    // =================================================================================================================
    //  The score
    // =================================================================================================================

    /** The score of one candidate, from 0 to 1. */
    fun score(candidate: Movie, taste: PicksTaste): Double = breakdown(candidate, taste).total

    fun breakdown(candidate: Movie, taste: PicksTaste): ScoreBreakdown = ScoreBreakdown(
        genreMatch = genreMatch(candidate, taste),
        quality = quality(candidate),
        credibility = credibility(candidate),
        eraAndLanguage = eraAndLanguageMatch(candidate, taste),
        passPenalty = passPenalty(candidate, taste)
    )

    /** IMDb's weighted rating of the movie, on TMDB's 0 to 10 scale. Few votes pull it toward the average movie. */
    fun weightedRating(voteAverage: Double?, voteCount: Int): Double {
        val votes = max(0, voteCount).toDouble()
        val rating = voteAverage ?: AVERAGE_RATING
        return votes / (votes + MIN_VOTES_WEIGHT) * rating + MIN_VOTES_WEIGHT / (votes + MIN_VOTES_WEIGHT) * AVERAGE_RATING
    }

    /** [weightedRating] put on 0..1 (5.0 and below is 0, 9.0 and above is 1). */
    fun quality(movie: Movie): Double =
        ((weightedRating(movie.voteAverage, movie.voteCount) - QUALITY_FLOOR) / (QUALITY_CEILING - QUALITY_FLOOR))
            .coerceIn(0.0, 1.0)

    /** How well known the movie is, on a log scale: 0 votes is 0, [CREDIBLE_VOTES] votes or more is 1. */
    fun credibility(movie: Movie): Double =
        (ln(1.0 + max(0, movie.voteCount)) / ln(1.0 + CREDIBLE_VOTES)).coerceIn(0.0, 1.0)

    /** Cosine similarity between the candidate's genres (each counts 1) and the genre profile. */
    private fun genreMatch(candidate: Movie, taste: PicksTaste): Double {
        if (taste.genres.isEmpty()) return NEUTRAL // nothing to go on: no genre is better than another
        val genres = candidate.genreIds.distinct()
        if (genres.isEmpty()) return 0.0
        val dot = genres.sumOf { taste.genres[it] ?: 0.0 }
        return (dot / (sqrt(genres.size.toDouble()) * taste.genreLength)).coerceIn(0.0, 1.0)
    }

    /** Half decade, half language: how much the movie looks like the swiped-right ones in age and original language. */
    private fun eraAndLanguageMatch(candidate: Movie, taste: PicksTaste): Double =
        (decadeMatch(candidate.releaseYear, taste.liked) + languageMatch(candidate.originalLanguage, taste.liked)) / 2.0

    /** Same decade as a swiped-right movie 1, the next decade 0.5, further away 0: the average over those movies. */
    private fun decadeMatch(year: Int?, liked: List<Movie>): Double {
        val decades = liked.mapNotNull { it.releaseYear }.map { it / 10 }
        if (year == null || decades.isEmpty()) return NEUTRAL
        return decades.map { decade ->
            when (abs(decade - year / 10)) {
                0 -> 1.0
                1 -> 0.5
                else -> 0.0
            }
        }.average()
    }

    /** The share of the swiped-right movies that have the same original language. */
    private fun languageMatch(language: String?, liked: List<Movie>): Double {
        val languages = liked.mapNotNull { it.originalLanguage }
        if (language == null || languages.isEmpty()) return NEUTRAL
        return languages.count { it.equals(language, ignoreCase = true) }.toDouble() / languages.size
    }

    /** Taken off the score of a movie whose first genre the user clearly passed more often than they liked. */
    private fun passPenalty(candidate: Movie, taste: PicksTaste): Double {
        val primary = candidate.genreIds.firstOrNull() ?: return 0.0
        val net = (taste.passedByGenre[primary] ?: 0) - (taste.likedByGenre[primary] ?: 0)
        if (net < HEAVY_PASS_NET) return 0.0
        return min(MAX_PASS_PENALTY, MAX_PASS_PENALTY * net / PASS_PENALTY_FULL_AT)
    }

    // =================================================================================================================
    //  The list
    // =================================================================================================================

    /**
     * Scores the [candidates] and returns the picks, best first.
     * A movie is never picked when its id is in [excludeIds], when it was liked or passed in this sitting,
     * when it has no poster, or when it is in the list twice. The order is the same every time for the same input.
     *
     * [similarTo] says which candidates TMDB listed as similar to one of the swiped-right movies:
     * candidate id -> that movie's id. It only decides the wording of the reason.
     */
    fun recommend(
        candidates: List<Movie>,
        moods: List<Mood>,
        liked: List<Movie>,
        passed: List<Movie>,
        excludeIds: Set<Int> = emptySet(),
        similarTo: Map<Int, Int> = emptyMap()
    ): List<Recommendation> {
        val taste = tasteOf(moods, liked, passed)
        val skip = excludeIds + liked.map { it.id } + passed.map { it.id }

        val ranked = candidates
            .filter { it.id !in skip && it.posterPath != null }
            .distinctBy { it.id }
            .map { Recommendation(it, score(it, taste), reasonFor(it, moods, liked, similarTo[it.id])) }
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

    /**
     * The reason, from the strongest claim to the weakest:
     * 1. TMDB says it is similar to a movie you swiped right, or it shares most genres with one: "Similar to ...".
     * 2. It is very well rated and fits a mood (or there is no mood): "Top-rated ... pick".
     * 3. It fits a mood: "Matches your ... mood".
     * 4. Nothing to say: "Picked for you".
     */
    private fun reasonFor(candidate: Movie, moods: List<Mood>, liked: List<Movie>, similarSeedId: Int?): PickReason {
        val seed = liked.firstOrNull { it.id == similarSeedId } ?: mostSimilarLiked(candidate, liked)
        if (seed != null) return PickReason.SimilarTo(seed.title)

        val genres = candidate.genreIds.toSet()
        val bestMood = moods
            .map { it to it.genreIds.count { genre -> genre in genres } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
            ?: moods.firstOrNull() // a mood with no genres (Surprise Me) still names the mood

        return when {
            quality(candidate) >= TOP_RATED_QUALITY -> PickReason.TopRated(bestMood?.id)
            bestMood != null -> PickReason.MoodMatch(bestMood.id)
            else -> PickReason.Generic
        }
    }

    /** The swiped-right movie that shares at least half of its genres with the candidate (the first one wins a tie). */
    private fun mostSimilarLiked(candidate: Movie, liked: List<Movie>): Movie? {
        val genres = candidate.genreIds.toSet()
        if (genres.isEmpty()) return null
        return liked
            .map { movie ->
                val shared = movie.genreIds.toSet().count { it in genres }
                val all = (movie.genreIds.toSet() + genres).size
                movie to shared.toDouble() / all // Jaccard: 1 = exactly the same genres
            }
            .filter { it.second >= SIMILAR_GENRE_OVERLAP }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun HashMap<Int, Double>.add(genre: Int, points: Double) {
        this[genre] = (this[genre] ?: 0.0) + points
    }

    companion object {
        const val MAX_PICKS = 10
        const val MAX_PER_FIRST_GENRE = 3
        const val TOP_GENRE_COUNT = 3
        const val SIMILAR_SEED_COUNT = 3

        // The genre profile
        private const val MOOD_GENRE_POINTS = 1.0
        private const val LIKED_GENRE_POINTS = 2.0
        private const val PASSED_GENRE_POINTS = -1.0

        // The quality score: IMDb's weighted rating (m = 500 votes, C = 6.8) mapped from 5.0..9.0 to 0..1
        private const val MIN_VOTES_WEIGHT = 500.0
        private const val AVERAGE_RATING = 6.8
        private const val QUALITY_FLOOR = 5.0
        private const val QUALITY_CEILING = 9.0
        private const val CREDIBLE_VOTES = 20_000.0

        // The pass penalty
        private const val HEAVY_PASS_NET = 2          // passed at least twice more often than liked...
        private const val PASS_PENALTY_FULL_AT = 4.0  // ...the penalty grows to its maximum at 4
        private const val MAX_PASS_PENALTY = 0.20

        // The reason
        private const val SIMILAR_GENRE_OVERLAP = 0.5
        private const val TOP_RATED_QUALITY = 0.6     // a weighted rating of about 7.4 and up

        private const val NEUTRAL = 0.5
        private const val NO_GENRE = -1
    }
}
