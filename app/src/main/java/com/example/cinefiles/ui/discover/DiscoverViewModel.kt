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
import com.example.cinefiles.logic.withStateOf
import com.example.cinefiles.util.Event
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Holds movies for each category and genre section in Discover. */
data class DiscoverSections(
    val popular: List<Movie> = emptyList(),
    val trending: List<Movie> = emptyList(),
    val topRated: List<Movie> = emptyList(),
    val action: List<Movie> = emptyList(),
    val adventure: List<Movie> = emptyList(),
    val animation: List<Movie> = emptyList(),
    val comedy: List<Movie> = emptyList(),
    val drama: List<Movie> = emptyList(),
    val horror: List<Movie> = emptyList(),
    val romance: List<Movie> = emptyList(),
    val scifi: List<Movie> = emptyList(),
    val thriller: List<Movie> = emptyList()
)

/**
 * Holds what the Discover tab shows: the chosen filters, the sections of categories and genres,
 * search results, and pagination.
 */
class DiscoverViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository

    private val filtersFlow = MutableStateFlow(DiscoverFilters())

    /** The chosen filters (the screen shows them in the search field and the chips). */
    val filters: LiveData<DiscoverFilters> = filtersFlow.asLiveData()

    private val load = MutableStateFlow<DiscoverLoad>(DiscoverLoad.Loading)

    /** What to draw for search results or full-grid browsing. */
    val state: LiveData<DiscoverState> = combine(
        load,
        repository.observeWithState().map { movies -> movies.associateBy { it.id } },
        filtersFlow
    ) { load, saved, filters -> buildDiscoverState(load, saved, filters) }.asLiveData()

    private val rawSections = MutableStateFlow(DiscoverSections())

    /** Live sections data merged with user library status (liked, watchlist, watched, rating). */
    val sections: LiveData<DiscoverSections> = combine(
        rawSections,
        repository.observeWithState().map { movies -> movies.associateBy { it.id } }
    ) { raw, saved ->
        DiscoverSections(
            popular = raw.popular.map { it.withStateOf(saved[it.id]) },
            trending = raw.trending.map { it.withStateOf(saved[it.id]) },
            topRated = raw.topRated.map { it.withStateOf(saved[it.id]) },
            action = raw.action.map { it.withStateOf(saved[it.id]) },
            adventure = raw.adventure.map { it.withStateOf(saved[it.id]) },
            animation = raw.animation.map { it.withStateOf(saved[it.id]) },
            comedy = raw.comedy.map { it.withStateOf(saved[it.id]) },
            drama = raw.drama.map { it.withStateOf(saved[it.id]) },
            horror = raw.horror.map { it.withStateOf(saved[it.id]) },
            romance = raw.romance.map { it.withStateOf(saved[it.id]) },
            scifi = raw.scifi.map { it.withStateOf(saved[it.id]) },
            thriller = raw.thriller.map { it.withStateOf(saved[it.id]) }
        )
    }.asLiveData()

    private val _focusSearch = MutableLiveData<Event<Unit>>()

    /** Home's search icon asks for the keyboard. */
    val focusSearch: LiveData<Event<Unit>> = _focusSearch

    private val _loadMoreFailed = MutableLiveData<Event<Unit>>()

    /** The next page could not be loaded. */
    val loadMoreFailed: LiveData<Event<Unit>> = _loadMoreFailed

    private var started = false
    private var requestJob: Job? = null
    private var loaded: List<Movie> = emptyList()
    private var lastPage = 0
    private var hasMore = false
    private var autoPagesLeft = 0

    /** The first call loads the sections and initial browse list. */
    fun start() {
        if (started) return
        started = true
        loadSections()
        reload(debounce = false)
    }

    private fun loadSections() {
        viewModelScope.launch {
            try {
                val popJob = async { repository.browsePage(BrowseCategory.POPULAR, null).getOrNull()?.movies.orEmpty() }
                val trendJob = async { repository.browsePage(BrowseCategory.TRENDING, null).getOrNull()?.movies.orEmpty() }
                val topJob = async { repository.browsePage(BrowseCategory.TOP_RATED, null).getOrNull()?.movies.orEmpty() }

                val actionJob = async { repository.browsePage(BrowseCategory.POPULAR, 28).getOrNull()?.movies.orEmpty() }
                val advJob = async { repository.browsePage(BrowseCategory.POPULAR, 12).getOrNull()?.movies.orEmpty() }
                val animJob = async { repository.browsePage(BrowseCategory.POPULAR, 16).getOrNull()?.movies.orEmpty() }
                val comJob = async { repository.browsePage(BrowseCategory.POPULAR, 35).getOrNull()?.movies.orEmpty() }
                val dramaJob = async { repository.browsePage(BrowseCategory.POPULAR, 18).getOrNull()?.movies.orEmpty() }
                val horrorJob = async { repository.browsePage(BrowseCategory.POPULAR, 27).getOrNull()?.movies.orEmpty() }
                val romJob = async { repository.browsePage(BrowseCategory.POPULAR, 10749).getOrNull()?.movies.orEmpty() }
                val scifiJob = async { repository.browsePage(BrowseCategory.POPULAR, 878).getOrNull()?.movies.orEmpty() }
                val thrilJob = async { repository.browsePage(BrowseCategory.POPULAR, 53).getOrNull()?.movies.orEmpty() }

                rawSections.value = DiscoverSections(
                    popular = popJob.await(),
                    trending = trendJob.await(),
                    topRated = topJob.await(),
                    action = actionJob.await(),
                    adventure = advJob.await(),
                    animation = animJob.await(),
                    comedy = comJob.await(),
                    drama = dramaJob.await(),
                    horror = horrorJob.await(),
                    romance = romJob.await(),
                    scifi = scifiJob.await(),
                    thriller = thrilJob.await()
                )
            } catch (_: Exception) {
                // If network is completely offline, rawSections remains empty
            }
        }
    }

    /** Home's search icon: open the tab with the keyboard ready. */
    fun requestSearch() {
        _focusSearch.value = Event(Unit)
    }

    /** The text of the search field changed. A search starts 400 ms after the last key. */
    fun setQuery(text: String) {
        val before = filtersFlow.value
        if (text == before.query) return
        val now = before.copy(query = text)
        filtersFlow.value = now
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
        if (!filtersFlow.value.isSearching) reload(debounce = false)
    }

    /** A genre chip, or null when the chip was switched off. */
    fun selectGenre(genreId: Int?) {
        val before = filtersFlow.value
        if (genreId == before.genreId) return
        filtersFlow.value = before.copy(genreId = genreId)
        if (filtersFlow.value.isSearching) {
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

    /** The Retry buttons: reload first page and sections. */
    fun retry() {
        loadSections()
        reload(debounce = false)
    }

    /** The user scrolled near the end of the list. */
    fun loadNextPage() {
        if (requestJob?.isActive == true || !hasMore || lastPage == 0) return
        publish(isLoadingMore = true)
        requestJob = viewModelScope.launch {
            requestPage(lastPage + 1)
            loadMoreWhileShort()
        }
    }

    /** Starts the list again from page 1. */
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
                loaded = appendUnique(loaded, page.movies)
                publish(isLoadingMore = false)
            }
            .onFailure { error ->
                if (number == 1) {
                    load.value = DiscoverLoad.Failed(friendlyError(error))
                } else {
                    publish(isLoadingMore = false)
                    _loadMoreFailed.value = Event(Unit)
                }
            }
    }

    private suspend fun loadMoreWhileShort() {
        while (hasMore && autoPagesLeft > 0 && shownCount() < MIN_SHOWN && load.value is DiscoverLoad.Loaded) {
            autoPagesLeft--
            publish(isLoadingMore = true)
            val before = lastPage
            requestPage(lastPage + 1)
            if (lastPage == before) break
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
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MIN_SHOWN = 12
        const val MAX_AUTO_PAGES = 5
    }
}
