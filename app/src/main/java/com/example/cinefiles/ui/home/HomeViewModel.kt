package com.example.cinefiles.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.R
import com.example.cinefiles.data.SampleData
import com.example.cinefiles.logic.DayPart
import com.example.cinefiles.logic.dayPartForHour
import com.example.cinefiles.util.TestMode
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // TEMP: see TestMode. testMode is the mode picked by long-press; failNextLoad makes
    // the FAIL_ONCE mode fail exactly one load, so that Retry can then succeed.
    private var testMode = TestMode.NORMAL
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
                TestMode.NORMAL -> _state.value = content
                TestMode.LOADING_FOREVER -> Unit // stay on Loading
                TestMode.FAIL_ONCE -> {
                    if (failNextLoad) {
                        failNextLoad = false
                        _state.value = HomeState.Error
                    } else {
                        _state.value = content // this is what Retry gets
                    }
                }
                TestMode.EMPTY -> _state.value = HomeState.Empty
            }
        }
    }

    // TEMP: step to the next test mode, reload, and say which mode is now active.
    fun nextTestMode(): TestMode {
        val modes = TestMode.values()
        testMode = modes[(testMode.ordinal + 1) % modes.size]
        failNextLoad = testMode == TestMode.FAIL_ONCE
        load()
        return testMode
    }

    private companion object {
        const val SAMPLE_LOAD_TIME_MS = 700L // TEMP
    }
}
