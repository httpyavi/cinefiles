package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeaturedTest {

    private fun movie(id: Int, backdrop: String? = "/b$id.jpg") =
        Movie(id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = backdrop)

    // Movie 1 has no backdrop, so the five candidates are movies 2, 3, 4, 5 and 6
    private val trending = listOf(movie(1, backdrop = null)) + (2..8).map { movie(it) }

    @Test
    fun theDayOfTheYearPicksAmongTheFirstFiveWithABackdrop() {
        assertEquals(2, pickFeatured(trending, dayOfYear = 5)?.id)  // 5 % 5 = 0
        assertEquals(3, pickFeatured(trending, dayOfYear = 6)?.id)  // 6 % 5 = 1
        assertEquals(6, pickFeatured(trending, dayOfYear = 9)?.id)  // 9 % 5 = 4
        assertEquals(3, pickFeatured(trending, dayOfYear = 366)?.id) // 366 % 5 = 1
    }

    @Test
    fun theSameDayGivesTheSameMovie() {
        assertEquals(pickFeatured(trending, 100), pickFeatured(trending, 100))
    }

    @Test
    fun moviesBeyondTheFirstFiveAreNeverFeatured() {
        val picked = (1..366).mapNotNull { pickFeatured(trending, it)?.id }.toSet()
        assertEquals(setOf(2, 3, 4, 5, 6), picked)
    }

    @Test
    fun withFewerThanFiveCandidatesTheyTakeTurns() {
        val two = listOf(movie(1), movie(2))
        assertEquals(1, pickFeatured(two, dayOfYear = 4)?.id)
        assertEquals(2, pickFeatured(two, dayOfYear = 5)?.id)
    }

    @Test
    fun noBackdropAnywhereMeansNothingToFeature() {
        assertNull(pickFeatured(listOf(movie(1, backdrop = null), movie(2, backdrop = null)), dayOfYear = 10))
        assertNull(pickFeatured(emptyList(), dayOfYear = 10))
    }
}
