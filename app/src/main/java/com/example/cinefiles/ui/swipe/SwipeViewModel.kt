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
import com.example.cinefiles.logic.Achievement
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
        val position: Int,       // the "7" in "7 of 20"
        val total: Int,          // the "20"
        val likedCount: Int,     // the number in the "Picks (n)" pill
        val canUndo: Boolean,
        val isFinished: Boolean, // every card has been swiped: show the end screen
        val hasMore: Boolean,    // false once "Keep swiping" found nothing new: there is nothing left to show
        val isLoadingMore: Boolean = false // more cards are being fetched: the end screen says so
    ) : SwipeScreenState()
}

/**
 * Holds the swipe deck while the screen is open. It survives rotation and leaving the screen for
 * Details and coming back, so the deck, the progress and the liked list are never lost.
 *
 * The rules are in logic/SwipeDeck.kt (the deck) and logic/Discovery.kt (which movies go into it: the mood,
 * the preferences, a mix of kinds, no repeats). This class asks TMDB (through MovieRepository) the questions
 * Discovery makes up, keeps the current deck, and turns it into a [SwipeScreenState] after every change,
 * so progress, cards and the Picks count always agree.
 * Every like and pass is saved to the database at once (MovieRepository), and Undo takes it back. The "Picks (n)"
 * pill counts the likes of this sitting only. Every swipe is also reported to the achievements (CineFilesApp.achievements).
 */
class SwipeViewModel(app: Application) : AndroidViewModel(app) {

    private val achievements = (app as CineFilesApp).achievements
    private val repository = (app as CineFilesApp).movieRepository

    private val _screen = MutableLiveData<SwipeScreenState>()
    val screen: LiveData<SwipeScreenState> = _screen

    private val _earned = MutableLiveData<Event<List<Achievement>>>()

    /** Badges just earned by a swipe. The screen shows a banner for them (once: see [Event]). */
    val earned: LiveData<Event<List<Achievement>>> = _earned

    private val _message = MutableLiveData<Event<ApiError>>()

    /** "Keep swiping" could not get more cards: the screen tells the user (once: see [Event]). */
    val message: LiveData<Event<ApiError>> = _message

    private val _likeBlocked = MutableLiveData<Event<Movie>>()

    /** A right swipe that could not like its movie because it is on the watchlist. The screen says so (once). */
    val likeBlocked: LiveData<Event<Movie>> = _likeBlocked

    // Right swipes that the like rule blocked: they are not likes, so they are left out of the counts and of Your Picks
    private val blockedLikes = HashSet<Int>()

    /** The movies liked in this sitting (each once). Your Picks builds on them. */
    val sessionLiked: List<Movie> get() = deck.likedMovies.filter { it.id !in blockedLikes }

    /** The movies passed in this sitting (each once). Your Picks lowers the genres of these. */
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

    // Why each movie is in the deck ("Top rated"...), and whether there is anything new left to show
    private val reasons = HashMap<Int, DiscoverySource>()
    private var exhausted = false

    // Which page of TMDB each question is on, so asking again brings NEW movies
    private val pages = PageCounter()

    // More cards are being fetched right now. A failed background load is not repeated on every swipe.
    private var loadingMore = false
    private var backgroundLoadFailed = false

    // For the achievements: how many cards this sitting has swiped
    private var sessionSwipes = 0

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

    /** Gets a fresh deck from TMDB. The Retry button calls this too. */
    fun load() {
        loadJob?.cancel() // never run two loads at once
        moreJob?.cancel()
        _screen.value = SwipeScreenState.Loading

        loadJob = viewModelScope.launch {
            pages.reset() // a new deck starts again from the first page of every question
            nextPicks(exclude = emptySet())
                .onSuccess { picks -> showFreshDeck(picks) }
                .onFailure { _screen.value = SwipeScreenState.Error(friendlyError(it)) }
        }
    }

    private fun showFreshDeck(picks: List<Pick>) {
        if (picks.isEmpty()) {
            _screen.value = SwipeScreenState.Empty // no movies for this mix
            return
        }
        reasons.clear()
        blockedLikes.clear()
        exhausted = false
        backgroundLoadFailed = false
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

        // Saved at once, so it is still there after the app is closed. One small save per swipe is enough:
        // the card is locked while it flies, so two swipes can never overlap.
        viewModelScope.launch {
            if (direction == SwipeDirection.LIKE) {
                // THE LIKE RULE: a movie on the watchlist cannot be liked. (This only happens when the user added the
                // top card to the watchlist from its Details page and then swiped it right.)
                if (!repository.like(movie)) {
                    blockedLikes.add(movie.id) // so it is not counted as a like in "Picks (n)" and Your Picks
                    _likeBlocked.value = Event(movie)
                    publish()
                }
            } else {
                repository.pass(movie)
            }
        }
        publish()

        // Running low: fetch the next cards now, quietly, so the user never reaches the end screen waiting for them
        val cardsLeft = deck.total - deck.position
        if (cardsLeft <= LOAD_MORE_WHEN_CARDS_LEFT && !exhausted && !backgroundLoadFailed) {
            loadMore(userAsked = false)
        }
    }

    /** The last swipe was taken back (the card has finished flying back). */
    fun onUndone() {
        val last = deck.history.lastOrNull()
        deck = deck.undo()
        if (last != null) {
            sessionSwipes = (sessionSwipes - 1).coerceAtLeast(0)
            achievements.onUndo(last.movie, last.direction, emotionalSession())
            // The like or pass is taken back in the database too (the movie itself stays saved)
            blockedLikes.remove(last.movie.id)
            viewModelScope.launch { repository.clearReaction(last.movie.id) }
        }
        publish()
    }

    /**
     * "Keep swiping" / "Try new cards" (on the end screen): more cards are added at the end of the deck.
     * Movies that are already in the deck are never added again. If nothing new is left, the screen says so
     * instead, and if the network fails the user gets a message.
     */
    fun keepSwiping() = loadMore(userAsked = true)

    /**
     * Adds the next cards to the end of the deck. [userAsked] is true when the user pressed a button (so a
     * failure is reported to them); false for the quiet load that starts when few cards are left.
     *
     * The screen is only told about the new cards when its end screen is showing. While the user is in the
     * middle of the deck the cards are added silently: updating the screen then could interrupt a card
     * that is being dragged or is flying away. The next swipe shows them anyway.
     */
    private fun loadMore(userAsked: Boolean) {
        if (moreJob?.isActive == true) return // one at a time
        moreJob = viewModelScope.launch {
            loadingMore = true
            if (deck.isFinished) publish() // the end screen says "Finding more movies..."

            val result = try {
                nextPicks(exclude = deck.cards.map { it.id }.toSet())
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

    /**
     * Chooses the next movies for the deck, skipping [exclude] (everything already in the deck) and every movie
     * the user already has a state for in the database (liked, passed, on the watchlist, watched): those never
     * come back. Logic/Discovery decides WHAT to ask; [fetchPage] asks TMDB. A failure (no internet...) comes
     * back as a failed Result, never as a crash.
     */
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
        throw e // the screen went away: not an error
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Asks TMDB one question of the deck, on the question's current page. Throws when TMDB cannot be reached. */
    private suspend fun fetchPage(request: DiscoveryRequest): List<Movie> {
        val movies = repository.moviesFor(request, pages.pageFor(request)).getOrThrow()
        pages.markAnswered(request) // only after a good answer, so a failed page is asked again, not skipped
        return movies
    }

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
            likedCount = sessionLiked.size,
            canUndo = deck.canUndo,
            isFinished = deck.isFinished,
            hasMore = !exhausted,
            isLoadingMore = loadingMore
        )
    }

    private companion object {
        /** When this many cards (or fewer) are left in the deck, the next ones are fetched in the background. */
        const val LOAD_MORE_WHEN_CARDS_LEFT = 5
    }
}
