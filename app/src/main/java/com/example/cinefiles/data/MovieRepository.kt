package com.example.cinefiles.data

import com.example.cinefiles.data.dto.MovieListResponse
import com.example.cinefiles.data.dto.toMovies
import com.example.cinefiles.data.dto.toStillPaths
import com.example.cinefiles.logic.BrowseCategory
import com.example.cinefiles.logic.Discovery
import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.Languages
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.RecommendationEngine
import com.example.cinefiles.logic.MAX_FAVORITES
import com.example.cinefiles.logic.PopcornRating
import com.example.cinefiles.logic.canFavorite
import com.example.cinefiles.logic.canWriteDiary
import com.example.cinefiles.logic.trendingSince
import com.example.cinefiles.logic.withoutWatched
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
 * What the user did with a movie: liked, watchlist, watched, rated, favorite, and the diary pages. Works without internet.
 * Every "set a state" function first saves the movie if it is not saved yet, then changes the state.
 *
 * THE WATCH RULE (logic/WatchRules.kt) lives here: like, favorite and rate refuse a movie that is not watched (they look
 * at the SAVED copy), and un-watching clears the like, the rating and the favorite (in the DAO's queries).
 */
class MovieRepository(
    private val api: TmdbApi,
    private val hasToken: Boolean,
    private val dao: MovieDao,
    private val diaryDao: DiaryDao
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
    suspend fun moviesFor(request: DiscoveryRequest, page: Int = 1): Result<List<Movie>> {
        val needsServerFilter = request.language != null || request.minMinutes != null || request.maxMinutes != null
        return when (request.source) {
            DiscoverySource.TRENDING -> if (needsServerFilter) discover(request, page) else trending(page).map { movies -> movies.filter(request::matches) }
            DiscoverySource.POPULAR -> if (needsServerFilter) discover(request, page) else popular(page).map { movies -> movies.filter(request::matches) }
            else -> discover(request, page)
        }
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

            for (movie in discovered) {
                if (movie.id in hidden) continue
                if (preferences.language != null && (movie.originalLanguage == null || !com.example.cinefiles.logic.Languages.matches(preferences.language, movie.originalLanguage))) continue
                found.putIfAbsent(movie.id, movie)
            }
            for ((seedId, movies) in answers) {
                for (movie in movies) {
                    if (movie.id in hidden || !similarFilter.matches(movie)) continue
                    if ((similarFilter.minMinutes != null || similarFilter.maxMinutes != null) && movie.runtimeMinutes == null) continue
                    if (similarFilter.language != null && movie.originalLanguage == null) continue
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
        val few = preferences.language == Languages.FILIPINO
        val discoverBase = base.copy(minRating = rating, minVotes = if (few) FILIPINO_MIN_VOTES else floor.minVotes)
        return listOf(
            discoverBase.copy(source = DiscoverySource.TOP_RATED),  // /discover sorted by vote_average.desc
            discoverBase                                            // /discover sorted by popularity.desc
        ) to discoverBase.copy(genreIds = emptyList(), minVotes = if (few) FILIPINO_MIN_VOTES else floor.minSimilarVotes)
    }

    /** The TMDB person with their movies, for the Person screen. */
    suspend fun person(personId: Int): Result<PersonDetails> = call {
        val details = api.person(personId).toPersonDetails() ?: error("TMDB sent no usable person for id $personId")
        remember(details.allMovies) // opening one of their movies then shows its title and poster at once
        details
    }

    /** The official stills / backdrops of a movie as TMDB paths ("/abc.jpg"), best first, for the Diary's picture picker. */
    suspend fun movieStills(movieId: Int): Result<List<String>> =
        call { api.movieImages(movieId).toStillPaths() }

    // ====================================================================================
    //  TMDB: long lists for Discover (page after page)
    // ====================================================================================

    /** One page of search results. */
    suspend fun searchPage(query: String, page: Int = 1): Result<MoviePage> {
        if (query.isBlank()) return Result.success(MoviePage(emptyList(), 1, 1))
        return call { api.search(query.trim(), page).toMoviePage(page) }
    }

    /**
     * One page of a Discover list. With no genre, the categories are TMDB's own lists (popular, trending, top rated) and "All" is
     * everything most popular first. With a genre they are /discover calls, because TMDB's own lists cannot be filtered by genre.
     *
     * The Discover tab is independent of the "Let's watch something" flow: it never takes the mood or the preferences chosen there
     * (those only shape the swipe deck and Your Picks), so nothing here knows about them.
     */
    suspend fun browsePage(category: BrowseCategory, genreId: Int?, page: Int = 1): Result<MoviePage> = call {
        val response = if (genreId == null) {
            when (category) {
                BrowseCategory.POPULAR -> api.popular(page)
                BrowseCategory.TRENDING -> api.trending(page)
                BrowseCategory.TOP_RATED -> api.topRated(page)
                BrowseCategory.ALL -> api.discover(minVotes = BROWSE_MIN_VOTES_TRENDING, sortBy = SORT_BY_POPULARITY, page = page)
            }
        } else {
            val withGenre = genreId.toString()
            when (category) {
                // TMDB's trending list cannot be filtered by genre: recent movies, most popular first
                BrowseCategory.TRENDING -> api.discover(
                    withGenres = withGenre, releasedFrom = trendingSince(currentYear()),
                    minVotes = BROWSE_MIN_VOTES_TRENDING, sortBy = SORT_BY_POPULARITY, page = page
                )
                BrowseCategory.TOP_RATED -> api.discover(
                    withGenres = withGenre, minRating = BROWSE_TOP_RATED_MIN_RATING,
                    minVotes = BROWSE_MIN_VOTES_TOP_RATED, sortBy = SORT_BY_RATING, page = page
                )
                BrowseCategory.POPULAR -> api.discover(
                    withGenres = withGenre, minVotes = BROWSE_MIN_VOTES_POPULAR, sortBy = SORT_BY_POPULARITY, page = page
                )
                BrowseCategory.ALL -> api.discover(
                    withGenres = withGenre, sortBy = SORT_BY_POPULARITY, page = page
                )
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

    /** THE WATCH RULE for the diary (canWriteDiary): is this movie watched, so a new page may be started? Looks at the SAVED copy. */
    suspend fun canWriteDiary(movieId: Int): Boolean = canWriteDiary(dao.get(movieId))

    /** The ids of all movies the user has any state for. The swipe deck never shows them again. */
    suspend fun idsWithState(): List<Int> = dao.idsWithState()

    /** Live: every movie with a state. Discover puts badges on the movies TMDB sends with it. */
    fun observeWithState(): Flow<List<Movie>> = dao.observeWithState()

    // ====================================================================================
    //  Room: changing what the user saved
    // ====================================================================================

    /**
     * Likes the movie. THE WATCH RULE (logic/WatchRules.kt: canLike): a movie that is not watched (the watchlist counts
     * as not watched) cannot be liked. Then nothing changes and this returns false, so the screen can say why.
     * True = it is liked now. The check uses the SAVED copy of the movie, not the one the caller holds, which may be out of date.
     */
    suspend fun like(movie: Movie): Boolean {
        saveIfMissing(movie)
        return dao.like(movie.id, now()) > 0 // the query itself refuses a movie that is not watched
    }

    suspend fun pass(movie: Movie) {
        saveIfMissing(movie)
        dao.setReaction(movie.id, Reaction.PASSED, now())
    }

    /** Takes back a like or a pass (Undo on the swipe screen, or "Unlike"). The movie itself stays saved. */
    suspend fun clearReaction(movieId: Int) = dao.setReaction(movieId, Reaction.NONE, 0L)

    /**
     * Adds to the watchlist. If the movie was marked watched, it is moved back (it is never both), and its like, rating and
     * favorite go away with "watched" (THE WATCH RULE: the DAO does it in the same statement).
     */
    suspend fun addToWatchlist(movie: Movie) {
        saveIfMissing(movie)
        dao.addToWatchlist(movie.id, now())
        dao.reindexFavorites() // it may have been a favorite: the others close the gap
    }

    suspend fun removeFromWatchlist(movieId: Int) = dao.removeFromWatchlist(movieId)

    /** Marks as watched, which also takes the movie off the watchlist. */
    suspend fun markWatched(movie: Movie) {
        saveIfMissing(movie)
        dao.markWatched(movie.id, now())
    }

    /** Marks as not watched. THE WATCH RULE: the like, the rating and the favorite of the movie go away with it. */
    suspend fun unmarkWatched(movieId: Int) {
        dao.unmarkWatched(movieId)
        dao.reindexFavorites() // it may have been a favorite: the others close the gap
    }

    /**
     * Undo: puts the state of [before] back exactly as it was (the lists, and the rating and favorite that un-watching
     * cleared). Nothing else is touched. If it was a favorite but the four places are taken by now, it comes back
     * without the favorite (the limit of four wins).
     */
    suspend fun restoreState(before: Movie) {
        // A movie that was not watched had no like, rating or favorite: whatever the copy says, the database must not get them back
        val state = if (before.isWatched) before else before.withoutWatched()
        val savedNow = dao.get(state.id)
        val favoriteFits = state.isFavorite && (savedNow?.isFavorite == true || dao.countFavorites() < MAX_FAVORITES)
        if (favoriteFits && savedNow?.isFavorite != true) dao.makeRoomForFavorite(state.favoriteOrder, state.id) // back in its own place
        dao.restoreState(
            id = state.id,
            reaction = state.reaction,
            reactedAt = state.reactedAt,
            inWatchlist = state.inWatchlist,
            watchlistAddedAt = state.watchlistAddedAt,
            isWatched = state.isWatched,
            watchedAt = state.watchedAt,
            userRating = state.userRating,
            ratedAt = state.ratedAt,
            isFavorite = favoriteFits,
            favoritedAt = if (favoriteFits) state.favoritedAt else 0L,
            favoriteOrder = if (favoriteFits) state.favoriteOrder else 0
        )
        dao.reindexFavorites()
    }

    /** Deletes every saved movie, every state and every diary page. (The gallery pictures of the pages: DiaryImageStore.) */
    suspend fun clearAllData() {
        dao.deleteAll()
        diaryDao.deleteAll()
    }

    /** Saves the movie's information if this movie is not saved yet. A saved movie is never overwritten. */
    private suspend fun saveIfMissing(movie: Movie) {
        dao.insertIfMissing(movie)
    }

    private fun now(): Long = System.currentTimeMillis()

    // ====================================================================================
    //  The popcorn rating (0.5 to 5.0, half steps)
    // ====================================================================================

    /**
     * Sets the popcorn rating, or clears it when [rating] is null or 0 (clearing is always allowed).
     * THE WATCH RULE (logic/WatchRules.kt: canRate): only a WATCHED movie can be rated. For any other movie nothing changes
     * and this returns false, so the screen can say "Mark as watched first". We do not mark it watched for the user.
     * The rating is rounded to the nearest half step. True = the rating is saved (or cleared).
     */
    suspend fun setUserRating(movie: Movie, rating: Float?): Boolean {
        val value = PopcornRating.normalize(rating)
        if (value == null) {
            dao.clearUserRating(movie.id)
            return true
        }
        saveIfMissing(movie)
        return dao.setUserRating(movie.id, value, now()) > 0 // the query itself refuses a movie that is not watched
    }

    fun observeRated(): Flow<List<Movie>> = dao.observeRated()

    fun observeRatedCount(): Flow<Int> = dao.observeRatedCount()

    // ====================================================================================
    //  Favorites (at most 4, in the order the user chose)
    // ====================================================================================

    /**
     * Adds the movie to the favorites, or takes it out when it already is one.
     * THE WATCH RULE (canFavorite): only a WATCHED movie can become a favorite ([FavoriteResult.NOT_WATCHED] otherwise),
     * and there is room for [MAX_FAVORITES] ([FavoriteResult.LIMIT_REACHED] when all places are taken). A new favorite
     * takes the last place.
     */
    suspend fun toggleFavorite(movie: Movie): FavoriteResult {
        saveIfMissing(movie)
        if (dao.get(movie.id)?.isFavorite == true) {
            removeFavorite(movie.id)
            return FavoriteResult.REMOVED
        }
        return addFavorite(movie)
    }

    /** Adds the movie to the favorites (the favorites picker of the Profile). It is the same checks as [toggleFavorite]. */
    suspend fun addFavorite(movie: Movie): FavoriteResult {
        saveIfMissing(movie)
        val saved = dao.get(movie.id)
        if (saved?.isFavorite == true) return FavoriteResult.ADDED // already there
        if (!canFavorite(saved)) return FavoriteResult.NOT_WATCHED
        // One statement checks "watched", "not a favorite yet" and "there is room", so two quick taps can never make a fifth favorite
        return if (dao.addFavorite(movie.id, now()) > 0) FavoriteResult.ADDED else FavoriteResult.LIMIT_REACHED
    }

    suspend fun removeFavorite(movieId: Int) {
        dao.removeFavorite(movieId)
        dao.reindexFavorites()
    }

    /**
     * Saves the order the user dragged the favorites into ([orderedIds]: first place first). Ids that are not favorites
     * are ignored, and a favorite the list forgot keeps its place after the others, so nothing is ever lost.
     * It is one database statement, so the Profile never sees a half-moved order.
     */
    suspend fun reorderFavorites(orderedIds: List<Int>) {
        val current = dao.getFavorites().map { it.id }
        val wanted = orderedIds.filter { it in current }.distinct()
        val complete = (wanted + current.filter { it !in wanted }).take(MAX_FAVORITES)
        val places = complete + List(MAX_FAVORITES - complete.size) { NO_MOVIE }
        dao.setFavoriteOrder(places[0], places[1], places[2], places[3])
    }

    fun observeFavorites(): Flow<List<Movie>> = dao.observeFavorites()

    suspend fun getFavorites(): List<Movie> = dao.getFavorites()

    // ====================================================================================
    //  The Movie Diary (pages of text and picture blocks)
    // ====================================================================================

    suspend fun diaryEntry(id: Long): DiaryEntry? = diaryDao.get(id)

    /**
     * Saves a diary page: a new one (id 0) is inserted, a saved one is updated. It stamps the times. Returns the page's id.
     * [DiaryEntry.createdAt] of a new page is now; the update never changes it.
     */
    suspend fun saveDiaryEntry(entry: DiaryEntry): Long {
        val time = now()
        return if (entry.id == 0L) {
            diaryDao.insert(entry.copy(createdAt = time, updatedAt = time))
        } else {
            diaryDao.update(entry.copy(updatedAt = time))
            entry.id
        }
    }

    suspend fun deleteDiaryEntry(id: Long) = diaryDao.delete(id)

    suspend fun allDiaryEntries(): List<DiaryEntry> = diaryDao.getAll()

    /** Every page, newest first. */
    fun observeDiary(): Flow<List<DiaryEntry>> = diaryDao.observeAll()

    /** The pages about one movie, newest first. */
    fun observeDiaryForMovie(movieId: Int): Flow<List<DiaryEntry>> = diaryDao.observeForMovie(movieId)

    fun observeDiaryCount(): Flow<Int> = diaryDao.observeCount()

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
        const val NO_MOVIE = -1              // "nobody here" in the four places of the favorites

        // Discover (browse) lists
        const val SORT_BY_POPULARITY = "popularity.desc"
        const val SORT_BY_RATING = "vote_average.desc"
        const val BROWSE_MIN_VOTES_TRENDING = 20     // also "All": a few votes, so empty entries stay out
        const val BROWSE_MIN_VOTES_POPULAR = 100
        const val BROWSE_MIN_VOTES_TOP_RATED = 300
        const val BROWSE_TOP_RATED_MIN_RATING = 7.5

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
            PicksStep(RELAXED_FLOOR, dropPreferences = false)
        )
    }
}

/** What happened when the user asked to add a movie to the Favorites (or to toggle it). */
enum class FavoriteResult {
    /** It is a favorite now. */
    ADDED,

    /** It was a favorite and is not any more. */
    REMOVED,

    /** Refused: THE WATCH RULE, only a watched movie can be a favorite. */
    NOT_WATCHED,

    /** Refused: all four places are taken. */
    LIMIT_REACHED
}
