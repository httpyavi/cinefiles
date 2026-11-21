package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodsTest {

    private fun genresOf(id: String) = Moods.all.first { it.id == id }.genreIds

    @Test
    fun thereAreNineMoodsWithUniqueIds() {
        assertEquals(9, Moods.all.size)
        assertEquals(9, Moods.all.map { it.id }.toSet().size)
    }

    @Test
    fun surpriseMeIsTheLastMoodAndHasNoGenres() {
        val last = Moods.all.last()
        assertTrue(last.isSurprise)
        assertTrue(last.genreIds.isEmpty())
        // and no other mood is a "surprise"
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
        assertEquals(listOf(28, 12), genresOf("excited"))         // Action + Adventure
        assertEquals(listOf(10749), genresOf("romantic"))         // Romance
        assertEquals(listOf(27, 53), genresOf("scared"))          // Horror + Thriller
        assertEquals(listOf(18), genresOf("emotional"))           // Drama
        assertEquals(listOf(16, 10751), genresOf("relaxed"))      // Animation + Family
        assertEquals(listOf(14, 878), genresOf("escape"))         // Fantasy + Science Fiction
        assertEquals(listOf(9648), genresOf("mind_bending"))      // Mystery
    }

    @Test
    fun genreIdsForJoinsTheGenresOfTheChosenMoods() {
        assertEquals(listOf(35, 27, 53), Moods.genreIdsFor(listOf("happy", "scared")))
    }

    @Test
    fun genreIdsForListsEachIdOnlyOnce() {
        // Action 28 appears in only one mood, but asking twice must not repeat anything
        assertEquals(listOf(28, 12), Moods.genreIdsFor(listOf("excited", "excited")))
    }

    @Test
    fun surpriseMeAndNothingGiveNoGenreFilter() {
        assertTrue(Moods.genreIdsFor(listOf(Moods.SURPRISE_ID)).isEmpty())
        assertTrue(Moods.genreIdsFor(emptyList()).isEmpty())
    }
}
