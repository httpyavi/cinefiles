package com.example.cinefiles.data

import com.example.cinefiles.data.dto.MovieDto
import com.example.cinefiles.data.dto.MovieListResponse
import com.example.cinefiles.data.dto.PersonDto
import com.example.cinefiles.data.dto.toMovie
import com.example.cinefiles.data.dto.toMovies
import com.example.cinefiles.logic.CrewRole
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
          "tagline": "  Mankind was born on Earth. It was never meant to die here.  ", "status": "Released",
          "production_countries": [ { "iso_3166_1": "US", "name": "United States of America" }, { "name": "United Kingdom" } ],
          "credits": {
            "cast": [
              { "id": 10297, "name": "Matthew McConaughey", "character": "Cooper", "profile_path": "/m.jpg" },
              { "id": 99, "name": "", "character": "Nobody" },
              { "name": "No id", "character": "Cannot be opened" },
              { "id": 1813, "name": "Anne Hathaway", "character": "Brand" }
            ],
            "crew": [
              { "id": 525, "name": "Christopher Nolan", "job": "Director" },
              { "id": 525, "name": "Christopher Nolan", "job": "Screenplay" },
              { "id": 7, "name": "Jonathan Nolan", "job": "Writer" },
              { "id": 1, "name": "Emma Thomas", "job": "Producer" },
              { "id": 5, "name": "Hoyte van Hoytema", "job": "Director of Photography" },
              { "id": 947, "name": "Hans Zimmer", "job": "Original Music Composer" },
              { "id": 8, "name": "Somebody", "job": "Best Boy" },
              { "id": 525, "name": "Christopher Nolan", "job": "Director" }
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
        assertEquals(listOf("Christopher Nolan"), details.directors.map { it.name }) // listed once
        assertEquals("trailer1", details.trailerKey) // not the teaser, not the Vimeo one
    }

    @Test
    fun everyoneInTheCastCarriesTheirPersonIdAndPeopleWithoutOneAreLeftOut() {
        val cast = gson.fromJson(detailsJson, MovieDto::class.java).toDetails()!!.cast
        assertEquals(listOf(10297, 1813), cast.map { it.id }) // "No id" cannot be opened, so it is not shown
        assertEquals("/m.jpg", cast.first().profilePath)
        assertNull(cast.last().profilePath)
    }

    @Test
    fun theCrewIsGroupedByWhatTheDetailsScreenShows() {
        val details = gson.fromJson(detailsJson, MovieDto::class.java).toDetails()!!

        assertEquals(525, details.directors.single().id)
        assertEquals(
            listOf(CrewRole.WRITER, CrewRole.PRODUCER, CrewRole.CINEMATOGRAPHY, CrewRole.COMPOSER),
            details.crew.keys.toList() // the director is shown on his own, and "Best Boy" is not shown at all
        )
        assertEquals(listOf("Christopher Nolan", "Jonathan Nolan"), details.crew.getValue(CrewRole.WRITER).map { it.name })
        assertEquals(listOf("Hans Zimmer"), details.crew.getValue(CrewRole.COMPOSER).map { it.name })
    }

    @Test
    fun theDetailsHaveTheTaglineStatusAndCountries() {
        val details = gson.fromJson(detailsJson, MovieDto::class.java).toDetails()!!
        assertEquals("Mankind was born on Earth. It was never meant to die here.", details.tagline)
        assertEquals("Released", details.status)
        assertEquals(listOf("United States of America", "United Kingdom"), details.productionCountries)
    }

    @Test
    fun aBlankTaglineAndStatusAreNull() {
        val details = MovieDto(id = 1, title = "Quiet", tagline = "  ", status = "").toDetails()!!
        assertNull(details.tagline)
        assertNull(details.status)
        assertTrue(details.productionCountries.isEmpty())
        assertTrue(details.crew.isEmpty())
    }

    // ---------- The person answer ----------

    private val personJson = """
        {
          "id": 525, "name": "  Christopher Nolan ", "biography": "British-American filmmaker.",
          "profile_path": "/nolan.jpg", "known_for_department": "Directing",
          "movie_credits": {
            "cast": [
              { "id": 1, "title": "Quiet cameo", "poster_path": "/c.jpg", "release_date": "2001-01-01", "popularity": 3.0, "character": "Self" }
            ],
            "crew": [
              { "id": 10, "title": "Old one", "poster_path": "/old.jpg", "release_date": "1998-01-01", "popularity": 20.0, "job": "Director" },
              { "id": 20, "title": "Big one", "poster_path": "/big.jpg", "release_date": "2014-11-05", "popularity": 90.0, "job": "Director" },
              { "id": 20, "title": "Big one", "poster_path": "/big.jpg", "release_date": "2014-11-05", "popularity": 90.0, "job": "Producer" },
              { "id": 30, "title": "Same popularity, newer", "poster_path": "/n.jpg", "release_date": "2020-01-01", "popularity": 20.0, "job": "Writer" },
              { "id": 40, "title": "No poster", "poster_path": null, "release_date": "2010-01-01", "popularity": 99.0, "job": "Writer" },
              { "id": 50, "title": "Adult film", "poster_path": "/a.jpg", "popularity": 50.0, "job": "Writer", "adult": true }
            ]
          }
        }
    """.trimIndent()

    private fun person() = gson.fromJson(personJson, PersonDto::class.java).toPersonDetails()!!

    @Test
    fun thePersonHasANameAPhotoABiographyAndWhatTheyAreKnownFor() {
        val person = person()
        assertEquals(525, person.id)
        assertEquals("Christopher Nolan", person.name) // trimmed
        assertEquals("/nolan.jpg", person.profilePath)
        assertEquals("British-American filmmaker.", person.biography)
        assertEquals("Directing", person.knownFor)
    }

    @Test
    fun aPersonsMoviesAreOnePosterEachMostPopularFirstAndNewestFirstBetweenEquals() {
        val crew = person().crew
        assertEquals(listOf(20, 30, 10), crew.map { it.id }) // 90, then 20 (2020) before 20 (1998); no repeat of "Big one"
    }

    @Test
    fun moviesWithoutAPosterOrThatAreAdultNeverShow() {
        val ids = person().crew.map { it.id }
        assertFalse(40 in ids) // no poster
        assertFalse(50 in ids) // adult
    }

    @Test
    fun actingAndCrewAreTwoSeparateLists() {
        val person = person()
        assertEquals(listOf(1), person.acting.map { it.id })
        assertEquals(3, person.crew.size)
    }

    @Test
    fun aPersonWithoutAnIdOrANameIsNotUsable() {
        assertNull(PersonDto(name = "Nobody").toPersonDetails())
        assertNull(PersonDto(id = 1, name = "  ").toPersonDetails())
    }

    @Test
    fun aPersonWithoutMoviesHasEmptyLists() {
        val person = PersonDto(id = 1, name = "New face").toPersonDetails()!!
        assertTrue(person.acting.isEmpty())
        assertTrue(person.crew.isEmpty())
        assertEquals("", person.biography)
        assertNull(person.knownFor)
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

    @Test
    fun adultAndSexuallyExplicitMoviesAreDropped() {
        val adultMovie = MovieDto(id = 10, title = "Adult Title", adult = true, posterPath = "/a.jpg")
        val eroticMovie = MovieDto(id = 11, title = "Erotic Film", overview = "An erotic thriller.", posterPath = "/b.jpg")
        val normalRomance = MovieDto(id = 12, title = "Romantic Evening", overview = "A couple falls in love and marries.", posterPath = "/c.jpg")
        val list = listOf(adultMovie, eroticMovie, normalRomance).toMovies(requirePoster = false)

        assertEquals(1, list.size)
        assertEquals(12, list.first().id)
        assertEquals("Romantic Evening", list.first().title)
    }
}
