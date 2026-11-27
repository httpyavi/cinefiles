package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The swipe deck against a pretend TMDB that answers in PAGES (20 movies per page), the way the real one does.
 * It checks the two things the real deck depends on: asking again brings new movies (never a repeat),
 * and asking the questions at the same time does not change the mix.
 */
class DeckFromTmdbTest {

    private val comedy = 35
    private val pageSize = 20

    private fun movie(id: Int) = Movie(
        id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null, voteAverage = 8.0,
        releaseYear = 2010, genreIds = listOf(comedy), voteCount = 1000, originalLanguage = "en"
    )

    /** A pretend TMDB: it answers a question with that page of the matching movies. */
    private class PretendTmdb(private val catalog: List<Movie>, private val pageSize: Int) {
        suspend fun answer(request: DiscoveryRequest, page: Int): List<Movie> =
            catalog.filter { request.matches(it) }.drop((page - 1) * pageSize).take(pageSize)
    }

    @Test
    fun discoverTenMoreBringsNewMoviesAndNeverARepeat() = runBlocking {
        val tmdb = PretendTmdb((1..100).map { movie(it) }, pageSize)
        val feed = DiscoveryFeed(listOf(comedy), Preferences()) // one feed for the whole sitting, like SwipeViewModel
        val seen = HashSet<Int>()

        repeat(4) { round ->
            val deck = feed.next(exclude = seen.toSet(), fetch = tmdb::answer)
            val ids = deck.map { it.movie.id }
            assertEquals("round ${round + 1} should fill the deck", Discovery.DECK_SIZE, ids.size)
            assertTrue("round ${round + 1} repeated a movie", ids.none { it in seen })
            assertEquals("a movie is in a deck once", ids.size, ids.toSet().size)
            seen.addAll(ids)
        }
        assertEquals(4 * Discovery.DECK_SIZE, seen.size)
    }

    @Test
    fun whenTheCatalogRunsOutTheDeckEndsInsteadOfRepeating() = runBlocking {
        val tmdb = PretendTmdb((1..30).map { movie(it) }, pageSize)
        val feed = DiscoveryFeed(listOf(comedy), Preferences())
        val seen = HashSet<Int>()

        repeat(3) {
            val batch = feed.next(seen.toSet(), tmdb::answer)
            assertEquals(Discovery.DECK_SIZE, batch.size)
            seen.addAll(batch.map { it.movie.id })
        }
        val fourth = feed.next(seen.toSet(), tmdb::answer)

        assertEquals(30, seen.size)
        assertTrue(fourth.isEmpty())
    }

    @Test
    fun askingTheQuestionsTogetherKeepsTheSameMixAsOneByOne() = runBlocking {
        // The first question is the SLOWEST to answer: if answers were mixed in the order they arrive, it would come last
        val slowFirst: suspend (DiscoveryRequest, Int) -> List<Movie> = { request, page ->
            if (request.source == DiscoverySource.MOOD_MATCH) delay(60)
            val base = when (request.source) {
                DiscoverySource.MOOD_MATCH -> 100
                DiscoverySource.TOP_RATED -> 200
                DiscoverySource.HIDDEN_GEM -> 300
                DiscoverySource.TRENDING -> 400
                DiscoverySource.POPULAR, DiscoverySource.CLOSE_MATCH -> 500
            }
            if (page == 1) listOf(movie(base + 1), movie(base + 2)) else emptyList()
        }

        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), slowFirst, limit = 5)

        assertEquals(
            listOf(DiscoverySource.MOOD_MATCH, DiscoverySource.TOP_RATED, DiscoverySource.HIDDEN_GEM,
                DiscoverySource.TRENDING, DiscoverySource.POPULAR),
            deck.map { it.source }
        )
        assertEquals(listOf(101, 201, 301, 401, 501), deck.map { it.movie.id })
    }

    @Test
    fun aFailingQuestionFailsTheWholeDeckSoTheScreenCanShowAnError() {
        val failing: suspend (DiscoveryRequest, Int) -> List<Movie> = { request, _ ->
            if (request.source == DiscoverySource.HIDDEN_GEM) throw java.io.IOException("no connection")
            listOf(movie(1))
        }
        var caught: Exception? = null
        try {
            runBlocking { Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), failing) }
        } catch (e: java.io.IOException) {
            caught = e
        }
        assertTrue(caught is java.io.IOException)
    }

    @Test
    fun aFailingRuntimeLookUpFailsTheDeckToo() {
        val tmdb = PretendTmdb((1..30).map { movie(it) }, pageSize)
        var caught: Exception? = null
        try {
            runBlocking {
                Discovery.buildDeck(
                    listOf(comedy), Preferences(length = Length.UNDER_2_HOURS), emptySet(), tmdb::answer,
                    lookUpRuntimes = { throw java.io.IOException("no connection") }
                )
            }
        } catch (e: java.io.IOException) {
            caught = e
        }
        assertTrue(caught is java.io.IOException)
    }
}
