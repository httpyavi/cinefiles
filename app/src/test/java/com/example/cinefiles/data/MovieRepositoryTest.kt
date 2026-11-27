package com.example.cinefiles.data

import com.example.cinefiles.logic.BrowseCategory
import com.example.cinefiles.logic.ContentFilter
import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.MinRating
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.ReleasePeriod
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The REAL network stack (Retrofit + OkHttp + Gson + MovieRepository) talking to a pretend TMDB that runs on
 * this computer (a tiny web server made from a plain ServerSocket, no extra library). It checks what we send (address, parameters,
 * the token header) and what we do with the answers and the errors, without needing internet or a TMDB key.
 */
class MovieRepositoryTest {

    private class Recorded(val path: String, rawQuery: String, val authorization: String?) {
        /** The query as name -> value, decoded ("with_genres=35%7C28" gives "with_genres" -> "35|28"). */
        val params: Map<String, String> = rawQuery.split("&").filter { it.isNotEmpty() }.associate {
            val (name, value) = it.split("=", limit = 2).let { parts -> parts[0] to parts.getOrElse(1) { "" } }
            URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
    }

    /**
     * A very small web server: it answers every request with [status] and [body], and writes down what was asked.
     * Each request gets its own connection ("Connection: close"), so one loop that handles them one by one is enough.
     */
    private class PretendTmdbServer {
        val requests = CopyOnWriteArrayList<Recorded>()
        @Volatile var status = 200
        @Volatile var body = "{}"

        /** When set, it decides the answer of every request (a test can answer differently to different questions). */
        @Volatile var respond: ((Recorded) -> String)? = null

        private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val baseUrl = "http://127.0.0.1:${socket.localPort}/3/"

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    try {
                        socket.accept().use { handle(it) }
                    } catch (e: IOException) {
                        // the server was stopped (or a client hung up): nothing to do
                    }
                }
            }
        }

        private fun handle(client: Socket) {
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return // for example: GET /3/trending/movie/week?page=1 HTTP/1.1
            var authorization: String? = null
            while (true) {
                val header = reader.readLine()
                if (header.isNullOrEmpty()) break // an empty line ends the headers
                if (header.startsWith("Authorization:", ignoreCase = true)) authorization = header.substringAfter(":").trim()
            }
            val target = requestLine.split(" ")[1]
            val recorded = Recorded(target.substringBefore("?"), target.substringAfter("?", ""), authorization)
            requests += recorded

            val bytes = (respond?.invoke(recorded) ?: body).toByteArray()
            val head = "HTTP/1.1 $status Pretend\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            client.getOutputStream().apply {
                write(head.toByteArray())
                write(bytes)
                flush()
            }
        }

        fun stop() = socket.close()
    }

    private lateinit var server: PretendTmdbServer
    private val requests get() = server.requests
    private var status: Int
        get() = server.status
        set(value) { server.status = value }
    private var body: String
        get() = server.body
        set(value) { server.body = value }

    private val baseUrl get() = server.baseUrl

    private fun repository(token: String = "test-token", hasToken: Boolean = true, url: String = baseUrl) =
        MovieRepository(TmdbApi.create(token, url), hasToken, dao, diaryDao)

    // A fake database: these tests are about the internet part
    private val dao = FakeMovieDao()
    private val diaryDao = FakeDiaryDao()

    @Before
    fun startPretendTmdb() {
        server = PretendTmdbServer()
    }

    @After
    fun stopPretendTmdb() = server.stop()

    private val listJson = """
        { "page": 1, "total_pages": 3, "results": [
          { "id": 1, "title": "Funny", "poster_path": "/f.jpg", "genre_ids": [35], "release_date": "2016-05-01",
            "vote_average": 7.5, "vote_count": 900, "original_language": "en" },
          { "id": 2, "title": "Serious", "poster_path": "/s.jpg", "genre_ids": [18], "release_date": "2016-06-01",
            "vote_average": 8.0, "vote_count": 1200, "original_language": "en" },
          { "id": 3, "title": "No poster", "poster_path": null, "genre_ids": [35], "vote_count": 5 }
        ] }
    """.trimIndent()

    // ---------- What we send ----------

    @Test
    fun trendingAsksTheRightAddressWithTheBearerToken() = runBlocking {
        body = listJson
        val result = repository(token = "secret-token").trending(page = 2)

        val request = requests.single()
        assertEquals("/3/trending/movie/week", request.path)
        assertEquals("Bearer secret-token", request.authorization)
        assertEquals("2", request.params["page"])
        assertEquals("en-US", request.params["language"])
        assertTrue(result.isSuccess)
    }

    @Test
    fun popularUsesItsOwnAddress() = runBlocking {
        body = listJson
        repository().popular()
        assertEquals(listOf("/3/movie/popular"), requests.map { it.path })
    }

    @Test
    fun discoverSendsTheMoodAndPreferencesAsTmdbParameters() = runBlocking {
        body = listJson
        val request = DiscoveryRequest(
            source = DiscoverySource.MOOD_MATCH, genreIds = listOf(35, 28), yearFrom = 2015, minRating = 8.0,
            maxMinutes = 119, language = "ko"
        )
        repository().discover(request, page = 3)

        val sent = requests.single()
        assertEquals("/3/discover/movie", sent.path)
        assertEquals("35|28", sent.params["with_genres"])
        assertEquals("2015-01-01", sent.params["primary_release_date.gte"])
        assertEquals("8.0", sent.params["vote_average.gte"])
        assertEquals("200", sent.params["vote_count.gte"])
        assertEquals("119", sent.params["with_runtime.lte"])
        assertEquals("ko", sent.params["with_original_language"])
        assertEquals("popularity.desc", sent.params["sort_by"])
        assertEquals("3", sent.params["page"])
        assertEquals("en-US", sent.params["language"])
        assertEquals("false", sent.params["include_adult"])
        assertEquals(ContentFilter.EXCLUDED_COMPANIES, sent.params["without_companies"])
        assertEquals(ContentFilter.EXCLUDED_KEYWORDS, sent.params["without_keywords"])
        // Things the user did not choose are not sent at all
        assertNull(sent.params["primary_release_date.lte"])
        assertNull(sent.params["with_runtime.gte"])
        assertNull(sent.params["vote_count.lte"])
        assertNull(sent.params["without_genres"])
    }

    @Test
    fun genresAMoodAvoidsAreSentAsWithoutGenres() = runBlocking {
        body = listJson
        repository().discover(DiscoveryRequest(DiscoverySource.MOOD_MATCH, genreIds = listOf(16, 10751), withoutGenreIds = listOf(27, 53)))
        assertEquals("27,53", requests.single().params["without_genres"])
        assertEquals("16|10751", requests.single().params["with_genres"])
    }

    @Test
    fun hiddenGemsAlsoSendTheMaximumNumberOfVotes() = runBlocking {
        body = listJson
        repository().discover(DiscoveryRequest(DiscoverySource.HIDDEN_GEM, minRating = 7.0, minVotes = 200, maxVotes = 5000))
        val sent = requests.single()
        assertEquals("5000", sent.params["vote_count.lte"])
        assertEquals("vote_average.desc", sent.params["sort_by"])
    }

    @Test
    fun detailsAskForCreditsAndVideosToo() = runBlocking {
        body = """{ "id": 157336, "title": "Interstellar", "poster_path": "/p.jpg", "runtime": 169 }"""
        val result = repository().movieDetails(157336)

        val sent = requests.single()
        assertEquals("/3/movie/157336", sent.path)
        assertEquals("credits,videos", sent.params["append_to_response"])
        assertEquals(169, result.getOrThrow().movie.runtimeMinutes)
    }

    @Test
    fun detailsRefreshTheSavedCopyOfTheMovie() = runBlocking {
        // So that Details can show the runtime offline next time (a no-op in the database if the movie is not saved)
        body = """{ "id": 157336, "title": "Interstellar", "poster_path": "/p.jpg", "runtime": 169, "overview": "Space." }"""
        repository().movieDetails(157336)
        assertEquals(listOf(Triple(157336, "Space.", 169)), dao.infoUpdates)
    }

    @Test
    fun moviesFromListsAreRememberedForDetails() = runBlocking {
        val repo = repository()
        assertNull(repo.recentlySeen(1))
        body = listJson
        repo.trending()
        assertEquals("Funny", repo.recentlySeen(1)?.title)
        assertNull(repo.recentlySeen(3)) // the one without a poster was dropped, so it is not remembered either
    }

    @Test
    fun similarUsesItsOwnAddress() = runBlocking {
        body = listJson
        repository().similar(27205)
        assertEquals(listOf("/3/movie/27205/similar"), requests.map { it.path })
    }

    @Test
    fun aSearchPageTrimsTheTextItSends() = runBlocking {
        body = listJson
        repository().searchPage("  blade runner ")
        assertEquals("/3/search/movie", requests.single().path)
        assertEquals("blade runner", requests.single().params["query"])
    }

    @Test
    fun pagesBeyondFiveHundredAreNotAsked() = runBlocking {
        val result = repository().discover(DiscoveryRequest(DiscoverySource.MOOD_MATCH), page = 501)
        assertTrue(result.getOrThrow().isEmpty())
        assertTrue(requests.isEmpty())
    }

    // ---------- What we do with the answers ----------

    @Test
    fun answersBecomeMoviesWithPostersOnly() = runBlocking {
        body = listJson
        val movies = repository().trending().getOrThrow()
        assertEquals(listOf(1, 2), movies.map { it.id }) // "No poster" is left out
        assertEquals("Funny", movies.first().title)
        assertEquals(2016, movies.first().releaseYear)
    }

    @Test
    fun trendingForTheDeckIsTmdbsOwnListPassedOnAsItCame() = runBlocking {
        // TMDB's trending list cannot be filtered by the server: the deck (DiscoveryFeed) checks the mood itself, and an
        // empty page must mean "the last page", so nothing is taken out here
        body = listJson
        val comedyOnly = DiscoveryRequest(DiscoverySource.TRENDING, genreIds = listOf(35))
        val movies = repository().moviesFor(comedyOnly).getOrThrow()
        assertEquals(listOf(1, 2), movies.map { it.id })
        assertEquals("/3/trending/movie/week", requests.single().path)
    }

    @Test
    fun trendingWithALanguageOrALengthIsAskedOfDiscover() = runBlocking {
        body = listJson
        repository().moviesFor(DiscoveryRequest(DiscoverySource.TRENDING, language = "ko"), page = 2).getOrThrow()
        repository().moviesFor(DiscoveryRequest(DiscoverySource.POPULAR, maxMinutes = 89)).getOrThrow()
        assertEquals(listOf("/3/discover/movie", "/3/discover/movie"), requests.map { it.path })
        assertEquals("ko", requests[0].params["with_original_language"])
        assertEquals("2", requests[0].params["page"])
        assertEquals("89", requests[1].params["with_runtime.lte"])
    }

    // ---------- Runtimes (a chosen length is checked on the real runtime) ----------

    private fun detailsJson(id: Int, runtime: Int?) =
        """{ "id": $id, "title": "Movie $id", "poster_path": "/p$id.jpg", "runtime": ${runtime ?: "null"} }"""

    @Test
    fun runtimesAreLookedUpOnceAndAMovieWithoutOneIsLeftOut() = runBlocking {
        val real = mapOf(1 to 85, 2 to 0, 3 to null) // TMDB writes an unknown runtime as 0 or leaves it out
        server.respond = { request -> detailsJson(request.path.substringAfterLast("/").toInt(), real[request.path.substringAfterLast("/").toInt()]) }
        val repo = repository()
        val known = movie(4).copy(runtimeMinutes = 140)

        val movies = repo.withRuntimes(listOf(movie(1), movie(2), movie(3), known)).getOrThrow()

        assertEquals(listOf(1, 4), movies.map { it.id })
        assertEquals(listOf(85, 140), movies.map { it.runtimeMinutes })
        assertEquals(setOf("/3/movie/1", "/3/movie/2", "/3/movie/3"), requests.map { it.path }.toSet()) // the known one costs nothing
        assertTrue(requests.all { it.params["append_to_response"] == null }) // the movie alone: no credits or videos

        repo.withRuntimes(listOf(movie(1), movie(2))).getOrThrow()
        assertEquals(3, requests.size) // remembered: not asked again
    }

    @Test
    fun runtimesFailOnlyWhenEveryLookUpFailed() = runBlocking {
        status = 503
        val error = repository().withRuntimes(listOf(movie(1), movie(2))).exceptionOrNull()
        assertEquals(ApiError.SERVER, friendlyError(error!!))
    }

    @Test
    fun discoverKindsGoToDiscoverAndAreNotFilteredAgain() = runBlocking {
        body = listJson
        val movies = repository().moviesFor(DiscoveryRequest(DiscoverySource.TOP_RATED, genreIds = listOf(35))).getOrThrow()
        assertEquals("/3/discover/movie", requests.single().path)
        assertEquals(listOf(1, 2), movies.map { it.id }) // TMDB already filtered: we trust its answer
    }

    // The rules of liking, favoriting and rating (THE WATCH RULE) are tested in WatchRulesRepositoryTest

    private fun movie(id: Int) = Movie(id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null)

    // ---------- Your picks: the candidates ----------

    /** One movie of a TMDB list answer (genre comedy unless [genre] says otherwise). */
    private fun movieJson(
        id: Int, rating: Double = 7.0, votes: Int = 500, language: String = "en", poster: Boolean = true,
        genre: Int = 35, year: Int = 2016
    ): String {
        val posterPath = if (poster) "\"/p$id.jpg\"" else "null"
        return """{ "id": $id, "title": "Movie $id", "poster_path": $posterPath, "genre_ids": [$genre],
               "release_date": "$year-05-01", "vote_average": $rating, "vote_count": $votes, "original_language": "$language" }"""
    }

    private fun resultsJson(vararg movies: String, totalPages: Int = 1) =
        """{ "page": 1, "total_pages": $totalPages, "results": [${movies.joinToString(",")}] }"""

    /** A TMDB list answer with one movie for every id in [ids]. Ids in [noPoster] have no poster. */
    private fun moviesJson(ids: IntRange, totalPages: Int = 1, noPoster: Set<Int> = emptySet(), language: String = "en"): String =
        resultsJson(*ids.map { movieJson(it, poster = it !in noPoster, language = language) }.toTypedArray(), totalPages = totalPages)

    private fun isSimilar(request: Recorded) = request.path.endsWith("/similar")

    private val discoverRequests get() = requests.filter { it.path == "/3/discover/movie" }

    @Test
    fun candidatesAskForTheBestGenresWithPreferencesBestRatedFirstAndMostPopularFirst() = runBlocking {
        body = moviesJson(1..20, language = "ko") // plenty: the quality floor never has to drop
        val preferences = Preferences(ReleasePeriod.FROM_2015, MinRating.SEVEN_PLUS, language = "ko")

        val pool = repository().picksCandidates(listOf(35, 18, 10749), preferences).getOrThrow()

        assertEquals(20, pool.movies.size)
        assertEquals(4, requests.size) // two ways of sorting, two pages each, and no "similar" without swiped-right movies
        assertTrue(requests.all { it.path == "/3/discover/movie" })
        assertEquals(setOf("vote_average.desc", "popularity.desc"), requests.map { it.params["sort_by"] }.toSet())
        assertEquals(listOf("1", "1", "2", "2"), requests.map { it.params.getValue("page") }.sorted())
        assertTrue(requests.all { it.params["with_genres"] == "35|18|10749" })
        assertTrue(requests.all { it.params["vote_count.gte"] == "500" })
        assertTrue(requests.all { it.params["primary_release_date.gte"] == "2015-01-01" })
        assertTrue(requests.all { it.params["vote_average.gte"] == "7.0" }) // the user's 7+ is stricter than the floor
        assertTrue(requests.all { it.params["with_original_language"] == "ko" })
    }

    @Test
    fun theQualityFloorIsARatingOfSixPointFiveAndFiveHundredVotes() = runBlocking {
        body = moviesJson(1..20)

        repository().picksCandidates(listOf(35), Preferences()).getOrThrow()

        assertTrue(requests.all { it.params["vote_average.gte"] == "6.5" && it.params["vote_count.gte"] == "500" })
    }

    @Test
    fun candidatesAlsoComeFromTheSimilarListOfSwipedRightMoviesThatPassTheFloor() = runBlocking {
        server.respond = { request ->
            when (request.path) {
                "/3/movie/101/similar" -> resultsJson(movieJson(201, rating = 7.5, votes = 400), movieJson(202, rating = 6.0, votes = 900))
                "/3/movie/102/similar" -> resultsJson(movieJson(203, rating = 7.0, votes = 100), movieJson(204, rating = 6.5, votes = 300))
                else -> moviesJson(1..12)
            }
        }
        val seeds = listOf(movie(101), movie(102))

        val pool = repository().picksCandidates(listOf(35), Preferences(), seeds = seeds).getOrThrow()

        val ids = pool.movies.map { it.id }
        assertTrue(201 in ids)  // 7.5 with 400 votes: passes (6.5 and 300)
        assertTrue(204 in ids)  // exactly on the floor: passes
        assertFalse(202 in ids) // rating 6.0: below the floor
        assertFalse(203 in ids) // 100 votes: below the floor
        assertEquals(101, pool.similarTo[201])
        assertEquals(102, pool.similarTo[204])
        assertEquals(listOf("/3/movie/101/similar", "/3/movie/102/similar"), requests.filter(::isSimilar).map { it.path }.sorted())
    }

    @Test
    fun similarMoviesAreOnlyAskedForUpToThreeSwipedRightMovies() = runBlocking {
        body = moviesJson(1..12)

        repository().picksCandidates(listOf(35), Preferences(), seeds = (101..105).map { movie(it) }).getOrThrow()

        assertEquals(3, requests.count(::isSimilar))
    }

    @Test
    fun similarMoviesMustAlsoFitThePreferences() = runBlocking {
        server.respond = { request ->
            if (isSimilar(request)) {
                resultsJson(movieJson(201, language = "ko", votes = 800), movieJson(202, language = "en", votes = 800))
            } else {
                moviesJson(1..12)
            }
        }

        val pool = repository().picksCandidates(listOf(35), Preferences(language = "ko"), seeds = listOf(movie(101))).getOrThrow()

        assertTrue(201 in pool.movies.map { it.id })
        assertFalse(202 in pool.movies.map { it.id }) // English: the user asked for Korean
    }

    @Test
    fun similarMoviesAreNeverTakenUnchecked() = runBlocking {
        // TMDB's "similar" list knows nothing of the mood or the preferences: every limit is checked on each of its movies
        server.respond = { request ->
            if (isSimilar(request)) {
                resultsJson(
                    movieJson(201, rating = 8.5, votes = 900, language = "ko", year = 2019),              // fits everything
                    movieJson(202, rating = 8.5, votes = 900, language = "ko", year = 2019, genre = 27),  // horror: not a Happy genre
                    movieJson(203, rating = 6.8, votes = 900, language = "ko", year = 2019),              // under the 8+ asked for
                    movieJson(204, rating = 8.5, votes = 900, language = "ko", year = 2009),              // before 2015
                    movieJson(205, rating = 8.5, votes = 900, language = "en", year = 2019)               // English
                )
            } else {
                resultsJson()
            }
        }
        val preferences = Preferences(ReleasePeriod.FROM_2015, MinRating.EIGHT_PLUS, language = "ko")

        val pool = repository().picksCandidates(listOf(35), preferences, seeds = listOf(movie(101))).getOrThrow()

        assertEquals(listOf(201), pool.movies.map { it.id })
        assertEquals(101, pool.similarTo[201])
    }

    @Test
    fun aMovieFoundByBothWaysIsOneCandidateAndStillSaysWhatItIsSimilarTo() = runBlocking {
        server.respond = { request ->
            if (isSimilar(request)) resultsJson(movieJson(5, votes = 800)) else moviesJson(1..12)
        }

        val pool = repository().picksCandidates(listOf(35), Preferences(), seeds = listOf(movie(101))).getOrThrow()

        assertEquals(1, pool.movies.count { it.id == 5 })
        assertEquals(101, pool.similarTo[5])
    }

    @Test
    fun aFailedSimilarCallIsNoProblem() = runBlocking {
        server.respond = { request -> if (isSimilar(request)) "<html>Bad gateway</html>" else moviesJson(1..12) }

        val result = repository().picksCandidates(listOf(35), Preferences(), seeds = listOf(movie(101)))

        assertEquals(12, result.getOrThrow().movies.size)
        assertTrue(result.getOrThrow().similarTo.isEmpty())
    }

    @Test
    fun candidatesNeverIncludeMoviesThatAlreadyHaveAState() = runBlocking {
        dao.put(movie(1).copy(reaction = Reaction.LIKED))
        dao.put(movie(2).copy(reaction = Reaction.PASSED))
        dao.put(movie(3).copy(inWatchlist = true))
        dao.put(movie(4).copy(isWatched = true))
        dao.put(movie(5)) // saved, but with no state at all: it may be suggested
        body = moviesJson(1..14)

        val ids = repository().picksCandidates(listOf(35), Preferences()).getOrThrow().movies.map { it.id }

        assertEquals((5..14).toList(), ids)
    }

    @Test
    fun theSwipeDeckIsToldToSkipWatchedMoviesToo() = runBlocking {
        // SwipeViewModel hides exactly these ids (the real query is checked in androidTest: MovieDaoTest)
        dao.put(movie(1).copy(isWatched = true))
        dao.put(movie(2).copy(inWatchlist = true))
        dao.put(movie(3).copy(reaction = Reaction.PASSED))
        dao.put(movie(4)) // saved, no state

        assertEquals(setOf(1, 2, 3), repository().idsWithState().toSet())
    }

    @Test
    fun theSwipesOfThisSittingAreNeverCandidates() = runBlocking {
        body = moviesJson(1..14)

        val ids = repository().picksCandidates(listOf(35), Preferences(), exclude = setOf(1, 2)).getOrThrow().movies.map { it.id }

        assertEquals((3..14).toList(), ids)
    }

    @Test
    fun candidatesWithoutAPosterNeverComeBack() = runBlocking {
        body = moviesJson(1..12, noPoster = setOf(3, 4))

        val ids = repository().picksCandidates(listOf(35), Preferences()).getOrThrow().movies.map { it.id }

        assertFalse(3 in ids || 4 in ids)
        assertEquals(10, ids.size)
    }

    @Test
    fun withTooFewCandidatesTheFloorDropsButPreferencesAndGenresStay() = runBlocking {
        // Korean + the strict floor: 2 movies. Korean + the relaxed floor: 3. Anything + the relaxed floor: plenty.
        server.respond = { request ->
            val korean = request.params.containsKey("with_original_language")
            when {
                korean && request.params["vote_count.gte"] == "500" -> moviesJson(1..2, language = "ko")
                korean -> moviesJson(1..3, language = "ko")
                else -> moviesJson(100..114, language = "en")
            }
        }

        val pool = repository().picksCandidates(listOf(35, 18), Preferences(language = "ko")).getOrThrow()

        // Three steps of four questions each: only the vote floor goes down (preferences are NEVER dropped)
        val steps = requests.chunked(4)
        assertEquals(3, steps.size)
        assertTrue(steps[0].all { it.params["vote_average.gte"] == "6.5" && it.params["vote_count.gte"] == "500" })
        assertTrue(steps[1].all { it.params["vote_average.gte"] == "6.0" && it.params["vote_count.gte"] == "200" })
        assertTrue(steps[2].all { it.params["vote_average.gte"] == "6.0" && it.params["vote_count.gte"] == "30" })
        assertTrue(requests.all { it.params["with_original_language"] == "ko" }) // preferences stay
        assertTrue(requests.all { it.params["with_genres"] == "35|18" })        // genres stay
        assertEquals(3, pool.movies.size) // only the 3 matching Korean movies
    }

    @Test
    fun theFloorOnlyDropsWhenFewerThanTenCandidatesWereFound() = runBlocking {
        body = moviesJson(1..10) // exactly ten: enough

        repository().picksCandidates(listOf(35), Preferences()).getOrThrow()

        assertEquals(4, requests.size)
    }

    @Test
    fun similarMoviesAreAskedOnlyOnceEvenWhenTheFloorDrops() = runBlocking {
        server.respond = { request -> if (isSimilar(request)) resultsJson(movieJson(201, votes = 800)) else moviesJson(1..3) }

        val pool = repository().picksCandidates(listOf(35), Preferences(), seeds = listOf(movie(101))).getOrThrow()

        assertEquals(1, requests.count(::isSimilar))
        assertEquals(12, discoverRequests.size) // three steps of four
        assertTrue(201 in pool.movies.map { it.id }) // a candidate from the similar list is kept at every step
    }

    @Test
    fun filipinoMoviesKeepATinyVoteLimitBecauseFewPeopleVoteOnThem() = runBlocking {
        body = moviesJson(1..20)

        repository().picksCandidates(listOf(18), Preferences(language = "tl")).getOrThrow()

        assertTrue(requests.all { it.params["vote_count.gte"] == "3" && it.params["with_original_language"] == "tl" })
    }

    @Test
    fun whenNothingIsFoundTheAnswerIsAnEmptyPoolNotAnError() = runBlocking {
        body = moviesJson(1..0)

        val result = repository().picksCandidates(listOf(35), Preferences(language = "ko"))

        assertTrue(result.getOrThrow().movies.isEmpty())
        assertEquals(12, requests.size) // three steps of four questions: preferences are never dropped
    }

    @Test
    fun candidatesReportANetworkProblemAsAFailure() = runBlocking {
        status = 503
        val error = repository().picksCandidates(listOf(35), Preferences()).exceptionOrNull()
        assertEquals(ApiError.SERVER, friendlyError(error!!))
    }

    // ---------- The stills of a movie (the Diary's picture picker) ----------

    @Test
    fun theStillsOfAMovieAreAskedForInEnglishAndWithoutTextAndComeBackAsPaths() = runBlocking {
        body = """{ "backdrops": [ { "file_path": "/text.jpg", "iso_639_1": "en" }, { "file_path": "/clean.jpg", "iso_639_1": null } ], "posters": [] }"""

        val stills = repository().movieStills(157336).getOrThrow()

        val sent = requests.single()
        assertEquals("/3/movie/157336/images", sent.path)
        assertEquals("en,null", sent.params["include_image_language"])
        assertEquals(listOf("/clean.jpg", "/text.jpg"), stills) // without text first
    }

    @Test
    fun withoutATokenNoStillsAreAskedFor() = runBlocking {
        val error = repository(token = "", hasToken = false).movieStills(1).exceptionOrNull()
        assertTrue(error is MissingTokenException)
        assertTrue(requests.isEmpty())
    }

    // ---------- A person ----------

    @Test
    fun aPersonIsAskedForWithTheirMovieCredits() = runBlocking {
        body = """
            { "id": 525, "name": "Christopher Nolan", "known_for_department": "Directing", "profile_path": "/n.jpg",
              "movie_credits": { "cast": [], "crew": [
                { "id": 20, "title": "Big one", "poster_path": "/big.jpg", "popularity": 90.0, "job": "Director" } ] } }
        """.trimIndent()
        val repo = repository()

        val person = repo.person(525).getOrThrow()

        val sent = requests.single()
        assertEquals("/3/person/525", sent.path)
        assertEquals("movie_credits", sent.params["append_to_response"])
        assertEquals("en-US", sent.params["language"])
        assertEquals("Christopher Nolan", person.name)
        assertEquals(listOf(20), person.credits.getValue(com.example.cinefiles.logic.PersonRole.DIRECTOR).map { it.id })
        assertEquals(listOf(com.example.cinefiles.logic.PersonRole.DIRECTOR), person.roles)
        assertEquals("Big one", repo.recentlySeen(20)?.title) // so that Details opens with a title and a picture at once
    }

    @Test
    fun aPersonTmdbHasNothingUsableForIsAFailure() = runBlocking {
        body = """{ "id": 1 }""" // no name
        assertTrue(repository().person(1).isFailure)
    }

    @Test
    fun withoutATokenAPersonIsNotAskedFor() = runBlocking {
        val error = repository(token = "", hasToken = false).person(525).exceptionOrNull()
        assertEquals(ApiError.TOKEN_MISSING, friendlyError(error!!))
        assertTrue(requests.isEmpty())
    }

    // ---------- Discover: pages ----------

    @Test
    fun aSearchPageSaysWhichPageItIsAndWhetherThereAreMore() = runBlocking {
        body = listJson // page 1 of 3

        val first = repository().searchPage("matrix", page = 1).getOrThrow()
        assertEquals(listOf(1, 2), first.movies.map { it.id })
        assertEquals(1, first.page)
        assertEquals(3, first.totalPages)
        assertTrue(first.hasMore)

        val last = repository().searchPage("matrix", page = 3).getOrThrow()
        assertFalse(last.hasMore) // page 3 of 3
        assertEquals("/3/search/movie", requests.last().path)
        assertEquals("3", requests.last().params["page"])
    }

    @Test
    fun aSearchWithNoResultsIsOneEmptyPageWithNothingMore() = runBlocking {
        body = """{ "page": 1, "total_pages": 0, "results": [] }"""

        val page = repository().searchPage("zzzzzz").getOrThrow()

        assertTrue(page.movies.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun anAnswerWithoutAPageCountIsTheOnlyPage() = runBlocking {
        body = """{ "results": [ { "id": 1, "title": "Funny", "poster_path": "/f.jpg" } ] }"""
        assertFalse(repository().searchPage("funny").getOrThrow().hasMore)
    }

    @Test
    fun pagesBeyondFiveHundredAreNeverPromised() = runBlocking {
        body = """{ "page": 1, "total_pages": 9000, "results": [] }"""
        assertEquals(500, repository().searchPage("the").getOrThrow().totalPages) // TMDB serves 500 pages at most
    }

    @Test
    fun aBlankSearchPageAsksNothing() = runBlocking {
        val page = repository().searchPage("   ").getOrThrow()
        assertTrue(page.movies.isEmpty())
        assertFalse(page.hasMore)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun withoutAGenreTheCategoriesAreTmdbsOwnLists() = runBlocking {
        body = listJson
        repository().browsePage(BrowseCategory.ALL, genreId = null)
        repository().browsePage(BrowseCategory.POPULAR, genreId = null)
        repository().browsePage(BrowseCategory.TRENDING, genreId = null)
        repository().browsePage(BrowseCategory.TOP_RATED, genreId = null, page = 2)

        assertEquals(
            listOf("/3/discover/movie", "/3/movie/popular", "/3/trending/movie/week", "/3/movie/top_rated"),
            requests.map { it.path }
        )
        // Verify discover browsePage has NO language restriction (all languages default)
        assertNull(requests[0].params["with_original_language"])
        assertEquals("popularity.desc", requests[0].params["sort_by"])
        assertEquals("2", requests.last().params["page"])
    }

    @Test
    fun theDiscoverTabNeverAsksForALanguageOrAMoodItIsIndependentOfLetsWatchSomething() = runBlocking {
        body = listJson
        // Every way the Discover tab can ask: all categories, with and without a genre
        for (category in BrowseCategory.values()) {
            repository().browsePage(category, genreId = null)
            repository().browsePage(category, genreId = 35)
        }

        assertTrue(requests.isNotEmpty())
        for (request in requests) {
            assertFalse("with_original_language leaked into ${request.path}", "with_original_language" in request.params)
            assertFalse("a runtime limit leaked into ${request.path}", "with_runtime.lte" in request.params || "with_runtime.gte" in request.params)
            assertFalse("a release limit leaked into ${request.path}", "primary_release_date.lte" in request.params)
        }
        // The only genre ever sent is the one the user picked in Discover (35), never a mood's genres
        assertTrue(requests.mapNotNull { it.params["with_genres"] }.all { it == "35" })
    }

    @Test
    fun theAllCategoryWithoutAGenreIsMostPopularFirst() = runBlocking {
        body = listJson
        repository().browsePage(BrowseCategory.ALL, genreId = null)
        val sent = requests.single()
        assertEquals("/3/discover/movie", sent.path)
        assertEquals("popularity.desc", sent.params["sort_by"])
        assertEquals("20", sent.params["vote_count.gte"])
        assertNull(sent.params["with_genres"])
    }

    @Test
    fun withAGenreTheCategoriesBecomeDiscoverQuestions() = runBlocking {
        body = listJson
        repository().browsePage(BrowseCategory.ALL, genreId = 35)
        repository().browsePage(BrowseCategory.POPULAR, genreId = 35)
        repository().browsePage(BrowseCategory.TRENDING, genreId = 35)
        repository().browsePage(BrowseCategory.TOP_RATED, genreId = 35, page = 4)

        assertTrue(requests.all { it.path == "/3/discover/movie" && it.params["with_genres"] == "35" })
        assertTrue(requests.all { it.params["with_original_language"] == null }) // No language restriction

        val (all, popular, trending, topRated) = requests

        assertEquals("popularity.desc", all.params["sort_by"])

        assertEquals("popularity.desc", popular.params["sort_by"])
        assertEquals("100", popular.params["vote_count.gte"])

        assertEquals("popularity.desc", trending.params["sort_by"])
        assertTrue(trending.params["primary_release_date.gte"]!!.endsWith("-01-01")) // recent movies only

        assertEquals("vote_average.desc", topRated.params["sort_by"])
        assertEquals("300", topRated.params["vote_count.gte"])
        assertEquals("4", topRated.params["page"])
    }

    // ---------- Things going wrong ----------

    @Test
    fun aRejectedTokenIsReportedAsInvalid() = runBlocking {
        status = 401
        body = """{ "status_code": 7, "status_message": "Invalid API key" }"""
        val error = repository().trending().exceptionOrNull()
        assertEquals(ApiError.TOKEN_INVALID, friendlyError(error!!))
    }

    @Test
    fun tooManyRequestsAndServerTroubleAreRecognised() = runBlocking {
        status = 429
        assertEquals(ApiError.TOO_MANY_REQUESTS, friendlyError(repository().popular().exceptionOrNull()!!))
        status = 503
        assertEquals(ApiError.SERVER, friendlyError(repository().popular().exceptionOrNull()!!))
    }

    @Test
    fun anAnswerThatIsNotJsonIsAFailureNotACrash() = runBlocking {
        body = "<html>Bad gateway</html>"
        val error = repository().trending().exceptionOrNull()
        assertEquals(ApiError.UNKNOWN, friendlyError(error!!))
    }

    @Test
    fun noServerAtAllMeansNoInternet() = runBlocking {
        val deadUrl = baseUrl
        server.stop() // nobody is listening any more: like airplane mode, the connection cannot be made
        val error = repository(url = deadUrl).trending().exceptionOrNull()
        assertEquals(ApiError.NO_INTERNET, friendlyError(error!!))
    }

    @Test
    fun withoutATokenNothingIsSentAndTheErrorSaysSo() = runBlocking {
        val repo = repository(token = "", hasToken = false)
        val error = repo.trending().exceptionOrNull()
        assertEquals(ApiError.TOKEN_MISSING, friendlyError(error!!))
        assertEquals(ApiError.TOKEN_MISSING, friendlyError(repo.discover(DiscoveryRequest(DiscoverySource.MOOD_MATCH)).exceptionOrNull()!!))
        assertEquals(ApiError.TOKEN_MISSING, friendlyError(repo.movieDetails(1).exceptionOrNull()!!))
        assertTrue(requests.isEmpty())
    }
}
