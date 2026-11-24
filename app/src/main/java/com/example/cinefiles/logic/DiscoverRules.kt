package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie

/*
 * THE RULES OF THE DISCOVER TAB (plain Kotlin, no screens: easy to test).
 *
 * Two modes:
 *   - BROWSING (the search field is empty): the category chips pick the list (Popular, Trending, Top rated).
 *     With a genre chip selected, TMDB's /discover answers instead, so the list is only that genre.
 *   - SEARCHING (2 or more letters typed): TMDB's /search answers. A genre chip then only filters, on the phone,
 *     the movies that are already loaded.
 */

/** The three lists of the Discover tab. */
enum class BrowseCategory(@StringRes val label: Int) {
    ALL(R.string.discover_all),
    POPULAR(R.string.discover_popular),
    TRENDING(R.string.discover_trending),
    TOP_RATED(R.string.discover_top_rated),
    GENRE(R.string.discover_title)
}

/** A search needs at least this many letters (one letter would match far too much). */
const val MIN_SEARCH_LETTERS = 2

/** Is this text a search? Spaces around it do not count. */
fun isSearchText(text: String): Boolean = text.trim().length >= MIN_SEARCH_LETTERS

/** What the user chose on the Discover tab. */
data class DiscoverFilters(
    val query: String = "",
    val category: BrowseCategory = BrowseCategory.ALL,
    val genreId: Int? = null
) {
    val isSearching: Boolean get() = isSearchText(query)

    /** Is anything chosen that "Clear filters" would undo? */
    val hasFilters: Boolean get() = query.isNotBlank() || genreId != null || category != BrowseCategory.ALL
}

/** The movies of the next page added to the ones already shown. A movie that is already there is not added again. */
fun appendUnique(existing: List<Movie>, more: List<Movie>): List<Movie> {
    val seen = existing.mapTo(HashSet()) { it.id }
    return existing + more.filter { seen.add(it.id) }
}

/** Only the movies of one genre (all of them when [genreId] is null). */
fun filterByGenre(movies: List<Movie>, genreId: Int?): List<Movie> =
    if (genreId == null) movies else movies.filter { genreId in it.genreIds }

/**
 * The date from which "trending" movies are asked for when a genre is chosen: the start of last year.
 * (TMDB's trending list cannot be filtered by genre, so "trending in a genre" means recent and popular.)
 */
fun trendingSince(currentYear: Int): String = "${currentYear - 1}-01-01"
