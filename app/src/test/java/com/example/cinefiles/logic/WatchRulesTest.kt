package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** THE WATCH RULE in plain Kotlin: only a watched movie can be liked, favorited or rated; un-watching clears them. */
class WatchRulesTest {

    private fun movie(watched: Boolean = false, watchlist: Boolean = false) = Movie(
        id = 1, title = "Movie", posterPath = "/p.jpg", backdropPath = null,
        isWatched = watched, inWatchlist = watchlist
    )

    @Test
    fun anUnsavedMovieIsNotWatchedSoItCannotBeLikedFavoritedOrRated() {
        assertFalse(canLike(null))
        assertFalse(canFavorite(null))
        assertFalse(canRate(null))
    }

    @Test
    fun aSavedMovieThatIsNotWatchedCannotEither() {
        for (unwatched in listOf(movie(), movie(watchlist = true))) {
            assertFalse(canLike(unwatched))
            assertFalse(canFavorite(unwatched))
            assertFalse(canRate(unwatched))
        }
    }

    @Test
    fun aWatchedMovieCanBeLikedFavoritedAndRated() {
        assertTrue(canLike(movie(watched = true)))
        assertTrue(canFavorite(movie(watched = true)))
        assertTrue(canRate(movie(watched = true)))
    }

    @Test
    fun theFavoritesHoldFour() {
        assertEquals(4, MAX_FAVORITES)
    }

    @Test
    fun aLikeARatingOrAFavoriteIsSomethingOnlyAWatchedMovieMayHave() {
        assertFalse(movie(watched = true).hasWatchDependents())
        assertTrue(movie(watched = true).copy(reaction = Reaction.LIKED).hasWatchDependents())
        assertTrue(movie(watched = true).copy(userRating = 0.5f).hasWatchDependents())
        assertTrue(movie(watched = true).copy(isFavorite = true).hasWatchDependents())
        assertFalse(movie(watched = true).copy(reaction = Reaction.PASSED).hasWatchDependents()) // a pass is not a like
    }

    @Test
    fun withoutWatchedClearsWatchedAndEverythingThatNeedsIt() {
        val loved = movie(watched = true).copy(
            watchedAt = 9L, reaction = Reaction.LIKED, reactedAt = 7L, userRating = 4.5f, ratedAt = 8L,
            isFavorite = true, favoritedAt = 6L, favoriteOrder = 2
        )

        val result = loved.withoutWatched()

        assertEquals(movie(watched = false).copy(id = 1), result)
    }

    @Test
    fun withoutWatchedKeepsAPassTheWatchlistAndTheInformation() {
        val passed = movie(watched = true, watchlist = true).copy(reaction = Reaction.PASSED, reactedAt = 3L, overview = "Story")

        val result = passed.withoutWatched()

        assertEquals(Reaction.PASSED, result.reaction)
        assertEquals(3L, result.reactedAt)
        assertTrue(result.inWatchlist)
        assertEquals("Story", result.overview)
        assertFalse(result.isWatched)
    }

    @Test
    fun aNewDiaryPageNeedsAWatchedMovie() {
        assertFalse(canWriteDiary(null))
        assertFalse(canWriteDiary(movie()))
        assertFalse(canWriteDiary(movie(watchlist = true)))
        assertTrue(canWriteDiary(movie(watched = true)))
    }
}
