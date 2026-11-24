package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodsTest {

    private fun genresOf(id: String) = Moods.all.first { it.id == id }.genreIds

    @Test
    fun thereAreTenMoodsWithUniqueIds() {
        assertEquals(10, Moods.all.size)
        assertEquals(10, Moods.all.map { it.id }.toSet().size)
    }

    @Test
    fun surpriseMeIsTheLastMoodAndHasNoGenres() {
        val last = Moods.all.last()
        assertTrue(last.isSurprise)
        assertTrue(last.genreIds.isEmpty())
        assertEquals(1, Moods.all.count { it.isSurprise })
    }

    @Test
    fun everyOtherMoodHasAtLeastOneGenre() {
        for (mood in Moods.all.filter { !it.isSurprise }) {
            assertTrue("${mood.id} needs genres", mood.genreIds.isNotEmpty())
        }
    }

    @Test
    fun genreIdsMatchTheBrief() {
        assertEquals(listOf(35), genresOf("happy"))               // Comedy
        assertEquals(listOf(10749), genresOf("romantic"))         // Romance
        assertEquals(listOf(16, 10751), genresOf("relaxed"))      // Animation + Family
        assertEquals(listOf(18), genresOf("emotional"))           // Drama
        assertEquals(listOf(28, 12), genresOf("excited"))         // Action + Adventure
        assertEquals(listOf(9648, 878), genresOf("curious"))      // Mystery + Sci-Fi
        assertEquals(listOf(36, 10751), genresOf("nostalgic"))    // History + Family
        assertEquals(listOf(14, 53), genresOf("bored"))           // Fantasy + Thriller
        assertEquals(listOf(28, 53), genresOf("angry"))           // Action + Thriller
    }

    @Test
    fun genreIdsForJoinsTheGenresOfTheChosenMoods() {
        assertEquals(listOf(35, 28, 53), Moods.genreIdsFor(listOf("happy", "angry")))
    }

    @Test
    fun genreIdsForListsEachIdOnlyOnce() {
        assertEquals(listOf(28, 12), Moods.genreIdsFor(listOf("excited", "excited")))
    }

    @Test
    fun surpriseMeAndNothingGiveNoGenreFilter() {
        assertTrue(Moods.genreIdsFor(listOf(Moods.SURPRISE_ID)).isEmpty())
        assertTrue(Moods.genreIdsFor(emptyList()).isEmpty())
    }
}
