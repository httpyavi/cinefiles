package com.example.cinefiles.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction
import com.example.cinefiles.data.friendlyError
import com.example.cinefiles.logic.WatchlistButton
import com.example.cinefiles.logic.watchlistButtonFor
import com.example.cinefiles.util.ARG_MOVIE_ID
import com.example.cinefiles.util.Event
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Holds what the Details screen shows for ONE movie (its id arrives as the navigation argument "movieId").
 *
 * Three sources are put together by buildDetailState (ui/detail/DetailState.kt):
 *  - the database: the user's own state (liked, watchlist, watched), live, and the saved information (works offline),
 *  - the movies seen in lists during this run: a title and a picture at once,
 *  - TMDB: the full details (runtime, trailer) and the similar movies.
 * The toggles write to the database, and the screen follows the database, so Details, My Collection and Home
 * always agree.
 */
class DetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository
    private val movieId: Int = handle.get<Int>(ARG_MOVIE_ID) ?: 0

    // What we knew before TMDB answered (null if this movie was never in a list during this run)
    private val seen: Movie? = repository.recentlySeen(movieId)

    private val remote = MutableStateFlow(DetailRemote())
    private var loadJob: Job? = null

    /** The state to draw. It changes when the database changes (a toggle) or when TMDB answers. */
    val state: LiveData<DetailState> =
        combine(repository.observeSaved(movieId), remote) { saved, remote ->
            buildDetailState(saved, seen, remote)
        }.asLiveData()

    private val _likeBlocked = MutableLiveData<Event<Unit>>()

    /** The user tried to like a movie that is on the watchlist (shown once: see [Event]). */
    val likeBlocked: LiveData<Event<Unit>> = _likeBlocked

    init {
        load()
    }

    /** Asks TMDB for the details and the similar movies. The Retry buttons call this too. */
    fun load() {
        if (movieId == 0) {
            remote.value = DetailRemote(error = ApiError.UNKNOWN, isLoading = false) // no movie was given
            return
        }

        loadJob?.cancel()
        val before = remote.value
        remote.value = before.copy(isLoading = true, error = null) // keep what we already have while we try again

        loadJob = viewModelScope.launch {
            // Both are asked at the same time
            val (details, similar) = coroutineScope {
                val detailsAnswer = async { repository.movieDetails(movieId) }
                val similarAnswer = async { repository.similar(movieId) }
                detailsAnswer.await() to similarAnswer.await()
            }
            remote.value = DetailRemote(
                details = details.getOrNull() ?: before.details,
                similar = similar.getOrNull() ?: before.similar,
                error = (details.exceptionOrNull() ?: similar.exceptionOrNull())?.let { friendlyError(it) },
                isLoading = false
            )
        }
    }

    // ---------- The three buttons ----------

    /** The big button: add to the watchlist, remove from it, or move a watched movie back to it. */
    fun toggleWatchlist() = withMovie { movie ->
        when (watchlistButtonFor(movie)) {
            WatchlistButton.ADD, WatchlistButton.MOVE_BACK -> repository.addToWatchlist(movie)
            WatchlistButton.IN_LIST -> repository.removeFromWatchlist(movie.id)
        }
    }

    /**
     * Like or unlike. THE LIKE RULE: a movie on the watchlist cannot be liked, so the like is refused and
     * the screen is told ([likeBlocked]) so that it can shake the button and say why.
     */
    fun toggleLike() = withMovie { movie ->
        if (movie.reaction == Reaction.LIKED) {
            repository.clearReaction(movie.id)
        } else if (!repository.like(movie)) {
            _likeBlocked.value = Event(Unit)
        }
    }

    fun toggleWatched() = withMovie { movie ->
        if (movie.isWatched) repository.unmarkWatched(movie.id) else repository.markWatched(movie)
    }

    /** Runs [action] on the movie as it is shown right now (nothing happens before there is a movie to act on). */
    private fun withMovie(action: suspend (Movie) -> Unit) {
        val movie = (state.value as? DetailState.Content)?.movie ?: return
        viewModelScope.launch { action(movie) }
    }
}
