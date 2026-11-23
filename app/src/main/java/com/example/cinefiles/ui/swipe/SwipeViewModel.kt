package com.example.cinefiles.ui.swipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.friendlyError
import com.example.cinefiles.logic.Discovery
import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.Moods
import com.example.cinefiles.logic.PageCounter
import com.example.cinefiles.logic.Pick
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.SwipeDeck
import com.example.cinefiles.logic.SwipeDirection
import com.example.cinefiles.logic.SwipeRecord
import com.example.cinefiles.util.Event
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Everything the swipe screen can be showing. SwipeFragment draws whichever one it is given. */
sealed class SwipeScreenState {
    /** Getting the cards ready. */
    object Loading : SwipeScreenState()

    /** Something went wrong (no internet, no TMDB key...). [error] says what, and the screen offers Retry. */
    data class Error(val error: ApiError) : SwipeScreenState()

    /** There are no movies for this mood and these preferences. */
    object Empty : SwipeScreenState()

    /** The deck is ready. Everything needed to draw it is in here, so the screen never has to ask again. */
    data class Ready(
        val top: Movie?,
        val second: Movie?,
        val third: Movie?,
        val position: Int,       // the "7" in "7 of 10"
        val total: Int,          // the "10"
        val likedCount: Int,     // session likes count
        val canUndo: Boolean,
        val isFinished: Boolean, // every card in this round has been swiped: show the end screen
        val hasMore: Boolean,    // false once no new eligible movies remain
        val isLoadingMore: Boolean = false
    ) : SwipeScreenState()
}

/**
 * Holds the swipe discovery round (up to 10 movies per round).
 * Session likes and permanent library collections remain strictly separate:
 * swiping right likes the movie for the CURRENT DISCOVERY SESSION only and does not
 * automatically save to the permanent Liked Movies collection.
 */
class SwipeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository

    private val _screen = MutableLiveData<SwipeScreenState>()
    val screen: LiveData<SwipeScreenState> = _screen

    private val _message = MutableLiveData<Event<ApiError>>()
    val message: LiveData<Event<ApiError>> = _message

    // Movies seen in this discovery session: never repeated during the session
    private val sessionSeenIds = HashSet<Int>()

    /** The movies liked in this sitting. Recommendations build on them. */
    val sessionLiked: List<Movie> get() = deck.likedMovies

    /** The movies passed in this sitting. */
    val sessionPassed: List<Movie> get() = deck.passedMovies

    /** The chosen moods as one line, for example "Happy · Romantic". */
    var moodCaption: String = ""
        private set

    private var started = false
    private var moodIds: List<String> = emptyList()
    private var preferences = Preferences()
    private var deck = SwipeDeck(emptyList())
    private var loadJob: Job? = null
    private var moreJob: Job? = null

    private val reasons = HashMap<Int, DiscoverySource>()
    private var exhausted = false
    private val pages = PageCounter()
    private var loadingMore = false
    private var backgroundLoadFailed = false

    val topMovie: Movie? get() = deck.top
    val lastSwipe: SwipeRecord? get() = deck.history.lastOrNull()

    fun reasonOf(movieId: Int): DiscoverySource? = reasons[movieId]

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

    /** Gets a fresh 10-movie deck. */
    fun load() {
        loadJob?.cancel()
        moreJob?.cancel()
        _screen.value = SwipeScreenState.Loading

        loadJob = viewModelScope.launch {
            pages.reset()
            sessionSeenIds.clear()
            nextPicks(exclude = sessionSeenIds)
                .onSuccess { picks ->
                    picks.forEach { sessionSeenIds.add(it.movie.id) }
                    showFreshDeck(picks)
                }
                .onFailure { _screen.value = SwipeScreenState.Error(friendlyError(it)) }
        }
    }

    private fun showFreshDeck(picks: List<Pick>) {
        if (picks.isEmpty()) {
            _screen.value = SwipeScreenState.Empty
            return
        }
        reasons.clear()
        exhausted = false
        backgroundLoadFailed = false
        addReasons(picks)
        deck = SwipeDeck(picks.map { it.movie })
        publish()
    }

    /**
     * Swiping right: likes for CURRENT DISCOVERY SESSION only.
     * Does NOT automatically save to the permanent Liked Movies collection.
     */
    fun onSwiped(direction: SwipeDirection) {
        if (deck.top == null) return
        deck = deck.swipe(direction)
        publish()
    }

    /** Reverts the last swipe in the current discovery session. */
    fun onUndone() {
        deck = deck.undo()
        publish()
    }

    /**
     * Loads the next round of up to 10 movies.
     * Does not repeat movies already seen in this session.
     */
    fun discoverMore() = loadMore(userAsked = true)
    fun keepSwiping() = discoverMore()

    private fun loadMore(userAsked: Boolean) {
        if (moreJob?.isActive == true) return
        moreJob = viewModelScope.launch {
            loadingMore = true
            if (deck.isFinished) publish()

            val result = try {
                nextPicks(exclude = sessionSeenIds)
            } finally {
                loadingMore = false
            }

            val endScreenShowing = deck.isFinished
            result
                .onSuccess { picks ->
                    backgroundLoadFailed = false
                    if (picks.isEmpty()) {
                        exhausted = true
                    } else {
                        picks.forEach { sessionSeenIds.add(it.movie.id) }
                        addReasons(picks)
                        deck = deck.withMoreCards(picks.map { it.movie })
                    }
                }
                .onFailure { error ->
                    if (userAsked) _message.value = Event(friendlyError(error)) else backgroundLoadFailed = true
                }
            if (endScreenShowing) publish()
        }
    }

    private suspend fun nextPicks(exclude: Set<Int>): Result<List<Pick>> = try {
        val hidden = exclude + repository.idsWithState()
        Result.success(
            Discovery.buildDeck(
                moodGenreIds = Moods.genreIdsFor(moodIds),
                preferences = preferences,
                exclude = hidden,
                fetch = { request -> fetchPage(request) }
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun fetchPage(request: DiscoveryRequest): List<Movie> {
        val movies = repository.moviesFor(request, pages.pageFor(request)).getOrThrow()
        pages.markAnswered(request)
        return movies
    }

    private fun addReasons(picks: List<Pick>) {
        for (pick in picks) reasons[pick.movie.id] = pick.source
    }

    private fun publish() {
        _screen.value = SwipeScreenState.Ready(
            top = deck.top,
            second = deck.second,
            third = deck.third,
            position = deck.shownPosition,
            total = deck.total,
            likedCount = sessionLiked.size,
            canUndo = deck.canUndo,
            isFinished = deck.isFinished,
            hasMore = !exhausted,
            isLoadingMore = loadingMore
        )
    }
}
