package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Profile rules: which display names are accepted, the avatar letter, and "your taste". */
class ProfileTest {

    private fun movie(id: Int, genres: List<String>) =
        Movie(id = id, title = "Movie $id", posterPath = "/p.jpg", backdropPath = null, genreNames = genres)

    // ---------- the display name ----------

    @Test
    fun aNameLosesTheSpacesAroundItAndInsideIt() {
        assertEquals("Mia Santos", cleanDisplayName("   Mia    Santos  ", maxLength = 20))
    }

    @Test
    fun aBlankNameIsNotAllowed() {
        assertNull(cleanDisplayName("", maxLength = 20))
        assertNull(cleanDisplayName("     ", maxLength = 20))
    }

    @Test
    fun aLongNameIsCutToTheLimit() {
        assertEquals("abcdefghij", cleanDisplayName("abcdefghijklmnop", maxLength = 10))
    }

    @Test
    fun aCutNameNeverEndsWithASpace() {
        // "abc def" cut after 4 letters is "abc " and must come out as "abc"
        assertEquals("abc", cleanDisplayName("abc def", maxLength = 4))
    }

    @Test
    fun aNameThatFitsIsKeptAsItIs() {
        assertEquals("Movie Lover", cleanDisplayName("Movie Lover", maxLength = 20))
    }

    // ---------- the avatar letter ----------

    @Test
    fun theAvatarShowsTheFirstLetterAsACapital() {
        assertEquals("M", initialOf("movie lover"))
        assertEquals("J", initialOf("  jun"))
    }

    @Test
    fun anEmptyNameHasNoInitial() {
        assertEquals("", initialOf(""))
        assertEquals("", initialOf("   "))
    }

    @Test
    fun aLetterMadeOfTwoCharactersIsNotSplit() {
        // U+1F3AC (a clapper board) is two Java characters long
        val clapper = String(Character.toChars(0x1F3AC))
        assertEquals(clapper, initialOf(clapper + " Fan"))
    }

    // ---------- your taste ----------

    @Test
    fun withInsufficientLikedMoviesThereIsNoPersonalityYet() {
        assertEquals(Taste(topGenre = null, likedCount = 0), tasteOf(emptyList()))
        assertFalse(tasteOf(emptyList()).hasEnoughData)

        val single = listOf(movie(1, listOf("Drama")))
        val singleTaste = tasteOf(single)
        assertEquals("Drama", singleTaste.topGenre)
        assertEquals(1, singleTaste.likedCount)
        assertFalse(singleTaste.hasEnoughData)
    }

    @Test
    fun theTasteDerivesPersonalityAndSupportingGenresFromLikedMovies() {
        val liked = listOf(
            movie(1, listOf("Drama", "Romance")),
            movie(2, listOf("Drama")),
            movie(3, listOf("Comedy", "Romance")),
            movie(4, listOf("Drama", "Crime"))
        )
        val taste = tasteOf(liked)
        assertEquals("Drama", taste.topGenre)
        assertEquals("Romance", taste.secondaryGenre)
        assertEquals(3, taste.topGenreCount)
        assertEquals(4, taste.likedCount)
        assertEquals(TastePersonality.DRAMA_ROMANCE, taste.personality)
        assertTrue(taste.hasEnoughData)
    }

    @Test
    fun equalGenresAreSettledAlphabeticallySoTheAnswerNeverChanges() {
        val liked = listOf(movie(1, listOf("Romance")), movie(2, listOf("Comedy")))
        assertEquals("Comedy", tasteOf(liked).topGenre)
        assertEquals("Comedy", tasteOf(liked.reversed()).topGenre)
        assertEquals(TastePersonality.ROMCOM_FAN, tasteOf(liked).personality)
    }

    @Test
    fun aMovieCountsOnceForAGenreEvenIfItIsListedTwice() {
        val liked = listOf(movie(1, listOf("Horror", "Horror")), movie(2, listOf("Comedy")), movie(3, listOf("Comedy")))
        assertEquals("Comedy", tasteOf(liked).topGenre) // Horror is 1 movie, not 2
        assertEquals(2, tasteOf(liked).topGenreCount)
    }

    @Test
    fun likedMoviesWithoutGenresHaveNoPersonality() {
        val taste = tasteOf(listOf(movie(1, emptyList()), movie(2, emptyList())))
        assertEquals(Taste(topGenre = null, likedCount = 2), taste)
        assertFalse(taste.hasEnoughData)
    }

    @Test
    fun sciFiAndAdventureMapToDistinctPersonalities() {
        val sciFi = tasteOf(listOf(movie(1, listOf("Science Fiction")), movie(2, listOf("Science Fiction"))))
        assertEquals(TastePersonality.MIND_BENDING, sciFi.personality)

        val adventure = tasteOf(listOf(movie(1, listOf("Adventure")), movie(2, listOf("Action"))))
        assertEquals(TastePersonality.THRILL_SEEKER, adventure.personality)
    }
}
