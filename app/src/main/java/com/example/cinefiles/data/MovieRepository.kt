package com.example.cinefiles.data

import com.example.cinefiles.data.dto.MovieListResponse
import com.example.cinefiles.data.dto.toMovies
import com.example.cinefiles.logic.BrowseCategory
import com.example.cinefiles.logic.Discovery
import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.Language
import com.example.cinefiles.logic.Length
import com.example.cinefiles.logic.MinRating
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.RecommendationEngine
import com.example.cinefiles.logic.ReleasePeriod
import com.example.cinefiles.logic.canLike
import com.example.cinefiles.logic.trendingSince
import com.example.cinefiles.util.currentYear
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * THE one class that gets movies: from TMDB (the internet) and from Room (the phone's own storage).
 * The app has one of these, made in CineFilesApp: `(application as CineFilesApp).movieRepository`.
 *
 * ---- The TMDB part ----
 * Every function:
 *  - is a `suspend` function (call it from a ViewModel's viewModelScope),
 *  - never throws. It returns a [Result]: `onSuccess { movies -> ... }` or `onFailure { error -> friendlyError(error) }`,
 *  - returns only movies that have a title and a poster (see toMovies()),
 *  - fails with [MissingTokenException] when there is no TMDB token, without touching the network.
 * Lists are paged: [page] 1 is the first 20 movies, page 2 the next 20, and so on.
 *
 * ---- The Room part (further down) ----
 * What the user did with a movie: liked, passed, watchlist, watched. Works without internet.
 * Every "set a state" function first saves the movie if it is not saved yet, then changes the state.
 */
class MovieRepository(
    private val api: TmdbApi,
    private val hasToken: Boolean,
    private val dao: MovieDao,
    private val diaryDao: DiaryDao? = null
) {

    // Movies seen in the lists during this run of the app (NOT saved to the database). When the user opens
    // Details, the title and the picture are known at once, while the rest is still loading.
    // It keeps the last 500 movies; the oldest ones are forgotten.
    private val seen = object : LinkedHashMap<Int, Movie>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Movie>?): Boolean = size > MAX_SEEN
    }

    @Synchronized
    private fun remember(movies: List<Movie>): List<Movie> {
        for (movie in movies) seen[movie.id] = movie
        return movies
    }

    /** A movie that was in a list during this run (title, poster...), or null. */
    @Synchronized
    fun recentlySeen(movieId: Int): Movie? = seen[movieId]

    // ====================================================================================
    //  TMDB: lists
    // ====================================================================================

    suspend fun trending(page: Int = 1): Result<List<Movie>> =
        call { remember(api.trending(page).results.toMovies()) }

    suspend fun popular(page: Int = 1): Result<List<Movie>> =
        call { remember(api.popular(page).results.toMovies()) }

    suspend fun similar(movieId: Int, page: Int = 1): Result<List<Movie>> =
        call { remember(api.similar(movieId, page).results.toMovies()) }

    // ====================================================================================
    //  TMDB: the swipe deck
    // ====================================================================================

    /** TMDB's /discover with the mood and preferences of [request] as filters (see DiscoverQuery). */
    suspend fun discover(request: DiscoveryRequest, page: Int = 1): Result<List<Movie>> = call {
        if (page > MAX_PAGE) {
            emptyList() // TMDB refuses pages beyond 500: there is simply nothing more
        } else {
            val query = request.toDiscoverQuery()
            remember(
                api.discover(
                    withGenres = query.withGenres,
                    releasedFrom = query.releasedFrom,
                    releasedTo = query.releasedTo,
                    minRating = query.minRating,
                    minVotes = query.minVotes,
                    maxVotes = query.maxVotes,
                    minRuntime = query.minRuntime,
                    maxRuntime = query.maxRuntime,
                    originalLanguage = query.originalLanguage,
                    sortBy = query.sortBy,
                    page = page
                ).results.toMovies()
            )
        }
    }

    /**
     * The answer to ONE question of the swipe deck (logic/Discovery.kt), whatever its kind.
     * "Trending" and "popular" are TMDB lists that cannot be filtered by the server, so they are filtered
     * here with [DiscoveryRequest.matches]. Every other kind is a /discover call that TMDB filters itself.
     */
    suspend fun moviesFor(request: DiscoveryRequest, page: Int = 1): Result<List<Movie>> = when (request.source) {
        DiscoverySource.TRENDING -> if (request.language != null) discover(request, page) else trending(page).map { movies -> movies.filter(request::matches) }
        DiscoverySource.POPULAR -> if (request.language != null) discover(request, page) else popular(page).map { movies -> movies.filter(request::matches) }
        else -> discover(request, page)
    }

    // ====================================================================================
    //  TMDB: your picks (the candidates the RecommendationEngine scores)
    // ====================================================================================

    /**
     * The movies the engine may choose from, in three kinds of answers that are merged:
     *  - /discover for the user's best [genreIds] (joined with | = OR) and the [preferences], asked twice: best rated
     *    first (`vote_average.desc`) and most popular first (`popularity.desc`), two pages each;
     *  - /movie/{id}/similar for each of the [seeds] (the swiped-right movies, the engine chooses up to 3), kept when
     *    they pass the quality floor and the preferences.
     * The QUALITY FLOOR is a rating of 6.5 and 500 votes (300 for the "similar" movies). Only when that leaves fewer
     * than 10 valid candidates does the floor drop to 6.0 and 200 votes, and if there are still too few, the
     * preferences are forgotten too (the genres stay).
     * Movies that already have a state in the database (liked, passed, watchlist, watched), the ids in [exclude]
     * (this sitting's swipes) and movies without a poster never come back.
     * A failed "similar" call is no problem (it is a bonus); a failed /discover call fails the whole answer.
     */
    suspend fun picksCandidates(
        genreIds: List<Int>,
        preferences: Preferences,
        seeds: List<Movie> = emptyList(),
        exclude: Set<Int> = emptySet()
    ): Result<PicksPool> = call {
        val hidden = dao.idsWithState().toSet() + exclude
        val found = LinkedHashMap<Int, Movie>()
        val similarTo = HashMap<Int, Int>()
        var similarAnswers: Map<Int, List<Movie>>? = null // asked once: the floor is applied to it again at every step

        for (step in PICKS_STEPS) {
            val pref = if (step.dropPreferences) Preferences() else preferences
            val (discoverRequests, similarFilter) = picksRequests(genreIds, pref, step.floor)

            val askSimilar = similarAnswers == null
            val (discovered, similarAsked) = coroutineScope {
                val lists = discoverRequests.flatMap { request ->
                    (1..PICKS_PAGES).map { page -> async { discover(request, page).getOrThrow() } }
                }
                val similarLists = if (askSimilar) {
                    seeds.take(RecommendationEngine.SIMILAR_SEED_COUNT)
                        .map { seed -> async { seed.id to (similar(seed.id).getOrNull() ?: emptyList()) } }
                } else {
                    emptyList()
                }
                lists.awaitAll().flatten() to similarLists.awaitAll()
            }
            val answers = similarAnswers ?: similarAsked.toMap().also { similarAnswers = it }

            for (movie in discovered) if (movie.id !in hidden) found.putIfAbsent(movie.id, movie)
            for ((seedId, movies) in answers) {
                for (movie in movies) {
                    if (movie.id in hidden || !similarFilter.matches(movie)) continue
                    found.putIfAbsent(movie.id, movie)
                    similarTo.putIfAbsent(movie.id, seedId)
                }
            }
            if (found.size >= RecommendationEngine.MAX_PICKS) break
        }
        PicksPool(found.values.toList(), similarTo.filterKeys { it in found })
    }

    /**
     * The questions of one step of [picksCandidates]: the two /discover requests (best rated first, most popular first)
     * and the filter that "similar" movies must pass. The floor never lowers what the user's own preferences ask
     * (a minimum rating of 8 stays 8). Filipino movies have few votes on TMDB, so their vote limits stay tiny.
     */
    private fun picksRequests(genreIds: List<Int>, preferences: Preferences, floor: QualityFloor): Pair<List<DiscoveryRequest>, DiscoveryRequest> {
        val base = Discovery.plan(genreIds, preferences, NO_RELAXING).first()
        val rating = max(base.minRating ?: 0.0, floor.minRating)
        val few = preferences.language == Language.FILIPINO
        val discoverBase = base.copy(minRating = rating, minVotes = if (few) FILIPINO_MIN_VOTES else floor.minVotes)
        return listOf(
            discoverBase.copy(source = DiscoverySource.TOP_RATED),  // /discover sorted by vote_average.desc
            discoverBase                                            // /discover sorted by popularity.desc
        ) to discoverBase.copy(genreIds = emptyList(), minVotes = if (few) FILIPINO_MIN_VOTES else floor.minSimilarVotes)
    }

    /** The TMDB person with their movies, for the Person screen. */
    suspend fun person(personId: Int): Result<PersonDetails> = call {
        val details = api.person(personId).toPersonDetails() ?: error("TMDB sent no usable person for id $personId")
        remember(details.acting + details.crew) // opening one of their movies then shows its title and poster at once
        details
    }

    // ====================================================================================
    //  TMDB: long lists for Discover (page after page)
    // ====================================================================================

    /** One page of search results. */
    suspend fun searchPage(query: String, page: Int = 1): Result<MoviePage> {
        if (query.isBlank()) return Result.success(MoviePage(emptyList(), 1, 1))
        return call { api.search(query.trim(), page).toMoviePage(page) }
    }

    /**
     * One page of a Discover list. With no genre, the three categories are TMDB's own lists (popular, trending,
     * top rated). With a genre they are /discover calls, because TMDB's own lists cannot be filtered by genre.
     */
    suspend fun browsePage(
        category: BrowseCategory,
        genreId: Int?,
        page: Int = 1,
        preferences: Preferences = Preferences(),
        moodGenreIds: List<Int> = emptyList()
    ): Result<MoviePage> = call {
        val (fromYear, toYear) = Discovery.yearsOf(preferences.releasePeriod)
        val (minMin, maxMin) = Discovery.minutesOf(preferences.length)
        val langCode = Discovery.languageCode(preferences.language)
        val minRating = Discovery.ratingOf(preferences.minRating)
        val hasCustomPrefs = preferences.releasePeriod != ReleasePeriod.ANY ||
                preferences.minRating != MinRating.ANY ||
                preferences.length != Length.ANY ||
                preferences.language != Language.ANY

        val response = if (genreId == null) {
            when (category) {
                BrowseCategory.ALL -> {
                    val genresStr = if (moodGenreIds.isNotEmpty()) moodGenreIds.joinToString("|") else null
                    val minVotes = if (langCode == "tl") 5 else 20
                    api.discover(
                        withGenres = genresStr,
                        releasedFrom = fromYear?.let { "$it-01-01" },
                        releasedTo = toYear?.let { "$it-12-31" },
                        minRating = minRating,
                        minVotes = minVotes,
                        minRuntime = minMin,
                        maxRuntime = maxMin,
                        originalLanguage = langCode,
                        sortBy = "popularity.desc",
                        page = page
                    )
                }
                BrowseCategory.POPULAR -> {
                    if (hasCustomPrefs) {
                        val minVotes = if (langCode == "tl") 5 else 100
                        api.discover(
                            releasedFrom = fromYear?.let { "$it-01-01" },
                            releasedTo = toYear?.let { "$it-12-31" },
                            minRating = minRating,
                            minVotes = minVotes,
                            minRuntime = minMin,
                            maxRuntime = maxMin,
                            originalLanguage = langCode,
                            sortBy = "popularity.desc",
                            page = page
                        )
                    } else {
                        api.popular(page)
                    }
                }
                BrowseCategory.TRENDING -> {
                    if (hasCustomPrefs) {
                        val minVotes = if (langCode == "tl") 5 else 20
                        api.discover(
                            releasedFrom = fromYear?.let { "$it-01-01" } ?: trendingSince(currentYear()),
                            releasedTo = toYear?.let { "$it-12-31" },
                            minRating = minRating,
                            minVotes = minVotes,
                            minRuntime = minMin,
                            maxRuntime = maxMin,
                            originalLanguage = langCode,
                            sortBy = "popularity.desc",
                            page = page
                        )
                    } else {
                        api.trending(page)
                    }
                }
                BrowseCategory.TOP_RATED -> {
                    if (hasCustomPrefs) {
                        val minVotes = if (langCode == "tl") 5 else 300
                        val rating = maxOf(minRating ?: 0.0, 7.5)
                        api.discover(
                            releasedFrom = fromYear?.let { "$it-01-01" },
                            releasedTo = toYear?.let { "$it-12-31" },
                            minRating = rating,
                            minVotes = minVotes,
                            minRuntime = minMin,
                            maxRuntime = maxMin,
                            originalLanguage = langCode,
                            sortBy = "vote_average.desc",
                            page = page
                        )
                    } else {
                        api.topRated(page)
                    }
                }
                else -> api.popular(page)
            }
        } else {
            val withGenre = genreId.toString()
            when (category) {
                BrowseCategory.TRENDING -> {
                    val minVotes = if (langCode == "tl") 5 else 20
                    api.discover(
                        withGenres = withGenre,
                        releasedFrom = fromYear?.let { "$it-01-01" } ?: trendingSince(currentYear()),
                        releasedTo = toYear?.let { "$it-12-31" },
                        minRating = minRating,
                        minVotes = minVotes,
                        minRuntime = minMin,
                        maxRuntime = maxMin,
                        originalLanguage = langCode,
                        sortBy = "popularity.desc",
                        page = page
                    )
                }
                BrowseCategory.TOP_RATED -> {
                    val minVotes = if (langCode == "tl") 5 else 300
                    val rating = maxOf(minRating ?: 0.0, 7.5)
                    api.discover(
                        withGenres = withGenre,
                        releasedFrom = fromYear?.let { "$it-01-01" },
                        releasedTo = toYear?.let { "$it-12-31" },
                        minRating = rating,
                        minVotes = minVotes,
                        minRuntime = minMin,
                        maxRuntime = maxMin,
                        originalLanguage = langCode,
                        sortBy = "vote_average.desc",
                        page = page
                    )
                }
                else -> {
                    val minVotes = if (langCode == "tl") 5 else 100
                    api.discover(
                        withGenres = withGenre,
                        releasedFrom = fromYear?.let { "$it-01-01" },
                        releasedTo = toYear?.let { "$it-12-31" },
                        minRating = minRating,
                        minVotes = minVotes,
                        minRuntime = minMin,
                        maxRuntime = maxMin,
                        originalLanguage = langCode,
                        sortBy = "popularity.desc",
                        page = page
                    )
                }
            }
        }
        response.toMoviePage(page)
    }

    private fun MovieListResponse.toMoviePage(page: Int): MoviePage {
        // TMDB never serves pages beyond 500, and a missing page count means "this was the only page"
        val lastPage = maxOf(page, minOf(totalPages ?: page, MAX_PAGE))
        return MoviePage(remember(results.toMovies()), page, lastPage)
    }

    // ====================================================================================
    //  TMDB: one movie
    // ====================================================================================

    /**
     * The movie with its cast, directors and trailer, for the Details screen.
     * If the movie is saved on the phone, its saved runtime and overview are refreshed too, so the Details
     * screen can show them next time without internet.
     */
    suspend fun movieDetails(movieId: Int): Result<MovieDetails> = call {
        val details = api.movieDetails(movieId).toDetails() ?: error("TMDB sent no usable movie for id $movieId")
        remember(listOf(details.movie))
        dao.updateInfo(movieId, details.movie.overview, details.movie.runtimeMinutes)
        details
    }

    // ====================================================================================
    //  Room: reading what the user saved (live: the lists send themselves again when something changes)
    // ====================================================================================

    fun observeLiked(): Flow<List<Movie>> = dao.observeReacted(Reaction.LIKED)

    fun observePassed(): Flow<List<Movie>> = dao.observeReacted(Reaction.PASSED)

    fun observeWatchlist(): Flow<List<Movie>> = dao.observeWatchlist()

    fun observeWatched(): Flow<List<Movie>> = dao.observeWatched()

    /** The numbers for Home and the Collection tabs. */
    fun observeCounts(): Flow<CollectionCounts> = dao.observeCounts()

    /** One saved movie, live (null while it is not saved). */
    fun observeSaved(movieId: Int): Flow<Movie?> = dao.observe(movieId)

    suspend fun savedMovie(movieId: Int): Movie? = dao.get(movieId)

    /** The ids of all movies the user has any state for. The swipe deck never shows them again. */
    suspend fun idsWithState(): List<Int> = dao.idsWithState()

    /** Live: every movie with a state. Discover puts badges on the movies TMDB sends with it. */
    fun observeWithState(): Flow<List<Movie>> = dao.observeWithState()

    // ====================================================================================
    //  Room: changing what the user saved
    // ====================================================================================

    /**
     * Likes the movie. THE LIKE RULE (logic/CollectionRules.kt: canLike): a movie that is on the watchlist cannot
     * be liked. Then nothing changes and this returns false, so the screen can say why. True = it is liked now.
     * The check uses the SAVED copy of the movie, not the one the caller holds, which may be out of date.
     */
    suspend fun like(movie: Movie): Boolean {
        saveIfMissing(movie)
        if (!canLike(dao.get(movie.id))) return false
        dao.setReaction(movie.id, Reaction.LIKED, now())
        return true
    }

    suspend fun pass(movie: Movie) {
        saveIfMissing(movie)
        dao.setReaction(movie.id, Reaction.PASSED, now())
    }

    /** Takes back a like or a pass (Undo on the swipe screen, or "Unlike"). The movie itself stays saved. */
    suspend fun clearReaction(movieId: Int) = dao.setReaction(movieId, Reaction.NONE, 0L)

    /** Adds to the watchlist. If the movie was marked watched, it is moved back (it is never both). */
    suspend fun addToWatchlist(movie: Movie) {
        saveIfMissing(movie)
        dao.addToWatchlist(movie.id, now())
    }

    suspend fun removeFromWatchlist(movieId: Int) = dao.removeFromWatchlist(movieId)

    /** Marks as watched, which also takes the movie off the watchlist. */
    suspend fun markWatched(movie: Movie) {
        saveIfMissing(movie)
        dao.markWatched(movie.id, now())
    }

    suspend fun unmarkWatched(movieId: Int) = dao.unmarkWatched(movieId)

    /** Undo: puts the six state fields of [before] back, exactly as they were (nothing else is touched). */
    suspend fun restoreState(before: Movie) = dao.restoreState(
        id = before.id,
        reaction = before.reaction,
        reactedAt = before.reactedAt,
        inWatchlist = before.inWatchlist,
        watchlistAddedAt = before.watchlistAddedAt,
        isWatched = before.isWatched,
        watchedAt = before.watchedAt
    )

    /** Deletes every saved movie and every state. Nothing in the app calls this yet (a later settings screen will). */
    suspend fun clearAllData() = dao.deleteAll()

    /** Saves the movie's information if this movie is not saved yet. A saved movie is never overwritten. */
    private suspend fun saveIfMissing(movie: Movie) {
        dao.insertIfMissing(movie)
    }

    private fun now(): Long = System.currentTimeMillis()

    // ====================================================================================
    //  Personal Ratings (0.5 to 5.0 stars, half-star increments)
    // ====================================================================================

    suspend fun setPersonalRating(movie: Movie, rating: Float?) {
        saveIfMissing(movie)
        if (rating == null || rating <= 0f) {
            dao.clearPersonalRating(movie.id)
        } else {
            val clamped = (Math.round(rating * 2f) / 2f).coerceIn(0.5f, 5.0f)
            dao.setPersonalRating(movie.id, clamped, now())
        }
    }

    suspend fun removePersonalRating(movieId: Int) {
        dao.clearPersonalRating(movieId)
    }

    fun observeRated(): Flow<List<Movie>> = dao.observeRated()

    fun observeRatedCount(): Flow<Int> = dao.observeRatedCount()

    // ====================================================================================
    //  Favorites (Maximum 4 movies)
    // ====================================================================================

    /**
     * Toggles favorite status.
     * Returns true if favorite was toggled.
     * Returns false if limit of 4 is already reached and cannot add another.
     */
    suspend fun toggleFavorite(movie: Movie): Boolean {
        saveIfMissing(movie)
        val current = dao.get(movie.id)
        if (current?.isFavorite == true) {
            dao.setFavorite(movie.id, false, 0L)
            return true
        }
        val count = dao.countFavorites()
        if (count >= 4) {
            return false // Limit of 4 reached
        }
        dao.setFavorite(movie.id, true, now())
        return true
    }

    suspend fun removeFavorite(movieId: Int) {
        dao.setFavorite(movieId, false, 0L)
    }

    fun observeFavorites(): Flow<List<Movie>> = dao.observeFavorites()

    suspend fun getFavorites(): List<Movie> = dao.getFavorites()

    // ====================================================================================
    //  Personal Movie Diary
    // ====================================================================================

    suspend fun saveDiaryEntry(
        movie: Movie,
        notes: String,
        rating: Float?,
        dateWatched: Long,
        isRewatch: Boolean
    ): Long {
        saveIfMissing(movie)
        if (rating != null && rating > 0f) {
            setPersonalRating(movie, rating)
        }
        val entry = DiaryEntry(
            movieId = movie.id,
            movieTitle = movie.title,
            posterPath = movie.posterPath,
            releaseYear = movie.releaseYear,
            rating = rating,
            notes = notes,
            dateWatched = dateWatched,
            isRewatch = isRewatch,
            createdAt = now()
        )
        return diaryDao?.insert(entry) ?: 0L
    }

    suspend fun deleteDiaryEntry(id: Long) {
        diaryDao?.delete(id)
    }

    fun observeDiaryForMovie(movieId: Int): Flow<List<DiaryEntry>>? =
        diaryDao?.observeForMovie(movieId)

    fun observeDiary(): Flow<List<DiaryEntry>>? =
        diaryDao?.observeAll()

    fun observeDiaryCount(): Flow<Int>? =
        diaryDao?.observeCount()

    suspend fun getDiaryCount(): Int =
        diaryDao?.count() ?: 0

    // ====================================================================================
    //  Shared by the TMDB functions
    // ====================================================================================

    /** Runs one network call and turns anything that goes wrong into a failed [Result] instead of a crash. */
    private suspend fun <T> call(block: suspend () -> T): Result<T> {
        if (!hasToken) return Result.failure(MissingTokenException())
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e // the screen went away: this is not an error, and coroutines must be allowed to stop
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val MAX_PAGE = 500
        const val MAX_SEEN = 500

        // Your picks
        const val PICKS_PAGES = 2            // two pages of 20 for each way of sorting
        const val FILIPINO_MIN_VOTES = 3     // few people vote on Filipino movies on TMDB
        const val NO_RELAXING = 0            // Discovery.plan: keep every preference

        /** The quality a candidate must have: a rating, the votes of a /discover movie and the votes of a "similar" movie. */
        class QualityFloor(val minRating: Double, val minVotes: Int, val minSimilarVotes: Int)

        val STRICT_FLOOR = QualityFloor(minRating = 6.5, minVotes = 500, minSimilarVotes = 300)
        val RELAXED_FLOOR = QualityFloor(minRating = 6.0, minVotes = 200, minSimilarVotes = 200)

        /** What [picksCandidates] tries, in this order, until it has 10 candidates. */
        class PicksStep(val floor: QualityFloor, val dropPreferences: Boolean)

        val PICKS_STEPS = listOf(
            PicksStep(STRICT_FLOOR, dropPreferences = false),
            PicksStep(RELAXED_FLOOR, dropPreferences = false),
            PicksStep(RELAXED_FLOOR, dropPreferences = true)
        )
    }
}
