package com.example.cinefiles.util

import com.example.cinefiles.data.Movie
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The small pieces of text every screen writes about a movie. (The phone's language is set to US English here.) */
class MovieTextsTest {

    private var originalLocale: Locale = Locale.getDefault()

    @Before
    fun useEnglishNumbers() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun putTheLanguageBack() = Locale.setDefault(originalLocale)

    private fun movie(year: Int? = null, genres: List<String> = emptyList(), rating: Double? = null) = Movie(
        id = 1, title = "Movie", posterPath = "/p.jpg", backdropPath = null,
        releaseYear = year, genreNames = genres, voteAverage = rating
    )

    @Test
    fun theRatingHasOneDecimal() {
        assertEquals("8.4", movie(rating = 8.4012).ratingText())
        assertEquals("7.0", movie(rating = 7.0).ratingText())
        assertEquals("8.5", movie(rating = 8.46).ratingText())
    }

    @Test
    fun aMovieNobodyRatedHasNoRatingText() {
        assertNull(movie(rating = null).ratingText())
    }

    @Test
    fun genresAreJoinedAndCanBeLimited() {
        val drama = movie(genres = listOf("Drama", "Crime", "Thriller"))
        assertEquals("Drama, Crime, Thriller", drama.genresText())
        assertEquals("Drama, Crime", drama.genresText(max = 2))
        assertEquals("", movie().genresText())
    }

    @Test
    fun theYearAndUpToTwoGenresAreWrittenTogether() {
        val full = movie(year = 2017, genres = listOf("Science Fiction", "Drama", "Mystery"))
        assertEquals("2017 · Science Fiction, Drama", full.yearAndGenres())
        assertEquals("2017 · Science Fiction", full.yearAndGenres(maxGenres = 1))
    }

    @Test
    fun anythingUnknownIsLeftOut() {
        assertEquals("2017", movie(year = 2017).yearAndGenres())
        assertEquals("Drama", movie(genres = listOf("Drama")).yearAndGenres())
        assertEquals("", movie().yearAndGenres())
    }

    @Test
    fun aLanguageCodeBecomesItsEnglishName() {
        assertEquals("English", languageName("en"))
        assertEquals("Korean", languageName("ko"))
        assertEquals("Japanese", languageName(" JA "))
        assertEquals("Cantonese", languageName("cn")) // TMDB's own code
    }

    @Test
    fun noLanguageOrAnUnknownCodeHasNoName() {
        assertNull(languageName(null))
        assertNull(languageName(""))
        assertNull(languageName("xx")) // TMDB's "no spoken language"
    }

    @Test
    fun wholeNumbersAreWrittenWithTheLanguagesSeparator() {
        assertEquals("1,234", 1234.formatted())
        assertEquals("7", 7.formatted())
    }
}
