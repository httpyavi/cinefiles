package com.example.cinefiles.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.CollectionCounts
import com.example.cinefiles.data.DiaryEntry
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.Taste
import com.example.cinefiles.logic.tasteOf
import com.example.cinefiles.util.Event
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds data for the redesigned Profile tab: personal film statistics, 4 favorites,
 * personal ratings, diary entries, and user settings. Gamification/achievements have been removed.
 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as CineFilesApp).userSettings
    private val repository = (app as CineFilesApp).movieRepository

    /** The user's display name ("Movie Lover" until they edit it). */
    val name: LiveData<String> = settings.name

    /** Top genre and liked movies count from the database. */
    val taste: LiveData<Taste> = repository.observeLiked().map { tasteOf(it) }.asLiveData()

    /** Live counts for watched, liked, watchlist, passed. */
    val counts: LiveData<CollectionCounts> = repository.observeCounts().asLiveData()

    /** Up to 4 favorited movies. */
    val favorites: LiveData<List<Movie>> = repository.observeFavorites().asLiveData()

    /** Total count of movies personally rated. */
    val ratedCount: LiveData<Int> = repository.observeRatedCount().asLiveData()

    /** Total count of personal diary entries. */
    val diaryCount: LiveData<Int> = repository.observeDiaryCount()?.asLiveData() ?: MutableLiveData(0)

    /** All movies personally rated by the user. */
    val ratedMovies: LiveData<List<Movie>> = repository.observeRated().asLiveData()

    /** All diary entries recorded by the user. */
    val diaryEntries: LiveData<List<DiaryEntry>> = repository.observeDiary()?.asLiveData() ?: MutableLiveData(emptyList())

    private val _dataCleared = MutableLiveData<Event<Unit>>()
    val dataCleared: LiveData<Event<Unit>> = _dataCleared

    fun rename(typed: String): Boolean = settings.rename(typed)

    fun removeFavorite(movieId: Int) {
        viewModelScope.launch {
            repository.removeFavorite(movieId)
        }
    }

    /** Clears all database data and user settings. */
    fun clearMyData() {
        viewModelScope.launch {
            repository.clearAllData()
            settings.clear()
            _dataCleared.value = Event(Unit)
        }
    }
}
