package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/*
 * HOW THE SWIPE DECK IS CHOSEN
 *
 * The deck is not "a list": it is a mix of several KINDS of movies, so it does not feel repetitive.
 *   - what matches your mood (and your preferences)
 *   - the best rated ones in those genres
 *   - hidden gems: highly rated but not many votes
 *   - what is trending, what is popular
 * Movies that appear in more than one kind are shown once, and movies you already swiped are skipped.
 *
 * If your mood + preferences are too narrow (few movies match), we loosen them one step at a time:
 * first the language, then the length, the release period, the minimum rating and, last, the genres.
 * The cards that only appear after loosening are labelled "Something different".
 *
 * This file is plain Kotlin and knows nothing about TMDB. It only describes WHAT to look for
 * ([DiscoveryRequest]) and how to mix the answers ([mix], [buildDeck]).
 * The answers come from TMDB: SwipeViewModel sends every request to MovieRepository.moviesFor
 * (a /discover, /trending or /movie/popular call, see data/DiscoverQuery.kt for the translation).
 */

/** The kind of movie a card is, shown as a small line above its title. */
enum class DiscoverySource(@StringRes val label: Int) {
    MOOD_MATCH(R.string.discovery_mood_match),
    TOP_RATED(R.string.discovery_top_rated),
    HIDDEN_GEM(R.string.discovery_hidden_gem),
    TRENDING(R.string.discovery_trending),
    POPULAR(R.string.discovery_popular),

    /** The mood and preferences were loosened to find this one. */
    EXPLORE(R.string.discovery_explore)
}

/**
 * One question to ask a movie source: "give me [source]-type movies that fit all of this".
 * A null or empty field means "no limit".
 *
 * [matches] says whether a movie fits. TMDB's own /discover does this filtering on its server, but its
 * trending and popular lists cannot be filtered, so those answers are checked with [matches] on the phone.
 */
data class DiscoveryRequest(
    val source: DiscoverySource,
    val genreIds: List<Int> = emptyList(),   // a movie needs at least ONE of these genres
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minRating: Double? = null,
    val minMinutes: Int? = null,
    val maxMinutes: Int? = null,
    val language: String? = null,
    val minVotes: Int? = null,
    val maxVotes: Int? = null
) {
    fun matches(movie: Movie): Boolean {
        if (movie.isExplicit()) return false
        if (genreIds.isNotEmpty() && movie.genreIds.none { it in genreIds }) return false

        // The year and rating are always known, so a movie without them cannot meet a limit
        if (yearFrom != null && (movie.releaseYear ?: return false) < yearFrom) return false
        if (yearTo != null && (movie.releaseYear ?: return false) > yearTo) return false
        if (minRating != null && (movie.voteAverage ?: return false) < minRating) return false

        // TMDB's list answers have no runtime. When it is unknown we do not throw the movie out:
        // the server already applied the length limit when it was part of the question.
        val minutes = movie.runtimeMinutes
        if (minMinutes != null && minutes != null && minutes < minMinutes) return false
        if (maxMinutes != null && minutes != null && minutes > maxMinutes) return false

        val movieLanguage = movie.originalLanguage
        if (language != null && movieLanguage != null) {
            val matches = if (language == "tl") movieLanguage == "tl" || movieLanguage == "fil" else movieLanguage.equals(language, ignoreCase = true)
            if (!matches) return false
        }

        // Vote limits are about "how well known": 0 means we do not know, which can never be a hidden gem
        if (minVotes != null && movie.voteCount < minVotes) return false
        if (maxVotes != null && (movie.voteCount == 0 || movie.voteCount > maxVotes)) return false
        return true
    }
}

/** A movie in the deck together with the reason it is there. */
data class Pick(val movie: Movie, val source: DiscoverySource)

object Discovery {

    /** A new discovery round has exactly 10 cards at most (or fewer only if exhausted). */
    const val DECK_SIZE = 10

    /** ...and if fewer than this many fit the mood and preferences, the search is loosened to add more. */
    const val MIN_DECK_SIZE = 6

    /** How many times the search can be loosened (0 = exactly what the user asked for). */
    const val MAX_RELAX = 5

    private const val TOP_RATED_MIN_RATING = 7.5
    private const val HIDDEN_GEM_MIN_RATING = 7.0
    private const val HIDDEN_GEM_MIN_VOTES = 200
    private const val HIDDEN_GEM_MAX_VOTES = 5000

    /**
     * The questions to ask for a deck, at a given looseness: [relax] = 0 is everything the user asked for;
     * 1 forgets the runtime length; 2 also the release period; 3 also the minimum rating;
     * 4 also the mood's genres (anything goes).
     * The selected language is strictly preserved when set (never silently relaxed).
     */
    fun plan(moodGenreIds: List<Int>, preferences: Preferences, relax: Int): List<DiscoveryRequest> {
        val dropGenres = relax >= 4
        val (yearFrom, yearTo) = if (relax >= 2) NO_LIMIT else yearsOf(preferences.releasePeriod)
        val (minMinutes, maxMinutes) = if (relax >= 1) NO_LIMIT else minutesOf(preferences.length)

        val base = DiscoveryRequest(
            source = if (dropGenres) DiscoverySource.EXPLORE else DiscoverySource.MOOD_MATCH,
            genreIds = if (dropGenres) emptyList() else moodGenreIds,
            yearFrom = yearFrom,
            yearTo = yearTo,
            minRating = if (relax >= 3) null else ratingOf(preferences.minRating),
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
            language = languageCode(preferences.language)
        )
        val rating = base.minRating ?: 0.0

        return listOf(
            base,
            base.copy(source = DiscoverySource.TOP_RATED, minRating = max(rating, TOP_RATED_MIN_RATING)),
            base.copy(
                source = DiscoverySource.HIDDEN_GEM, minRating = max(rating, HIDDEN_GEM_MIN_RATING),
                minVotes = if (base.language == "tl") 3 else HIDDEN_GEM_MIN_VOTES,
                maxVotes = if (base.language == "tl") 100 else HIDDEN_GEM_MAX_VOTES
            ),
            base.copy(source = DiscoverySource.TRENDING),
            base.copy(source = DiscoverySource.POPULAR)
        )
    }

    /**
     * Mixes the answers into one list: one movie from the first kind, one from the second, and so on,
     * round and round. A movie is used only once (the first time it shows up), movies in [exclude]
     * (already swiped or already in the deck) are skipped, and at most [limit] picks are returned.
     */
    fun mix(
        batches: List<Pair<DiscoverySource, List<Movie>>>,
        exclude: Set<Int>,
        limit: Int
    ): List<Pick> {
        val used = exclude.toMutableSet()
        val queues = batches.map { (source, movies) -> source to ArrayDeque(movies) }
        val picks = ArrayList<Pick>()

        while (picks.size < limit && queues.any { it.second.isNotEmpty() }) {
            for ((source, queue) in queues) {
                if (picks.size >= limit) break
                // The next movie of this kind that has not been used yet
                while (queue.isNotEmpty()) {
                    val movie = queue.removeFirst()
                    if (used.add(movie.id)) {
                        picks.add(Pick(movie, source))
                        break
                    }
                }
            }
        }
        return picks
    }

    /**
     * Builds a deck (or the next page of one). It asks [fetch] the questions of [plan] and mixes the answers.
     * If too few movies fit, it asks again with the search loosened, and adds those after the first ones.
     * Movies in [exclude] are never returned. An empty result means there is nothing new to show.
     *
     * [fetch] is the ONLY place that knows where movies come from: the MovieRepository (TMDB).
     * The five questions of one step are asked at the same time (not one after the other), so a deck
     * costs one round trip to TMDB instead of five. If [fetch] throws, the whole call throws.
     */
    suspend fun buildDeck(
        moodGenreIds: List<Int>,
        preferences: Preferences,
        exclude: Set<Int>,
        fetch: suspend (DiscoveryRequest) -> List<Movie>,
        limit: Int = DECK_SIZE,
        wanted: Int = MIN_DECK_SIZE
    ): List<Pick> {
        val deck = ArrayList<Pick>()
        val used = exclude.toMutableSet()
        val langCode = languageCode(preferences.language)

        val maxRelax = if (langCode != null) 3 else MAX_RELAX
        for (relax in 0..maxRelax) {
            if (deck.size >= wanted) break
            // awaitAll keeps the order of the questions, so the mix is the same as when they were asked one by one
            val batches = coroutineScope {
                plan(moodGenreIds, preferences, relax)
                    .map { request -> async { request.source to fetch(request) } }
                    .awaitAll()
            }
            val filteredBatches = if (langCode != null) {
                batches.map { (source, movies) ->
                    source to movies.filter { movie ->
                        val ml = movie.originalLanguage
                        if (ml == null) true
                        else if (langCode == "tl") ml == "tl" || ml == "fil"
                        else ml.equals(langCode, ignoreCase = true)
                    }
                }
            } else batches
            val more = mix(filteredBatches, used, limit - deck.size)
            deck.addAll(more)
            used.addAll(more.map { it.movie.id })
            if (langCode != null && deck.isNotEmpty() && relax >= 3) break
        }
        return deck
    }

    // ---------- What the Preferences screen's choices mean ----------

    // Each of these gives a pair: (from, to), where null means "no limit on that side"
    private val NO_LIMIT = Pair<Int?, Int?>(null, null)

    private fun yearsOf(period: ReleasePeriod): Pair<Int?, Int?> = when (period) {
        ReleasePeriod.ANY -> NO_LIMIT
        ReleasePeriod.BEFORE_2000 -> Pair(null, 1999)
        ReleasePeriod.FROM_2000_TO_2014 -> Pair(2000, 2014)
        ReleasePeriod.FROM_2015 -> Pair(2015, null)
    }

    private fun ratingOf(rating: MinRating): Double? = when (rating) {
        MinRating.ANY -> null
        MinRating.SIX_PLUS -> 6.0
        MinRating.SEVEN_PLUS -> 7.0
        MinRating.EIGHT_PLUS -> 8.0
    }

    private fun minutesOf(length: Length): Pair<Int?, Int?> = when (length) {
        Length.ANY -> NO_LIMIT
        Length.UNDER_90_MIN -> Pair(null, 89)
        Length.UNDER_2_HOURS -> Pair(null, 119)
        Length.OVER_2_HOURS -> Pair(120, null)
    }

    fun languageCode(language: Language): String? = when (language) {
        Language.ANY -> null
        Language.ENGLISH -> "en"
        Language.FILIPINO -> "tl"
        Language.KOREAN -> "ko"
        Language.JAPANESE -> "ja"
    }
}
