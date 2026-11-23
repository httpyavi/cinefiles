package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginationTest {

    // ---------- When a list asks for its next page ----------

    @Test
    fun asksWhenFewerThanSixItemsAreLeftBelow() {
        // 20 items; the last one you can see is item number 14 (counting from 0): 5 are below it
        assertTrue(shouldLoadNextPage(totalItems = 20, lastVisiblePosition = 14))
        assertTrue(shouldLoadNextPage(totalItems = 20, lastVisiblePosition = 19))
    }

    @Test
    fun doesNotAskWhileMoreItemsAreLeft() {
        // 6 items below is still plenty
        assertFalse(shouldLoadNextPage(totalItems = 20, lastVisiblePosition = 13))
        assertFalse(shouldLoadNextPage(totalItems = 20, lastVisiblePosition = 0))
    }

    @Test
    fun aShortListAsksImmediately() {
        assertTrue(shouldLoadNextPage(totalItems = 4, lastVisiblePosition = 0))
    }

    @Test
    fun doesNotAskForAnEmptyListOrWhenNothingIsVisible() {
        assertFalse(shouldLoadNextPage(totalItems = 0, lastVisiblePosition = -1))
        assertFalse(shouldLoadNextPage(totalItems = 20, lastVisiblePosition = -1))
    }

    @Test
    fun theDistanceCanBeChanged() {
        assertTrue(shouldLoadNextPage(totalItems = 20, lastVisiblePosition = 10, itemsLeft = 10))
        assertFalse(shouldLoadNextPage(totalItems = 20, lastVisiblePosition = 10, itemsLeft = 5))
    }

    // ---------- The page counter of the swipe deck ----------

    private val comedies = DiscoveryRequest(DiscoverySource.MOOD_MATCH, genreIds = listOf(35))
    private val trending = DiscoveryRequest(DiscoverySource.TRENDING, genreIds = listOf(35))

    @Test
    fun everyQuestionStartsOnPageOne() {
        assertEquals(1, PageCounter().pageFor(comedies))
    }

    @Test
    fun eachAnswerMovesThatQuestionOnToTheNextPage() {
        val pages = PageCounter()
        pages.markAnswered(comedies)
        pages.markAnswered(comedies)
        assertEquals(3, pages.pageFor(comedies))
        assertEquals(1, pages.pageFor(trending)) // other questions are not affected
    }

    @Test
    fun aQuestionThatWasNotAnsweredIsAskedAgainOnTheSamePage() {
        val pages = PageCounter()
        pages.pageFor(comedies) // asked, but the network failed: markAnswered is never called
        assertEquals(1, pages.pageFor(comedies))
    }

    @Test
    fun resetStartsAgainFromPageOne() {
        val pages = PageCounter()
        pages.markAnswered(comedies)
        pages.reset()
        assertEquals(1, pages.pageFor(comedies))
    }
}
