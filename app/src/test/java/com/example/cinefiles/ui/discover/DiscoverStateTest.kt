package com.example.cinefiles.ui.discover

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction
import com.example.cinefiles.logic.DiscoverFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Discover tab shows for what was loaded, what the user saved and what the user chose. */
class DiscoverStateTest {

    private fun movie(id: Int, genres: List<Int> = emptyList()) =
        Movie(id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null, genreIds = genres)

    private fun loaded(movies: List<Movie>, hasMore: Boolean = false, isLoadingMore: Boolean = false) =
        DiscoverLoad.Loaded(movies, hasMore, isLoadingMore)

    private fun build(load: DiscoverLoad, saved: Map<Int, Movie> = emptyMap(), filters: DiscoverFilters = DiscoverFilters()) =
        buildDiscoverState(load, saved, filters)

    // ---------- the plain cases ----------

    @Test
    fun beforeAnythingArrivesItIsLoading() {
        assertEquals(DiscoverState.Loading, build(DiscoverLoad.Loading))
    }

    @Test
    fun aFailedFirstPageIsAnErrorWithItsReason() {
        val state = build(DiscoverLoad.Failed(ApiError.NO_INTERNET))
        assertEquals(DiscoverState.Error(ApiError.NO_INTERNET), state)
    }

    @Test
    fun moviesAreShownInTheOrderTmdbSentThem() {
        val state = build(loaded(listOf(movie(3), movie(1), movie(2)))) as DiscoverState.Content
        assertEquals(listOf(3, 1, 2), state.movies.map { it.id })
        assertEquals(false, state.isLoadingMore)
    }

    @Test
    fun theNextPageBeingLoadedIsPassedOn() {
        val state = build(loaded(listOf(movie(1)), hasMore = true, isLoadingMore = true)) as DiscoverState.Content
        assertTrue(state.isLoadingMore)
    }

    // ---------- empty ----------

    @Test
    fun noMoviesAndNoMorePagesIsEmpty() {
        assertEquals(DiscoverState.Empty(hasFilters = false), build(loaded(emptyList())))
    }

    @Test
    fun emptyRemembersWhetherClearingFiltersCouldHelp() {
        val searching = DiscoverFilters(query = "zzzzz")
        assertEquals(DiscoverState.Empty(hasFilters = true), build(loaded(emptyList()), filters = searching))
    }

    @Test
    fun nothingToShowYetButMorePagesAreComingIsStillLoading() {
        // A page can be all movies the genre filter removes: the next page may have matches
        assertEquals(DiscoverState.Loading, build(loaded(emptyList(), hasMore = true)))
        assertEquals(DiscoverState.Loading, build(loaded(emptyList(), isLoadingMore = true)))
    }

    // ---------- the user's own state ----------

    @Test
    fun aSavedMovieShowsItsStateSoItCanWearABadge() {
        val saved = movie(2).copy(reaction = Reaction.LIKED, reactedAt = 7L)
        val state = build(loaded(listOf(movie(1), movie(2))), saved = mapOf(2 to saved)) as DiscoverState.Content

        assertEquals(Reaction.NONE, state.movies[0].reaction)
        assertEquals(Reaction.LIKED, state.movies[1].reaction)
    }

    @Test
    fun theWatchlistAndWatchedStateComeAlongToo() {
        val onList = movie(1).copy(inWatchlist = true, watchlistAddedAt = 5L)
        val watched = movie(2).copy(isWatched = true, watchedAt = 6L)
        val state = build(loaded(listOf(movie(1), movie(2))), saved = mapOf(1 to onList, 2 to watched)) as DiscoverState.Content

        assertTrue(state.movies[0].inWatchlist)
        assertTrue(state.movies[1].isWatched)
    }

    @Test
    fun theInformationStaysTmdbsWhenTheStateIsMerged() {
        val fromTmdb = movie(1).copy(title = "Fresh title")
        val saved = movie(1).copy(title = "Old title", inWatchlist = true)

        val state = build(loaded(listOf(fromTmdb)), saved = mapOf(1 to saved)) as DiscoverState.Content

        assertEquals("Fresh title", state.movies.single().title)
        assertTrue(state.movies.single().inWatchlist)
    }

    // ---------- the genre chip ----------

    @Test
    fun whileBrowsingTheGenreChipFiltersNothingHere() {
        // Browsing: TMDB already answered with that genre only
        val state = build(
            loaded(listOf(movie(1, listOf(35)), movie(2, listOf(18)))),
            filters = DiscoverFilters(genreId = 35)
        ) as DiscoverState.Content
        assertEquals(listOf(1, 2), state.movies.map { it.id })
    }

    @Test
    fun whileSearchingTheGenreChipFiltersTheLoadedMovies() {
        val state = build(
            loaded(listOf(movie(1, listOf(35)), movie(2, listOf(18)), movie(3, listOf(35, 18)))),
            filters = DiscoverFilters(query = "love", genreId = 35)
        ) as DiscoverState.Content
        assertEquals(listOf(1, 3), state.movies.map { it.id })
    }

    @Test
    fun aGenreThatMatchesNothingWhileSearchingIsEmptyWhenThereAreNoMorePages() {
        val state = build(
            loaded(listOf(movie(1, listOf(18)))),
            filters = DiscoverFilters(query = "love", genreId = 35)
        )
        assertEquals(DiscoverState.Empty(hasFilters = true), state)
    }

    @Test
    fun aGenreThatMatchesNothingYetKeepsLoadingWhileThereAreMorePages() {
        val state = build(
            loaded(listOf(movie(1, listOf(18))), hasMore = true),
            filters = DiscoverFilters(query = "love", genreId = 35)
        )
        assertEquals(DiscoverState.Loading, state)
    }

    @Test
    fun oneLetterIsNotASearchSoTheGenreChipDoesNotFilterOnThePhone() {
        val state = build(
            loaded(listOf(movie(1, listOf(18)))),
            filters = DiscoverFilters(query = "l", genreId = 35)
        ) as DiscoverState.Content
        assertEquals(listOf(1), state.movies.map { it.id })
    }
}
