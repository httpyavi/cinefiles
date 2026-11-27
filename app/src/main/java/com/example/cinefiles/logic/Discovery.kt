package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import kotlin.math.max
import kotlin.math.min
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
 * EXACT MATCHES COME FIRST. They have one of the moods' genres, none of the genres those moods avoid (no horror for
 * Happy), and fit every preference (release period, minimum rating, length, language). A deck is 10 cards: each kind asks
 * TMDB page after page (at most [Discovery.MAX_PAGES_PER_BATCH] pages per batch) until 10 cards fit. When a narrow mix
 * still has too few, TMDB is asked again for less-known movies (200 votes, then 100, then 30).
 *
 * THEN THE CLOSEST ONES. Some mixes have no exact match at all on TMDB (a Korean comedy rated 8+, under 90 minutes, from
 * 2015 on). Then the rest of the batch is filled with the movies CLOSEST to what was asked ([Discovery.closeLevels]),
 * nearest first, and each says so on its card ("Close match"). The moods' genres are kept to the very end, so the deck
 * still fits the mood.
 *
 * This file is plain Kotlin and knows nothing about TMDB. It only describes WHAT to look for
 * ([DiscoveryRequest]) and how to mix the answers ([DiscoveryFeed]).
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

    /** Nothing more fits every choice: this is one of the closest movies (see [Discovery.closeLevels]). */
    CLOSE_MATCH(R.string.discovery_close_match)
}

/**
 * One question to ask a movie source: "give me [source]-type movies that fit all of this".
 * A null or empty field means "no limit".
 *
 * [matches] says whether a movie fits. TMDB's own /discover does this filtering on its server, but its
 * trending and popular lists cannot be filtered, so every answer is checked with [matches] on the phone too.
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
    val maxVotes: Int? = null,
    val withoutGenreIds: List<Int> = emptyList() // a movie may have NONE of these genres
) {
    /** A length was chosen. TMDB's list answers have no runtime, so it is looked up before such a movie is shown. */
    val hasRuntimeLimit: Boolean get() = minMinutes != null || maxMinutes != null

    /**
     * Trending and popular are TMDB's own lists, which only work when every limit can be checked on the phone from a list
     * answer (genres, year, rating). A language, a length or a vote limit needs /discover, and there trending or popular
     * would be the very same question as the mood match (same filters, same order): see [Discovery.plan].
     */
    val usesOwnList: Boolean
        get() = (source == DiscoverySource.TRENDING || source == DiscoverySource.POPULAR) &&
            language == null && !hasRuntimeLimit && minVotes == null && maxVotes == null

    fun matches(movie: Movie): Boolean {
        if (movie.isExplicit()) return false
        if (genreIds.isNotEmpty() && movie.genreIds.none { it in genreIds }) return false
        if (movie.genreIds.any { it in withoutGenreIds }) return false

        // A movie whose year, rating or language is unknown cannot meet a limit on it
        if (yearFrom != null && (movie.releaseYear ?: return false) < yearFrom) return false
        if (yearTo != null && (movie.releaseYear ?: return false) > yearTo) return false
        if (minRating != null && (movie.voteAverage ?: return false) < minRating) return false
        if (language != null && !Languages.matches(language, movie.originalLanguage ?: return false)) return false

        // TMDB's list answers have no runtime: an unknown one passes HERE, and [matchesStrictly] needs it known
        val minutes = movie.runtimeMinutes
        if (minMinutes != null && minutes != null && minutes < minMinutes) return false
        if (maxMinutes != null && minutes != null && minutes > maxMinutes) return false

        // Vote limits are about "how well known": 0 means we do not know, which can never be a hidden gem
        if (minVotes != null && movie.voteCount < minVotes) return false
        if (maxVotes != null && (movie.voteCount == 0 || movie.voteCount > maxVotes)) return false
        return true
    }

    /** [matches], and when a length was chosen the runtime must be KNOWN and inside it. What a shown movie must pass. */
    fun matchesStrictly(movie: Movie): Boolean = matches(movie) && (!hasRuntimeLimit || movie.runtimeMinutes != null)
}

/** A movie in the deck together with the reason it is there. */
data class Pick(val movie: Movie, val source: DiscoverySource)

object Discovery {

    /** A discovery batch has exactly 10 cards (fewer only when TMDB has nothing new at all for the moods). */
    const val DECK_SIZE = 10

    /** How many pages (of 20 movies) each question may ask TMDB for while one batch is filled. */
    const val MAX_PAGES_PER_BATCH = 5

    /** How well known a /discover movie must be by default: below this, a rating means little. */
    const val DEFAULT_MIN_VOTES = 200

    /** Few people vote on Filipino movies on TMDB, so their vote limit stays tiny. */
    const val FILIPINO_MIN_VOTES = 5

    /** The lower vote limits tried, in this order, when a narrow mix finds fewer than 10 exact matches. */
    val FEWER_VOTES = listOf(100, 30)

    private const val TOP_RATED_MIN_RATING = 7.5
    private const val HIDDEN_GEM_MIN_RATING = 7.0
    private const val HIDDEN_GEM_MIN_VOTES = 200
    private const val HIDDEN_GEM_MAX_VOTES = 5000
    private const val FILIPINO_GEM_MIN_VOTES = 3
    private const val FILIPINO_GEM_MAX_VOTES = 100

    // How much the close matches let go, step by step (see closeLevels)
    private const val LOWEST_CLOSE_RATING = 5.0

    /** A "quick watch" is still a movie: anything shorter than this is a short film, never dealt for a chosen length. */
    const val FEATURE_MIN_MINUTES = 40

    /** The vote limit a request without one gets ("vote_count.gte"). */
    fun defaultMinVotes(language: String?): Int =
        if (language == Languages.FILIPINO) FILIPINO_MIN_VOTES else DEFAULT_MIN_VOTES

    /**
     * What the user asked for, as one question: one of the moods' genres ([moodGenreIds], empty = any genre), none of the
     * genres those moods avoid ([avoidGenreIds], see Moods.avoidedGenreIds) and every preference. An EXACT match passes it.
     */
    fun userLimits(moodGenreIds: List<Int>, preferences: Preferences, avoidGenreIds: List<Int> = emptyList()): DiscoveryRequest {
        val (yearFrom, yearTo) = yearsOf(preferences.releasePeriod)
        val (minMinutes, maxMinutes) = minutesOf(preferences.length)
        return DiscoveryRequest(
            source = DiscoverySource.MOOD_MATCH,
            genreIds = moodGenreIds,
            yearFrom = yearFrom,
            yearTo = yearTo,
            minRating = ratingOf(preferences.minRating),
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
            language = preferences.language,
            withoutGenreIds = avoidGenreIds
        )
    }

    /**
     * The questions to ask for a deck: one per kind of movie, each with ALL of the user's limits ([userLimits]).
     * A kind may be stricter than the user (top rated wants 7.5 and up) but never looser.
     * Trending and popular are left out when they cannot use TMDB's own lists ([DiscoveryRequest.usesOwnList]):
     * they would only repeat the mood match's movies.
     */
    fun plan(moodGenreIds: List<Int>, preferences: Preferences, avoidGenreIds: List<Int> = emptyList()): List<DiscoveryRequest> {
        val base = userLimits(moodGenreIds, preferences, avoidGenreIds)
        val rating = base.minRating ?: 0.0
        val filipino = base.language == Languages.FILIPINO
        return listOf(
            base,
            base.copy(source = DiscoverySource.TOP_RATED, minRating = max(rating, TOP_RATED_MIN_RATING)),
            base.copy(
                source = DiscoverySource.HIDDEN_GEM,
                minRating = max(rating, HIDDEN_GEM_MIN_RATING),
                minVotes = if (filipino) FILIPINO_GEM_MIN_VOTES else HIDDEN_GEM_MIN_VOTES,
                maxVotes = if (filipino) FILIPINO_GEM_MAX_VOTES else HIDDEN_GEM_MAX_VOTES
            ),
            base.copy(source = DiscoverySource.TRENDING),
            base.copy(source = DiscoverySource.POPULAR)
        ).filter { !it.isOwnListKind() || it.usesOwnList }
    }

    private fun DiscoveryRequest.isOwnListKind() =
        source == DiscoverySource.TRENDING || source == DiscoverySource.POPULAR

    /**
     * The same question for LESS-KNOWN movies: between [floor] votes and one less than it asked for before, so it never
     * brings back a movie the question already found. Every other limit stays exactly the same.
     * Null when [floor] is not lower, or for TMDB's own lists (they have no vote limit).
     */
    fun withFewerVotes(request: DiscoveryRequest, floor: Int): DiscoveryRequest? {
        if (request.isOwnListKind()) return null
        val current = request.minVotes ?: defaultMinVotes(request.language)
        if (floor >= current) return null
        return request.copy(minVotes = floor, maxVotes = min(request.maxVotes ?: Int.MAX_VALUE, current - 1))
    }

    /** The questions of [plan], then the same questions for less-known movies ([FEWER_VOTES]), one level at a time. */
    fun levels(moodGenreIds: List<Int>, preferences: Preferences, avoidGenreIds: List<Int> = emptyList()): List<List<DiscoveryRequest>> {
        val levels = mutableListOf(plan(moodGenreIds, preferences, avoidGenreIds))
        var previous = levels.first()
        for (floor in FEWER_VOTES) {
            val lowered = previous.mapNotNull { withFewerVotes(it, floor) }
            if (lowered.isEmpty()) continue
            levels += lowered
            previous = lowered
        }
        return levels
    }

    /**
     * When too few movies fit [exact] (the [userLimits]): the searches for the CLOSEST movies, nearest first. Each step
     * lets go a little more of the less important choices; the moods' genres (and the genres they avoid) stay to the end:
     *  1. the minimum rating 1 point lower, the length 20 minutes wider, the release period 5 years wider;
     *  2. the rating 2 points lower (never under 5), the length 40 minutes wider, the release period 15 years wider;
     *  3. no rating, length or release period, but still the language;
     *  4. any language too (only the moods are left).
     * A step that would ask exactly what was already asked is left out, so with no preferences there is none at all.
     */
    fun closeLevels(exact: DiscoveryRequest): List<DiscoveryRequest> {
        fun loosen(ratingDrop: Double, minutes: Int, years: Int) = exact.copy(
            minRating = exact.minRating?.let { it - ratingDrop }?.takeIf { it >= LOWEST_CLOSE_RATING },
            minMinutes = exact.minMinutes?.let { (it - minutes).coerceAtLeast(FEATURE_MIN_MINUTES) },
            maxMinutes = exact.maxMinutes?.let { it + minutes },
            yearFrom = exact.yearFrom?.let { it - years },
            yearTo = exact.yearTo?.let { it + years }
        )
        val noPreferences = exact.copy(minRating = null, minMinutes = null, maxMinutes = null, yearFrom = null, yearTo = null)
        val asked = exact.asCloseMatch()
        return listOf(loosen(1.0, 20, 5), loosen(2.0, 40, 15), noPreferences, noPreferences.copy(language = null))
            .map { it.asCloseMatch() }
            .distinct()
            .filter { it != asked }
    }

    /** A close-match question: the best known first, down to the lowest vote limit the exact search used. */
    private fun DiscoveryRequest.asCloseMatch() = copy(
        source = DiscoverySource.CLOSE_MATCH,
        minVotes = if (language == Languages.FILIPINO) FILIPINO_MIN_VOTES else FEWER_VOTES.last(),
        maxVotes = null
    )

    /**
     * One deck of up to [limit] cards from a new [DiscoveryFeed] (handy for one-off decks and tests).
     * The swipe screen keeps ONE feed for the whole sitting instead, so "Discover 10 more" goes on where it stopped.
     */
    suspend fun buildDeck(
        moodGenreIds: List<Int>,
        preferences: Preferences,
        exclude: Set<Int>,
        fetch: suspend (request: DiscoveryRequest, page: Int) -> List<Movie>,
        lookUpRuntimes: suspend (List<Movie>) -> List<Movie> = { it },
        limit: Int = DECK_SIZE,
        avoidGenreIds: List<Int> = emptyList()
    ): List<Pick> = DiscoveryFeed(moodGenreIds, preferences, avoidGenreIds).next(exclude, fetch, lookUpRuntimes, limit)

    // ---------- What the Preferences screen's choices mean ----------

    // Each of these gives a pair: (from, to), where null means "no limit on that side"
    val NO_LIMIT = Pair<Int?, Int?>(null, null)

    fun yearsOf(period: ReleasePeriod): Pair<Int?, Int?> = when (period) {
        ReleasePeriod.ANY -> NO_LIMIT
        ReleasePeriod.BEFORE_2000 -> Pair(null, 1999)
        ReleasePeriod.FROM_2000_TO_2014 -> Pair(2000, 2014)
        ReleasePeriod.FROM_2015 -> Pair(2015, null)
    }

    fun ratingOf(rating: MinRating): Double? = when (rating) {
        MinRating.ANY -> null
        MinRating.SIX_PLUS -> 6.0
        MinRating.SEVEN_PLUS -> 7.0
        MinRating.EIGHT_PLUS -> 8.0
    }

    fun minutesOf(length: Length): Pair<Int?, Int?> = when (length) {
        Length.ANY -> NO_LIMIT
        Length.UNDER_90_MIN -> Pair(FEATURE_MIN_MINUTES, 89)
        Length.UNDER_2_HOURS -> Pair(FEATURE_MIN_MINUTES, 119)
        Length.OVER_2_HOURS -> Pair(120, null)
    }
}

/**
 * Where the swipe deck's cards come from, batch after batch, for ONE mood and set of preferences.
 * SwipeViewModel keeps one for the whole sitting, so every "Discover 10 more" goes on from where the last batch stopped:
 * each question remembers the next TMDB page to ask for and the movies it fetched but did not use yet.
 *
 * [next] fills a batch like this:
 *  1. each question with nothing waiting asks TMDB for its next page (all of them at the same time);
 *  2. one movie is taken from each question in turn (mood match, top rated, hidden gem, trending, popular...), skipping
 *     movies already seen, until the batch is full;
 *  3. when a length was chosen, the runtimes of those movies are looked up, and a movie that does not fit is dropped;
 *  4. this repeats until there are 10, or every question has asked [maxPagesPerBatch] pages or reached TMDB's last page.
 * The exact questions go first (all the vote levels of [Discovery.levels]); if the batch is still short, the close-match
 * questions of [Discovery.closeLevels] fill it, nearest first.
 */
class DiscoveryFeed(
    moodGenreIds: List<Int>,
    preferences: Preferences,
    avoidGenreIds: List<Int> = emptyList(),
    private val maxPagesPerBatch: Int = Discovery.MAX_PAGES_PER_BATCH
) {
    /** What an EXACT match fits: the moods' genres and all of the preferences. */
    val limits: DiscoveryRequest = Discovery.userLimits(moodGenreIds, preferences, avoidGenreIds)

    private class Question(val request: DiscoveryRequest) {
        var nextPage = 1
        var lastPage = false
        val waiting = ArrayDeque<Movie>()
    }

    /** A step of the search: its questions, and the limits a movie must fit to be dealt at this step. */
    private class Level(val questions: List<Question>, val fits: DiscoveryRequest)

    private val levels: List<Level> =
        Discovery.levels(moodGenreIds, preferences, avoidGenreIds).map { level -> Level(level.map { Question(it) }, limits) } +
            Discovery.closeLevels(limits).map { close -> Level(listOf(Question(close)), close) }

    /** Movies that turned out not to fit a step's limits (wrong or unknown runtime): never offered at that step again. */
    private val rejected = HashMap<DiscoveryRequest, MutableSet<Int>>()

    /**
     * The next batch of up to [limit] cards: exact matches first, then the closest ones. Movies in [exclude] (already seen)
     * are never returned, and an empty answer means TMDB has nothing new at all for the moods. [fetch] gets one page of a
     * question's answer: it is the ONLY place that knows where movies come from (the MovieRepository, TMDB). If it throws,
     * this throws. [lookUpRuntimes] fills in the runtime of movies that have none (only called when a length is part of
     * the limits); a movie it leaves out is dropped.
     */
    suspend fun next(
        exclude: Set<Int>,
        fetch: suspend (request: DiscoveryRequest, page: Int) -> List<Movie>,
        lookUpRuntimes: suspend (List<Movie>) -> List<Movie> = { it },
        limit: Int = Discovery.DECK_SIZE
    ): List<Pick> {
        val used = HashSet(exclude)
        val picks = ArrayList<Pick>()
        for (level in levels) {
            fill(level, picks, used, limit, fetch, lookUpRuntimes)
            if (picks.size >= limit) break
        }
        return picks
    }

    private suspend fun fill(
        level: Level,
        picks: MutableList<Pick>,
        used: MutableSet<Int>,
        limit: Int,
        fetch: suspend (DiscoveryRequest, Int) -> List<Movie>,
        lookUpRuntimes: suspend (List<Movie>) -> List<Movie>
    ) {
        val fits = level.fits
        val rejectedHere = rejected.getOrPut(fits) { HashSet() }
        val asked = HashMap<Question, Int>() // pages asked in THIS batch
        while (picks.size < limit) {
            // 1. Questions with nothing waiting ask for their next page. awaitAll keeps the order of the questions.
            val hungry = level.questions.filter { it.waiting.isEmpty() && !it.lastPage && (asked[it] ?: 0) < maxPagesPerBatch }
            if (hungry.isNotEmpty()) {
                val answers = coroutineScope {
                    hungry.map { question -> async { fetch(question.request, question.nextPage) } }.awaitAll()
                }
                hungry.zip(answers).forEach { (question, movies) ->
                    question.nextPage++
                    asked[question] = (asked[question] ?: 0) + 1
                    if (movies.isEmpty()) question.lastPage = true
                    question.waiting.addAll(movies.filter { fits.matches(it) && question.request.matches(it) })
                }
            }

            // 2. One from each question in turn
            val fresh = takeInTurn(level.questions, used, rejectedHere, limit - picks.size)
            if (fresh.isEmpty()) {
                if (hungry.isEmpty()) return // nothing waiting and nothing more to ask at this level
                continue
            }

            // 3. The runtime check, then the final strict check of every limit of this step
            val unknown = if (fits.hasRuntimeLimit) fresh.map { it.movie }.filter { it.runtimeMinutes == null } else emptyList()
            val found = if (unknown.isEmpty()) emptyMap() else lookUpRuntimes(unknown).associateBy { it.id }
            for (pick in fresh) {
                val movie = if (pick.movie.runtimeMinutes == null && fits.hasRuntimeLimit) found[pick.movie.id] else pick.movie
                if (movie != null && fits.matchesStrictly(movie)) {
                    // A close-match step can still meet an exact match (one TMDB listed late): it is not called "close"
                    val reallyExact = pick.source == DiscoverySource.CLOSE_MATCH && limits.matchesStrictly(movie)
                    picks += Pick(movie, if (reallyExact) DiscoverySource.MOOD_MATCH else pick.source)
                } else {
                    used -= pick.movie.id      // a looser step may still deal it
                    rejectedHere += pick.movie.id
                }
            }
        }
    }

    /** Up to [count] new movies, one from each question in turn. A movie is used once (the first question that has it). */
    private fun takeInTurn(questions: List<Question>, used: MutableSet<Int>, rejectedHere: Set<Int>, count: Int): List<Pick> {
        val taken = ArrayList<Pick>()
        while (taken.size < count && questions.any { it.waiting.isNotEmpty() }) {
            for (question in questions) {
                if (taken.size >= count) break
                while (question.waiting.isNotEmpty()) {
                    val movie = question.waiting.removeFirst()
                    if (movie.id !in rejectedHere && used.add(movie.id)) {
                        taken += Pick(movie, question.request.source)
                        break
                    }
                }
            }
        }
        return taken
    }
}
