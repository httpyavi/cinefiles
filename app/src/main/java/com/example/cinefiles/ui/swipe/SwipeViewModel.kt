package com.example.cinefiles.ui.swipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.friendlyError
import com.example.cinefiles.logic.DiscoveryFeed
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.Moods
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
        val position: Int,       // the "7" in "7 of 10": counted inside the current batch
        val total: Int,          // the "10": the cards of the current batch
        val likedCount: Int,     // session likes count
        val canUndo: Boolean,
        val isFinished: Boolean, // every card in this batch has been swiped: show the end screen
        val hasMore: Boolean,    // false once no new eligible movies remain
        val isLoadingMore: Boolean = false
    ) : SwipeScreenState()
}

/**
 * Holds the swipe discovery sitting: batches of 10 movies. After the 10th swipe of a batch the end screen offers
 * "See recommendations" and "Discover 10 more movies" (the next 10 unseen movies, same moods and preferences).
 * Which movies come up is decided by logic/Discovery.kt ([DiscoveryFeed]); one feed serves the whole sitting.
 *
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

    private val _notice = MutableLiveData<Event<Int>>()

    /** A one-time line for the screen: "Nothing fit every choice, so we added the closest movies". */
    val notice: LiveData<Event<Int>> = _notice
    private var closeMatchesExplained = false

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
    private var feed = DiscoveryFeed(emptyList(), Preferences())
    private var deck = SwipeDeck(emptyList())
    private var loadJob: Job? = null
    private var moreJob: Job? = null

    /** How many cards of [deck] belong to earlier batches: the current batch starts at this card. */
    private var batchStart = 0

    private val reasons = HashMap<Int, DiscoverySource>()
    private var exhausted = false
    private var loadingMore = false

    val topMovie: Movie? get() = deck.top

    /** The swipe Undo would take back. Only swipes of the current batch can be taken back. */
    val lastSwipe: SwipeRecord? get() = if (canUndo) deck.history.lastOrNull() else null

    private val canUndo: Boolean get() = deck.position > batchStart && !loadingMore

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

    /** Gets a fresh 10-movie deck (a new sitting: TMDB is asked from its first page again). */
    fun load() {
        loadJob?.cancel()
        moreJob?.cancel()
        loadingMore = false
        _screen.value = SwipeScreenState.Loading

        loadJob = viewModelScope.launch {
            feed = DiscoveryFeed(Moods.genreIdsFor(moodIds), preferences, Moods.avoidedGenreIdsFor(moodIds))
            sessionSeenIds.clear()
            nextPicks()
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
        addReasons(picks)
        deck = SwipeDeck(picks.map { it.movie })
        batchStart = 0
        publish()
        explainCloseMatches(picks)
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

    /** Reverts the last swipe of the current batch. */
    fun onUndone() {
        if (!canUndo) return
        deck = deck.undo()
        publish()
    }

    /**
     * "Discover 10 more movies" on the end screen: the next batch of up to 10 unseen movies, with exactly the same
     * moods and preferences. Never repeats a movie already seen in this sitting.
     */
    fun discoverMore() {
        if (moreJob?.isActive == true || !deck.isFinished) return
        moreJob = viewModelScope.launch {
            loadingMore = true
            publish()

            val result = try {
                nextPicks()
            } finally {
                loadingMore = false
            }

            result
                .onSuccess { picks ->
                    if (picks.isEmpty()) {
                        exhausted = true
                    } else {
                        exhausted = false
                        picks.forEach { sessionSeenIds.add(it.movie.id) }
                        addReasons(picks)
                        batchStart = deck.total // the new batch starts after every card swiped so far
                        deck = deck.withMoreCards(picks.map { it.movie })
                    }
                }
                .onFailure { error -> _message.value = Event(friendlyError(error)) }
            publish()
            result.getOrNull()?.let { explainCloseMatches(it) }
        }
    }

    private suspend fun nextPicks(): Result<List<Pick>> = try {
        val hidden = sessionSeenIds + repository.idsWithState()
        Result.success(
            feed.next(
                exclude = hidden,
                fetch = { request, page -> repository.moviesFor(request, page).getOrThrow() },
                lookUpRuntimes = { movies -> repository.withRuntimes(movies).getOrThrow() }
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** The first time close matches are dealt in this sitting, the screen says why (their cards say "Close match"). */
    private fun explainCloseMatches(picks: List<Pick>) {
        if (closeMatchesExplained || picks.none { it.source == DiscoverySource.CLOSE_MATCH }) return
        closeMatchesExplained = true
        _notice.value = Event(R.string.swipe_close_matches_notice)
    }

    private fun addReasons(picks: List<Pick>) {
        for (pick in picks) reasons[pick.movie.id] = pick.source
    }

    private fun publish() {
        val batchTotal = deck.total - batchStart
        val batchSwiped = deck.position - batchStart
        _screen.value = SwipeScreenState.Ready(
            top = deck.top,
            second = deck.second,
            third = deck.third,
            position = minOf(batchSwiped + 1, batchTotal),
            total = batchTotal,
            likedCount = sessionLiked.size,
            canUndo = canUndo,
            isFinished = deck.isFinished,
            hasMore = !exhausted,
            isLoadingMore = loadingMore
        )
    }
}
