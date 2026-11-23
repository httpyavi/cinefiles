package com.example.cinefiles.data

import com.example.cinefiles.data.dto.MovieListResponse
import com.example.cinefiles.data.dto.toMovies
import com.example.cinefiles.logic.BrowseCategory
import com.example.cinefiles.logic.Discovery
import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.RecommendationEngine
import com.example.cinefiles.logic.canLike
import com.example.cinefiles.logic.trendingSince
import com.example.cinefiles.util.currentYear
import kotlin.coroutines.cancellation.CancellationException
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
    private val dao: MovieDao
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
        DiscoverySource.TRENDING -> trending(page).map { movies -> movies.filter(request::matches) }
        DiscoverySource.POPULAR -> popular(page).map { movies -> movies.filter(request::matches) }
        else -> discover(request, page)
    }

    // ====================================================================================
    //  TMDB: your picks (the candidates the RecommendationEngine scores)
    // ====================================================================================

    /**
     * The movies the engine may choose from. [genreIds] are the user's best genres (joined with | = OR).
     * It asks /discover for page 1, and page 2 if that gave too few, with the user's preferences and at least
     * 100 votes. If there are still fewer than 10 candidates, it asks again ONCE without the preferences (the genres
     * stay). Movies that already have a state in the database (liked, passed, watchlist, watched) and movies without
     * a poster never come back.
     */
    suspend fun picksCandidates(genreIds: List<Int>, preferences: Preferences): Result<List<Movie>> = call {
        val hidden = dao.idsWithState().toSet()
        val found = LinkedHashMap<Int, Movie>()

        for (relax in listOf(NO_RELAXING, DROP_PREFERENCES)) {
            // relax 0 keeps every preference; relax 4 forgets language, length, release period and rating but keeps the genres
            val request = Discovery.plan(genreIds, preferences, relax).first().copy(minVotes = PICKS_MIN_VOTES)
            for (page in 1..PICKS_PAGES) {
                for (movie in discover(request, page).getOrThrow()) {
                    if (movie.id !in hidden) found.putIfAbsent(movie.id, movie)
                }
                if (found.size >= RecommendationEngine.MAX_PICKS) break
            }
            if (found.size >= RecommendationEngine.MAX_PICKS) break
        }
        found.values.toList()
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
    suspend fun browsePage(category: BrowseCategory, genreId: Int?, page: Int = 1): Result<MoviePage> = call {
        val response = if (genreId == null) {
            when (category) {
                BrowseCategory.POPULAR -> api.popular(page)
                BrowseCategory.TRENDING -> api.trending(page)
                BrowseCategory.TOP_RATED -> api.topRated(page)
            }
        } else {
            val withGenre = genreId.toString()
            when (category) {
                BrowseCategory.POPULAR ->
                    api.discover(withGenres = withGenre, minVotes = 100, sortBy = "popularity.desc", page = page)
                BrowseCategory.TRENDING -> api.discover(
                    withGenres = withGenre, minVotes = 20, sortBy = "popularity.desc", page = page,
                    releasedFrom = trendingSince(currentYear())
                )
                BrowseCategory.TOP_RATED ->
                    api.discover(withGenres = withGenre, minVotes = 300, sortBy = "vote_average.desc", page = page)
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
        const val PICKS_MIN_VOTES = 100  // ignore movies that only a handful of people rated
        const val PICKS_PAGES = 2        // at most two pages of 20 for each try
        const val NO_RELAXING = 0        // Discovery.plan: keep every preference
        const val DROP_PREFERENCES = 4   // Discovery.plan: drop the preferences, keep the genres
    }
}
