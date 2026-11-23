package com.example.cinefiles.ui.discover

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.DiscoverFilters
import com.example.cinefiles.logic.filterByGenre
import com.example.cinefiles.logic.withStateOf

/** Everything the Discover tab can be showing. DiscoverFragment draws whichever one it is given. */
sealed class DiscoverState {
    /** The first page is on its way (or the loaded movies were all filtered away and more pages are coming). */
    object Loading : DiscoverState()

    /** The first page could not be loaded (no internet, no key...). The screen offers Retry. */
    data class Error(val error: ApiError) : DiscoverState()

    /** Nothing was found. [hasFilters] says whether "Clear filters" can change that. */
    data class Empty(val hasFilters: Boolean) : DiscoverState()

    /** Movies to show. [isLoadingMore]: the next page is on its way (a thin bar shows at the bottom). */
    data class Content(val movies: List<Movie>, val isLoadingMore: Boolean) : DiscoverState()
}

/** What the network has delivered so far for the current search or list. (Private to the ViewModel's way of working.) */
sealed class DiscoverLoad {
    object Loading : DiscoverLoad()
    data class Failed(val error: ApiError) : DiscoverLoad()

    /** [movies] are all the pages loaded so far, without repeats. [hasMore]: TMDB has more pages. */
    data class Loaded(val movies: List<Movie>, val hasMore: Boolean, val isLoadingMore: Boolean) : DiscoverLoad()
}

/**
 * Puts what was loaded, what the user saved, and what the user chose into one [DiscoverState]. Plain Kotlin: tested.
 *
 * - Every movie gets the user's own state from [saved] (movie id -> saved copy), so it can wear a badge.
 * - While a search is active, a chosen genre filters the loaded movies here, on the phone.
 * - Nothing to show while more pages are coming is still "loading", not "empty": the next page may have matches.
 */
fun buildDiscoverState(load: DiscoverLoad, saved: Map<Int, Movie>, filters: DiscoverFilters): DiscoverState = when (load) {
    DiscoverLoad.Loading -> DiscoverState.Loading
    is DiscoverLoad.Failed -> DiscoverState.Error(load.error)
    is DiscoverLoad.Loaded -> {
        val withState = load.movies.map { it.withStateOf(saved[it.id]) }
        val visible = if (filters.isSearching) filterByGenre(withState, filters.genreId) else withState
        when {
            visible.isNotEmpty() -> DiscoverState.Content(visible, load.isLoadingMore)
            load.hasMore || load.isLoadingMore -> DiscoverState.Loading
            else -> DiscoverState.Empty(filters.hasFilters)
        }
    }
}
