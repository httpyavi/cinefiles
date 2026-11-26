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
 * and asking the five questions at the same time does not change the mix.
 */
class DeckFromTmdbTest {

    private val comedy = 35
    private val pageSize = 20

    private fun movie(id: Int) = Movie(
        id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null, voteAverage = 8.0,
        releaseYear = 2010, genreIds = listOf(comedy), voteCount = 1000, originalLanguage = "en"
    )

    /** A pretend TMDB with [size] comedies. It answers a question with the matching movies, one page at a time. */
    private class PretendTmdb(private val catalog: List<Movie>, private val pageSize: Int) {
        val pages = PageCounter()

        suspend fun answer(request: DiscoveryRequest): List<Movie> {
            val page = pages.pageFor(request)
            val matching = catalog.filter { request.matches(it) }
            val answer = matching.drop((page - 1) * pageSize).take(pageSize)
            pages.markAnswered(request)
            return answer
        }
    }

    @Test
    fun keepSwipingBringsNewMoviesAndNeverARepeat() = runBlocking {
        val tmdb = PretendTmdb((1..100).map { movie(it) }, pageSize)
        val seen = HashSet<Int>()

        repeat(4) { round ->
            val deck = Discovery.buildDeck(listOf(comedy), Preferences(), exclude = seen.toSet(), fetch = tmdb::answer)
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
        val seen = HashSet<Int>()

        val first = Discovery.buildDeck(listOf(comedy), Preferences(), seen.toSet(), tmdb::answer)
        seen.addAll(first.map { it.movie.id })
        val second = Discovery.buildDeck(listOf(comedy), Preferences(), seen.toSet(), tmdb::answer)
        seen.addAll(second.map { it.movie.id })
        val third = Discovery.buildDeck(listOf(comedy), Preferences(), seen.toSet(), tmdb::answer)
        seen.addAll(third.map { it.movie.id })
        val fourth = Discovery.buildDeck(listOf(comedy), Preferences(), seen.toSet(), tmdb::answer)

        assertTrue(seen.isNotEmpty())
        assertTrue(fourth.isEmpty())
    }

    @Test
    fun askingTheFiveQuestionsTogetherKeepsTheSameMixAsOneByOne() = runBlocking {
        // The first question is the SLOWEST to answer: if answers were mixed in the order they arrive, it would come last
        val slowFirst: suspend (DiscoveryRequest) -> List<Movie> = { request ->
            if (request.source == DiscoverySource.MOOD_MATCH) delay(60)
            val base = when (request.source) {
                DiscoverySource.MOOD_MATCH -> 100
                DiscoverySource.TOP_RATED -> 200
                DiscoverySource.HIDDEN_GEM -> 300
                DiscoverySource.TRENDING -> 400
                else -> 500
            }
            listOf(movie(base + 1), movie(base + 2))
        }

        val deck = Discovery.buildDeck(listOf(comedy), Preferences(), emptySet(), slowFirst, limit = 5, wanted = 5)

        assertEquals(
            listOf(DiscoverySource.MOOD_MATCH, DiscoverySource.TOP_RATED, DiscoverySource.HIDDEN_GEM,
                DiscoverySource.TRENDING, DiscoverySource.POPULAR),
            deck.map { it.source }
        )
        assertEquals(listOf(101, 201, 301, 401, 501), deck.map { it.movie.id })
    }

    @Test
    fun aFailingQuestionFailsTheWholeDeckSoTheScreenCanShowAnError() {
        val failing: suspend (DiscoveryRequest) -> List<Movie> = { request ->
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
}
