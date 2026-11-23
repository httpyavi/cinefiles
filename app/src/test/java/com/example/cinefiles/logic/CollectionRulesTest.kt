package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The badge a poster wears, the long-press menu of each tab, and what the Details watchlist button says. */
class CollectionRulesTest {

    private fun movie(
        liked: Boolean = false,
        passed: Boolean = false,
        watchlist: Boolean = false,
        watched: Boolean = false
    ) = Movie(
        id = 1, title = "Movie", posterPath = "/p.jpg", backdropPath = null,
        reaction = if (liked) Reaction.LIKED else if (passed) Reaction.PASSED else Reaction.NONE,
        inWatchlist = watchlist, isWatched = watched
    )

    // ---------- statuses ----------

    @Test
    fun aWatchedMovieIsNeverCountedOnTheWatchlist() {
        // Even if the flag is still set, the watchlist only holds movies that are not watched
        assertFalse(movie(watchlist = true, watched = true).hasStatus(MovieStatus.WATCHLIST))
        assertTrue(movie(watchlist = true).hasStatus(MovieStatus.WATCHLIST))
    }

    @Test
    fun passedIsNotLiked() {
        assertFalse(movie(passed = true).hasStatus(MovieStatus.LIKED))
    }

    // ---------- badges ----------

    @Test
    fun aPosterWithNothingElseToSayHasNoBadge() {
        assertNull(badgeStatusFor(movie(liked = true), CollectionTab.LIKED))
        assertNull(badgeStatusFor(movie(watchlist = true), CollectionTab.WATCHLIST))
        assertNull(badgeStatusFor(movie(watched = true), CollectionTab.WATCHED))
    }

    @Test
    fun theTabsOwnStatusIsNeverShownBecauseEveryPosterThereHasIt() {
        val everything = movie(liked = true, watched = true)
        assertEquals(MovieStatus.WATCHED, badgeStatusFor(everything, CollectionTab.LIKED))   // not the heart
        assertEquals(MovieStatus.LIKED, badgeStatusFor(everything, CollectionTab.WATCHED))   // not the check
    }

    @Test
    fun theMostImportantOtherStatusWins() {
        // watched, then liked, then watchlist (a watched movie is not on the watchlist, so that pair cannot meet)
        assertEquals(MovieStatus.WATCHED, badgeStatusFor(movie(liked = true, watched = true), CollectionTab.WATCHLIST))
        assertEquals(MovieStatus.LIKED, badgeStatusFor(movie(liked = true, watchlist = true), CollectionTab.WATCHLIST))
        assertEquals(MovieStatus.WATCHLIST, badgeStatusFor(movie(liked = true, watchlist = true), CollectionTab.LIKED))
    }

    @Test
    fun aPassedMovieHasNoBadgeOfItsOwn() {
        assertNull(badgeStatusFor(movie(passed = true, watched = true), CollectionTab.WATCHED))
    }

    // ---------- the long-press menu ----------

    @Test
    fun likedTabOffersTheWatchlistTheWatchedFlagAndUnlike() {
        assertEquals(
            listOf(CollectionAction.ADD_TO_WATCHLIST, CollectionAction.MARK_WATCHED, CollectionAction.UNLIKE),
            actionsFor(CollectionTab.LIKED, movie(liked = true))
        )
        assertEquals(
            listOf(CollectionAction.REMOVE_FROM_WATCHLIST, CollectionAction.MARK_WATCHED, CollectionAction.UNLIKE),
            actionsFor(CollectionTab.LIKED, movie(liked = true, watchlist = true))
        )
        // A watched movie cannot be "added" to the watchlist: it is moved back
        assertEquals(
            listOf(CollectionAction.MOVE_BACK_TO_WATCHLIST, CollectionAction.MARK_UNWATCHED, CollectionAction.UNLIKE),
            actionsFor(CollectionTab.LIKED, movie(liked = true, watched = true))
        )
    }

    @Test
    fun watchlistTabOffersWatchedAndRemoveButNeverLike() {
        // THE LIKE RULE: a movie on the watchlist cannot be liked, so "Like" is not in its menu
        assertEquals(
            listOf(CollectionAction.MARK_WATCHED, CollectionAction.REMOVE_FROM_WATCHLIST),
            actionsFor(CollectionTab.WATCHLIST, movie(watchlist = true))
        )
        // A movie that was liked before it joined the watchlist can still be unliked
        assertEquals(
            listOf(CollectionAction.MARK_WATCHED, CollectionAction.UNLIKE, CollectionAction.REMOVE_FROM_WATCHLIST),
            actionsFor(CollectionTab.WATCHLIST, movie(watchlist = true, liked = true))
        )
    }

    @Test
    fun theLikedTabNeverOffersLikeEither() {
        // Every movie there is already liked: the menu has Unlike, never Like
        for (movie in listOf(movie(liked = true), movie(liked = true, watchlist = true), movie(liked = true, watched = true))) {
            assertFalse(CollectionAction.LIKE in actionsFor(CollectionTab.LIKED, movie))
        }
    }

    // ---------- the like rule ----------

    @Test
    fun aMovieOnTheWatchlistCannotBeLiked() {
        assertFalse(canLike(movie(watchlist = true)))
    }

    @Test
    fun otherMoviesCanBeLiked() {
        assertTrue(canLike(null))                  // not saved yet
        assertTrue(canLike(movie()))               // saved, nothing else
        assertTrue(canLike(movie(watched = true))) // watched
        assertTrue(canLike(movie(passed = true)))  // passed: a changed mind is fine
        assertTrue(canLike(movie(liked = true)))   // already liked
    }

    @Test
    fun aWatchedMovieIsNotOnTheWatchlistSoItCanBeLiked() {
        // Marking watched takes a movie off the watchlist; a stale flag must not block the like
        assertTrue(canLike(movie(watchlist = true, watched = true)))
    }

    @Test
    fun likingIsPossibleAgainAfterLeavingTheWatchlist() {
        val onList = movie(watchlist = true)
        assertFalse(canLike(onList))
        assertTrue(canLike(onList.copy(inWatchlist = false))) // removed from the watchlist
        assertTrue(canLike(onList.copy(inWatchlist = false, isWatched = true))) // marked watched
    }

    // ---------- the user's state on a movie that came from TMDB ----------

    @Test
    fun aMovieFromTmdbTakesOverTheStateOfItsSavedCopy() {
        val fromTmdb = movie().copy(title = "Fresh title")
        val saved = movie(liked = true, watched = true).copy(reactedAt = 5L, watchedAt = 9L)

        val merged = fromTmdb.withStateOf(saved)

        assertEquals("Fresh title", merged.title) // the information stays TMDB's
        assertEquals(Reaction.LIKED, merged.reaction)
        assertTrue(merged.isWatched)
        assertEquals(5L, merged.reactedAt)
        assertEquals(9L, merged.watchedAt)
    }

    @Test
    fun aMovieThatIsNotSavedKeepsItsOwnState() {
        val fromTmdb = movie()
        assertEquals(fromTmdb, fromTmdb.withStateOf(null))
    }

    @Test
    fun withoutATabTheMostImportantStatusIsTheBadge() {
        // Discover has no tab: nothing is left out
        assertEquals(MovieStatus.WATCHED, badgeStatusFor(movie(liked = true, watched = true), null))
        assertEquals(MovieStatus.LIKED, badgeStatusFor(movie(liked = true, watchlist = true), null))
        assertEquals(MovieStatus.WATCHLIST, badgeStatusFor(movie(watchlist = true), null))
        assertNull(badgeStatusFor(movie(), null))
        assertNull(badgeStatusFor(movie(passed = true), null))
    }

    @Test
    fun watchedTabOffersMoveBackLikeAndRemove() {
        assertEquals(
            listOf(CollectionAction.MOVE_BACK_TO_WATCHLIST, CollectionAction.LIKE, CollectionAction.MARK_UNWATCHED),
            actionsFor(CollectionTab.WATCHED, movie(watched = true))
        )
        assertEquals(
            listOf(CollectionAction.MOVE_BACK_TO_WATCHLIST, CollectionAction.UNLIKE, CollectionAction.MARK_UNWATCHED),
            actionsFor(CollectionTab.WATCHED, movie(watched = true, liked = true))
        )
    }

    @Test
    fun theLastEntryAlwaysRemovesTheMovieFromItsTab() {
        assertEquals(CollectionAction.UNLIKE, actionsFor(CollectionTab.LIKED, movie(liked = true)).last())
        assertEquals(
            CollectionAction.REMOVE_FROM_WATCHLIST,
            actionsFor(CollectionTab.WATCHLIST, movie(watchlist = true)).last()
        )
        assertEquals(CollectionAction.MARK_UNWATCHED, actionsFor(CollectionTab.WATCHED, movie(watched = true)).last())
    }

    // ---------- the Details watchlist button ----------

    @Test
    fun theWatchlistButtonFollowsTheMovie() {
        assertEquals(WatchlistButton.ADD, watchlistButtonFor(movie()))
        assertEquals(WatchlistButton.IN_LIST, watchlistButtonFor(movie(watchlist = true)))
        assertEquals(WatchlistButton.MOVE_BACK, watchlistButtonFor(movie(watched = true)))
        // Liking changes nothing here: liked is independent of the watchlist
        assertEquals(WatchlistButton.ADD, watchlistButtonFor(movie(liked = true)))
    }

    @Test
    fun onlyTheInListButtonIsMarkedAsOnTheList() {
        assertFalse(WatchlistButton.ADD.isOnList)
        assertTrue(WatchlistButton.IN_LIST.isOnList)
        assertFalse(WatchlistButton.MOVE_BACK.isOnList)
    }

    // ---------- length ----------

    @Test
    fun aRuntimeIsSplitIntoHoursAndMinutes() {
        assertEquals(Pair(2, 49), splitRuntime(169))
        assertEquals(Pair(1, 0), splitRuntime(60))
        assertEquals(Pair(0, 45), splitRuntime(45))
    }
}
