package com.example.cinefiles.ui.detail

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.MovieDetails
import com.example.cinefiles.logic.withStateOf

/** Everything the Details screen can be showing. DetailFragment draws whichever one it is given. */
sealed class DetailState {

    /** Nothing is known about this movie yet (not saved, not seen in a list), and TMDB has not answered. */
    object Loading : DetailState()

    /** Nothing is known and TMDB could not be reached (no internet, no key...). The screen offers Retry. */
    data class Error(val error: ApiError) : DetailState()

    /**
     * The screen can be drawn. [movie] is the best information we have (saved, seen in a list, or fresh from TMDB),
     * with the user's own state (liked, watchlist, watched) from the database on it.
     *
     * [isLoadingMore]: the trailer and the similar movies are still on their way.
     * [loadError]: TMDB could not be reached, so only the saved or already-seen information is shown.
     */
    data class Content(
        val movie: Movie,
        val trailerKey: String?,
        val similar: List<Movie>,
        val isLoadingMore: Boolean,
        val loadError: ApiError?
    ) : DetailState()
}

/** What TMDB has told us so far about the movie. A part that has not arrived (or failed) is null. */
data class DetailRemote(
    val details: MovieDetails? = null,
    val similar: List<Movie>? = null,
    val error: ApiError? = null,
    val isLoading: Boolean = true
)

/**
 * Puts the three sources together into one [DetailState]. Plain Kotlin, so it can be tested.
 *
 * - saved:  the movie as the database has it (or null if the user never saved it). It is the only source of the
 *           user's own state, and it makes the Details screen work without internet.
 * - seen:   the movie as it appeared in a list during this run of the app (or null): a title and a picture at once.
 * - remote: what TMDB has answered.
 */
fun buildDetailState(saved: Movie?, seen: Movie?, remote: DetailRemote): DetailState {
    // The freshest information first, the oldest last
    val base = remote.details?.movie ?: saved ?: seen
    if (base == null) {
        return if (remote.isLoading) DetailState.Loading else DetailState.Error(remote.error ?: ApiError.UNKNOWN)
    }

    var movie = base
    if (saved != null) {
        movie = movie.copy(
            // Fill gaps from the saved copy (a list answer has no runtime, for example)
            runtimeMinutes = movie.runtimeMinutes ?: saved.runtimeMinutes,
            overview = movie.overview.ifBlank { saved.overview }
        ).withStateOf(saved) // the user's own state always comes from the database
    }

    return DetailState.Content(
        movie = movie,
        trailerKey = remote.details?.trailerKey,
        similar = remote.similar.orEmpty(),
        isLoadingMore = remote.isLoading,
        loadError = if (remote.isLoading) null else remote.error
    )
}
