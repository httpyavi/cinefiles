package com.example.cinefiles.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A fake of the real database, for the tests that run on the computer (the real DAO needs a phone or
 * emulator: see MovieDaoTest in androidTest). It keeps the movies in memory and follows the same rules as the
 * real queries: a saved movie is never replaced, and every "set a state" call only changes its own columns.
 */
class FakeMovieDao : MovieDao {

    /** Every call of updateInfo: (movie id, overview, runtime). */
    val infoUpdates = ArrayList<Triple<Int, String, Int?>>()

    private val rows = MutableStateFlow<Map<Int, Movie>>(emptyMap())

    /** For a test that needs a movie that is already saved, with whatever state it should have. */
    fun put(movie: Movie) {
        rows.value = rows.value + (movie.id to movie)
    }

    /** The saved copy of a movie (null when it is not saved). */
    fun saved(id: Int): Movie? = rows.value[id]

    private fun change(id: Int, update: (Movie) -> Movie) {
        val movie = rows.value[id] ?: return
        rows.value = rows.value + (id to update(movie))
    }

    private fun Movie.hasAnyState() = reaction != Reaction.NONE || inWatchlist || isWatched

    override suspend fun insertIfMissing(movie: Movie): Long {
        if (rows.value.containsKey(movie.id)) return -1L // IGNORE: the saved movie, and its state, stay as they are
        put(movie)
        return rows.value.size.toLong()
    }

    override suspend fun setReaction(id: Int, reaction: Reaction, at: Long) =
        change(id) { it.copy(reaction = reaction, reactedAt = at) }

    override suspend fun addToWatchlist(id: Int, at: Long) =
        change(id) { it.copy(inWatchlist = true, watchlistAddedAt = at, isWatched = false, watchedAt = 0L) }

    override suspend fun removeFromWatchlist(id: Int) =
        change(id) { it.copy(inWatchlist = false, watchlistAddedAt = 0L) }

    override suspend fun markWatched(id: Int, at: Long) =
        change(id) { it.copy(isWatched = true, watchedAt = at, inWatchlist = false, watchlistAddedAt = 0L) }

    override suspend fun unmarkWatched(id: Int) =
        change(id) { it.copy(isWatched = false, watchedAt = 0L) }

    override suspend fun restoreState(
        id: Int, reaction: Reaction, reactedAt: Long, inWatchlist: Boolean,
        watchlistAddedAt: Long, isWatched: Boolean, watchedAt: Long
    ) = change(id) {
        it.copy(
            reaction = reaction, reactedAt = reactedAt, inWatchlist = inWatchlist,
            watchlistAddedAt = watchlistAddedAt, isWatched = isWatched, watchedAt = watchedAt
        )
    }

    override suspend fun updateInfo(id: Int, overview: String, runtimeMinutes: Int?) {
        infoUpdates.add(Triple(id, overview, runtimeMinutes))
        change(id) {
            it.copy(
                overview = if (overview.isEmpty()) it.overview else overview,
                runtimeMinutes = runtimeMinutes ?: it.runtimeMinutes
            )
        }
    }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }

    override suspend fun get(id: Int): Movie? = rows.value[id]
    override fun observe(id: Int): Flow<Movie?> = rows.map { it[id] }

    override fun observeReacted(reaction: Reaction): Flow<List<Movie>> =
        rows.map { all -> all.values.filter { it.reaction == reaction }.sortedByDescending { it.reactedAt } }

    override fun observeWatchlist(): Flow<List<Movie>> =
        rows.map { all -> all.values.filter { it.inWatchlist && !it.isWatched }.sortedByDescending { it.watchlistAddedAt } }

    override fun observeWatched(): Flow<List<Movie>> =
        rows.map { all -> all.values.filter { it.isWatched }.sortedByDescending { it.watchedAt } }

    override fun observeCounts(): Flow<CollectionCounts> = rows.map { all ->
        CollectionCounts(
            liked = all.values.count { it.reaction == Reaction.LIKED },
            watchlist = all.values.count { it.inWatchlist && !it.isWatched },
            watched = all.values.count { it.isWatched },
            passed = all.values.count { it.reaction == Reaction.PASSED }
        )
    }

    override suspend fun idsWithState(): List<Int> = rows.value.values.filter { it.hasAnyState() }.map { it.id }

    override fun observeWithState(): Flow<List<Movie>> = rows.map { all -> all.values.filter { it.hasAnyState() } }
}
