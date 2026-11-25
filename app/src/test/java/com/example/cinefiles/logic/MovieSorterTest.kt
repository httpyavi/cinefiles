package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one sort system of My Collection and the Person screen: every field, both directions, and what happens to movies without a value. */
class MovieSorterTest {

    private fun movie(
        id: Int,
        title: String = "Movie $id",
        release: String? = null,
        year: Int? = null,
        rating: Float? = null,
        runtime: Int? = null,
        tmdb: Double? = null,
        votes: Int = 0
    ) = Movie(
        id = id, title = title, posterPath = "/p$id.jpg", backdropPath = null, releaseDate = release, releaseYear = year,
        userRating = rating, runtimeMinutes = runtime, voteAverage = tmdb, voteCount = votes
    )

    private fun ids(movies: List<Movie>, field: SortField, direction: SortDirection, addedAt: (Movie) -> Long = { 0L }) =
        MovieSorter.sort(movies, SortOption(field, direction), addedAt).map { it.id }

    // ---------- Film name ----------

    @Test
    fun filmNameIsAToZAndZToAWithoutCaringAboutCase() {
        val movies = listOf(movie(1, "banana"), movie(2, "Cherry"), movie(3, "Apple"))
        assertEquals(listOf(3, 1, 2), ids(movies, SortField.NAME, SortDirection.ASCENDING))
        assertEquals(listOf(2, 1, 3), ids(movies, SortField.NAME, SortDirection.DESCENDING))
    }

    @Test
    fun aLeadingTheAOrAnIsIgnoredLikeInALibrary() {
        val movies = listOf(movie(1, "The Godfather"), movie(2, "Alien"), movie(3, "A Quiet Place"), movie(4, "An American Tail"), movie(5, "Heat"))
        // Alien, American Tail (An), Godfather (The), Heat, Quiet Place (A)
        assertEquals(listOf(2, 4, 1, 5, 3), ids(movies, SortField.NAME, SortDirection.ASCENDING))
    }

    @Test
    fun aTitleThatIsOnlyAnArticleKeepsItsName() {
        assertEquals("the", MovieSorter.nameKey("The"))
        assertEquals("godfather", MovieSorter.nameKey("  The   Godfather "))
        assertEquals("theory of everything", MovieSorter.nameKey("Theory of Everything")) // "The" must be a whole word
    }

    // ---------- Release date ----------

    @Test
    fun releaseDateIsNewestOrEarliestFirst() {
        val movies = listOf(movie(1, release = "2014-11-05"), movie(2, release = "1999-03-31"), movie(3, release = "2020-01-01"))
        assertEquals(listOf(3, 1, 2), ids(movies, SortField.RELEASE_DATE, SortDirection.DESCENDING))
        assertEquals(listOf(2, 1, 3), ids(movies, SortField.RELEASE_DATE, SortDirection.ASCENDING))
    }

    @Test
    fun aMovieWithOnlyAYearSortsAtTheStartOfThatYear() {
        val movies = listOf(movie(1, release = "2014-06-01"), movie(2, year = 2014), movie(3, release = "2013-12-31"))
        assertEquals(listOf(3, 2, 1), ids(movies, SortField.RELEASE_DATE, SortDirection.ASCENDING))
    }

    // ---------- Your rating, length, TMDB rating ----------

    @Test
    fun yourRatingIsHighestOrLowestFirstAndUnratedMoviesAreAlwaysLast() {
        val movies = listOf(movie(1, rating = 3.5f), movie(2), movie(3, rating = 5.0f), movie(4, rating = 0.5f))
        assertEquals(listOf(3, 1, 4, 2), ids(movies, SortField.USER_RATING, SortDirection.DESCENDING))
        assertEquals(listOf(4, 1, 3, 2), ids(movies, SortField.USER_RATING, SortDirection.ASCENDING)) // 2 is still last
    }

    @Test
    fun filmLengthIsLongestOrShortestFirstAndUnknownLengthsAreLast() {
        val movies = listOf(movie(1, runtime = 120), movie(2), movie(3, runtime = 90), movie(4, runtime = 169))
        assertEquals(listOf(4, 1, 3, 2), ids(movies, SortField.RUNTIME, SortDirection.DESCENDING))
        assertEquals(listOf(3, 1, 4, 2), ids(movies, SortField.RUNTIME, SortDirection.ASCENDING))
    }

    @Test
    fun tmdbRatingIsHighestOrLowestFirstAndUnknownIsLast() {
        val movies = listOf(movie(1, tmdb = 7.2), movie(2, tmdb = 8.8), movie(3), movie(4, tmdb = 5.0))
        assertEquals(listOf(2, 1, 4, 3), ids(movies, SortField.TMDB_RATING, SortDirection.DESCENDING))
        assertEquals(listOf(4, 1, 2, 3), ids(movies, SortField.TMDB_RATING, SortDirection.ASCENDING))
    }

    // ---------- When added (My Collection) ----------

    @Test
    fun whenAddedUsesTheTimeTheMovieGotIntoTheTab() {
        val times = mapOf(1 to 100L, 2 to 300L, 3 to 200L, 4 to 0L)
        val movies = listOf(movie(1), movie(2), movie(3), movie(4))
        assertEquals(listOf(2, 3, 1, 4), ids(movies, SortField.WHEN_ADDED, SortDirection.DESCENDING) { times.getValue(it.id) })
        assertEquals(listOf(1, 3, 2, 4), ids(movies, SortField.WHEN_ADDED, SortDirection.ASCENDING) { times.getValue(it.id) })
    }

    // ---------- Popularity (the Person screen) ----------

    @Test
    fun popularityKeepsTmdbsOrderOrReversesIt() {
        val movies = listOf(movie(5), movie(3), movie(9), movie(1)) // TMDB sent the most popular first
        assertEquals(listOf(5, 3, 9, 1), ids(movies, SortField.POPULARITY, SortDirection.DESCENDING))
        assertEquals(listOf(1, 9, 3, 5), ids(movies, SortField.POPULARITY, SortDirection.ASCENDING))
    }

    // ---------- Order that never changes by chance ----------

    @Test
    fun movieEqualOnTheSortedValueAreOrderedByNameThenId() {
        val movies = listOf(movie(3, "B", rating = 4.0f), movie(1, "B", rating = 4.0f), movie(2, "A", rating = 4.0f))
        assertEquals(listOf(2, 1, 3), ids(movies, SortField.USER_RATING, SortDirection.DESCENDING))
        assertEquals(listOf(2, 1, 3), ids(movies.reversed(), SortField.USER_RATING, SortDirection.DESCENDING))
    }

    @Test
    fun sortingNeverChangesTheMoviesThemselves() {
        val movies = listOf(movie(1, "B", rating = 2.0f), movie(2, "A", rating = 3.0f))
        val sorted = MovieSorter.sort(movies, SortOption(SortField.NAME, SortDirection.ASCENDING))
        assertEquals(movies.toSet(), sorted.toSet())
        assertEquals(listOf(1, 2), movies.map { it.id }) // the original list is not touched
    }

    // ---------- The options ----------

    @Test
    fun everyFieldHasTwoDifferentWordingsAndTheFirstOneIsItsDefaultDirection() {
        for (field in SortField.values()) {
            assertTrue(field.labelOf(field.firstDirection) == field.firstLabel)
            assertTrue(field.labelOf(field.opposite(field.firstDirection)) == field.secondLabel)
            assertFalse(field.firstLabel == field.secondLabel)
        }
        assertEquals(SortDirection.ASCENDING, SortField.NAME.firstDirection)        // A to Z
        assertEquals(SortDirection.DESCENDING, SortField.RELEASE_DATE.firstDirection) // newest first
        assertEquals(SortDirection.DESCENDING, SortField.USER_RATING.firstDirection)  // highest first
    }

    @Test
    fun theDefaultsAreWhatTheScreensAlreadyShowed() {
        assertEquals(SortOption(SortField.WHEN_ADDED, SortDirection.DESCENDING), MovieSorter.defaultOf(MovieSorter.COLLECTION_FIELDS))
        assertEquals(SortOption(SortField.POPULARITY, SortDirection.DESCENDING), MovieSorter.defaultOf(MovieSorter.PERSON_FIELDS))
    }

    @Test
    fun theBriefsSortOptionsAreOfferedWhereTheyBelong() {
        for (field in listOf(SortField.NAME, SortField.RELEASE_DATE, SortField.USER_RATING, SortField.RUNTIME, SortField.TMDB_RATING)) {
            assertTrue(field in MovieSorter.COLLECTION_FIELDS)
            assertTrue(field in MovieSorter.PERSON_FIELDS)
        }
        assertTrue(SortField.WHEN_ADDED in MovieSorter.COLLECTION_FIELDS && SortField.WHEN_ADDED !in MovieSorter.PERSON_FIELDS)
        assertTrue(SortField.POPULARITY in MovieSorter.PERSON_FIELDS && SortField.POPULARITY !in MovieSorter.COLLECTION_FIELDS)
    }

    @Test
    fun aFieldNoMovieHasAValueForIsNotOffered() {
        val plain = listOf(movie(1), movie(2))
        assertEquals(
            listOf(SortField.WHEN_ADDED, SortField.NAME, SortField.RELEASE_DATE),
            MovieSorter.availableFields(plain, MovieSorter.COLLECTION_FIELDS)
        )
        val rich = listOf(movie(1, rating = 3.0f, runtime = 100, tmdb = 7.0))
        assertEquals(MovieSorter.COLLECTION_FIELDS, MovieSorter.availableFields(rich, MovieSorter.COLLECTION_FIELDS))
    }

    @Test
    fun collectionTabsKnowWhenAMovieGotIn() {
        val m = movie(1).copy(reactedAt = 10L, watchlistAddedAt = 20L, watchedAt = 30L)
        assertEquals(10L, CollectionTab.LIKED.addedAt(m))
        assertEquals(20L, CollectionTab.WATCHLIST.addedAt(m))
        assertEquals(30L, CollectionTab.WATCHED.addedAt(m))
    }
}
