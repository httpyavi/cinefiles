package com.example.cinefiles.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.data.AvatarLook
import com.example.cinefiles.data.CollectionCounts
import com.example.cinefiles.data.friendlyError
import com.example.cinefiles.logic.DayPart
import com.example.cinefiles.logic.dayPartForHour
import com.example.cinefiles.logic.pickFeatured
import com.example.cinefiles.util.currentDayOfYear
import com.example.cinefiles.util.currentHour
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Holds what the Home screen shows. It survives screen rotation and tab switches, so the movies
 * are not loaded again each time you come back to Home.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository
    private val settings = (app as CineFilesApp).userSettings

    private val _state = MutableLiveData<HomeState>(HomeState.Loading)
    val state: LiveData<HomeState> = _state

    // The greeting text as a string resource id ("Good morning" ...)
    private val _greeting = MutableLiveData<Int>()
    val greeting: LiveData<Int> = _greeting

    // The three numbers at the bottom of Home. They come live from the database: when you like a movie, or
    // change your watchlist anywhere in the app, the numbers change by themselves. They work offline.
    val counts: LiveData<CollectionCounts> = repository.observeCounts().asLiveData()

    // The name from the Profile screen ("Movie Lover" until the user edits it), and the avatar: the photo, or the name's first letter
    val userName: LiveData<String> = settings.name
    val avatarLook: LiveData<AvatarLook> = settings.avatarLook

    private var loadJob: Job? = null

    init {
        refreshGreeting()
        load()
    }

    /** Work out "Good morning / afternoon / evening" from the current time. */
    fun refreshGreeting() {
        _greeting.value = when (dayPartForHour(currentHour())) {
            DayPart.MORNING -> R.string.home_greeting_morning
            DayPart.AFTERNOON -> R.string.home_greeting_afternoon
            DayPart.EVENING -> R.string.home_greeting_evening
        }
    }

    /** Loads (or reloads) the movies from TMDB. The Retry buttons call this too. */
    fun load() {
        loadJob?.cancel() // never run two loads at once
        _state.value = HomeState.Loading

        loadJob = viewModelScope.launch {
            // Both lists are asked for at the same time
            val (trending, popular) = coroutineScope {
                val trendingAnswer = async { repository.trending() }
                val popularAnswer = async { repository.popular() }
                trendingAnswer.await() to popularAnswer.await()
            }

            // If either list failed, Home shows the error (with Retry): half a Home would be confusing
            val failure = trending.exceptionOrNull() ?: popular.exceptionOrNull()
            if (failure != null) {
                _state.value = HomeState.Error(friendlyError(failure))
                return@launch
            }

            val trendingMovies = trending.getOrThrow()
            val popularMovies = popular.getOrThrow()
            if (trendingMovies.isEmpty() && popularMovies.isEmpty()) {
                _state.value = HomeState.Empty
                return@launch
            }

            _state.value = HomeState.Content(
                featured = pickFeatured(trendingMovies, currentDayOfYear()),
                trending = trendingMovies,
                popular = popularMovies
            )
        }
    }
}
