package com.example.cinefiles.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.FavoriteResult
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction
import com.example.cinefiles.data.friendlyError
import com.example.cinefiles.logic.WatchRuleBlock
import com.example.cinefiles.logic.WatchlistButton
import com.example.cinefiles.logic.hasWatchDependents
import com.example.cinefiles.logic.watchlistButtonFor
import com.example.cinefiles.util.ARG_MOVIE_ID
import com.example.cinefiles.util.Event
import com.example.cinefiles.util.UndoInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
 *
 * THE WATCH RULE (logic/WatchRules.kt): Like, Favorite and the popcorn rating only work on a WATCHED movie. When one is
 * refused, [blocked] tells the screen which, so it can shake the control and say why. Taking "watched" away also
 * clears the like, the rating and the favorite, and [undo] offers to put it all back.
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

    private val _blocked = MutableLiveData<Event<WatchRuleBlock>>()

    /** The user tried to like, favorite or rate a movie that is not watched (shown once: see [Event]). */
    val blocked: LiveData<Event<WatchRuleBlock>> = _blocked

    private val _favoriteLimit = MutableLiveData<Event<Unit>>()

    /** The user tried to add a favorite when all 4 places are taken. */
    val favoriteLimit: LiveData<Event<Unit>> = _favoriteLimit

    private val _undo = MutableLiveData<Event<UndoInfo>>()

    /** "Marked as not watched. Undo": the line to show and the movie as it was before (once: see [Event]). */
    val undo: LiveData<Event<UndoInfo>> = _undo

    private val _diaryRequest = MutableLiveData<Event<Boolean>>()

    /**
     * The user asked for the diary and the movie is watched: true = open the movie's pages, false = start a new page
     * (once: see [Event]). When it is not watched, [blocked] says DIARY instead.
     */
    val diaryRequest: LiveData<Event<Boolean>> = _diaryRequest

    /** How many diary pages this movie has. */
    val diaryCount: LiveData<Int> = repository.observeDiaryForMovie(movieId).map { it.size }.asLiveData()

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

    // ---------- The buttons ----------

    /**
     * The big button: add to the watchlist, remove from it, or move a watched movie back to it. Moving a watched movie
     * back takes "watched" away, and with it the like, the rating and the favorite, so it offers Undo.
     */
    fun toggleWatchlist() = withMovie { movie ->
        when (watchlistButtonFor(movie)) {
            WatchlistButton.ADD -> repository.addToWatchlist(movie)
            WatchlistButton.IN_LIST -> repository.removeFromWatchlist(movie.id)
            WatchlistButton.MOVE_BACK -> {
                val before = repository.savedMovie(movie.id) ?: movie
                repository.addToWatchlist(movie)
                _undo.value = Event(
                    UndoInfo(
                        if (before.hasWatchDependents()) R.string.msg_moved_back_watchlist_cleared else R.string.msg_moved_back_watchlist,
                        before
                    )
                )
            }
        }
    }

    /**
     * Like or unlike. THE WATCH RULE: a movie that is not watched cannot be liked, so the like is refused and
     * the screen is told ([blocked]) so that it can shake the button and say why.
     */
    fun toggleLike() = withMovie { movie ->
        if (movie.reaction == Reaction.LIKED) {
            repository.clearReaction(movie.id)
        } else if (!repository.like(movie)) {
            _blocked.value = Event(WatchRuleBlock.LIKE)
        }
    }

    /**
     * Watched or not. Marking is simple. Un-marking also clears the like, the rating and the favorite (THE WATCH RULE), so
     * it offers Undo, which puts them back.
     */
    fun toggleWatched() = withMovie { movie ->
        if (movie.isWatched) {
            val before = repository.savedMovie(movie.id) ?: movie
            repository.unmarkWatched(movie.id)
            _undo.value = Event(
                UndoInfo(
                    if (before.hasWatchDependents()) R.string.msg_unmarked_watched_cleared else R.string.msg_unmarked_watched,
                    before
                )
            )
        } else {
            repository.markWatched(movie)
        }
    }

    /** Toggles Favorite. Only a watched movie can be one, and there are only 4 places. */
    fun toggleFavorite() = withMovie { movie ->
        when (repository.toggleFavorite(movie)) {
            FavoriteResult.NOT_WATCHED -> _blocked.value = Event(WatchRuleBlock.FAVORITE)
            FavoriteResult.LIMIT_REACHED -> _favoriteLimit.value = Event(Unit)
            FavoriteResult.ADDED, FavoriteResult.REMOVED -> Unit
        }
    }

    /** Sets the popcorn rating (0.5 to 5.0), or clears it with null. A movie that is not watched cannot be rated. */
    fun setRating(rating: Float?) = withMovie { movie ->
        if (!repository.setUserRating(movie, rating)) _blocked.value = Event(WatchRuleBlock.RATE)
    }

    /**
     * "Write in Diary" ([openList] false) or "Open Diary" (true). THE WATCH RULE: only a watched movie has a diary here, so for
     * any other movie nothing opens and the screen is told ([blocked]) so that it can shake the panel and say why.
     */
    fun requestDiary(openList: Boolean) = withMovie { movie ->
        if (repository.canWriteDiary(movie.id)) _diaryRequest.value = Event(openList) else _blocked.value = Event(WatchRuleBlock.DIARY)
    }

    /** Undo: puts the movie's state back as it was before the last "not watched". */
    fun restore(before: Movie) {
        viewModelScope.launch { repository.restoreState(before) }
    }

    /** Runs [action] on the movie as it is shown right now (nothing happens before there is a movie to act on). */
    private fun withMovie(action: suspend (Movie) -> Unit) {
        val movie = (state.value as? DetailState.Content)?.movie ?: return
        viewModelScope.launch { action(movie) }
    }
}
