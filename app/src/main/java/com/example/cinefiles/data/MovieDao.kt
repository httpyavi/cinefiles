package com.example.cinefiles.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Every question we ask the `movies` table, and every change we make to it.
 *
 * THE RULE THAT PROTECTS THE USER'S DATA: a movie is saved with [insertIfMissing], which IGNORES a movie that
 * is already there. It must never REPLACE one, because replacing a row would wipe the user's liked / watchlist /
 * watched state. After a movie is saved, its state is changed with the small UPDATE queries below, which only
 * touch the columns they name.
 *
 * Functions that return a [Flow] are "live": they send the new list again every time the table changes,
 * so a screen that shows them is always up to date. The others are `suspend` functions: call them from a
 * coroutine (Room runs them off the main thread by itself).
 *
 * The enum names in the queries ('LIKED', 'NONE') are how Room stores [Reaction]: as its name.
 */
@Dao
interface MovieDao {

    // ---------- Saving a movie ----------

    /** Saves the movie, unless it is already saved. Returns the new row number, or -1 when it was already there. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(movie: Movie): Long

    // ---------- Changing the user's state (each query only touches the columns it names) ----------

    @Query("UPDATE movies SET reaction = :reaction, reactedAt = :at WHERE id = :id")
    suspend fun setReaction(id: Int, reaction: Reaction, at: Long)

    /** Puts the movie on the watchlist. A movie is on the watchlist OR watched, so this also clears "watched". */
    @Query("UPDATE movies SET inWatchlist = 1, watchlistAddedAt = :at, isWatched = 0, watchedAt = 0 WHERE id = :id")
    suspend fun addToWatchlist(id: Int, at: Long)

    @Query("UPDATE movies SET inWatchlist = 0, watchlistAddedAt = 0 WHERE id = :id")
    suspend fun removeFromWatchlist(id: Int)

    /** Marks the movie as watched. A watched movie is not on the watchlist any more. */
    @Query("UPDATE movies SET isWatched = 1, watchedAt = :at, inWatchlist = 0, watchlistAddedAt = 0 WHERE id = :id")
    suspend fun markWatched(id: Int, at: Long)

    @Query("UPDATE movies SET isWatched = 0, watchedAt = 0 WHERE id = :id")
    suspend fun unmarkWatched(id: Int)

    /** Puts all six state columns back to the given values. This is what every "Undo" uses. */
    @Query(
        "UPDATE movies SET reaction = :reaction, reactedAt = :reactedAt, inWatchlist = :inWatchlist, " +
            "watchlistAddedAt = :watchlistAddedAt, isWatched = :isWatched, watchedAt = :watchedAt WHERE id = :id"
    )
    suspend fun restoreState(
        id: Int,
        reaction: Reaction,
        reactedAt: Long,
        inWatchlist: Boolean,
        watchlistAddedAt: Long,
        isWatched: Boolean,
        watchedAt: Long
    )

    /**
     * Refreshes what only the details answer knows (the runtime, and often a better overview) on a saved movie,
     * so the Details screen can show it offline. An empty overview or a missing runtime never wipes what is saved.
     */
    @Query(
        "UPDATE movies SET overview = CASE WHEN :overview = '' THEN overview ELSE :overview END, " +
            "runtimeMinutes = COALESCE(:runtimeMinutes, runtimeMinutes) WHERE id = :id"
    )
    suspend fun updateInfo(id: Int, overview: String, runtimeMinutes: Int?)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()

    // ---------- One movie ----------

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun get(id: Int): Movie?

    /** Live: sends the movie again whenever it changes (null while it is not saved). */
    @Query("SELECT * FROM movies WHERE id = :id")
    fun observe(id: Int): Flow<Movie?>

    // ---------- The lists (newest first) ----------

    /** The liked movies, or the passed ones (pass Reaction.LIKED or Reaction.PASSED). */
    @Query("SELECT * FROM movies WHERE reaction = :reaction ORDER BY reactedAt DESC, id DESC")
    fun observeReacted(reaction: Reaction): Flow<List<Movie>>

    /** The watchlist: saved to watch later and not watched yet. */
    @Query("SELECT * FROM movies WHERE inWatchlist = 1 AND isWatched = 0 ORDER BY watchlistAddedAt DESC, id DESC")
    fun observeWatchlist(): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE isWatched = 1 ORDER BY watchedAt DESC, id DESC")
    fun observeWatched(): Flow<List<Movie>>

    // ---------- Numbers ----------

    /** Live counts for all the lists. They use the same conditions as the lists above, so they always agree. */
    @Query(
        "SELECT " +
            "(SELECT COUNT(*) FROM movies WHERE reaction = 'LIKED') AS liked, " +
            "(SELECT COUNT(*) FROM movies WHERE inWatchlist = 1 AND isWatched = 0) AS watchlist, " +
            "(SELECT COUNT(*) FROM movies WHERE isWatched = 1) AS watched, " +
            "(SELECT COUNT(*) FROM movies WHERE reaction = 'PASSED') AS passed"
    )
    fun observeCounts(): Flow<CollectionCounts>

    /**
     * The id of every movie the user has any state for (liked, passed, on the watchlist or watched).
     * The swipe deck never shows these movies again.
     */
    @Query("SELECT id FROM movies WHERE reaction <> 'NONE' OR inWatchlist = 1 OR isWatched = 1")
    suspend fun idsWithState(): List<Int>

    /** Live: every movie the user has any state for. Discover uses it to put badges on the movies TMDB sends. */
    @Query("SELECT * FROM movies WHERE reaction <> 'NONE' OR inWatchlist = 1 OR isWatched = 1")
    fun observeWithState(): Flow<List<Movie>>
}
