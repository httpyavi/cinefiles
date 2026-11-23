package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenresTest {

    @Test
    fun tmdbHasNineteenMovieGenres() {
        assertEquals(19, Genres.count)
    }

    @Test
    fun namesAreLookedUpByNumber() {
        assertEquals("Action", Genres.nameOf(28))
        assertEquals("Science Fiction", Genres.nameOf(878))
        assertEquals("TV Movie", Genres.nameOf(10770))
        assertNull(Genres.nameOf(12345))
    }

    @Test
    fun namesOfKeepsTheOrderAndSkipsUnknownNumbers() {
        assertEquals(listOf("Drama", "Comedy"), Genres.namesOf(listOf(18, 0, 35)))
    }

    @Test
    fun everyGenreAMoodAsksForHasAName() {
        // If a mood used a genre number we cannot name, its cards would show no genre
        val used = Moods.all.flatMap { it.genreIds }.distinct()
        assertTrue(used.all { Genres.nameOf(it) != null })
    }
}
