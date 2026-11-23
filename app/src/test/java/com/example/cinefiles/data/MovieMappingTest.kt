package com.example.cinefiles.data

import com.example.cinefiles.data.dto.MovieDto
import com.example.cinefiles.data.dto.MovieListResponse
import com.example.cinefiles.data.dto.toMovie
import com.example.cinefiles.data.dto.toMovies
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TMDB's JSON (written here the way TMDB sends it) becomes our Movie. Gson reads it exactly as in the app. */
class MovieMappingTest {

    private val gson = Gson()

    private fun list(json: String): MovieListResponse = gson.fromJson(json, MovieListResponse::class.java)

    private val listJson = """
        {
          "page": 1,
          "total_pages": 500,
          "results": [
            {
              "id": 157336, "title": "Interstellar", "poster_path": "/poster.jpg", "backdrop_path": "/back.jpg",
              "overview": "Explorers travel through a wormhole.", "release_date": "2014-11-05",
              "vote_average": 8.4, "vote_count": 35000, "original_language": "en",
              "genre_ids": [12, 18, 878], "adult": false, "popularity": 120.5
            },
            { "id": 2, "title": "No poster", "poster_path": null, "release_date": "2020-01-01", "vote_average": 6.0, "vote_count": 10 },
            { "id": 3, "title": "   " },
            { "title": "No id at all", "poster_path": "/x.jpg" },
            { "id": 5, "original_title": "Only the original title", "poster_path": "/y.jpg" }
          ]
        }
    """.trimIndent()

    // ---------- A normal movie ----------

    @Test
    fun aFullMovieIsMappedFieldByField() {
        val movie = list(listJson).results.toMovies().first()
        assertEquals(157336, movie.id)
        assertEquals("Interstellar", movie.title)
        assertEquals("/poster.jpg", movie.posterPath)
        assertEquals("/back.jpg", movie.backdropPath)
        assertEquals("Explorers travel through a wormhole.", movie.overview)
        assertEquals("2014-11-05", movie.releaseDate)
        assertEquals(2014, movie.releaseYear)
        assertEquals(8.4, movie.voteAverage)
        assertEquals(35000, movie.voteCount)
        assertEquals("en", movie.originalLanguage)
        assertEquals(listOf(12, 18, 878), movie.genreIds)
        assertEquals(listOf("Adventure", "Drama", "Science Fiction"), movie.genreNames)
    }

    @Test
    fun theUserStateStartsEmpty() {
        val movie = list(listJson).results.toMovies().first()
        assertEquals(Reaction.NONE, movie.reaction)
        assertFalse(movie.inWatchlist)
        assertFalse(movie.isWatched)
    }

    // ---------- TMDB leaves things out ----------

    @Test
    fun movieWithoutPosterIsDroppedFromLists() {
        val ids = list(listJson).results.toMovies().map { it.id }
        assertFalse(2 in ids)
    }

    @Test
    fun movieWithoutPosterIsKeptWhenAPosterIsNotRequired() {
        val ids = list(listJson).results.toMovies(requirePoster = false).map { it.id }
        assertTrue(2 in ids)
    }

    @Test
    fun movieWithoutIdOrTitleIsDropped() {
        val ids = list(listJson).results.toMovies(requirePoster = false).map { it.id }
        assertFalse(3 in ids) // blank title
        assertEquals(listOf(157336, 2, 5), ids) // the one with no id is gone too
    }

    @Test
    fun theOriginalTitleIsUsedWhenThereIsNoTitle() {
        val movie = list(listJson).results.toMovies().first { it.id == 5 }
        assertEquals("Only the original title", movie.title)
    }

    @Test
    fun missingFieldsGetSafeDefaults() {
        val movie = list(listJson).results.toMovies().first { it.id == 5 }
        assertNull(movie.backdropPath)
        assertNull(movie.voteAverage)
        assertNull(movie.releaseYear)
        assertNull(movie.releaseDate)
        assertNull(movie.originalLanguage)
        assertNull(movie.runtimeMinutes)
        assertEquals("", movie.overview)
        assertEquals(0, movie.voteCount)
        assertTrue(movie.genreIds.isEmpty())
        assertTrue(movie.genreNames.isEmpty())
    }

    @Test
    fun anEmptyOrMissingResultsListIsAnEmptyList() {
        assertTrue(list("""{ "page": 1 }""").results.toMovies().isEmpty())
        assertTrue(list("""{ "results": [] }""").results.toMovies().isEmpty())
    }

    @Test
    fun aMovieNobodyHasRatedHasNoRatingInsteadOfZero() {
        val unrated = gson.fromJson(
            """{ "id": 9, "title": "New", "poster_path": "/p.jpg", "vote_average": 0.0, "vote_count": 0 }""",
            MovieDto::class.java
        ).toMovie()
        assertNotNull(unrated)
        assertNull(unrated!!.voteAverage)
    }

    @Test
    fun aBlankPosterPathCountsAsNoPoster() {
        val dto = MovieDto(id = 1, title = "Blank", posterPath = "")
        assertNull(dto.toMovie()!!.posterPath)
        assertTrue(listOf(dto).toMovies().isEmpty())
    }

    @Test
    fun unknownGenreNumbersAreSkippedInTheNames() {
        val movie = MovieDto(id = 1, title = "Odd", posterPath = "/p.jpg", genreIds = listOf(35, 99999)).toMovie()!!
        assertEquals(listOf(35, 99999), movie.genreIds) // the numbers are kept for the deck's filters
        assertEquals(listOf("Comedy"), movie.genreNames)
    }

    // ---------- The details answer ----------

    private val detailsJson = """
        {
          "id": 157336, "title": "Interstellar", "poster_path": "/poster.jpg", "release_date": "2014-11-05",
          "vote_average": 8.4, "vote_count": 35000, "runtime": 169,
          "genres": [ { "id": 12, "name": "Adventure" }, { "id": 878, "name": "Science Fiction" } ],
          "credits": {
            "cast": [
              { "name": "Matthew McConaughey", "character": "Cooper", "profile_path": "/m.jpg" },
              { "name": "", "character": "Nobody" },
              { "name": "Anne Hathaway", "character": "Brand" }
            ],
            "crew": [
              { "name": "Christopher Nolan", "job": "Director" },
              { "name": "Emma Thomas", "job": "Producer" },
              { "name": "Christopher Nolan", "job": "Director" }
            ]
          },
          "videos": { "results": [
            { "key": "teaser1", "site": "YouTube", "type": "Teaser" },
            { "key": "vimeo1", "site": "Vimeo", "type": "Trailer" },
            { "key": "trailer1", "site": "YouTube", "type": "Trailer" }
          ] }
        }
    """.trimIndent()

    @Test
    fun theDetailsAnswerFillsRuntimeAndGenresWithNames() {
        val details = gson.fromJson(detailsJson, MovieDto::class.java).toDetails()!!
        assertEquals(169, details.movie.runtimeMinutes)
        assertEquals(listOf(12, 878), details.movie.genreIds)
        assertEquals(listOf("Adventure", "Science Fiction"), details.movie.genreNames)
    }

    @Test
    fun theDetailsHaveCastDirectorsAndTheYouTubeTrailer() {
        val details = gson.fromJson(detailsJson, MovieDto::class.java).toDetails()!!
        assertEquals(listOf("Matthew McConaughey", "Anne Hathaway"), details.cast.map { it.name }) // the nameless one is gone
        assertEquals("Cooper", details.cast.first().character)
        assertEquals(listOf("Christopher Nolan"), details.directors) // listed once
        assertEquals("trailer1", details.trailerKey) // not the teaser, not the Vimeo one
    }

    @Test
    fun noVideosMeansNoTrailer() {
        val details = MovieDto(id = 1, title = "Quiet").toDetails()!!
        assertNull(details.trailerKey)
        assertTrue(details.cast.isEmpty())
        assertTrue(details.directors.isEmpty())
    }

    @Test
    fun aDetailsAnswerWithoutATitleIsNotUsable() {
        assertNull(MovieDto(id = 1).toDetails())
    }
}
