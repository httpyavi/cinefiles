package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SwipeDeckTest {

    private fun movie(id: Int) = Movie(id, "Movie $id", "/p$id.jpg", null)

    /** A deck of [size] movies with ids 1..size. */
    private fun deckOf(size: Int) = SwipeDeck((1..size).map { movie(it) })

    @Test
    fun aNewDeckStartsAtTheFirstCard() {
        val deck = deckOf(5)
        assertEquals(0, deck.position)
        assertEquals(1, deck.shownPosition)
        assertEquals(5, deck.total)
        assertEquals(1, deck.top?.id)
        assertEquals(2, deck.second?.id)
        assertEquals(3, deck.third?.id)
        assertFalse(deck.isFinished)
        assertFalse(deck.canUndo)
    }

    @Test
    fun likingMovesToTheNextCardAndRemembersTheMovie() {
        val deck = deckOf(5).swipe(SwipeDirection.LIKE)
        assertEquals(1, deck.position)
        assertEquals(2, deck.shownPosition)
        assertEquals(2, deck.top?.id)
        assertEquals(listOf(1), deck.likedMovies.map { it.id })
        assertTrue(deck.passedMovies.isEmpty())
        assertTrue(deck.canUndo)
    }

    @Test
    fun passingMovesToTheNextCardAndIsNotLiked() {
        val deck = deckOf(5).swipe(SwipeDirection.PASS)
        assertEquals(2, deck.top?.id)
        assertTrue(deck.likedMovies.isEmpty())
        assertEquals(listOf(1), deck.passedMovies.map { it.id })
    }

    @Test
    fun undoBringsTheLastCardBack() {
        val deck = deckOf(5).swipe(SwipeDirection.LIKE).swipe(SwipeDirection.PASS).undo()
        assertEquals(1, deck.position)
        assertEquals(2, deck.top?.id)
        assertEquals(listOf(1), deck.likedMovies.map { it.id })
        assertTrue(deck.passedMovies.isEmpty()) // the pass was taken back
    }

    @Test
    fun undoCanBeUsedAgainAndAgainUntilTheHistoryIsEmpty() {
        var deck = deckOf(5)
        repeat(4) { deck = deck.swipe(SwipeDirection.LIKE) }
        repeat(4) { deck = deck.undo() }
        assertEquals(0, deck.position)
        assertEquals(1, deck.top?.id)
        assertFalse(deck.canUndo)
        assertTrue(deck.likedMovies.isEmpty())
    }

    @Test
    fun undoWithNoHistoryChangesNothing() {
        val deck = deckOf(3)
        assertEquals(deck, deck.undo())
    }

    @Test
    fun theLastSwipeFinishesTheDeck() {
        var deck = deckOf(3)
        repeat(3) { deck = deck.swipe(SwipeDirection.PASS) }
        assertTrue(deck.isFinished)
        assertNull(deck.top)
        assertEquals(3, deck.shownPosition) // "3 of 3", never "4 of 3"
    }

    @Test
    fun swipingAFinishedDeckDoesNothing() {
        var deck = deckOf(2)
        repeat(2) { deck = deck.swipe(SwipeDirection.LIKE) }
        assertEquals(deck, deck.swipe(SwipeDirection.LIKE))
        assertEquals(2, deck.history.size)
    }

    @Test
    fun undoWorksFromAFinishedDeck() {
        var deck = deckOf(2)
        repeat(2) { deck = deck.swipe(SwipeDirection.LIKE) }
        deck = deck.undo()
        assertFalse(deck.isFinished)
        assertEquals(2, deck.top?.id)
        assertEquals(listOf(1), deck.likedMovies.map { it.id })
    }

    @Test
    fun nearTheEndThereAreNoSecondOrThirdCards() {
        val deck = deckOf(3).swipe(SwipeDirection.PASS).swipe(SwipeDirection.PASS)
        assertEquals(3, deck.top?.id)
        assertNull(deck.second)
        assertNull(deck.third)
    }

    @Test
    fun keepSwipingAddsCardsAndTheCountKeepsGoing() {
        var deck = deckOf(2)
        repeat(2) { deck = deck.swipe(SwipeDirection.LIKE) }
        deck = deck.withMoreCards(listOf(movie(10), movie(11)))
        assertFalse(deck.isFinished)
        assertEquals(10, deck.top?.id)
        assertEquals(4, deck.total)
        assertEquals(3, deck.shownPosition) // "3 of 4"
    }

    @Test
    fun aMovieLikedTwiceIsInThePicksOnlyOnce() {
        var deck = SwipeDeck(listOf(movie(1), movie(2)))
        repeat(2) { deck = deck.swipe(SwipeDirection.LIKE) }
        deck = deck.withMoreCards(listOf(movie(1))) // movie 1 shows up again
        deck = deck.swipe(SwipeDirection.LIKE)
        assertEquals(3, deck.history.size)
        assertEquals(listOf(1, 2), deck.likedMovies.map { it.id })
    }

    @Test
    fun swipeNeverChangesTheOldDeck() {
        val before = deckOf(3)
        before.swipe(SwipeDirection.LIKE)
        assertEquals(0, before.position)
        assertTrue(before.history.isEmpty())
    }

    @Test
    fun anEmptyDeckIsFinishedAndSafe() {
        val deck = SwipeDeck(emptyList())
        assertTrue(deck.isFinished)
        assertEquals(0, deck.shownPosition)
        assertEquals(deck, deck.swipe(SwipeDirection.LIKE))
        assertEquals(deck, deck.undo())
    }

    /**
     * Hundreds of random swipes and undos. After each step the deck must still agree with itself:
     * the position, the top card and the liked list must match the history.
     */
    @Test
    fun randomSwipesAndUndosKeepEverythingConsistent() {
        val random = Random(42)
        var deck = deckOf(20)
        repeat(500) {
            deck = when (random.nextInt(3)) {
                0 -> deck.swipe(SwipeDirection.LIKE)
                1 -> deck.swipe(SwipeDirection.PASS)
                else -> deck.undo()
            }
            assertEquals(deck.history.size, deck.position)
            assertEquals(deck.cards.getOrNull(deck.position)?.id, deck.top?.id)
            assertEquals(deck.history.map { it.movie.id }, deck.cards.take(deck.position).map { it.id })
            val likedInHistory = deck.history.filter { it.direction == SwipeDirection.LIKE }.map { it.movie.id }
            assertEquals(likedInHistory.distinct(), deck.likedMovies.map { it.id })
            assertTrue(deck.shownPosition in 0..deck.total)
        }
    }
}
