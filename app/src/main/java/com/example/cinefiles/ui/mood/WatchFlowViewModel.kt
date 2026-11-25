package com.example.cinefiles.ui.mood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.friendlyError
import com.example.cinefiles.logic.Length
import com.example.cinefiles.logic.MinRating
import com.example.cinefiles.logic.Mood
import com.example.cinefiles.logic.MoodSelection
import com.example.cinefiles.logic.Moods
import com.example.cinefiles.logic.MovieStatus
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.RecommendationEngine
import com.example.cinefiles.logic.ReleasePeriod
import com.example.cinefiles.logic.ToggleResult
import com.example.cinefiles.logic.hasStatus
import com.example.cinefiles.ui.picks.PicksState
import com.example.cinefiles.util.Event
import com.example.cinefiles.util.UndoInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Remembers what the user chose while they walk through "Watch something":
 * Mood -> Preferences -> Swipe -> Picks.
 *
 * All of those screens get THE SAME instance with `by activityViewModels()`, so a choice made on one
 * screen is still there on the next, and when you go back. It also survives screen rotation.
 * Home calls [reset] when a new flow starts.
 *
 * The rules for picking moods live in logic/MoodSelection.kt; this class only holds the result.
 *
 * It also holds YOUR PICKS while the flow is open (Swipe -> Your Picks): the swipe screen hands it the liked and
 * passed movies with [buildPicks], logic/RecommendationEngine.kt scores the candidates that MovieRepository fetches,
 * and Your Picks shows [picks]. Picks are not saved: they are forgotten when a new flow starts ([reset]).
 */
class WatchFlowViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository
    private val engine = RecommendationEngine()

    private var currentSelection = MoodSelection()
    private val _selection = MutableLiveData(currentSelection)

    /** The moods picked so far. */
    val selection: LiveData<MoodSelection> = _selection

    /**
     * Which place on the Mood screen's slider is showing (an index into Moods.spectrum). Kept here so that
     * going on to Preferences and coming back, or turning the phone, leaves the slider where it was.
     */
    var moodSliderIndex = DEFAULT_MOOD_INDEX

    /** The choices of the Preferences screen. Nothing needs to watch them yet, so this is a plain value. */
    var preferences = Preferences()
        private set

    /**
     * The user tapped a mood card. Applies the selection rules and says what happened,
     * so the screen can shake the card when the limit of three was reached.
     */
    fun onMoodTapped(mood: Mood): ToggleResult {
        val outcome = currentSelection.toggle(mood)
        currentSelection = outcome.selection
        _selection.value = currentSelection
        return outcome.result
    }

    fun setReleasePeriod(value: ReleasePeriod) {
        preferences = preferences.copy(releasePeriod = value)
    }

    fun setMinRating(value: MinRating) {
        preferences = preferences.copy(minRating = value)
    }

    fun setLength(value: Length) {
        preferences = preferences.copy(length = value)
    }

    /** The language to ask for as a TMDB code ("ko"), or null for any language. */
    fun setLanguage(code: String?) {
        preferences = preferences.copy(language = code)
    }

    /** Back to "Any" everywhere. Used by Skip. */
    fun resetPreferences() {
        preferences = Preferences()
    }

    /** Forget everything. Home calls this when a new flow starts. */
    fun reset() {
        currentSelection = MoodSelection()
        _selection.value = currentSelection
        preferences = Preferences()
        moodSliderIndex = DEFAULT_MOOD_INDEX
        picksJob?.cancel()
        picksLiked = emptyList()
        picksPassed = emptyList()
        _picks.value = PicksState.Idle
    }

    // ====================================================================================
    //  Your Picks
    // ====================================================================================

    private val _picks = MutableLiveData<PicksState>(PicksState.Idle)

    /** What Your Picks shows. */
    val picks: LiveData<PicksState> = _picks

    private var picksJob: Job? = null
    private var picksLiked = emptyList<Movie>()
    private var picksPassed = emptyList<Movie>()
    private var picksAnimate = true

    /** How many movies were liked in the sitting the picks are built from (0 = the picks come from the mood alone). */
    val picksLikedCount: Int get() = picksLiked.size

    /** The names of the chosen moods, for example "Happy + Romantic" (for the "building your picks" line). */
    fun moodNames(separator: String): String =
        chosenMoods().joinToString(separator) { getApplication<Application>().getString(it.shortLabel) }

    /**
     * The swipe screen calls this just before it opens Your Picks. [liked] and [passed] are the movies of this sitting.
     * If they are the same as the last time (the user went back and forth), the picks already built are kept;
     * after more swiping they are built again. [animate] false (the phone's animations are off) skips the short
     * "building" moment.
     */
    fun buildPicks(liked: List<Movie>, passed: List<Movie>, animate: Boolean) {
        val unchanged = liked.map { it.id } == picksLiked.map { it.id } && passed.map { it.id } == picksPassed.map { it.id }
        val current = _picks.value
        if (unchanged && (current is PicksState.Ready || current is PicksState.Building)) return

        picksLiked = liked
        picksPassed = passed
        picksAnimate = animate
        startPicks()
    }

    /** The Retry button of Your Picks. */
    fun retryPicks() = startPicks()

    private fun startPicks() {
        picksJob?.cancel()
        _picks.value = PicksState.Building
        picksJob = viewModelScope.launch {
            // The "building your picks" moment is never shorter than BUILDING_MS (it is a small reward), but it
            // also never makes a slow answer slower: the two run side by side and we wait for the longer one.
            val shownFor = if (picksAnimate) BUILDING_MS else 0L
            val result = coroutineScope {
                val moment = async { delay(shownFor) }
                val work = async { choosePicks() }
                moment.await()
                work.await()
            }
            _picks.value = result
        }
    }

    /** Asks TMDB for candidates and lets the engine choose (the rules are in logic/RecommendationEngine.kt). */
    private suspend fun choosePicks(): PicksState {
        val moods = chosenMoods()
        val profile = engine.genreProfile(moods, picksLiked, picksPassed)
        val pool = repository.picksCandidates(
            genreIds = engine.topGenres(profile),
            preferences = preferences,
            seeds = engine.similarSeeds(picksLiked),
            exclude = (picksLiked + picksPassed).map { it.id }.toSet()
        ).getOrElse { return PicksState.Error(friendlyError(it)) }

        val picks = engine.recommend(pool.movies, moods, picksLiked, picksPassed, similarTo = pool.similarTo)
        if (picks.isEmpty()) return PicksState.Empty
        return PicksState.Ready(
            summary = getApplication<Application>().getString(R.string.picks_subtitle),
            top = picks.first(),
            more = picks.drop(1),
            liked = picksLiked
        )
    }

    private fun chosenMoods(): List<Mood> = Moods.all.filter { it.id in currentSelection.ids }

    // ---------- What the user can do with a pick (the grid, the Top Match card and the quick view) ----------

    /** The ids of the movies on the watchlist, live: the bookmark of a pick is filled when it is on the list. */
    val watchlistIds: LiveData<Set<Int>> = repository.observeWatchlist().map { list -> list.map { it.id }.toSet() }.asLiveData()

    private val _undo = MutableLiveData<Event<UndoInfo>>()

    /** The last change, so the screen can say "Added to your watchlist. Undo" (once: see [Event]). */
    val undo: LiveData<Event<UndoInfo>> = _undo

    /** The bookmark: puts the movie on the watchlist, or takes it off when it is already there. */
    fun toggleWatchlist(movie: Movie) {
        viewModelScope.launch {
            val before = repository.savedMovie(movie.id) ?: movie
            if (before.hasStatus(MovieStatus.WATCHLIST)) {
                repository.removeFromWatchlist(movie.id)
                _undo.value = Event(UndoInfo(R.string.msg_removed_watchlist, before))
            } else {
                repository.addToWatchlist(movie)
                _undo.value = Event(UndoInfo(R.string.msg_added_watchlist, before))
            }
        }
    }

    /** "Mark as watched" in the quick view. It also takes the movie off the watchlist. */
    fun markWatched(movie: Movie) {
        viewModelScope.launch {
            val before = repository.savedMovie(movie.id) ?: movie
            repository.markWatched(movie)
            _undo.value = Event(UndoInfo(R.string.msg_marked_watched, before))
        }
    }

    /** Undo: puts the movie's state back exactly as it was. */
    fun undo(before: Movie) {
        viewModelScope.launch { repository.restoreState(before) }
    }

    private companion object {
        const val DEFAULT_MOOD_INDEX = 1 // the slider starts on the second mood, Happy
        const val BUILDING_MS = 1_000L   // the "building your picks" moment: between 0.7 and 1.2 seconds
    }
}
