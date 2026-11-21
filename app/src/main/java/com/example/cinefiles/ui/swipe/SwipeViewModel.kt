package com.example.cinefiles.ui.swipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.SampleData
import com.example.cinefiles.logic.Achievement
import com.example.cinefiles.logic.Discovery
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.Moods
import com.example.cinefiles.logic.Pick
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.SwipeDeck
import com.example.cinefiles.logic.SwipeDirection
import com.example.cinefiles.logic.SwipeRecord
import com.example.cinefiles.util.Event
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
        val isFinished: Boolean, // every card has been swiped: show the end screen
        val hasMore: Boolean     // false once "Keep swiping" found nothing new: there is nothing left to show
    ) : SwipeScreenState()
}

/**
 * Holds the swipe deck while the screen is open. It survives rotation and leaving the screen for
 * Details and coming back, so the deck, the progress and the liked list are never lost.
 *
 * The rules are in logic/SwipeDeck.kt (the deck) and logic/Discovery.kt (which movies go into it: the mood,
 * the preferences, a mix of kinds, no repeats). This class only keeps the current deck and turns it into
 * a [SwipeScreenState] after every change, so progress, cards and the Picks count always agree.
 * The liked and passed lists live here for now; Batch 6 stores them in the database.
 * Every swipe is also reported to the achievements (CineFilesApp.achievements).
 */
class SwipeViewModel(app: Application) : AndroidViewModel(app) {

    private val achievements = (app as CineFilesApp).achievements

    private val _screen = MutableLiveData<SwipeScreenState>()
    val screen: LiveData<SwipeScreenState> = _screen

    private val _earned = MutableLiveData<Event<List<Achievement>>>()

    /** Badges just earned by a swipe. The screen shows a banner for them (once: see [Event]). */
    val earned: LiveData<Event<List<Achievement>>> = _earned

    /** The chosen moods as one line, for example "Happy · Romantic". */
    var moodCaption: String = ""
        private set

    private var started = false
    private var moodIds: List<String> = emptyList()
    private var preferences = Preferences()
    private var deck = SwipeDeck(emptyList())
    private var loadJob: Job? = null
    private var keepJob: Job? = null

    // Why each movie is in the deck ("Top rated"...), and whether there is anything new left to show
    private val reasons = HashMap<Int, DiscoverySource>()
    private var exhausted = false

    // For the achievements: how many cards this sitting has swiped
    private var sessionSwipes = 0

    // TEMP: see TestMode. testMode is the mode picked by long-press; failNextLoad makes FAIL_ONCE fail one load.
    private var testMode = TestMode.NORMAL
    private var failNextLoad = false

    /** The card on top right now, or null. */
    val topMovie: Movie? get() = deck.top

    /** The swipe that Undo would take back, or null when there is nothing to undo. */
    val lastSwipe: SwipeRecord? get() = deck.history.lastOrNull()

    /** Why this movie is in the deck (the small line above its title), or null. */
    fun reasonOf(movieId: Int): DiscoverySource? = reasons[movieId]

    /**
     * Called by the screen every time it appears. Only the FIRST call does anything,
     * so coming back to the screen keeps the deck exactly as it was.
     */
    fun start(selectedMoodIds: List<String>, selectedPreferences: Preferences) {
        if (started) return
        started = true

        moodIds = selectedMoodIds
        preferences = selectedPreferences
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
            // TEMP: pretend to wait for the network.
            // Batch 5 replaces this wait (and the sample movies in nextPicks) with a call to MovieRepository.
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

    private suspend fun showFreshDeck() {
        val picks = nextPicks(exclude = emptySet())
        if (picks.isEmpty()) {
            _screen.value = SwipeScreenState.Empty // no movies for this mix
            return
        }
        reasons.clear()
        exhausted = false
        sessionSwipes = 0
        addReasons(picks)
        deck = SwipeDeck(picks.map { it.movie })
        publish()
    }

    /** The top card was liked or passed (the card has finished flying away). */
    fun onSwiped(direction: SwipeDirection) {
        val movie = deck.top ?: return
        deck = deck.swipe(direction)
        sessionSwipes++

        val newBadges = achievements.onSwipe(movie, direction, sessionSwipes, emotionalSession())
        if (newBadges.isNotEmpty()) _earned.value = Event(newBadges)
        publish()
    }

    /** The last swipe was taken back (the card has finished flying back). */
    fun onUndone() {
        val last = deck.history.lastOrNull()
        deck = deck.undo()
        if (last != null) {
            sessionSwipes = (sessionSwipes - 1).coerceAtLeast(0)
            achievements.onUndo(last.movie, last.direction, emotionalSession())
        }
        publish()
    }

    /**
     * "Keep swiping" / "Try new cards": more cards are added at the end of the deck. Movies that are already
     * in the deck are never added again. If nothing new is left, the screen says so instead.
     */
    fun keepSwiping() {
        if (keepJob?.isActive == true) return
        keepJob = viewModelScope.launch {
            val picks = nextPicks(exclude = deck.cards.map { it.id }.toSet())
            if (picks.isEmpty()) {
                exhausted = true
            } else {
                addReasons(picks)
                deck = deck.withMoreCards(picks.map { it.movie })
            }
            publish()
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

    /**
     * Chooses the next movies for the deck, skipping [exclude] (everything already in the deck).
     * TEMP: the questions are answered from the sample movies. Batch 5 answers the same questions with
     * MovieRepository (TMDB): only the `fetch` line changes.
     */
    private suspend fun nextPicks(exclude: Set<Int>): List<Pick> = Discovery.buildDeck(
        moodGenreIds = Moods.genreIdsFor(moodIds),
        preferences = preferences,
        exclude = exclude,
        fetch = { request -> SampleData.discover(request) }
    )

    private fun addReasons(picks: List<Pick>) {
        for (pick in picks) reasons[pick.movie.id] = pick.source
    }

    private fun emotionalSession(): Boolean = "emotional" in moodIds

    private fun publish() {
        _screen.value = SwipeScreenState.Ready(
            top = deck.top,
            second = deck.second,
            third = deck.third,
            position = deck.shownPosition,
            total = deck.total,
            likedCount = deck.likedMovies.size,
            canUndo = deck.canUndo,
            isFinished = deck.isFinished,
            hasMore = !exhausted
        )
    }

    private companion object {
        const val SAMPLE_LOAD_TIME_MS = 600L // TEMP
    }
}
