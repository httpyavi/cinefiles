package com.example.cinefiles.ui.swipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.SampleData
import com.example.cinefiles.logic.Moods
import com.example.cinefiles.logic.SwipeDeck
import com.example.cinefiles.logic.SwipeDirection
import com.example.cinefiles.logic.SwipeRecord
import com.example.cinefiles.util.TestMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the swipe screen can be showing. SwipeFragment draws whichever one it is given. */
sealed class SwipeScreenState {
    /** Getting the cards ready. */
    object Loading : SwipeScreenState()

    /** Something went wrong (for example no internet). The screen offers Retry. */
    object Error : SwipeScreenState()

    /** There are no movies for this mood and these preferences. */
    object Empty : SwipeScreenState()

    /** The deck is ready. Everything needed to draw it is in here, so the screen never has to ask again. */
    data class Ready(
        val top: Movie?,
        val second: Movie?,
        val third: Movie?,
        val position: Int,       // the "7" in "7 of 20"
        val total: Int,          // the "20"
        val likedCount: Int,     // the number in the "Picks (n)" pill
        val canUndo: Boolean,
        val isFinished: Boolean  // every card has been swiped: show the end screen
    ) : SwipeScreenState()
}

/**
 * Holds the swipe deck while the screen is open. It survives rotation and leaving the screen for
 * Details and coming back, so the deck, the progress and the liked list are never lost.
 *
 * The rules are in logic/SwipeDeck.kt. This class only keeps the current deck and turns it into
 * a [SwipeScreenState] after every change, so progress, cards and the Picks count always agree.
 * The liked and passed lists live here for now; Batch 6 stores them in the database.
 */
class SwipeViewModel(app: Application) : AndroidViewModel(app) {

    private val _screen = MutableLiveData<SwipeScreenState>()
    val screen: LiveData<SwipeScreenState> = _screen

    /** The chosen moods as one line, for example "Happy · Romantic". */
    var moodCaption: String = ""
        private set

    private var started = false
    private var deck = SwipeDeck(emptyList())
    private var page = 0
    private var loadJob: Job? = null

    // TEMP: see TestMode. testMode is the mode picked by long-press; failNextLoad makes FAIL_ONCE fail one load.
    private var testMode = TestMode.NORMAL
    private var failNextLoad = false

    /** The card on top right now, or null. */
    val topMovie: Movie? get() = deck.top

    /** The swipe that Undo would take back, or null when there is nothing to undo. */
    val lastSwipe: SwipeRecord? get() = deck.history.lastOrNull()

    /**
     * Called by the screen every time it appears. Only the FIRST call does anything,
     * so coming back to the screen keeps the deck exactly as it was.
     */
    fun start(selectedMoodIds: List<String>) {
        if (started) return
        started = true

        moodCaption = Moods.all
            .filter { it.id in selectedMoodIds }
            .joinToString(" · ") { getApplication<Application>().getString(it.shortLabel) }
        load()
    }

    /** Gets a fresh deck. The Retry button calls this too. */
    fun load() {
        loadJob?.cancel() // never run two loads at once
        _screen.value = SwipeScreenState.Loading

        loadJob = viewModelScope.launch {
            // TEMP: pretend to wait for the network, then use the sample movies.
            // Batch 5 replaces this block with a call to MovieRepository.
            delay(SAMPLE_LOAD_TIME_MS)
            when (testMode) {
                TestMode.NORMAL -> showFreshDeck()
                TestMode.LOADING_FOREVER -> Unit // stay on Loading
                TestMode.FAIL_ONCE -> {
                    if (failNextLoad) {
                        failNextLoad = false
                        _screen.value = SwipeScreenState.Error
                    } else {
                        showFreshDeck() // this is what Retry gets
                    }
                }
                TestMode.EMPTY -> _screen.value = SwipeScreenState.Empty
            }
        }
    }

    private fun showFreshDeck() {
        page = 0
        val cards = SampleData.swipePage(page)
        if (cards.isEmpty()) {
            _screen.value = SwipeScreenState.Empty // no movies for this mix
            return
        }
        deck = SwipeDeck(cards)
        publish()
    }

    /** The top card was liked or passed (the card has finished flying away). */
    fun onSwiped(direction: SwipeDirection) {
        deck = deck.swipe(direction)
        publish()
    }

    /** The last swipe was taken back (the card has finished flying back). */
    fun onUndone() {
        deck = deck.undo()
        publish()
    }

    /** "Keep swiping" / "Try new cards": more cards are added at the end of the deck. */
    fun keepSwiping() {
        page++
        // TEMP: the same sample movies again, in a different order. Batch 5 loads the next real page.
        deck = deck.withMoreCards(SampleData.swipePage(page))
        publish()
    }

    // TEMP: step to the next test mode, reload, and say which mode is now active.
    fun nextTestMode(): TestMode {
        val modes = TestMode.values()
        testMode = modes[(testMode.ordinal + 1) % modes.size]
        failNextLoad = testMode == TestMode.FAIL_ONCE
        load()
        return testMode
    }

    private fun publish() {
        _screen.value = SwipeScreenState.Ready(
            top = deck.top,
            second = deck.second,
            third = deck.third,
            position = deck.shownPosition,
            total = deck.total,
            likedCount = deck.likedMovies.size,
            canUndo = deck.canUndo,
            isFinished = deck.isFinished
        )
    }

    private companion object {
        const val SAMPLE_LOAD_TIME_MS = 600L // TEMP
    }
}
