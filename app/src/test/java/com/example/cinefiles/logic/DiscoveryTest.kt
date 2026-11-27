package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {

    private val comedy = 35
    private val drama = 18
    private val action = 28
    private val horror = 27

    private fun movie(
        id: Int, genres: List<Int> = listOf(comedy), rating: Double = 7.0, year: Int = 2010,
        votes: Int = 1000, language: String? = "en", minutes: Int? = 100
    ) = Movie(
        id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null, voteAverage = rating,
        releaseYear = year, genreIds = genres, voteCount = votes, originalLanguage = language, runtimeMinutes = minutes
    )

    private fun request(
        source: DiscoverySource = DiscoverySource.MOOD_MATCH,
        genres: List<Int> = emptyList(), yearFrom: Int? = null, yearTo: Int? = null, minRating: Double? = null,
        minMinutes: Int? = null, maxMinutes: Int? = null, language: String? = null,
        minVotes: Int? = null, maxVotes: Int? = null, withoutGenres: List<Int> = emptyList()
    ) = DiscoveryRequest(source, genres, yearFrom, yearTo, minRating, minMinutes, maxMinutes, language, minVotes, maxVotes, withoutGenres)

    /**
     * A pretend TMDB over a fixed catalog that answers in PAGES, the way the real one does. Like TMDB's list answers, the
     * movies it sends have NO runtime (TMDB applied the length on its side), and /discover leaves out movies with fewer
     * votes than the default limit. Every question asked is written to [asked].
     */
    private class PretendTmdb(private val catalog: List<Movie>, private val pageSize: Int = 20) {
        val asked = ArrayList<Pair<DiscoveryRequest, Int>>()

        val fetch: suspend (DiscoveryRequest, Int) -> List<Movie> = { request, page ->
            synchronized(asked) { asked += request to page }
            val asTmdb = if (request.usesOwnList) request else request.copy(minVotes = request.minVotes ?: Discovery.defaultMinVotes(request.language))
            catalog.filter { asTmdb.matches(it) }
                .drop((page - 1) * pageSize).take(pageSize)
                .map { it.copy(runtimeMinutes = null) }
        }

        /** TMDB's details: the real runtime of a movie (a movie whose runtime TMDB does not know is left out). */
        val lookUpRuntimes: suspend (List<Movie>) -> List<Movie> = { movies ->
            movies.mapNotNull { movie -> catalog.first { it.id == movie.id }.runtimeMinutes?.let { movie.copy(runtimeMinutes = it) } }
        }

        /** The pages asked for exactly this question, in order. */
        fun pagesOf(request: DiscoveryRequest) = asked.filter { it.first == request }.map { it.second }
    }

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
    fun theLanguageIsStrictAndAnUnknownLanguageNeverPasses() {
        val korean = request(language = "ko")
        assertTrue(korean.matches(movie(1, language = "ko")))
        assertFalse(korean.matches(movie(2, language = "en")))
        assertFalse(korean.matches(movie(3, language = null)))
        assertTrue(request().matches(movie(4, language = null))) // no language chosen: anything goes
    }

    @Test
    fun aShortFilmIsNeverAQuickWatch() {
        val quick = Discovery.userLimits(listOf(comedy), Preferences(length = Length.UNDER_90_MIN))
        assertFalse(quick.matches(movie(1, minutes = 5)))
        assertTrue(quick.matches(movie(2, minutes = 85)))
    }

    @Test
    fun aKnownRuntimeIsCheckedAndAnUnknownOneMustBeLookedUp() {
        val short = request(maxMinutes = 89)
        assertTrue(short.matches(movie(1, minutes = 85)))
        assertFalse(short.matches(movie(2, minutes = 95)))
        // TMDB's list answers have no runtime: it passes matches, but never matchesStrictly (what a SHOWN movie must pass)
        assertTrue(short.matches(movie(3, minutes = null)))
        assertFalse(short.matchesStrictly(movie(3, minutes = null)))
        assertTrue(short.matchesStrictly(movie(1, minutes = 85)))
        assertTrue(request().matchesStrictly(movie(4, minutes = null))) // no length chosen
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

    // ---------- plan: every question carries every choice of the user ----------

    private val strict = Preferences(
        releasePeriod = ReleasePeriod.FROM_2015, minRating = MinRating.EIGHT_PLUS,
        length = Length.UNDER_90_MIN, language = "ko"
    )

    @Test
    fun theUserLimitsAreTheMoodGenresAndEveryPreference() {
        val r = Discovery.userLimits(listOf(comedy), strict)
        assertEquals(DiscoverySource.MOOD_MATCH, r.source)
        assertEquals(listOf(comedy), r.genreIds)
        assertEquals(2015, r.yearFrom)
        assertEquals(8.0, r.minRating)
        assertEquals(89, r.maxMinutes)
        assertEquals("ko", r.language)
    }

    @Test
    fun everyQuestionKeepsEveryLimitAndIsNeverLooser() {
        for (r in Discovery.levels(listOf(comedy, action), strict).flatten()) {
            assertEquals(listOf(comedy, action), r.genreIds)
            assertEquals(2015, r.yearFrom)
            assertTrue((r.minRating ?: 0.0) >= 8.0)
            assertEquals(89, r.maxMinutes)
            assertEquals("ko", r.language)
        }
    }

    @Test
    fun theDeckAsksForFiveKindsOfMovieWhenTmdbsOwnListsCanBeUsed() {
        val kinds = Discovery.plan(listOf(comedy), Preferences()).map { it.source }
        assertEquals(
            listOf(
                DiscoverySource.MOOD_MATCH, DiscoverySource.TOP_RATED, DiscoverySource.HIDDEN_GEM,
                DiscoverySource.TRENDING, DiscoverySource.POPULAR
            ),
            kinds
        )
    }

    @Test
    fun withALanguageOrALengthTrendingAndPopularAreNotAskedTwice() {
        // They would be the very same /discover question as the mood match and only repeat its movies
        for (prefs in listOf(Preferences(language = "ko"), Preferences(length = Length.UNDER_2_HOURS))) {
            val kinds = Discovery.plan(listOf(comedy), prefs).map { it.source }
            assertEquals(listOf(DiscoverySource.MOOD_MATCH, DiscoverySource.TOP_RATED, DiscoverySource.HIDDEN_GEM), kinds)
        }
        // A year or a rating can be checked on TMDB's own lists: they stay
        assertEquals(5, Discovery.plan(listOf(comedy), Preferences(minRating = MinRating.SEVEN_PLUS)).size)
    }

    @Test
    fun topRatedNeverAsksForLessThanSevenAndAHalf() {
        val top = Discovery.plan(listOf(comedy), Preferences()).first { it.source == DiscoverySource.TOP_RATED }
        assertEquals(7.5, top.minRating)
        // but a user who wants 8+ is respected
        val picky = Discovery.plan(listOf(comedy), Preferences(minRating = MinRating.EIGHT_PLUS))
            .first { it.source == DiscoverySource.TOP_RATED }
        assertEquals(8.0, picky.minRating)
    }

    // ---------- fewer votes: the ONLY thing that is ever lowered ----------

    @Test
    fun fewerVotesChangesOnlyTheVoteLimitsAndNeverAsksForTheSameMoviesAgain() {
        val base = Discovery.plan(listOf(comedy), strict).first()
        val lower = Discovery.withFewerVotes(base, 100)!!
        assertEquals(base.copy(minVotes = 100, maxVotes = 199), lower) // 200 and up was asked already
        val lowest = Discovery.withFewerVotes(lower, 30)!!
        assertEquals(base.copy(minVotes = 30, maxVotes = 99), lowest)
    }

    @Test
    fun theLevelsGoFromTwoHundredVotesToOneHundredToThirty() {
        val levels = Discovery.levels(listOf(comedy), Preferences(language = "ko"))
        assertEquals(3, levels.size)
        assertEquals(listOf(100, 30), levels.drop(1).map { level -> level.first().minVotes })
        // TMDB's own lists have no vote limit, so they are not asked again
        val noLimits = Discovery.levels(listOf(comedy), Preferences())
        assertTrue(noLimits.drop(1).flatten().none { it.source == DiscoverySource.TRENDING || it.source == DiscoverySource.POPULAR })
    }

    @Test
    fun filipinoMoviesAlreadyHaveATinyVoteLimitSoNothingIsLowered() {
        assertEquals(1, Discovery.levels(listOf(drama), Preferences(language = Languages.FILIPINO)).size)
        assertNull(Discovery.withFewerVotes(Discovery.plan(listOf(drama), Preferences(language = "tl")).first(), 30))
    }

    // ---------- the batch: exactly 10 cards, page after page ----------

    @Test
    fun aBatchHasTenCardsWhenTenFit() = runBlocking {
        val tmdb = PretendTmdb((1..40).map { movie(it) } + (100..130).map { movie(it, genres = listOf(drama)) })
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), tmdb.fetch)
        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertTrue(deck.all { comedy in it.movie.genreIds })
    }

    @Test
    fun aBatchKeepsAskingForTheNextPagesWhenTheFirstOnesWereAlreadySeen() = runBlocking {
        // Korean comedies with 10,000 votes: only the mood match finds them (too well known for a hidden gem, under 7.5).
        // The first 45 were swiped already, so its pages 1 and 2 bring nothing new.
        val tmdb = PretendTmdb((1..100).map { movie(it, language = "ko", votes = 10_000) })
        val seen = (1..45).toSet()

        val deck = Discovery.buildDeck(listOf(comedy), Preferences(language = "ko"), seen, tmdb.fetch)

        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertEquals((46..55).toList(), deck.map { it.movie.id })
        assertEquals(listOf(1, 2, 3), tmdb.pagesOf(Discovery.plan(listOf(comedy), Preferences(language = "ko")).first()))
    }

    @Test
    fun aBatchAsksAtMostFivePagesPerQuestionAndTheNextBatchGoesOnFromThere() = runBlocking {
        val tmdb = PretendTmdb((1..200).map { movie(it, language = "ko", votes = 10_000) })
        val feed = DiscoveryFeed(listOf(comedy), Preferences(language = "ko"))
        val seen = (1..100).toMutableSet() // every movie of pages 1 to 5

        val first = feed.next(seen, tmdb.fetch)
        assertTrue(first.isEmpty())
        assertEquals((1..5).toList(), tmdb.pagesOf(Discovery.plan(listOf(comedy), Preferences(language = "ko")).first()))

        val second = feed.next(seen, tmdb.fetch) // "Discover 10 more": page 6 onwards
        assertEquals((101..110).toList(), second.map { it.movie.id })
    }

    @Test
    fun discoverTenMoreBringsTheNextTenUnseenMoviesWithTheSameLimits() = runBlocking {
        val prefs = Preferences(releasePeriod = ReleasePeriod.FROM_2015, minRating = MinRating.SEVEN_PLUS, language = "ko")
        val fits = (1..60).map { movie(it, rating = 7.6, year = 2018, language = "ko") }
        val doesNot = (100..160).map { movie(it, rating = 7.6, year = 2005, language = "ko") } +
            (200..260).map { movie(it, rating = 7.6, year = 2018, language = "en") }
        val tmdb = PretendTmdb(fits + doesNot)
        val feed = DiscoveryFeed(listOf(comedy), prefs)
        val seen = HashSet<Int>()

        repeat(3) { round ->
            val batch = feed.next(seen.toSet(), tmdb.fetch)
            assertEquals("batch ${round + 1}", Discovery.DECK_SIZE, batch.size)
            assertTrue("batch ${round + 1} repeated a movie", batch.none { it.movie.id in seen })
            assertTrue(batch.all { feed.limits.matchesStrictly(it.movie) })
            seen += batch.map { it.movie.id }
        }
    }

    @Test
    fun aNarrowMixLowersOnlyTheVoteLimitToFillTheBatch() = runBlocking {
        // Korean comedies rated 8+ from 2015 on: 4 well known, 4 with 150 votes, 4 with 50 votes, 4 with 10 votes
        val prefs = Preferences(releasePeriod = ReleasePeriod.FROM_2015, minRating = MinRating.EIGHT_PLUS, language = "ko")
        fun korean(id: Int, votes: Int) = movie(id, rating = 8.3, year = 2019, language = "ko", votes = votes)
        val fits = (1..4).map { korean(it, 900) } + (11..14).map { korean(it, 150) } +
            (21..24).map { korean(it, 50) } + (31..34).map { korean(it, 10) }
        val violators = listOf(
            movie(90, rating = 6.4, year = 2019, language = "ko", votes = 900),            // rating
            movie(91, rating = 8.3, year = 2010, language = "ko", votes = 900),            // year
            movie(92, rating = 8.3, year = 2019, language = "en", votes = 900),            // language
            movie(93, rating = 8.3, year = 2019, language = null, votes = 900),            // unknown language
            movie(94, genres = listOf(horror), rating = 8.3, year = 2019, language = "ko") // genre
        )
        val tmdb = PretendTmdb(fits + violators)

        val deck = Discovery.buildDeck(listOf(comedy), prefs, emptySet(), tmdb.fetch)

        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertTrue(deck.all { it.movie.id in fits.map { m -> m.id } })
        assertTrue(deck.none { it.movie.voteCount < 30 })       // 30 votes is the lowest limit
        assertTrue(tmdb.asked.map { it.first.minVotes }.containsAll(listOf(100, 30)))
        // Every question asked kept every preference
        assertTrue(tmdb.asked.all { (r, _) -> r.language == "ko" && r.yearFrom == 2015 && (r.minRating ?: 0.0) >= 8.0 && r.genreIds == listOf(comedy) })
    }

    @Test
    fun whenTooFewFitTheExactMatchesComeFirstAndTheClosestFillTheBatch() = runBlocking {
        val tmdb = PretendTmdb(
            listOf(movie(1, language = "ko", rating = 8.5, year = 2020)) +
                (100..160).map { movie(it, language = "en", rating = 8.5, year = 2020) } +   // close, but another language
                (200..260).map { movie(it, language = "ko", rating = 6.0, year = 2020) }     // closer: only the rating is off
        )
        val prefs = Preferences(minRating = MinRating.EIGHT_PLUS, language = "ko")

        val deck = Discovery.buildDeck(listOf(comedy), prefs, emptySet(), tmdb.fetch)

        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertEquals(1, deck.first().movie.id)
        assertEquals(DiscoverySource.MOOD_MATCH, deck.first().source)
        assertTrue(deck.drop(1).all { it.source == DiscoverySource.CLOSE_MATCH })
        assertTrue(deck.drop(1).all { it.movie.id in 200..260 }) // the nearest come first: the language was kept
    }

    @Test
    fun aMixWithNoExactMatchStillGetsTheClosestMoviesNearestFirst() = runBlocking {
        // The phone's case: Happy, Korean, 8+, under 90 minutes, 2015 on. TMDB has no such movie at all.
        val prefs = Preferences(ReleasePeriod.FROM_2015, MinRating.EIGHT_PLUS, Length.UNDER_90_MIN, "ko")
        val nearest = (1..4).map { movie(it, rating = 7.6, year = 2018, language = "ko", minutes = 100) }   // step 1
        val further = (11..13).map { movie(it, rating = 6.2, year = 2005, language = "ko", minutes = 150) } // step 3
        val otherLanguage = (21..30).map { movie(it, rating = 8.5, year = 2019, language = "en", minutes = 85) } // step 4
        val horrorComedy = movie(99, genres = listOf(comedy, horror), rating = 8.6, year = 2019, language = "ko", minutes = 85)
        val tmdb = PretendTmdb(nearest + further + otherLanguage + horrorComedy)

        val deck = Discovery.buildDeck(
            listOf(comedy), prefs, emptySet(), tmdb.fetch, tmdb.lookUpRuntimes,
            avoidGenreIds = Moods.avoidedGenreIdsFor(listOf("happy"))
        )

        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertTrue(deck.all { it.source == DiscoverySource.CLOSE_MATCH })
        assertEquals((1..4).toSet(), deck.take(4).map { it.movie.id }.toSet())
        assertEquals((11..13).toSet(), deck.drop(4).take(3).map { it.movie.id }.toSet())
        assertTrue(deck.drop(7).all { it.movie.id in 21..30 })
        assertTrue(deck.none { it.movie.id == 99 }) // never a horror film for Happy
    }

    @Test
    fun aChosenLengthIsCheckedOnTheRealRuntimeOfEveryCard() = runBlocking {
        // TMDB "forgot" the length: its pages hold long movies and movies with an unknown runtime too
        val short = (1..12).map { movie(it, minutes = 80) }
        val long = (20..40).map { movie(it, minutes = 140) }
        val unknown = (50..60).map { movie(it, minutes = null) }
        val catalog = (long + unknown + short).sortedBy { it.id % 7 } // mixed together
        val leaky = PretendTmdb(catalog)
        val ignoringLength: suspend (DiscoveryRequest, Int) -> List<Movie> =
            { request, page -> leaky.fetch(request.copy(maxMinutes = null), page) }

        val deck = Discovery.buildDeck(
            listOf(comedy), Preferences(length = Length.UNDER_90_MIN), emptySet(), ignoringLength, leaky.lookUpRuntimes
        )

        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertTrue(deck.all { (it.movie.runtimeMinutes ?: Int.MAX_VALUE) <= 89 })
    }

    @Test
    fun withoutALengthNoRuntimeIsLookedUp() = runBlocking {
        val tmdb = PretendTmdb((1..30).map { movie(it) })
        var lookedUp = 0
        Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), tmdb.fetch, lookUpRuntimes = { lookedUp += it.size; it })
        assertEquals(0, lookedUp)
    }

    @Test
    fun everyCardFitsTheMoodsAndEveryPreference() = runBlocking {
        // A catalog with every kind of mismatch: only the cards that fit everything may be dealt
        val catalog = (1..400).map { id ->
            movie(
                id,
                genres = listOf(listOf(comedy), listOf(drama), listOf(horror), listOf(action, comedy))[id % 4],
                rating = 5.5 + (id % 8) * 0.5,
                year = 1990 + id % 35,
                votes = listOf(20, 60, 150, 900, 12_000)[id % 5],
                language = listOf("ko", "en", null, "ko", "ja")[id % 5],
                minutes = listOf(80, 95, 130, null)[id % 4]
            )
        }
        val tmdb = PretendTmdb(catalog)
        val prefs = Preferences(ReleasePeriod.FROM_2015, MinRating.SEVEN_PLUS, Length.UNDER_2_HOURS, "ko")
        val limits = Discovery.userLimits(listOf(comedy, action), prefs)
        val feed = DiscoveryFeed(listOf(comedy, action), prefs)
        val seen = HashSet<Int>()

        repeat(4) { round ->
            val batch = feed.next(seen.toSet(), tmdb.fetch, tmdb.lookUpRuntimes)
            assertEquals("batch ${round + 1}", Discovery.DECK_SIZE, batch.size)
            // Every exact card fits everything; the rest say "Close match" and come after them
            val exactCount = batch.indexOfFirst { it.source == DiscoverySource.CLOSE_MATCH }.let { if (it < 0) batch.size else it }
            assertTrue(batch.take(exactCount).all { limits.matchesStrictly(it.movie) })
            assertTrue(batch.drop(exactCount).all { it.source == DiscoverySource.CLOSE_MATCH && !limits.matchesStrictly(it.movie) })
            // ...and even a close match keeps the moods
            assertTrue(batch.all { card -> card.movie.genreIds.any { it == comedy || it == action } })
            seen += batch.map { it.movie.id }
        }
    }

    // ---------- close matches: what is let go, step by step ----------

    @Test
    fun closeMatchesLetGoALittleMoreAtEachStepAndKeepTheMoodsToTheEnd() {
        val prefs = Preferences(ReleasePeriod.FROM_2015, MinRating.EIGHT_PLUS, Length.UNDER_90_MIN, "ko")
        val close = Discovery.closeLevels(Discovery.userLimits(listOf(comedy), prefs, listOf(horror)))

        assertEquals(4, close.size)
        assertEquals(listOf(7.0, 6.0, null, null), close.map { it.minRating })
        assertEquals(listOf(109, 129, null, null), close.map { it.maxMinutes })
        assertEquals(listOf(2010, 2000, null, null), close.map { it.yearFrom })
        assertEquals(listOf("ko", "ko", "ko", null), close.map { it.language })
        assertTrue(close.all { it.genreIds == listOf(comedy) && it.withoutGenreIds == listOf(horror) })
        assertTrue(close.all { it.source == DiscoverySource.CLOSE_MATCH && it.minVotes == 30 })
    }

    @Test
    fun closeMatchesWidenEveryKindOfChoice() {
        val long = Discovery.closeLevels(
            Discovery.userLimits(listOf(drama), Preferences(ReleasePeriod.BEFORE_2000, MinRating.SIX_PLUS, Length.OVER_2_HOURS))
        )
        assertEquals(listOf(100, 80, null), long.map { it.minMinutes })
        // a quick watch keeps out short films at every step
        val quick = Discovery.closeLevels(Discovery.userLimits(listOf(comedy), Preferences(length = Length.UNDER_90_MIN)))
        assertEquals(listOf(40, 40, null), quick.map { it.minMinutes }) // the last step lets go of the length altogether
        assertEquals(listOf(2004, 2014, null), long.map { it.yearTo })
        assertEquals(listOf(5.0, null, null), long.map { it.minRating }) // never under 5
    }

    @Test
    fun withNoPreferencesThereIsNothingCloserToLookFor() {
        assertTrue(Discovery.closeLevels(Discovery.userLimits(listOf(comedy), Preferences())).isEmpty())
        // Only a language: the one close step is "any language"
        val languageOnly = Discovery.closeLevels(Discovery.userLimits(listOf(comedy), Preferences(language = "ko")))
        assertEquals(listOf<String?>(null), languageOnly.map { it.language })
    }

    // ---------- genres a mood avoids ----------

    @Test
    fun aMoodAvoidsTheGenresThatClashWithItUnlessAnotherChosenMoodWantsThem() {
        assertEquals(listOf(horror), Moods.avoidedGenreIdsFor(listOf("happy")))
        assertTrue(Moods.avoidedGenreIdsFor(listOf("happy", "scared")).isEmpty()) // Scared asks for horror
        assertEquals(listOf(horror, 53, 10752), Moods.avoidedGenreIdsFor(listOf("relaxed")))
        assertEquals(listOf(horror, 10752), Moods.avoidedGenreIdsFor(listOf("relaxed", "angry"))) // Angry asks for thrillers
        assertTrue(Moods.avoidedGenreIdsFor(listOf("excited")).isEmpty())
    }

    @Test
    fun aHorrorComedyIsNeverAHappyCard() = runBlocking {
        val tmdb = PretendTmdb(listOf(movie(1, genres = listOf(horror, comedy))) + (2..20).map { movie(it) })
        val happy = Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), tmdb.fetch, avoidGenreIds = Moods.avoidedGenreIdsFor(listOf("happy")))
        assertTrue(happy.none { it.movie.id == 1 })
        assertFalse(request(withoutGenres = listOf(horror)).matches(movie(1, genres = listOf(horror, comedy))))
    }

    // ---------- the mix ----------

    /** Every kind of question answers with its own two movies on page 1, and nothing after. */
    private val twoOfEachKind: suspend (DiscoveryRequest, Int) -> List<Movie> = { request, page ->
        val base = (request.source.ordinal + 1) * 100
        if (page == 1) listOf(movie(base + 1, rating = 8.0), movie(base + 2, rating = 8.0)) else emptyList()
    }

    @Test
    fun theMixTakesOneFromEachKindInTurn() = runBlocking {
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), twoOfEachKind)
        assertEquals(listOf(101, 201, 301, 401, 501, 102, 202, 302, 402, 502), deck.map { it.movie.id })
        assertEquals(
            listOf(
                DiscoverySource.MOOD_MATCH, DiscoverySource.TOP_RATED, DiscoverySource.HIDDEN_GEM,
                DiscoverySource.TRENDING, DiscoverySource.POPULAR
            ),
            deck.take(5).map { it.source }
        )
    }

    @Test
    fun aMovieFoundByTwoKindsIsShownOnceUnderTheFirst() = runBlocking {
        val same: suspend (DiscoveryRequest, Int) -> List<Movie> = { request, page ->
            when {
                page > 1 -> emptyList()
                request.source == DiscoverySource.MOOD_MATCH -> listOf(movie(1), movie(2))
                request.source == DiscoverySource.POPULAR -> listOf(movie(1), movie(3))
                else -> emptyList()
            }
        }
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), same)
        assertEquals(listOf(1, 2, 3), deck.map { it.movie.id }.sorted())
        assertEquals(DiscoverySource.MOOD_MATCH, deck.first { it.movie.id == 1 }.source)
    }

    @Test
    fun theBatchStopsAtTheLimit() = runBlocking {
        val tmdb = PretendTmdb((1..60).map { movie(it) })
        assertEquals(4, Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), tmdb.fetch, limit = 4).size)
    }

    @Test
    fun moviesAlreadySwipedOrSavedAreNeverShownAgain() = runBlocking {
        val tmdb = PretendTmdb((1..30).map { movie(it) })
        val seen = (1..10).toSet()
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), seen, tmdb.fetch)
        assertTrue(deck.none { it.movie.id in seen })
        assertEquals(Discovery.DECK_SIZE, deck.size)
    }

    @Test
    fun whenEverythingWasSeenTheDeckIsEmpty() = runBlocking {
        val catalog = (1..5).map { movie(it) }
        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), catalog.map { it.id }.toSet(), PretendTmdb(catalog).fetch)
        assertTrue(deck.isEmpty())
    }

    @Test
    fun whenNothingFitsAtAllTheClosestMoviesForTheMoodAreDealt() = runBlocking {
        val catalog = (1..20).map { movie(it, language = "en", year = 2005) }
        val strict = Preferences(language = "ko", releasePeriod = ReleasePeriod.FROM_2015)
        val deck = Discovery.buildDeck(listOf(comedy), strict, emptySet(), PretendTmdb(catalog).fetch)
        assertEquals(Discovery.DECK_SIZE, deck.size)
        assertTrue(deck.all { it.source == DiscoverySource.CLOSE_MATCH && comedy in it.movie.genreIds })
    }

    @Test
    fun onlyAMoodWithNothingLeftOnTmdbGivesAnEmptyDeck() = runBlocking {
        val catalog = (1..20).map { movie(it, genres = listOf(drama), language = "ko") }
        assertTrue(Discovery.buildDeck(listOf(comedy), Preferences(language = "ko"), emptySet(), PretendTmdb(catalog).fetch).isEmpty())
    }

    @Test
    fun noMoodMeansAnyGenre() = runBlocking {
        val tmdb = PretendTmdb((1..30).map { movie(it, genres = listOf(drama)) })
        assertEquals(Discovery.DECK_SIZE, Discovery.buildDeck(emptyList(), Preferences(), emptySet(), tmdb.fetch).size)
    }
}
