package com.example.cinefiles.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE WATCH RULE, through the real MovieRepository on the in-memory database (no network is used):
 *  - a movie can only be liked, favorited or rated while it is WATCHED (the watchlist is not watched),
 *  - taking "watched" away clears the like, the rating and the favorite, so the database can never hold an impossible state,
 *  - Undo (restoreState) puts them back.
 * Plus the favorites (at most 4, in the order the user chose) and the diary pages of the repository.
 */
class WatchRulesRepositoryTest {

    private val dao = FakeMovieDao()
    private val diaryDao = FakeDiaryDao()
    private val repo = MovieRepository(TmdbApi.create("test-token", "http://127.0.0.1:1/3/"), hasToken = true, dao = dao, diaryDao = diaryDao)

    private fun movie(id: Int) = Movie(id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null)

    /** A saved movie that is watched, liked, rated 4.0 and a favorite: everything the rule protects. */
    private suspend fun fullyLovedMovie(id: Int): Movie {
        repo.markWatched(movie(id))
        assertTrue(repo.like(movie(id)))
        assertTrue(repo.setUserRating(movie(id), 4.0f))
        assertEquals(FavoriteResult.ADDED, repo.addFavorite(movie(id)))
        return dao.saved(id)!!
    }

    // ---------- Like ----------

    @Test
    fun aMovieThatIsNotSavedYetCannotBeLiked() = runBlocking {
        assertFalse(repo.like(movie(1)))
        assertEquals(Reaction.NONE, dao.saved(1)?.reaction) // it was saved (so the screen can follow it), but not liked
    }

    @Test
    fun aMovieOnTheWatchlistCannotBeLiked() = runBlocking {
        repo.addToWatchlist(movie(2))
        assertFalse(repo.like(movie(2)))
        assertEquals(Reaction.NONE, dao.saved(2)?.reaction)
        assertTrue(dao.saved(2)!!.inWatchlist) // nothing else changed
    }

    @Test
    fun aWatchedMovieCanBeLikedAndUnliked() = runBlocking {
        repo.markWatched(movie(3))
        assertTrue(repo.like(movie(3)))
        assertEquals(Reaction.LIKED, dao.saved(3)?.reaction)
        repo.clearReaction(3)
        assertEquals(Reaction.NONE, dao.saved(3)?.reaction)
    }

    @Test
    fun theLikeRuleLooksAtTheSavedCopyNotAtTheOneInTheCallersHand() = runBlocking {
        dao.put(movie(4)) // saved, not watched
        assertFalse(repo.like(movie(4).copy(isWatched = true))) // the screen's copy is out of date and claims "watched"
        assertEquals(Reaction.NONE, dao.saved(4)?.reaction)
    }

    // ---------- Favorite ----------

    @Test
    fun anUnwatchedMovieCannotBeAFavorite() = runBlocking {
        assertEquals(FavoriteResult.NOT_WATCHED, repo.toggleFavorite(movie(5)))
        repo.addToWatchlist(movie(6))
        assertEquals(FavoriteResult.NOT_WATCHED, repo.addFavorite(movie(6)))
        assertEquals(0, dao.countFavorites())
    }

    @Test
    fun aWatchedMovieCanBeAFavoriteAndToggledOff() = runBlocking {
        repo.markWatched(movie(7))
        assertEquals(FavoriteResult.ADDED, repo.toggleFavorite(movie(7)))
        assertTrue(dao.saved(7)!!.isFavorite)
        assertEquals(FavoriteResult.REMOVED, repo.toggleFavorite(movie(7)))
        assertFalse(dao.saved(7)!!.isFavorite)
    }

    @Test
    fun thereIsRoomForFourFavoritesAndTheFifthIsRefused() = runBlocking {
        for (id in 1..4) {
            repo.markWatched(movie(id))
            assertEquals(FavoriteResult.ADDED, repo.addFavorite(movie(id)))
        }
        repo.markWatched(movie(5))
        assertEquals(FavoriteResult.LIMIT_REACHED, repo.addFavorite(movie(5)))
        assertEquals(listOf(1, 2, 3, 4), repo.getFavorites().map { it.id })
    }

    @Test
    fun aNewFavoriteTakesTheLastPlace() = runBlocking {
        for (id in listOf(10, 20, 30)) {
            repo.markWatched(movie(id))
            repo.addFavorite(movie(id))
        }
        assertEquals(listOf(10, 20, 30), repo.getFavorites().map { it.id })
    }

    @Test
    fun theOrderTheUserDraggedTheFavoritesToIsSaved() = runBlocking {
        for (id in listOf(1, 2, 3, 4)) {
            repo.markWatched(movie(id))
            repo.addFavorite(movie(id))
        }
        repo.reorderFavorites(listOf(3, 1, 4, 2))
        assertEquals(listOf(3, 1, 4, 2), repo.observeFavorites().first().map { it.id })
        assertEquals(listOf(0, 1, 2, 3), repo.getFavorites().map { it.favoriteOrder }) // places, not times

        repo.reorderFavorites(listOf(2, 3, 4, 1))
        assertEquals(listOf(2, 3, 4, 1), repo.getFavorites().map { it.id })
    }

    @Test
    fun reorderingIgnoresStrangersAndNeverLosesAFavorite() = runBlocking {
        for (id in listOf(1, 2, 3)) {
            repo.markWatched(movie(id))
            repo.addFavorite(movie(id))
        }
        // 99 is not a favorite; 1 and 2 were forgotten by the list
        repo.reorderFavorites(listOf(3, 99))
        assertEquals(listOf(3, 1, 2), repo.getFavorites().map { it.id })
    }

    @Test
    fun removingAFavoriteKeepsTheOthersInOrderAndANewOneComesLast() = runBlocking {
        for (id in listOf(1, 2, 3)) {
            repo.markWatched(movie(id))
            repo.addFavorite(movie(id))
        }
        repo.removeFavorite(2)
        assertEquals(listOf(1, 3), repo.getFavorites().map { it.id })
        repo.markWatched(movie(9))
        repo.addFavorite(movie(9))
        assertEquals(listOf(1, 3, 9), repo.getFavorites().map { it.id })
    }

    // ---------- Rate ----------

    @Test
    fun anUnwatchedMovieCannotBeRated() = runBlocking {
        assertFalse(repo.setUserRating(movie(1), 3.5f))
        assertNull(dao.saved(1)?.userRating)
        repo.addToWatchlist(movie(2))
        assertFalse(repo.setUserRating(movie(2), 5.0f))
        assertNull(dao.saved(2)?.userRating)
        assertTrue(dao.saved(2)!!.inWatchlist) // rating does not mark it watched behind the user's back
    }

    @Test
    fun aWatchedMovieCanBeRatedInHalfSteps() = runBlocking {
        repo.markWatched(movie(3))
        assertTrue(repo.setUserRating(movie(3), 3.5f))
        assertEquals(3.5f, dao.saved(3)?.userRating)
        assertTrue(dao.saved(3)!!.ratedAt > 0)
        assertEquals(1, repo.observeRatedCount().first())
    }

    @Test
    fun aRatingIsRoundedToTheNearestHalfAndKeptInsideZeroPointFiveToFive() = runBlocking {
        repo.markWatched(movie(4))
        repo.setUserRating(movie(4), 3.8f)
        assertEquals(4.0f, dao.saved(4)?.userRating)
        repo.setUserRating(movie(4), 3.7f)
        assertEquals(3.5f, dao.saved(4)?.userRating)
        repo.setUserRating(movie(4), 9f)
        assertEquals(5.0f, dao.saved(4)?.userRating)
        repo.setUserRating(movie(4), 0.3f)
        assertEquals(0.5f, dao.saved(4)?.userRating)
    }

    @Test
    fun clearingARatingIsAlwaysAllowed() = runBlocking {
        repo.markWatched(movie(5))
        repo.setUserRating(movie(5), 4.5f)
        assertTrue(repo.setUserRating(movie(5), null))
        assertNull(dao.saved(5)?.userRating)
        assertEquals(0L, dao.saved(5)?.ratedAt)

        repo.setUserRating(movie(5), 4.5f)
        assertTrue(repo.setUserRating(movie(5), 0f)) // zero means "no rating" too
        assertNull(dao.saved(5)?.userRating)
    }

    // ---------- Taking "watched" away ----------

    @Test
    fun unmarkingWatchedClearsTheLikeTheRatingAndTheFavorite() = runBlocking {
        fullyLovedMovie(1)

        repo.unmarkWatched(1)

        val row = dao.saved(1)!!
        assertFalse(row.isWatched)
        assertEquals(Reaction.NONE, row.reaction)
        assertEquals(0L, row.reactedAt)
        assertNull(row.userRating)
        assertEquals(0L, row.ratedAt)
        assertFalse(row.isFavorite)
        assertEquals(0L, row.favoritedAt)
        assertEquals(0, repo.getFavorites().size)
    }

    @Test
    fun movingAWatchedMovieBackToTheWatchlistClearsThemToo() = runBlocking {
        fullyLovedMovie(2)

        repo.addToWatchlist(movie(2))

        val row = dao.saved(2)!!
        assertTrue(row.inWatchlist)
        assertFalse(row.isWatched)
        assertEquals(Reaction.NONE, row.reaction)
        assertNull(row.userRating)
        assertFalse(row.isFavorite)
    }

    @Test
    fun unmarkingWatchedKeepsAPassAndOtherMovies() = runBlocking {
        repo.markWatched(movie(3))
        repo.pass(movie(3))
        val other = fullyLovedMovie(4)

        repo.unmarkWatched(3)

        assertEquals(Reaction.PASSED, dao.saved(3)?.reaction) // only a LIKE is a watch-rule state
        assertEquals(other, dao.saved(4)) // nothing happened to the other movie
    }

    @Test
    fun noSequenceOfActionsLeavesALikedRatedOrFavoritedUnwatchedMovie() = runBlocking {
        val actions: List<suspend (Int) -> Unit> = listOf(
            { id -> repo.markWatched(movie(id)) },
            { id -> repo.like(movie(id)) },
            { id -> repo.setUserRating(movie(id), 2.5f) },
            { id -> repo.addFavorite(movie(id)) },
            { id -> repo.unmarkWatched(id) },
            { id -> repo.addToWatchlist(movie(id)) },
            { id -> repo.removeFromWatchlist(id) },
            { id -> repo.clearReaction(id) }
        )
        // Every pair and triple of actions, in every order: the impossible state never appears
        for (a in actions.indices) for (b in actions.indices) for (c in actions.indices) {
            val id = 100 + a * 100 + b * 10 + c
            actions[a](id); actions[b](id); actions[c](id)
            val row = dao.saved(id) ?: continue
            if (!row.isWatched) {
                assertFalse("liked and not watched ($a,$b,$c)", row.reaction == Reaction.LIKED)
                assertNull("rated and not watched ($a,$b,$c)", row.userRating)
                assertFalse("favorite and not watched ($a,$b,$c)", row.isFavorite)
            }
        }
    }

    // ---------- Undo ----------

    @Test
    fun undoPutsTheLikeTheRatingAndTheFavoriteBackWithTheirPlace() = runBlocking {
        for (id in listOf(1, 2, 3)) {
            repo.markWatched(movie(id))
            repo.addFavorite(movie(id))
        }
        repo.like(movie(2))
        repo.setUserRating(movie(2), 3.0f)
        val before = dao.saved(2)!!

        repo.unmarkWatched(2)
        assertEquals(listOf(1, 3), repo.getFavorites().map { it.id })

        repo.restoreState(before)

        assertEquals(before, dao.saved(2))
        assertEquals(listOf(1, 2, 3), repo.getFavorites().map { it.id })
    }

    @Test
    fun undoDoesNotBreakTheLimitOfFourFavorites() = runBlocking {
        for (id in 1..4) {
            repo.markWatched(movie(id))
            repo.addFavorite(movie(id))
        }
        val before = dao.saved(1)!!
        repo.unmarkWatched(1)                  // 3 favorites left
        repo.markWatched(movie(5))
        repo.addFavorite(movie(5))             // the free place is taken
        assertEquals(4, dao.countFavorites())

        repo.restoreState(before)              // Undo: the like and rating may come back, the favorite cannot

        assertTrue(dao.saved(1)!!.isWatched)
        assertFalse(dao.saved(1)!!.isFavorite)
        assertEquals(4, dao.countFavorites())
    }

    // ---------- Diary pages ----------

    private fun page(movieId: Int, text: String = "[]") = DiaryEntry(movieId = movieId, movieTitle = "Movie $movieId", blocksJson = text)

    @Test
    fun aNewDiaryPageIsInsertedAndASavedOneIsUpdated() = runBlocking {
        val id = repo.saveDiaryEntry(page(1, "first"))
        assertTrue(id > 0)
        val created = repo.diaryEntry(id)!!
        assertTrue(created.createdAt > 0)
        assertEquals(created.createdAt, created.updatedAt)

        repo.saveDiaryEntry(created.copy(blocksJson = "second", createdAt = 1L))
        val updated = repo.diaryEntry(id)!!
        assertEquals("second", updated.blocksJson)
        assertEquals(1L, updated.createdAt) // the caller's createdAt is kept, only updatedAt is stamped
        assertTrue(updated.updatedAt >= created.updatedAt)
        assertEquals(1, repo.observeDiaryCount().first())
    }

    @Test
    fun diaryPagesCanBeListedPerMovieAndDeleted() = runBlocking {
        val first = repo.saveDiaryEntry(page(1))
        repo.saveDiaryEntry(page(1))
        repo.saveDiaryEntry(page(2))

        assertEquals(2, repo.observeDiaryForMovie(1).first().size)
        assertEquals(3, repo.observeDiary().first().size)

        repo.deleteDiaryEntry(first)
        assertNull(repo.diaryEntry(first))
        assertEquals(1, repo.observeDiaryForMovie(1).first().size)
    }

    @Test
    fun clearingAllDataAlsoDeletesTheDiary() = runBlocking {
        repo.saveDiaryEntry(page(1))
        fullyLovedMovie(1)

        repo.clearAllData()

        assertEquals(0, repo.observeDiaryCount().first())
        assertNull(dao.saved(1))
    }

    // ---------- The diary gate ----------

    @Test
    fun aNewDiaryPageNeedsAWatchedMovieAndUnwatchingKeepsThePagesAlreadyWritten() = runBlocking {
        assertFalse(repo.canWriteDiary(1)) // not even saved
        repo.addToWatchlist(movie(1))
        assertFalse(repo.canWriteDiary(1))

        repo.markWatched(movie(1))
        assertTrue(repo.canWriteDiary(1))
        val pageId = repo.saveDiaryEntry(DiaryEntry(movieId = 1, movieTitle = "Movie 1", blocksJson = "[]"))

        repo.unmarkWatched(1)
        assertFalse("no NEW page for a movie that is not watched", repo.canWriteDiary(1))
        assertTrue("the page that was written stays", repo.diaryEntry(pageId) != null)
    }
}
