package com.example.cinefiles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two lists of a movie become one line of text in the database, and back. */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun genreIdsBecomeACommaSeparatedLine() {
        assertEquals("28,12,878", converters.idsToText(listOf(28, 12, 878)))
    }

    @Test
    fun genreIdsComeBackAsTheSameList() {
        val ids = listOf(35, 10749, 18)
        assertEquals(ids, converters.textToIds(converters.idsToText(ids)))
    }

    @Test
    fun noGenreIdsIsAnEmptyLineAndComesBackEmpty() {
        assertEquals("", converters.idsToText(emptyList()))
        assertTrue(converters.textToIds("").isEmpty())
    }

    @Test
    fun badPiecesInTheLineAreSkippedInsteadOfCrashing() {
        assertEquals(listOf(28, 12), converters.textToIds("28, oops ,12,"))
    }

    @Test
    fun genreNamesKeepTheirSpacesAndComeBackTheSame() {
        val names = listOf("Science Fiction", "TV Movie", "Drama")
        assertEquals("Science Fiction,TV Movie,Drama", converters.namesToText(names))
        assertEquals(names, converters.textToNames(converters.namesToText(names)))
    }

    @Test
    fun noGenreNamesComesBackEmpty() {
        assertEquals("", converters.namesToText(emptyList()))
        assertTrue(converters.textToNames("").isEmpty())
    }
}
