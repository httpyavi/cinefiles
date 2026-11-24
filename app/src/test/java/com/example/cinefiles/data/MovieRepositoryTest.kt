package com.example.cinefiles.data

import com.example.cinefiles.logic.BrowseCategory
import com.example.cinefiles.logic.ContentFilter
import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.Language
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
        MovieRepository(TmdbApi.create(token, url), hasToken, dao)

    // A fake database: these tests are about the internet part
    private val dao = FakeMovieDao()

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
    fun trendingForTheDeckIsFilteredOnThePhoneByTheMood() = runBlocking {
        // TMDB's trending list cannot be filtered by the server, so a comedy mood keeps only the comedy
        body = listJson
        val comedyOnly = DiscoveryRequest(DiscoverySource.TRENDING, genreIds = listOf(35))
        val movies = repository().moviesFor(comedyOnly).getOrThrow()
        assertEquals(listOf(1), movies.map { it.id })
        assertEquals("/3/trending/movie/week", requests.single().path)
    }

    @Test
    fun discoverKindsGoToDiscoverAndAreNotFilteredAgain() = runBlocking {
        body = listJson
        val movies = repository().moviesFor(DiscoveryRequest(DiscoverySource.TOP_RATED, genreIds = listOf(35))).getOrThrow()
        assertEquals("/3/discover/movie", requests.single().path)
        assertEquals(listOf(1, 2), movies.map { it.id }) // TMDB already filtered: we trust its answer
    }

    // ---------- The like rule ----------

    private fun movie(id: Int) = Movie(id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null)

    @Test
    fun aMovieOnTheWatchlistCannotBeLiked() = runBlocking {
        val repo = repository()
        repo.addToWatchlist(movie(5))

        assertFalse(repo.like(movie(5)))
        assertEquals(Reaction.NONE, dao.saved(5)?.reaction) // nothing changed
        assertTrue(dao.saved(5)!!.inWatchlist)
    }

    @Test
    fun aMovieThatIsNotOnTheWatchlistCanBeLiked() = runBlocking {
        val repo = repository()

        assertTrue(repo.like(movie(6))) // not saved yet: it is saved and liked
        assertEquals(Reaction.LIKED, dao.saved(6)?.reaction)
    }

    @Test
    fun likingWorksAgainOnceTheMovieLeavesTheWatchlist() = runBlocking {
        val repo = repository()
        repo.addToWatchlist(movie(7))
        assertFalse(repo.like(movie(7)))

        repo.removeFromWatchlist(7)
        assertTrue(repo.like(movie(7)))
        assertEquals(Reaction.LIKED, dao.saved(7)?.reaction)
    }

    @Test
    fun likingWorksAgainOnceTheMovieIsMarkedWatched() = runBlocking {
        val repo = repository()
        repo.addToWatchlist(movie(8))
        assertFalse(repo.like(movie(8)))

        repo.markWatched(movie(8)) // watched takes it off the watchlist
        assertTrue(repo.like(movie(8)))
    }

    @Test
    fun theLikeRuleLooksAtTheSavedCopyNotAtTheOneInTheCallersHand() = runBlocking {
        dao.put(movie(9).copy(inWatchlist = true))
        // The screen still holds an old copy that says "not on the watchlist"
        assertFalse(repository().like(movie(9).copy(inWatchlist = false)))
    }

    @Test
    fun aMovieThatWasLikedKeepsItsLikeWhenItIsAddedToTheWatchlistLater() = runBlocking {
        val repo = repository()
        repo.like(movie(10))
        repo.addToWatchlist(movie(10))

        // The rule only stops NEW likes; nothing is taken away
        assertEquals(Reaction.LIKED, dao.saved(10)?.reaction)
        assertTrue(dao.saved(10)!!.inWatchlist)
    }

    // ---------- Your picks: the candidates ----------

    /** A TMDB list answer with one movie for every id in [ids]. Ids in [noPoster] have no poster. */
    private fun moviesJson(ids: IntRange, totalPages: Int = 1, noPoster: Set<Int> = emptySet()): String {
        val items = ids.joinToString(",") { id ->
            val poster = if (id in noPoster) "null" else "\"/p$id.jpg\""
            """{ "id": $id, "title": "Movie $id", "poster_path": $poster, "genre_ids": [35],
               "release_date": "2016-05-01", "vote_average": 7.0, "vote_count": 500, "original_language": "en" }"""
        }
        return """{ "page": 1, "total_pages": $totalPages, "results": [$items] }"""
    }

    @Test
    fun candidatesAskForTheBestGenresWithPreferencesAndAtLeastAHundredVotes() = runBlocking {
        body = moviesJson(1..20) // plenty: no second question is needed
        val preferences = Preferences(ReleasePeriod.FROM_2015, MinRating.SEVEN_PLUS, language = Language.KOREAN)

        val candidates = repository().picksCandidates(listOf(35, 18, 10749), preferences).getOrThrow()

        assertEquals(20, candidates.size)
        val sent = requests.single()
        assertEquals("/3/discover/movie", sent.path)
        assertEquals("35|18|10749", sent.params["with_genres"])
        assertEquals("100", sent.params["vote_count.gte"])
        assertEquals("2015-01-01", sent.params["primary_release_date.gte"])
        assertEquals("7.0", sent.params["vote_average.gte"])
        assertEquals("ko", sent.params["with_original_language"])
    }

    @Test
    fun candidatesNeverIncludeMoviesThatAlreadyHaveAState() = runBlocking {
        dao.put(movie(1).copy(reaction = Reaction.LIKED))
        dao.put(movie(2).copy(reaction = Reaction.PASSED))
        dao.put(movie(3).copy(inWatchlist = true))
        dao.put(movie(4).copy(isWatched = true))
        dao.put(movie(5)) // saved, but with no state at all: it may be suggested
        body = moviesJson(1..14)

        val ids = repository().picksCandidates(listOf(35), Preferences()).getOrThrow().map { it.id }

        assertEquals((5..14).toList(), ids)
    }

    @Test
    fun candidatesWithoutAPosterNeverComeBack() = runBlocking {
        body = moviesJson(1..12, noPoster = setOf(3, 4))

        val ids = repository().picksCandidates(listOf(35), Preferences()).getOrThrow().map { it.id }

        assertFalse(3 in ids || 4 in ids)
        assertEquals(10, ids.size)
    }

    @Test
    fun withTooFewCandidatesThePreferencesAreDroppedOnceAndTheGenresStay() = runBlocking {
        // With a language chosen TMDB knows only 2 movies; without it, plenty
        server.respond = { request ->
            if (request.params.containsKey("with_original_language")) moviesJson(1..2) else moviesJson(100..114)
        }

        val candidates = repository().picksCandidates(listOf(35, 18), Preferences(language = Language.KOREAN)).getOrThrow()

        // Page 1 and 2 with the preferences, then ONE try without them
        assertEquals(listOf("ko", "ko", null), requests.map { it.params["with_original_language"] })
        assertTrue(requests.all { it.params["with_genres"] == "35|18" })
        assertEquals(17, candidates.size) // the 2 from the first try and the 15 from the second, no repeats
    }

    @Test
    fun whenNothingIsFoundTheAnswerIsAnEmptyListNotAnError() = runBlocking {
        body = moviesJson(1..0)

        val result = repository().picksCandidates(listOf(35), Preferences(language = Language.KOREAN))

        assertTrue(result.getOrThrow().isEmpty())
        assertEquals(4, requests.size) // 2 pages with the preferences, 2 pages without: then it gives up
    }

    @Test
    fun candidatesReportANetworkProblemAsAFailure() = runBlocking {
        status = 503
        val error = repository().picksCandidates(listOf(35), Preferences()).exceptionOrNull()
        assertEquals(ApiError.SERVER, friendlyError(error!!))
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
        repository().browsePage(BrowseCategory.POPULAR, genreId = null)
        repository().browsePage(BrowseCategory.TRENDING, genreId = null)
        repository().browsePage(BrowseCategory.TOP_RATED, genreId = null, page = 2)

        assertEquals(
            listOf("/3/movie/popular", "/3/trending/movie/week", "/3/movie/top_rated"),
            requests.map { it.path }
        )
        assertEquals("2", requests.last().params["page"])
    }

    @Test
    fun withAGenreTheCategoriesBecomeDiscoverQuestions() = runBlocking {
        body = listJson
        repository().browsePage(BrowseCategory.POPULAR, genreId = 35)
        repository().browsePage(BrowseCategory.TRENDING, genreId = 35)
        repository().browsePage(BrowseCategory.TOP_RATED, genreId = 35, page = 4)

        assertTrue(requests.all { it.path == "/3/discover/movie" && it.params["with_genres"] == "35" })
        val (popular, trending, topRated) = requests

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
        assertEquals(ApiError.TOKEN_MISSING, friendlyError(repo.discover(DiscoveryRequest(DiscoverySource.EXPLORE)).exceptionOrNull()!!))
        assertEquals(ApiError.TOKEN_MISSING, friendlyError(repo.movieDetails(1).exceptionOrNull()!!))
        assertTrue(requests.isEmpty())
    }
}
