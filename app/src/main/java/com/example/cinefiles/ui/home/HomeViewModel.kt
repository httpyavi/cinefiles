package com.example.cinefiles.ui.home

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.R
import com.example.cinefiles.data.SampleData
import com.example.cinefiles.logic.DayPart
import com.example.cinefiles.logic.dayPartForHour
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// TEMP: ways to force the Home screen into each of its states, so loading / error / empty can be
// tested on a phone. Long-press the name "Movie Lover" on Home to step to the next mode.
// Removed in Batch 5, when Home has real loading and errors (see TEMP.md).
enum class HomeTestMode(@StringRes val label: Int) {
    NORMAL(R.string.temp_test_normal),
    LOADING_FOREVER(R.string.temp_test_loading),
    FAIL_ONCE(R.string.temp_test_error), // fails the next load; Retry then works
    EMPTY(R.string.temp_test_empty)
}

/**
 * Holds what the Home screen shows. It survives screen rotation and tab switches, so the movies
 * are not loaded again each time you come back to Home.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableLiveData<HomeState>(HomeState.Loading)
    val state: LiveData<HomeState> = _state

    // The greeting text as a string resource id ("Good morning" ...)
    private val _greeting = MutableLiveData<Int>()
    val greeting: LiveData<Int> = _greeting

    // TEMP: always 0 until the personal lists exist. Becomes live data from Room in Batch 6.
    private val _counts = MutableLiveData(CollectionCounts(liked = 0, watchlist = 0, watched = 0))
    val counts: LiveData<CollectionCounts> = _counts

    // The user has no account yet, so everyone is "Movie Lover".
    val userName: String = app.getString(R.string.home_user_name)
    val avatarInitial: String = userName.take(1).uppercase()

    private var loadJob: Job? = null

    // TEMP: see HomeTestMode. testMode is the mode picked by long-press; failNextLoad makes
    // the FAIL_ONCE mode fail exactly one load, so that Retry can then succeed.
    private var testMode = HomeTestMode.NORMAL
    private var failNextLoad = false

    init {
        refreshGreeting()
        load()
    }

    /** Work out "Good morning / afternoon / evening" from the current time. */
    fun refreshGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        _greeting.value = when (dayPartForHour(hour)) {
            DayPart.MORNING -> R.string.home_greeting_morning
            DayPart.AFTERNOON -> R.string.home_greeting_afternoon
            DayPart.EVENING -> R.string.home_greeting_evening
        }
    }

    /** Loads (or reloads) the movies. The Retry buttons call this too. */
    fun load() {
        loadJob?.cancel() // never run two loads at once
        _state.value = HomeState.Loading

        loadJob = viewModelScope.launch {
            // TEMP: pretend to wait for the network, then use the sample movies.
            // Batch 5 replaces this block with a call to MovieRepository.
            delay(SAMPLE_LOAD_TIME_MS)
            val content = HomeState.Content(
                featured = SampleData.featured,
                trending = SampleData.trending,
                popular = SampleData.popular
            )
            when (testMode) {
                HomeTestMode.NORMAL -> _state.value = content
                HomeTestMode.LOADING_FOREVER -> Unit // stay on Loading
                HomeTestMode.FAIL_ONCE -> {
                    if (failNextLoad) {
                        failNextLoad = false
                        _state.value = HomeState.Error
                    } else {
                        _state.value = content // this is what Retry gets
                    }
                }
                HomeTestMode.EMPTY -> _state.value = HomeState.Empty
            }
        }
    }

    // TEMP: step to the next test mode, reload, and say which mode is now active.
    fun nextTestMode(): HomeTestMode {
        val modes = HomeTestMode.values()
        testMode = modes[(testMode.ordinal + 1) % modes.size]
        failNextLoad = testMode == HomeTestMode.FAIL_ONCE
        load()
        return testMode
    }

    private companion object {
        const val SAMPLE_LOAD_TIME_MS = 700L // TEMP
    }
}
