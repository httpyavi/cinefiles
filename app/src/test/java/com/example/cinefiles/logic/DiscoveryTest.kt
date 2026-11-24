package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {

    private val comedy = 35
    private val drama = 18
    private val action = 28

    private fun movie(
        id: Int, genres: List<Int> = emptyList(), rating: Double = 7.0, year: Int = 2010,
        votes: Int = 0, language: String? = "en", minutes: Int? = 120
    ) = Movie(
        id = id, title = "Movie $id", posterPath = null, backdropPath = null, voteAverage = rating,
        releaseYear = year, genreIds = genres, voteCount = votes, originalLanguage = language, runtimeMinutes = minutes
    )

    private fun request(
        source: DiscoverySource = DiscoverySource.MOOD_MATCH,
        genres: List<Int> = emptyList(), yearFrom: Int? = null, yearTo: Int? = null, minRating: Double? = null,
        minMinutes: Int? = null, maxMinutes: Int? = null, language: String? = null,
        minVotes: Int? = null, maxVotes: Int? = null
    ) = DiscoveryRequest(source, genres, yearFrom, yearTo, minRating, minMinutes, maxMinutes, language, minVotes, maxVotes)

    // ---------- DiscoveryRequest.matches ----------

    @Test
    fun noLimitsMatchesEverything() {
        assertTrue(request().matches(movie(1)))
    }

    @Test
    fun aMovieNeedsAtLeastOneOfTheGenres() {
        val r = request(genres = listOf(comedy, drama))
        assertTrue(r.matches(movie(1, genres = listOf(action, drama))))
        assertFalse(r.matches(movie(2, genres = listOf(action))))
    }

    @Test
    fun yearAndRatingLimitsAreChecked() {
        val r = request(yearFrom = 2000, yearTo = 2014, minRating = 7.5)
        assertTrue(r.matches(movie(1, year = 2008, rating = 8.0)))
        assertFalse(r.matches(movie(2, year = 1999, rating = 8.0)))
        assertFalse(r.matches(movie(3, year = 2015, rating = 8.0)))
        assertFalse(r.matches(movie(4, year = 2008, rating = 7.4)))
    }

    @Test
    fun aMovieWithNoYearOrRatingCannotMeetALimitOnThem() {
        val unknown = movie(1).copy(voteAverage = null, releaseYear = null)
        assertFalse(request(minRating = 6.0).matches(unknown))
        assertFalse(request(yearFrom = 2000).matches(unknown))
        assertTrue(request().matches(unknown))
    }

    @Test
    fun anUnknownRuntimeOrLanguageIsNotThrownOut() {
        // TMDB's list answers have no runtime: the server applied the limit already
        val r = request(maxMinutes = 119, language = "ko")
        assertTrue(r.matches(movie(1, minutes = null, language = null)))
        assertFalse(r.matches(movie(2, minutes = 150, language = "ko")))
        assertFalse(r.matches(movie(3, minutes = 100, language = "en")))
        assertTrue(r.matches(movie(4, minutes = 100, language = "ko")))
    }

    @Test
    fun filipinoLanguageMatchesTlAndFil() {
        val r = request(language = "tl")
        assertTrue(r.matches(movie(1, language = "tl")))
        assertTrue(r.matches(movie(2, language = "fil")))
        assertFalse(r.matches(movie(3, language = "en")))
        assertFalse(r.matches(movie(4, language = "ja")))
    }

    @Test
    fun hiddenGemsNeedKnownAndSmallVoteCounts() {
        val gem = request(source = DiscoverySource.HIDDEN_GEM, minVotes = 200, maxVotes = 5000)
        assertTrue(gem.matches(movie(1, votes = 1500)))
        assertFalse(gem.matches(movie(2, votes = 100)))      // too few to trust
        assertFalse(gem.matches(movie(3, votes = 30000)))    // everybody knows it
        assertFalse(gem.matches(movie(4, votes = 0)))        // unknown can never be a gem
    }

    // ---------- plan: the looser the search, the fewer the limits ----------

    private val strict = Preferences(
        releasePeriod = ReleasePeriod.FROM_2015, minRating = MinRating.EIGHT_PLUS,
        length = Length.UNDER_2_HOURS, language = Language.KOREAN
    )

    private fun firstRequest(relax: Int) = Discovery.plan(listOf(comedy), strict, relax).first()

    @Test
    fun relaxZeroKeepsEverythingTheUserAskedFor() {
        val r = firstRequest(0)
        assertEquals(DiscoverySource.MOOD_MATCH, r.source)
        assertEquals(listOf(comedy), r.genreIds)
        assertEquals(2015, r.yearFrom)
        assertEquals(8.0, r.minRating)
        assertEquals(119, r.maxMinutes)
        assertEquals("ko", r.language)
    }

    @Test
    fun eachStepLetsGoOfOneMoreThing() {
        assertEquals("ko", firstRequest(1).language)
        assertEquals(null, firstRequest(1).maxMinutes)
        assertEquals(2015, firstRequest(1).yearFrom)
        assertEquals(null, firstRequest(2).yearFrom)
        assertEquals(8.0, firstRequest(2).minRating)
        assertEquals(null, firstRequest(3).minRating)
        assertEquals(listOf(comedy), firstRequest(3).genreIds)
        assertTrue(firstRequest(4).genreIds.isEmpty())
        assertEquals(DiscoverySource.EXPLORE, firstRequest(4).source)
        assertEquals("ko", firstRequest(4).language)
    }

    @Test
    fun theDeckAsksForFiveKindsOfMovie() {
        val kinds = Discovery.plan(listOf(comedy), Preferences(), 0).map { it.source }
        assertEquals(
            listOf(
                DiscoverySource.MOOD_MATCH, DiscoverySource.TOP_RATED, DiscoverySource.HIDDEN_GEM,
                DiscoverySource.TRENDING, DiscoverySource.POPULAR
            ),
            kinds
        )
    }

    @Test
    fun topRatedNeverAsksForLessThanSevenAndAHalf() {
        val top = Discovery.plan(listOf(comedy), Preferences(), 0).first { it.source == DiscoverySource.TOP_RATED }
        assertEquals(7.5, top.minRating)
        // but a user who wants 8+ is respected
        val picky = Discovery.plan(listOf(comedy), Preferences(minRating = MinRating.EIGHT_PLUS), 0)
            .first { it.source == DiscoverySource.TOP_RATED }
        assertEquals(8.0, picky.minRating)
    }

    // ---------- mix ----------

    @Test
    fun mixTakesOneFromEachKindInTurn() {
        val picks = Discovery.mix(
            listOf(
                DiscoverySource.MOOD_MATCH to listOf(movie(1), movie(2)),
                DiscoverySource.TOP_RATED to listOf(movie(3), movie(4)),
                DiscoverySource.TRENDING to listOf(movie(5))
            ),
            exclude = emptySet(), limit = 10
        )
        assertEquals(listOf(1, 3, 5, 2, 4), picks.map { it.movie.id })
        assertEquals(
            listOf(DiscoverySource.MOOD_MATCH, DiscoverySource.TOP_RATED, DiscoverySource.TRENDING,
                DiscoverySource.MOOD_MATCH, DiscoverySource.TOP_RATED),
            picks.map { it.source }
        )
    }

    @Test
    fun aMovieInTwoKindsIsShownOnceUnderTheFirst() {
        val picks = Discovery.mix(
            listOf(
                DiscoverySource.MOOD_MATCH to listOf(movie(1), movie(2)),
                DiscoverySource.POPULAR to listOf(movie(1), movie(3))
            ),
            exclude = emptySet(), limit = 10
        )
        assertEquals(listOf(1, 2, 3), picks.map { it.movie.id }.sorted())
        assertEquals(DiscoverySource.MOOD_MATCH, picks.first { it.movie.id == 1 }.source)
        assertEquals(3, picks.size)
    }

    @Test
    fun excludedMoviesAreSkipped() {
        val picks = Discovery.mix(
            listOf(DiscoverySource.MOOD_MATCH to listOf(movie(1), movie(2), movie(3))),
            exclude = setOf(2), limit = 10
        )
        assertEquals(listOf(1, 3), picks.map { it.movie.id })
    }

    @Test
    fun mixStopsAtTheLimit() {
        val picks = Discovery.mix(
            listOf(DiscoverySource.MOOD_MATCH to (1..10).map { movie(it) }),
            exclude = emptySet(), limit = 4
        )
        assertEquals(4, picks.size)
    }

    // ---------- buildDeck ----------

    /** A "movie source" over a fixed list: answers each question with the movies that match it. */
    private fun sourceOf(movies: List<Movie>): suspend (DiscoveryRequest) -> List<Movie> =
        { request -> movies.filter { request.matches(it) } }

    @Test
    fun aGoodDeckIsNotPaddedWithMoviesThatDoNotFit() = runBlocking {
        val catalog = (1..15).map { movie(it, genres = listOf(comedy)) } + (100..130).map { movie(it, genres = listOf(drama)) }
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), sourceOf(catalog))
        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertTrue(deck.all { comedy in it.movie.genreIds })
    }

    @Test
    fun aNarrowSearchIsLoosenedAndTheLooseOnesComeLast() = runBlocking {
        val catalog = listOf(movie(1, genres = listOf(comedy))) + (100..130).map { movie(it, genres = listOf(drama)) }
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), sourceOf(catalog))

        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertEquals(1, deck.first().movie.id) // what fits the mood comes first
        assertEquals(DiscoverySource.MOOD_MATCH, deck.first().source)
        assertTrue(deck.drop(1).all { it.source == DiscoverySource.EXPLORE || it.source == DiscoverySource.TOP_RATED ||
            it.source == DiscoverySource.TRENDING || it.source == DiscoverySource.POPULAR })
    }

    @Test
    fun preferencesAreRespectedBeforeAnythingIsLoosened() = runBlocking {
        // 14 comedies from 2018 fit; the 2005 comedies do not (the user wants 2015+)
        val catalog = (1..14).map { movie(it, genres = listOf(comedy), year = 2018) } +
            (50..60).map { movie(it, genres = listOf(comedy), year = 2005) }
        val prefs = Preferences(releasePeriod = ReleasePeriod.FROM_2015)
        val deck = Discovery.buildDeck(listOf(comedy), prefs, emptySet(), sourceOf(catalog))
        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertTrue(deck.all { (it.movie.releaseYear ?: 0) >= 2015 })
    }

    @Test
    fun moviesAlreadySwipedAreNeverShownAgain() = runBlocking {
        val catalog = (1..30).map { movie(it, genres = listOf(comedy)) }
        val seen = (1..10).toSet()
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), seen, sourceOf(catalog))
        assertTrue(deck.none { it.movie.id in seen })
        assertEquals(Discovery.DECK_SIZE, deck.size)
    }

    @Test
    fun whenEverythingWasSeenTheDeckIsEmpty() = runBlocking {
        val catalog = (1..5).map { movie(it, genres = listOf(comedy)) }
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), catalog.map { it.id }.toSet(), sourceOf(catalog))
        assertTrue(deck.isEmpty())
    }

    @Test
    fun noMoodMeansAnyGenre() = runBlocking {
        val catalog = (1..30).map { movie(it, genres = listOf(drama)) }
        val deck = Discovery.buildDeck(emptyList(), Preferences(), emptySet(), sourceOf(catalog))
        assertEquals(Discovery.DECK_SIZE, deck.size)
    }
}
