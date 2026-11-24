package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {

    private fun testMovie(id: Int, title: String, overview: String = "") = Movie(
        id = id,
        title = title,
        posterPath = null,
        backdropPath = null,
        overview = overview
    )

    @Test
    fun flagsSexuallyExplicitTitles() {
        assertTrue(ContentFilter.isExplicit("Sabik: Kasalanan Ba?", null, "An actor in a sexfilm."))
        assertTrue(ContentFilter.isExplicit("Foursome", null, "A weekend away."))
        assertTrue(ContentFilter.isExplicit("Dangerous Erotica", null, "A drama about artists."))
        assertTrue(ContentFilter.isExplicit("Softcore Fantasy", null, "Late night show."))
        assertTrue(ContentFilter.isExplicit("Summer Sexcapade", null, "Vacation adventures."))
    }

    @Test
    fun flagsExplicitOverviews() {
        val movie = testMovie(1, "Check-In", "A steamy threesome turns things upside down.")
        assertTrue(movie.isExplicit())

        val movie2 = testMovie(2, "Hotel Nights", "Contains sexually explicit encounters and adult video scenes.")
        assertTrue(movie2.isExplicit())
    }

    @Test
    fun preservesMainstreamRomanceAndComedy() {
        // Filipino mainstream romances
        assertFalse(testMovie(101, "She's Dating the Gangster", "Kenji and Athena pretend to date.").isExplicit())
        assertFalse(testMovie(102, "Four Sisters and a Wedding", "The siblings reunite for their brother's wedding.").isExplicit())
        assertFalse(testMovie(103, "Hello, Love, Goodbye", "Joy and Ethan meet in Hong Kong.").isExplicit())

        // Global mainstream romances & dramas
        assertFalse(testMovie(201, "Titanic", "Two lovers meet aboard the ship.").isExplicit())
        assertFalse(testMovie(202, "La La Land", "An aspiring actress and a jazz musician pursue their dreams in Los Angeles.").isExplicit())
        assertFalse(testMovie(203, "Pride and Prejudice", "A young woman deals with societal pressures.").isExplicit())
    }

    @Test
    fun preservesMatureAndActionStorylinesWithoutSexuality() {
        assertFalse(testMovie(301, "Oppenheimer", "The story of J. Robert Oppenheimer and the Manhattan Project.").isExplicit())
        assertFalse(testMovie(302, "Outside", "A family retreats to an isolated farm during a zombie outbreak.").isExplicit())
        assertFalse(testMovie(303, "Metro Manila", "An armored truck driver navigates the city.").isExplicit())
        assertFalse(testMovie(304, "Shake, Rattle & Roll", "Classic horror anthology.").isExplicit())
    }

    @Test
    fun doesNotMatchInnocentWordsContainingSubstrings() {
        // Words like "suspect", "sexism", "section", "essex"
        assertFalse(testMovie(401, "Primary Suspect", "Detectives investigate sexism in the police department.").isExplicit())
        assertFalse(testMovie(402, "Middle Section", "A journey through Essex countryside.").isExplicit())
    }
}
