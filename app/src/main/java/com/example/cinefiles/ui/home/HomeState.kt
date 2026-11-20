package com.example.cinefiles.ui.home

import com.example.cinefiles.data.Movie

/** Everything the Home screen can be showing. HomeFragment draws whichever one it is given. */
sealed class HomeState {
    /** Waiting for the movies. */
    object Loading : HomeState()

    /** Movies arrived. The hero is optional: Home simply hides the parts that have nothing to show. */
    data class Content(
        val featured: Movie?,
        val trending: List<Movie>,
        val popular: List<Movie>
    ) : HomeState()

    /** The call worked but there was nothing to show. */
    object Empty : HomeState()

    /** Something went wrong (for example no internet). The screen offers Retry. */
    object Error : HomeState()
}

/** How many movies are in each of the user's personal lists (the tiles at the bottom of Home). */
data class CollectionCounts(val liked: Int, val watchlist: Int, val watched: Int)
