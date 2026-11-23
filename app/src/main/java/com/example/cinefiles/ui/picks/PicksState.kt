package com.example.cinefiles.ui.picks

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.Recommendation

/** Everything the Your Picks screen can be showing. PicksFragment draws whichever one it is given. */
sealed class PicksState {

    /** Nothing was asked for yet (the screen was opened without the swipe screen handing it anything). */
    object Idle : PicksState()

    /** "Building your picks...": TMDB is being asked and the picks are being scored. */
    object Building : PicksState()

    /**
     * The picks are ready.
     * [summary] is the one line under the title ("Happy · Romantic · 7 liked · 12 passed · 10 new picks").
     * [top] is the Top Match, [more] the rest, [liked] the movies liked in this sitting.
     */
    data class Ready(
        val summary: String,
        val top: Recommendation,
        val more: List<Recommendation>,
        val liked: List<Movie>
    ) : PicksState()

    /** There was nothing new to suggest (every candidate was already seen, or none fit). */
    object Empty : PicksState()

    /** TMDB could not be reached (no internet, no key...). The screen offers Retry. */
    data class Error(val error: ApiError) : PicksState()
}
