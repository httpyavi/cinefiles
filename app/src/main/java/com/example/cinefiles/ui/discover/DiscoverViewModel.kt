package com.example.cinefiles.ui.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.friendlyError
import com.example.cinefiles.logic.BrowseCategory
import com.example.cinefiles.logic.DiscoverFilters
import com.example.cinefiles.logic.appendUnique
import com.example.cinefiles.logic.filterByGenre
import com.example.cinefiles.util.Event
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds what the Discover tab shows: the chosen filters, the pages loaded so far and the state to draw.
 * It is shared by the whole app window (activityViewModels), so Home's search icon can ask for the keyboard,
 * and the tab remembers your search while you visit other tabs.
 *
 * How the list is chosen (see logic/DiscoverRules.kt): with a search of 2 or more letters it asks TMDB's /search
 * (after 400 ms of no typing); otherwise the category chips and the genre chip pick the list. Only ONE request
 * runs at a time: a new search or chip cancels the old one, so an old answer can never overwrite a newer one.
 * The next page is asked for when the user scrolls near the end (PaginationScrollListener), until the last page.
 */
class DiscoverViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository

    private val filtersFlow = MutableStateFlow(DiscoverFilters())

    /** The chosen filters (the screen shows them in the search field and the chips). */
    val filters: LiveData<DiscoverFilters> = filtersFlow.asLiveData()

    private val load = MutableStateFlow<DiscoverLoad>(DiscoverLoad.Loading)

    /** What to draw. The saved movies are watched too, so a badge appears at once when a movie is saved elsewhere. */
    val state: LiveData<DiscoverState> = combine(
        load,
        repository.observeWithState().map { movies -> movies.associateBy { it.id } },
        filtersFlow
    ) { load, saved, filters -> buildDiscoverState(load, saved, filters) }.asLiveData()

    private val _focusSearch = MutableLiveData<Event<Unit>>()

    /** Home's search icon asks for the keyboard (once: see [Event]). */
    val focusSearch: LiveData<Event<Unit>> = _focusSearch

    private val _loadMoreFailed = MutableLiveData<Event<Unit>>()

    /** The next page could not be loaded. The list stays as it is and the screen offers to try again. */
    val loadMoreFailed: LiveData<Event<Unit>> = _loadMoreFailed

    private var started = false
    private var requestJob: Job? = null
    private var loaded: List<Movie> = emptyList() // all pages so far, without repeats
    private var lastPage = 0
    private var hasMore = false
    private var autoPagesLeft = 0                 // extra pages that may be asked for without the user scrolling

    /** The first call loads the first list. (Nothing is asked for before the tab is opened.) */
    fun start() {
        if (started) return
        started = true
        reload(debounce = false)
    }

    /** Home's search icon: open the tab with the keyboard ready. */
    fun requestSearch() {
        _focusSearch.value = Event(Unit)
    }

    // ---------- What the user does ----------

    /** The text of the search field changed. A search starts 400 ms after the last key. */
    fun setQuery(text: String) {
        val before = filtersFlow.value
        if (text == before.query) return
        val now = before.copy(query = text)
        filtersFlow.value = now
        // Typing one letter changes nothing: it is neither a search nor a way out of one
        if (!before.isSearching && !now.isSearching) return
        reload(debounce = now.isSearching)
    }

    /** The search key of the keyboard: no need to wait for the pause. */
    fun searchNow() {
        if (filtersFlow.value.isSearching) reload(debounce = false)
    }

    fun selectCategory(category: BrowseCategory) {
        val before = filtersFlow.value
        if (category == before.category) return
        filtersFlow.value = before.copy(category = category)
        if (!filtersFlow.value.isSearching) reload(debounce = false) // while searching, the category chips do nothing
    }

    /** A genre chip, or null when the chip was switched off. */
    fun selectGenre(genreId: Int?) {
        val before = filtersFlow.value
        if (genreId == before.genreId) return
        filtersFlow.value = before.copy(genreId = genreId)
        if (filtersFlow.value.isSearching) {
            // The loaded results are filtered on the phone; if that leaves few, look at a few more pages
            autoPagesLeft = MAX_AUTO_PAGES
            if (requestJob?.isActive != true) requestJob = viewModelScope.launch { loadMoreWhileShort() }
        } else {
            reload(debounce = false)
        }
    }

    fun clearFilters() {
        filtersFlow.value = DiscoverFilters()
        reload(debounce = false)
    }

    /** The Retry buttons: the first page again. */
    fun retry() = reload(debounce = false)

    /** The user scrolled near the end of the list. */
    fun loadNextPage() {
        if (requestJob?.isActive == true || !hasMore || lastPage == 0) return
        publish(isLoadingMore = true)
        requestJob = viewModelScope.launch {
            requestPage(lastPage + 1)
            loadMoreWhileShort()
        }
    }

    // ---------- Asking TMDB ----------

    /** Starts the list again from page 1 (and forgets the old request: a late answer can no longer arrive). */
    private fun reload(debounce: Boolean) {
        requestJob?.cancel()
        loaded = emptyList()
        lastPage = 0
        hasMore = false
        autoPagesLeft = MAX_AUTO_PAGES
        load.value = DiscoverLoad.Loading

        requestJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            requestPage(1)
            loadMoreWhileShort()
        }
    }

    /** Asks for one page of the current search or list and adds it to what is shown. */
    private suspend fun requestPage(number: Int) {
        val filters = filtersFlow.value
        val result = if (filters.isSearching) {
            repository.searchPage(filters.query, number)
        } else {
            repository.browsePage(filters.category, filters.genreId, number)
        }

        result
            .onSuccess { page ->
                lastPage = page.page
                hasMore = page.hasMore
                loaded = appendUnique(loaded, page.movies) // a movie that moved to the next page is not shown twice
                publish(isLoadingMore = false)
            }
            .onFailure { error ->
                if (number == 1) {
                    load.value = DiscoverLoad.Failed(friendlyError(error))
                } else {
                    publish(isLoadingMore = false) // keep what is shown; the screen offers to try again
                    _loadMoreFailed.value = Event(Unit)
                }
            }
    }

    /**
     * Pages can have few usable movies (TMDB movies without a poster are dropped, and a genre chip can filter most
     * of a page away). When fewer than MIN_SHOWN are left to show, the next pages are asked for without waiting for
     * the user to scroll, a few times at most. Otherwise a short list could never be scrolled, so never grow.
     */
    private suspend fun loadMoreWhileShort() {
        while (hasMore && autoPagesLeft > 0 && shownCount() < MIN_SHOWN && load.value is DiscoverLoad.Loaded) {
            autoPagesLeft--
            publish(isLoadingMore = true)
            val before = lastPage
            requestPage(lastPage + 1)
            if (lastPage == before) break // that page failed: stop asking
        }
    }

    private fun shownCount(): Int {
        val filters = filtersFlow.value
        return if (filters.isSearching) filterByGenre(loaded, filters.genreId).size else loaded.size
    }

    private fun publish(isLoadingMore: Boolean) {
        load.value = DiscoverLoad.Loaded(loaded, hasMore, isLoadingMore)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L // wait for a pause in typing before asking TMDB
        const val MIN_SHOWN = 12            // a list shorter than this asks for more pages by itself
        const val MAX_AUTO_PAGES = 5        // ...but never more than this many without the user scrolling
    }
}
