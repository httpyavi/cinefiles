package com.example.cinefiles.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.data.CollectionCounts
import com.example.cinefiles.data.FavoriteResult
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.Taste
import com.example.cinefiles.logic.tasteOf
import com.example.cinefiles.util.Event
import com.example.cinefiles.util.UndoInfo
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds data for the Profile tab: the name, the numbers, your taste, the 4 favorites (in the order you dragged them to),
 * the watched movies the favorites picker offers, and the number of diary pages.
 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as CineFilesApp).userSettings
    private val repository = (app as CineFilesApp).movieRepository
    private val diaryImages = (app as CineFilesApp).diaryImages

    /** The user's display name ("Movie Lover" until they edit it). */
    val name: LiveData<String> = settings.name

    /** Top genre and liked movies count from the database. */
    val taste: LiveData<Taste> = repository.observeLiked().map { tasteOf(it) }.asLiveData()

    /** Live counts for watched, liked, watchlist, passed. */
    val counts: LiveData<CollectionCounts> = repository.observeCounts().asLiveData()

    /** Up to 4 favorite movies, first place first. */
    val favorites: LiveData<List<Movie>> = repository.observeFavorites().asLiveData()

    /**
     * The movies the favorites picker offers: every WATCHED movie that is not a favorite yet (THE WATCH RULE: only a watched
     * movie can be a favorite). Newest watched first.
     */
    val pickable: LiveData<List<Movie>> = repository.observeWatched().map { watched -> watched.filter { !it.isFavorite } }.asLiveData()

    /** How many diary pages the user wrote. */
    val diaryCount: LiveData<Int> = repository.observeDiaryCount().asLiveData()

    private val _dataCleared = MutableLiveData<Event<Unit>>()
    val dataCleared: LiveData<Event<Unit>> = _dataCleared

    private val _undo = MutableLiveData<Event<UndoInfo>>()

    /** "Removed from your favorites. Undo" (shown once: see [Event]). */
    val undo: LiveData<Event<UndoInfo>> = _undo

    private val _favoriteRefused = MutableLiveData<Event<FavoriteResult>>()

    /** The picker's choice was refused (it cannot happen from the picker, which only offers watched movies: a safety net). */
    val favoriteRefused: LiveData<Event<FavoriteResult>> = _favoriteRefused

    fun rename(typed: String): Boolean = settings.rename(typed)

    /** Makes [movie] the next favorite. */
    fun addFavorite(movie: Movie) {
        viewModelScope.launch {
            val result = repository.addFavorite(movie)
            if (result != FavoriteResult.ADDED) _favoriteRefused.value = Event(result)
        }
    }

    /** Takes [movie] out of the favorites, and offers Undo (it comes back to the same place). */
    fun removeFavorite(movie: Movie) {
        viewModelScope.launch {
            val before = repository.savedMovie(movie.id) ?: movie
            repository.removeFavorite(movie.id)
            _undo.value = Event(UndoInfo(R.string.msg_favorite_removed, before))
        }
    }

    /** Undo: puts the movie back as it was. */
    fun restore(before: Movie) {
        viewModelScope.launch { repository.restoreState(before) }
    }

    /** Saves the order the user dragged the favorites into (first place first). It survives a restart. */
    fun reorderFavorites(orderedIds: List<Int>) {
        viewModelScope.launch { repository.reorderFavorites(orderedIds) }
    }

    /** Clears all database data, the diary pictures and the user settings. */
    fun clearMyData() {
        viewModelScope.launch {
            repository.clearAllData()
            diaryImages.deleteAll()
            settings.clear()
            _dataCleared.value = Event(Unit)
        }
    }
}
