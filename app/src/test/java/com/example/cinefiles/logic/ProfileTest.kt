package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun withNothingLikedThereIsNoTaste() {
        assertEquals(Taste(topGenre = null, likedCount = 0), tasteOf(emptyList()))
    }

    @Test
    fun theTasteIsTheGenreMostLikedMoviesHave() {
        val liked = listOf(
            movie(1, listOf("Drama", "Romance")),
            movie(2, listOf("Drama")),
            movie(3, listOf("Comedy", "Romance")),
            movie(4, listOf("Drama", "Crime"))
        )
        assertEquals(Taste(topGenre = "Drama", likedCount = 4), tasteOf(liked))
    }

    @Test
    fun equalGenresAreSettledAlphabeticallySoTheAnswerNeverChanges() {
        val liked = listOf(movie(1, listOf("Romance")), movie(2, listOf("Comedy")))
        assertEquals("Comedy", tasteOf(liked).topGenre)
        assertEquals("Comedy", tasteOf(liked.reversed()).topGenre)
    }

    @Test
    fun aMovieCountsOnceForAGenreEvenIfItIsListedTwice() {
        val liked = listOf(movie(1, listOf("Horror", "Horror")), movie(2, listOf("Comedy")), movie(3, listOf("Comedy")))
        assertEquals("Comedy", tasteOf(liked).topGenre) // Horror is 1 movie, not 2
    }

    @Test
    fun likedMoviesWithoutGenresStillCountInTheTotal() {
        assertEquals(Taste(topGenre = null, likedCount = 2), tasteOf(listOf(movie(1, emptyList()), movie(2, emptyList()))))
    }
}
