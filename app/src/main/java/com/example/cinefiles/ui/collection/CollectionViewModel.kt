package com.example.cinefiles.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.CollectionCounts
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.CollectionAction
import com.example.cinefiles.logic.CollectionTab
import com.example.cinefiles.util.Event
import com.example.cinefiles.util.UndoInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the list of one tab can be showing. */
sealed class CollectionState {
    /** The first answer from the database has not arrived yet (it takes a few milliseconds). */
    object Loading : CollectionState()

    /** The database could not be read. The screen offers Retry. */
    object Error : CollectionState()

    /** The movies of [tab], newest first. An empty list means the tab is empty. */
    data class Content(val tab: CollectionTab, val movies: List<Movie>) : CollectionState()
}

/**
 * Holds what My Collection shows. The lists and the counts come live from the database, so when a movie changes
 * anywhere in the app (a swipe, the Details screen, this screen) they update by themselves.
 *
 * It is shared by the whole app window (activityViewModels): Home's three tiles use [selectTab] to open the
 * Collection on the right tab, and the Collection remembers the tab you were on.
 */
class CollectionViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository

    private val _tab = MutableLiveData(CollectionTab.LIKED)

    /** The selected tab. */
    val tab: LiveData<CollectionTab> = _tab

    /** The numbers on the three tabs. */
    val counts: LiveData<CollectionCounts> = repository.observeCounts().asLiveData()

    /**
     * The list of the selected tab. When the tab changes, the old list stays on screen until the new one has
     * arrived (a few milliseconds), so there is no flash of "loading" between tabs.
     */
    val state: LiveData<CollectionState> = _tab.switchMap { tab ->
        flowFor(tab)
            .map<List<Movie>, CollectionState> { movies -> CollectionState.Content(tab, movies) }
            .catch { emit(CollectionState.Error) }
            .asLiveData()
    }

    private val _undo = MutableLiveData<Event<UndoInfo>>()

    /** The last change, to show "Removed from Watchlist. Undo" (once: see [Event]). */
    val undo: LiveData<Event<UndoInfo>> = _undo

    private val _likeBlocked = MutableLiveData<Event<Unit>>()

    /** A like was refused because the movie is on the watchlist. The menu does not offer it, so this is a safety net. */
    val likeBlocked: LiveData<Event<Unit>> = _likeBlocked

    private fun flowFor(tab: CollectionTab): Flow<List<Movie>> = when (tab) {
        CollectionTab.LIKED -> repository.observeLiked()
        CollectionTab.WATCHLIST -> repository.observeWatchlist()
        CollectionTab.WATCHED -> repository.observeWatched()
    }

    fun selectTab(tab: CollectionTab) {
        if (_tab.value != tab) _tab.value = tab
    }

    /** Reads the list again (the Retry button). */
    fun retry() {
        _tab.value = _tab.value
    }

    /** Does one long-press menu action on [movie], then offers Undo. */
    fun perform(action: CollectionAction, movie: Movie) {
        viewModelScope.launch {
            // How the movie is right now: that is what Undo puts back
            val before = repository.savedMovie(movie.id) ?: movie
            when (action) {
                CollectionAction.ADD_TO_WATCHLIST,
                CollectionAction.MOVE_BACK_TO_WATCHLIST -> repository.addToWatchlist(movie)
                CollectionAction.REMOVE_FROM_WATCHLIST -> repository.removeFromWatchlist(movie.id)
                CollectionAction.MARK_WATCHED -> repository.markWatched(movie)
                CollectionAction.MARK_UNWATCHED -> repository.unmarkWatched(movie.id)
                CollectionAction.LIKE -> if (!repository.like(movie)) {
                    _likeBlocked.value = Event(Unit) // THE LIKE RULE: nothing changed, so there is nothing to undo
                    return@launch
                }
                CollectionAction.UNLIKE -> repository.clearReaction(movie.id)
            }
            _undo.value = Event(UndoInfo(action.message, before))
        }
    }

    /** Undo: puts the movie's state back exactly as it was before the action. */
    fun undo(before: Movie) {
        viewModelScope.launch { repository.restoreState(before) }
    }
}
