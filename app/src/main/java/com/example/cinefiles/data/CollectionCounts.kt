package com.example.cinefiles.data

/**
 * How many movies are in each of the user's personal lists. Room fills it straight from the database
 * (see MovieDao.observeCounts), and Home and My Collection show the numbers.
 * "watchlist" counts only movies that are not watched, exactly like the Watchlist list itself.
 */
data class CollectionCounts(
    val liked: Int,
    val watchlist: Int,
    val watched: Int,
    val passed: Int = 0
)
