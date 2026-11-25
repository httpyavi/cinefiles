package com.example.cinefiles.ui.detail

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.CastMember
import com.example.cinefiles.data.CrewMember
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.MovieDetails
import com.example.cinefiles.data.Reaction
import com.example.cinefiles.logic.CrewRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the Details screen decides what to show from the database, the lists seen so far, and TMDB's answer. */
class DetailStateTest {

    private fun movie(title: String = "Interstellar", overview: String = "", runtime: Int? = null) = Movie(
        id = 157336, title = title, posterPath = "/p.jpg", backdropPath = "/b.jpg",
        overview = overview, runtimeMinutes = runtime
    )

    private fun details(movie: Movie, trailer: String? = "abc") = MovieDetails(movie, emptyList(), emptyList(), trailer)

    private fun content(state: DetailState) = state as DetailState.Content

    // ---------- nothing known ----------

    @Test
    fun nothingKnownWhileLoadingIsTheLoadingState() {
        assertEquals(DetailState.Loading, buildDetailState(null, null, DetailRemote(isLoading = true)))
    }

    @Test
    fun nothingKnownAfterAFailureIsTheErrorState() {
        val state = buildDetailState(null, null, DetailRemote(error = ApiError.NO_INTERNET, isLoading = false))
        assertEquals(DetailState.Error(ApiError.NO_INTERNET), state)
    }

    @Test
    fun anErrorWithoutAReasonIsUnknown() {
        val state = buildDetailState(null, null, DetailRemote(isLoading = false))
        assertEquals(DetailState.Error(ApiError.UNKNOWN), state)
    }

    // ---------- known data shows at once ----------

    @Test
    fun aMovieSeenInAListIsShownWhileTheRestLoads() {
        val state = content(buildDetailState(null, movie("Seen in Home"), DetailRemote(isLoading = true)))
        assertEquals("Seen in Home", state.movie.title)
        assertTrue(state.isLoadingMore)
        assertNull(state.loadError)
        assertNull(state.trailerKey)
    }

    @Test
    fun aSavedMovieIsShownWithoutInternetAndTheFailureIsReported() {
        val saved = movie("Saved", overview = "Saved story", runtime = 120).copy(reaction = Reaction.LIKED)
        val state = content(buildDetailState(saved, null, DetailRemote(error = ApiError.NO_INTERNET, isLoading = false)))
        assertEquals("Saved", state.movie.title)
        assertEquals(120, state.movie.runtimeMinutes)
        assertEquals(ApiError.NO_INTERNET, state.loadError)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun theLoadErrorIsHiddenWhileANewTryIsRunning() {
        val state = content(buildDetailState(movie(), null, DetailRemote(error = ApiError.NO_INTERNET, isLoading = true)))
        assertNull(state.loadError)
    }

    // ---------- merging ----------

    @Test
    fun theFreshAnswerBeatsTheSavedAndSeenCopies() {
        val fresh = details(movie("Fresh", overview = "New story", runtime = 169))
        val state = content(buildDetailState(movie("Saved"), movie("Seen"), DetailRemote(details = fresh, isLoading = false)))
        assertEquals("Fresh", state.movie.title)
        assertEquals("New story", state.movie.overview)
        assertEquals("abc", state.trailerKey)
    }

    @Test
    fun theUsersStateAlwaysComesFromTheDatabase() {
        val saved = movie().copy(
            reaction = Reaction.LIKED, reactedAt = 5, inWatchlist = true, watchlistAddedAt = 6,
            isWatched = false, watchedAt = 0
        )
        val fresh = details(movie("Fresh")) // TMDB knows nothing about the user: everything is at its default
        val state = content(buildDetailState(saved, null, DetailRemote(details = fresh, isLoading = false)))

        assertEquals(Reaction.LIKED, state.movie.reaction)
        assertTrue(state.movie.inWatchlist)
        assertEquals(5L, state.movie.reactedAt)
        assertEquals(6L, state.movie.watchlistAddedAt)
    }

    @Test
    fun gapsInTheFreshAnswerAreFilledFromTheSavedCopy() {
        val saved = movie(overview = "Saved story", runtime = 120)
        val fresh = details(movie(overview = "", runtime = null))
        val state = content(buildDetailState(saved, null, DetailRemote(details = fresh, isLoading = false)))
        assertEquals("Saved story", state.movie.overview)
        assertEquals(120, state.movie.runtimeMinutes)
    }

    @Test
    fun aMovieThatIsNotSavedKeepsItsDefaultState() {
        val state = content(buildDetailState(null, movie(), DetailRemote(isLoading = true)))
        assertEquals(Reaction.NONE, state.movie.reaction)
        assertFalse(state.movie.inWatchlist)
        assertFalse(state.movie.isWatched)
    }

    @Test
    fun similarMoviesArriveWithTheirOwnListOrAnEmptyOne() {
        val others = listOf(movie("A").copy(id = 1), movie("B").copy(id = 2))
        val withSimilar = content(buildDetailState(movie(), null, DetailRemote(similar = others, isLoading = false)))
        assertEquals(listOf(1, 2), withSimilar.similar.map { it.id })

        val without = content(buildDetailState(movie(), null, DetailRemote(isLoading = true)))
        assertTrue(without.similar.isEmpty())
    }

    // ---------- credits ----------

    @Test
    fun theCreditsAndFactsOfTheFreshAnswerReachTheScreen() {
        val nolan = CrewMember(525, "Christopher Nolan", "Director", null)
        val writer = CrewMember(7, "Jonathan Nolan", "Writer", null)
        val fresh = MovieDetails(
            movie = movie(),
            cast = listOf(CastMember(10297, "Matthew McConaughey", "Cooper", "/m.jpg")),
            directors = listOf(nolan),
            trailerKey = null,
            crew = mapOf(CrewRole.WRITER to listOf(writer)),
            tagline = "Go further.",
            status = "Released",
            productionCountries = listOf("United States of America")
        )

        val state = content(buildDetailState(null, null, DetailRemote(details = fresh, isLoading = false)))

        assertEquals(listOf(nolan), state.directors)
        assertEquals(listOf(10297), state.cast.map { it.id })
        assertEquals(listOf(writer), state.crew.getValue(CrewRole.WRITER))
        assertEquals("Go further.", state.tagline)
        assertEquals("Released", state.status)
        assertEquals(listOf("United States of America"), state.countries)
    }

    @Test
    fun withoutTmdbsAnswerThereAreNoCreditsAndNoFacts() {
        val state = content(buildDetailState(movie(), null, DetailRemote(error = ApiError.NO_INTERNET, isLoading = false)))

        assertTrue(state.directors.isEmpty())
        assertTrue(state.cast.isEmpty())
        assertTrue(state.crew.isEmpty())
        assertNull(state.tagline)
        assertNull(state.status)
        assertTrue(state.countries.isEmpty())
    }
}
