package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a search starts, what the filters mean, and how pages and the genre filter treat a list of movies. */
class DiscoverRulesTest {

    private fun movie(id: Int, genres: List<Int> = emptyList()) =
        Movie(id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null, genreIds = genres)

    // ---------- is it a search? ----------

    @Test
    fun aSearchNeedsAtLeastTwoLetters() {
        assertFalse(isSearchText(""))
        assertFalse(isSearchText("a"))
        assertTrue(isSearchText("ab"))
        assertTrue(isSearchText("blade runner"))
    }

    @Test
    fun spacesAroundTheTextDoNotCount() {
        assertFalse(isSearchText("   "))
        assertFalse(isSearchText(" a "))
        assertTrue(isSearchText(" ab "))
    }

    @Test
    fun theFiltersKnowWhetherTheyAreASearch() {
        assertFalse(DiscoverFilters().isSearching)
        assertFalse(DiscoverFilters(query = "x").isSearching) // one letter: still browsing
        assertTrue(DiscoverFilters(query = "xy").isSearching)
    }

    // ---------- "Clear filters" ----------

    @Test
    fun theStartingFiltersHaveNothingToClear() {
        assertFalse(DiscoverFilters().hasFilters)
    }

    @Test
    fun aSearchAGenreOrAnotherListCanBeCleared() {
        assertTrue(DiscoverFilters(query = "matrix").hasFilters)
        assertTrue(DiscoverFilters(genreId = 35).hasFilters)
        assertTrue(DiscoverFilters(category = BrowseCategory.TOP_RATED).hasFilters)
        assertTrue(DiscoverFilters(query = "m").hasFilters) // even one letter is something typed
    }

    @Test
    fun blankTextIsNotAFilter() {
        assertFalse(DiscoverFilters(query = "  ").hasFilters)
    }

    // ---------- pages ----------

    @Test
    fun theNextPageIsAddedAfterTheOnesAlreadyShown() {
        val result = appendUnique(listOf(movie(1), movie(2)), listOf(movie(3), movie(4)))
        assertEquals(listOf(1, 2, 3, 4), result.map { it.id })
    }

    @Test
    fun aMovieThatMovedToTheNextPageIsNotShownTwice() {
        // TMDB's lists shift while you scroll: a movie of page 1 can come again on page 2
        val result = appendUnique(listOf(movie(1), movie(2)), listOf(movie(2), movie(3), movie(1)))
        assertEquals(listOf(1, 2, 3), result.map { it.id })
    }

    @Test
    fun aRepeatInsideOnePageIsAddedOnce() {
        val result = appendUnique(emptyList(), listOf(movie(1), movie(1), movie(2)))
        assertEquals(listOf(1, 2), result.map { it.id })
    }

    @Test
    fun anEmptyPageChangesNothing() {
        val shown = listOf(movie(1))
        assertEquals(shown, appendUnique(shown, emptyList()))
    }

    // ---------- the genre filter on the phone ----------

    @Test
    fun noGenreKeepsEveryMovie() {
        val all = listOf(movie(1, listOf(35)), movie(2, listOf(18)))
        assertEquals(all, filterByGenre(all, null))
    }

    @Test
    fun aGenreKeepsOnlyMoviesWithThatGenreInAnyPosition() {
        val all = listOf(movie(1, listOf(35, 10749)), movie(2, listOf(18)), movie(3, listOf(10749, 35)), movie(4))

        assertEquals(listOf(1, 3), filterByGenre(all, 35).map { it.id })
        assertTrue(filterByGenre(all, 27).isEmpty())
    }

    // ---------- trending in a genre ----------

    @Test
    fun trendingInAGenreMeansSinceTheStartOfLastYear() {
        assertEquals("2025-01-01", trendingSince(2026))
    }
}
