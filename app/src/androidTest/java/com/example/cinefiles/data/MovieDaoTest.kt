package com.example.cinefiles.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of the REAL database code, on a phone or emulator (Room needs Android). Each test gets a fresh database that
 * lives in memory only, so nothing here touches the movies saved by the real app.
 *
 * Run them with: ./gradlew connectedDebugAndroidTest   (an emulator or phone must be connected)
 */
@RunWith(AndroidJUnit4::class)
class MovieDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MovieDao
    private lateinit var repository: MovieRepository

    @Before
    fun openDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.movieDao()
        // No token: these tests never go to the internet
        repository = MovieRepository(TmdbApi.create(""), hasToken = false, dao = dao, diaryDao = db.diaryDao())
    }

    @After
    fun closeDatabase() = db.close()

    private fun movie(id: Int, title: String = "Movie $id") = Movie(
        id = id, title = title, posterPath = "/p$id.jpg", backdropPath = "/b$id.jpg",
        voteAverage = 7.5, releaseYear = 2020, genreNames = listOf("Drama", "Science Fiction"),
        genreIds = listOf(18, 878), voteCount = 100, originalLanguage = "en", overview = "Story $id"
    )

    private suspend fun saved(id: Int): Movie = dao.get(id)!!

    // ==================================================================================
    //  The three rules the brief asks for
    // ==================================================================================

    @Test
    fun savingAMovieAgainKeepsTheUsersState() = runBlocking {
        dao.insertIfMissing(movie(1))
        dao.markWatched(1, 50L) // only a watched movie can be liked (THE WATCH RULE)
        dao.setReaction(1, Reaction.LIKED, 100L)
        dao.setUserRating(1, 4.0f, 120L)
        dao.addFavorite(1, 130L)

        // The same movie arrives again from TMDB, with different information and no state at all
        val result = dao.insertIfMissing(movie(1, title = "Changed by TMDB"))

        assertEquals("IGNORE: nothing was inserted", -1L, result)
        val row = saved(1)
        assertEquals(Reaction.LIKED, row.reaction)
        assertTrue(row.isWatched)
        assertEquals(4.0f, row.userRating)
        assertTrue(row.isFavorite)
        assertEquals(100L, row.reactedAt)
        assertEquals("Movie 1", row.title) // the saved information is not replaced either
    }

    @Test
    fun markingAMovieWatchedTakesItOffTheWatchlist() = runBlocking {
        dao.insertIfMissing(movie(1))
        dao.addToWatchlist(1, 100L)
        assertEquals(listOf(1), dao.observeWatchlist().first().map { it.id })

        dao.markWatched(1, 200L)

        assertTrue(dao.observeWatchlist().first().isEmpty())
        assertEquals(listOf(1), dao.observeWatched().first().map { it.id })
        val row = saved(1)
        assertTrue(row.isWatched)
        assertFalse(row.inWatchlist)
        assertEquals(200L, row.watchedAt)
    }

    @Test
    fun likingNeverChangesTheWatchlistOrWatched() = runBlocking {
        dao.insertIfMissing(movie(1))
        dao.addToWatchlist(1, 100L)

        dao.setReaction(1, Reaction.LIKED, 200L)
        assertTrue("still on the watchlist after a like", saved(1).inWatchlist)

        dao.markWatched(1, 300L)
        dao.setReaction(1, Reaction.NONE, 0L) // unlike
        assertTrue("still watched after an unlike", saved(1).isWatched)

        dao.setReaction(1, Reaction.PASSED, 400L)
        assertTrue("still watched after a pass", saved(1).isWatched)
    }

    // ==================================================================================
    //  The rest of the state rules
    // ==================================================================================

    @Test
    fun addingAWatchedMovieToTheWatchlistMovesItBack() = runBlocking {
        dao.insertIfMissing(movie(1))
        dao.markWatched(1, 100L)

        dao.addToWatchlist(1, 200L)

        val row = saved(1)
        assertTrue(row.inWatchlist)
        assertFalse("a movie is on the watchlist OR watched, never both", row.isWatched)
        assertEquals(0L, row.watchedAt)
    }

    @Test
    fun removingFromTheWatchlistAndUnmarkingWatchedWork() = runBlocking {
        dao.insertIfMissing(movie(1))
        dao.addToWatchlist(1, 100L)
        dao.removeFromWatchlist(1)
        assertFalse(saved(1).inWatchlist)

        dao.markWatched(1, 200L)
        dao.unmarkWatched(1)
        assertFalse(saved(1).isWatched)
        assertTrue("the movie itself stays saved", dao.get(1) != null)
    }

    @Test
    fun theWatchlistAndItsCountIgnoreAMovieThatIsAlsoMarkedWatched() = runBlocking {
        // Should never happen, but if both flags are ever set, the movie counts as watched only
        dao.insertIfMissing(movie(1))
        dao.restoreState(
            1, Reaction.NONE, 0L, inWatchlist = true, watchlistAddedAt = 5L, isWatched = true, watchedAt = 6L,
            userRating = null, ratedAt = 0L, isFavorite = false, favoritedAt = 0L, favoriteOrder = 0
        )

        assertTrue(dao.observeWatchlist().first().isEmpty())
        val counts = dao.observeCounts().first()
        assertEquals(0, counts.watchlist)
        assertEquals(1, counts.watched)
    }

    @Test
    fun listsAreNewestFirst() = runBlocking {
        for (id in 1..3) dao.insertIfMissing(movie(id))
        dao.setReaction(1, Reaction.LIKED, 100L)
        dao.setReaction(2, Reaction.LIKED, 300L)
        dao.setReaction(3, Reaction.LIKED, 200L)
        assertEquals(listOf(2, 3, 1), dao.observeReacted(Reaction.LIKED).first().map { it.id })

        dao.addToWatchlist(1, 10L)
        dao.addToWatchlist(3, 20L)
        assertEquals(listOf(3, 1), dao.observeWatchlist().first().map { it.id })
    }

    @Test
    fun countsAgreeWithTheLists() = runBlocking {
        for (id in 1..6) dao.insertIfMissing(movie(id))
        dao.setReaction(1, Reaction.LIKED, 1L)
        dao.setReaction(2, Reaction.LIKED, 2L)
        dao.setReaction(3, Reaction.PASSED, 3L)
        dao.addToWatchlist(4, 4L)
        dao.addToWatchlist(5, 5L)
        dao.markWatched(5, 6L) // leaves the watchlist
        dao.markWatched(6, 7L)

        val counts = dao.observeCounts().first()
        assertEquals(CollectionCounts(liked = 2, watchlist = 1, watched = 2, passed = 1), counts)
        assertEquals(counts.liked, dao.observeReacted(Reaction.LIKED).first().size)
        assertEquals(counts.passed, dao.observeReacted(Reaction.PASSED).first().size)
        assertEquals(counts.watchlist, dao.observeWatchlist().first().size)
        assertEquals(counts.watched, dao.observeWatched().first().size)
    }

    @Test
    fun theSwipeDeckSkipsEveryMovieThatHasAnyState() = runBlocking {
        for (id in 1..6) dao.insertIfMissing(movie(id))
        dao.setReaction(2, Reaction.LIKED, 1L)
        dao.setReaction(3, Reaction.PASSED, 1L)
        dao.addToWatchlist(4, 1L)
        dao.markWatched(5, 1L)
        // 1 and 6 are saved but have no state, so they may come back in a deck

        assertEquals(setOf(2, 3, 4, 5), dao.idsWithState().toSet())
    }

    @Test
    fun aTakenBackReactionLeavesNoStateBehind() = runBlocking {
        dao.insertIfMissing(movie(1))
        dao.setReaction(1, Reaction.LIKED, 100L)
        dao.setReaction(1, Reaction.NONE, 0L)

        assertTrue(dao.idsWithState().isEmpty())
        assertTrue(dao.observeReacted(Reaction.LIKED).first().isEmpty())
    }

    // ==================================================================================
    //  Undo, information, converters, live lists, clear
    // ==================================================================================

    @Test
    fun restoreStatePutsEveryStateFieldBackExactly() = runBlocking {
        dao.insertIfMissing(movie(1))
        dao.markWatched(1, 100L)
        dao.setReaction(1, Reaction.LIKED, 150L)
        dao.setUserRating(1, 3.5f, 160L)
        dao.addFavorite(1, 170L)
        val before = saved(1)

        dao.unmarkWatched(1)           // some action (it clears the like, the rating and the favorite)...
        repository.restoreState(before) // ...and Undo

        assertEquals(before, saved(1))
    }

    @Test
    fun updateInfoFillsTheRuntimeAndNeverWipesWhatIsSaved() = runBlocking {
        dao.insertIfMissing(movie(1)) // overview "Story 1", no runtime

        dao.updateInfo(1, overview = "", runtimeMinutes = 120)
        assertEquals("an empty overview does not wipe the saved one", "Story 1", saved(1).overview)
        assertEquals(120, saved(1).runtimeMinutes)

        dao.updateInfo(1, overview = "Better story", runtimeMinutes = null)
        assertEquals("Better story", saved(1).overview)
        assertEquals("a missing runtime does not wipe the saved one", 120, saved(1).runtimeMinutes)
    }

    @Test
    fun updateInfoOnAMovieThatIsNotSavedDoesNothing() = runBlocking {
        dao.updateInfo(99, "Story", 100)
        assertEquals(null, dao.get(99))
    }

    @Test
    fun theGenreListsSurviveTheTripThroughTheDatabase() = runBlocking {
        dao.insertIfMissing(movie(1))
        val row = saved(1)
        assertEquals(listOf(18, 878), row.genreIds)
        assertEquals(listOf("Drama", "Science Fiction"), row.genreNames)
    }

    @Test
    fun aMovieWithNoGenresComesBackWithEmptyLists() = runBlocking {
        dao.insertIfMissing(movie(1).copy(genreIds = emptyList(), genreNames = emptyList()))
        assertTrue(saved(1).genreIds.isEmpty())
        assertTrue(saved(1).genreNames.isEmpty())
    }

    @Test
    fun aLiveListSendsItselfAgainWhenSomethingChanges() = runBlocking {
        dao.insertIfMissing(movie(1))
        // Start listening first, then change the database
        val liveList = async { dao.observeReacted(Reaction.LIKED).first { it.isNotEmpty() } }

        dao.setReaction(1, Reaction.LIKED, 100L)

        val liked = withTimeout(5_000) { liveList.await() }
        assertEquals(listOf(1), liked.map { it.id })
    }

    @Test
    fun oneMovieCanBeWatchedLive() = runBlocking {
        assertEquals(null, dao.observe(1).first())
        dao.insertIfMissing(movie(1))
        assertNotNull(dao.observe(1).first())
    }

    @Test
    fun clearAllDataDeletesEveryMovieAndEveryState() = runBlocking {
        for (id in 1..3) dao.insertIfMissing(movie(id))
        dao.setReaction(1, Reaction.LIKED, 1L)

        repository.clearAllData()

        assertEquals(CollectionCounts(0, 0, 0, 0), dao.observeCounts().first())
        assertTrue(dao.idsWithState().isEmpty())
        assertEquals(null, dao.get(1))
    }

    // ==================================================================================
    //  The repository's "save the movie if it is missing, then set the state" helpers
    // ==================================================================================

    @Test
    fun likeSavesAMovieThatWasNeverSavedButOnlyLikesItOnceItIsWatched() = runBlocking {
        assertEquals(null, dao.get(7))
        assertFalse("not watched yet", repository.like(movie(7)))
        assertEquals(Reaction.NONE, saved(7).reaction)

        repository.markWatched(movie(7))
        assertTrue(repository.like(movie(7)))

        val row = saved(7)
        assertEquals("Movie 7", row.title)
        assertEquals(Reaction.LIKED, row.reaction)
        assertTrue("the time of the like is written down", row.reactedAt > 0L)
        assertEquals(listOf(7), repository.observeLiked().first().map { it.id })
    }

    @Test
    fun passSavesTheMovieAndMarksItPassed() = runBlocking {
        repository.pass(movie(8))
        assertEquals(Reaction.PASSED, saved(8).reaction)
        assertEquals(listOf(8), repository.observePassed().first().map { it.id })
        assertTrue(repository.observeLiked().first().isEmpty())
    }

    @Test
    fun undoingASwipeKeepsTheMovieButClearsTheReaction() = runBlocking {
        repository.pass(movie(9))
        repository.clearReaction(9)

        assertEquals(Reaction.NONE, saved(9).reaction)
        assertTrue("the movie is still saved", dao.get(9) != null)
        assertTrue("and it may be shown again", repository.idsWithState().isEmpty())
    }

    @Test
    fun likingAMovieThatIsAlreadySavedKeepsItsOtherStates() = runBlocking {
        repository.markWatched(movie(5))
        // The movie object has no state (like one fresh from TMDB), but the saved row has
        assertTrue(repository.like(movie(5)))

        val row = saved(5)
        assertEquals(Reaction.LIKED, row.reaction)
        assertTrue("watched survived", row.isWatched)
    }

    // ==================================================================================
    //  THE WATCH RULE: only a watched movie can be liked, favorited or rated; un-watching clears them
    // ==================================================================================

    @Test
    fun aMovieThatIsNotWatchedCannotBeLikedFavoritedOrRated() = runBlocking {
        repository.addToWatchlist(movie(5))

        assertFalse("the like is refused", repository.like(movie(5)))
        assertEquals(FavoriteResult.NOT_WATCHED, repository.addFavorite(movie(5)))
        assertFalse(repository.setUserRating(movie(5), 4.0f))

        val row = saved(5)
        assertEquals("nothing changed", Reaction.NONE, row.reaction)
        assertEquals(null, row.userRating)
        assertFalse(row.isFavorite)
        assertTrue(row.inWatchlist)
    }

    @Test
    fun aWatchedMovieCanBeLikedFavoritedAndRatedAndNeverReplacesTheRow() = runBlocking {
        repository.markWatched(movie(5))

        assertTrue(repository.like(movie(5)))
        assertEquals(FavoriteResult.ADDED, repository.addFavorite(movie(5)))
        assertTrue(repository.setUserRating(movie(5), 4.5f))

        val row = saved(5)
        assertEquals(Reaction.LIKED, row.reaction)
        assertTrue(row.isFavorite)
        assertEquals(4.5f, row.userRating)
        assertTrue(row.isWatched)
        assertEquals(listOf(5), repository.observeRated().first().map { it.id })
        assertEquals(listOf(5), repository.observeFavorites().first().map { it.id })
    }

    @Test
    fun unwatchingClearsTheLikeTheRatingAndTheFavoriteInTheRealQuery() = runBlocking {
        repository.markWatched(movie(5))
        repository.like(movie(5))
        repository.setUserRating(movie(5), 2.5f)
        repository.addFavorite(movie(5))
        repository.markWatched(movie(6))
        repository.addFavorite(movie(6))

        repository.unmarkWatched(5)

        val row = saved(5)
        assertFalse(row.isWatched)
        assertEquals(Reaction.NONE, row.reaction)
        assertEquals(null, row.userRating)
        assertFalse(row.isFavorite)
        assertEquals("the other favorite is untouched", listOf(6), repository.observeFavorites().first().map { it.id })
    }

    @Test
    fun movingAWatchedMovieBackToTheWatchlistClearsThemToo() = runBlocking {
        repository.markWatched(movie(5))
        repository.like(movie(5))
        repository.setUserRating(movie(5), 5.0f)
        repository.addFavorite(movie(5))

        repository.addToWatchlist(movie(5))

        val row = saved(5)
        assertTrue(row.inWatchlist)
        assertFalse(row.isWatched)
        assertEquals(Reaction.NONE, row.reaction)
        assertEquals(null, row.userRating)
        assertFalse(row.isFavorite)
    }

    @Test
    fun theFavoritesKeepTheOrderTheUserChoseInTheRealQuery() = runBlocking {
        for (id in 1..4) {
            repository.markWatched(movie(id))
            repository.addFavorite(movie(id))
        }
        repository.reorderFavorites(listOf(4, 2, 1, 3))

        assertEquals(listOf(4, 2, 1, 3), repository.observeFavorites().first().map { it.id })
        assertEquals(listOf(4, 2, 1, 3), repository.getFavorites().map { it.id })
    }

    @Test
    fun aLikedMovieThatWasMarkedWatchedLongAgoKeepsItsLikeWhenSavedAgain() = runBlocking {
        repository.markWatched(movie(5))
        assertTrue(repository.like(movie(5)))
        dao.insertIfMissing(movie(5).copy(title = "Changed by TMDB")) // IGNORE: the row and its state stay

        assertEquals(Reaction.LIKED, saved(5).reaction)
        assertEquals("Movie 5", saved(5).title)
    }

    // ==================================================================================
    //  Discover: the movies the user has any state for, live (they wear the badges)
    // ==================================================================================

    @Test
    fun observeWithStateListsOnlyMoviesThatHaveAState() = runBlocking {
        for (id in 1..6) dao.insertIfMissing(movie(id))
        dao.setReaction(2, Reaction.LIKED, 1L)
        dao.setReaction(3, Reaction.PASSED, 1L)
        dao.addToWatchlist(4, 1L)
        dao.markWatched(5, 1L)
        // 1 and 6 are saved without any state

        assertEquals(setOf(2, 3, 4, 5), repository.observeWithState().first().map { it.id }.toSet())
    }

    @Test
    fun theRepositoryWatchedAndWatchlistHelpersFollowTheRules() = runBlocking {
        repository.addToWatchlist(movie(3))
        assertEquals(listOf(3), repository.observeWatchlist().first().map { it.id })

        repository.markWatched(movie(3))
        assertTrue(repository.observeWatchlist().first().isEmpty())
        assertEquals(listOf(3), repository.observeWatched().first().map { it.id })

        repository.addToWatchlist(movie(3)) // "move back"
        assertTrue(repository.observeWatched().first().isEmpty())
        assertEquals(listOf(3), repository.observeWatchlist().first().map { it.id })

        repository.removeFromWatchlist(3)
        repository.unmarkWatched(3)
        assertTrue(repository.idsWithState().isEmpty())
    }
}
