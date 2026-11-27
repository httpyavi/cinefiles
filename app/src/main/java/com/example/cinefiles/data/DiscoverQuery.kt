package com.example.cinefiles.data

import com.example.cinefiles.logic.Discovery
import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource

/**
 * What we ask TMDB's /discover/movie for. It is [DiscoveryRequest] (the deck's own words: mood genres,
 * release years, rating...) translated into TMDB's parameter names, one field per parameter.
 * Kept separate from the network code so that it is plain Kotlin and easy to test.
 */
data class DiscoverQuery(
    val withGenres: String?,        // "35|28": the pipe means OR (comedy OR action)
    val withoutGenres: String?,     // "27,53": none of these (no horror, no thriller)
    val releasedFrom: String?,      // primary_release_date.gte, "2015-01-01"
    val releasedTo: String?,        // primary_release_date.lte, "1999-12-31"
    val minRating: Double?,         // vote_average.gte
    val minVotes: Int,              // vote_count.gte
    val maxVotes: Int?,             // vote_count.lte
    val minRuntime: Int?,           // with_runtime.gte
    val maxRuntime: Int?,           // with_runtime.lte
    val originalLanguage: String?,  // with_original_language: "en", "tl", "ko", "ja"
    val sortBy: String
)

fun DiscoveryRequest.toDiscoverQuery(): DiscoverQuery = DiscoverQuery(
    // Genres are joined with "|" (OR): a mood like "Excited" (Action, Adventure) wants either one
    withGenres = genreIds.takeIf { it.isNotEmpty() }?.joinToString("|"),
    withoutGenres = withoutGenreIds.takeIf { it.isNotEmpty() }?.joinToString(","),
    releasedFrom = yearFrom?.let { "$it-01-01" },
    releasedTo = yearTo?.let { "$it-12-31" },
    minRating = minRating,
    // Ignore movies that only a handful of people rated: their rating means nothing (Filipino movies: see Discovery)
    minVotes = minVotes ?: Discovery.defaultMinVotes(language),
    maxVotes = maxVotes,
    minRuntime = minMinutes,
    maxRuntime = maxMinutes,
    originalLanguage = language,
    sortBy = when (source) {
        // Best rated first (hidden gems too: their vote limits already keep them rare)
        DiscoverySource.TOP_RATED, DiscoverySource.HIDDEN_GEM -> "vote_average.desc"
        // Everything else (close matches too): the best-known first
        else -> "popularity.desc"
    }
)
