package com.example.cinefiles.ui.person

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.PersonDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the Person screen decides what to show from TMDB's answer and the tab the user tapped. */
class PersonStateTest {

    private fun movie(id: Int) = Movie(id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null)

    private fun person(knownFor: String?, acting: List<Int> = emptyList(), crew: List<Int> = emptyList()) = PersonDetails(
        id = 1, name = "Someone", knownFor = knownFor, biography = "", profilePath = null,
        acting = acting.map(::movie), crew = crew.map(::movie)
    )

    private fun content(state: PersonState) = state as PersonState.Content

    // ---------- nothing known ----------

    @Test
    fun whileLoadingNothingIsTheLoadingState() {
        assertEquals(PersonState.Loading, buildPersonState(PersonRemote(isLoading = true), null))
    }

    @Test
    fun afterAFailureNothingIsTheErrorState() {
        val state = buildPersonState(PersonRemote(error = ApiError.NO_INTERNET, isLoading = false), null)
        assertEquals(PersonState.Error(ApiError.NO_INTERNET), state)
    }

    @Test
    fun anErrorWithoutAReasonIsUnknown() {
        assertEquals(PersonState.Error(ApiError.UNKNOWN), buildPersonState(PersonRemote(isLoading = false), null))
    }

    // ---------- the tabs ----------

    @Test
    fun anActorStartsOnTheActingTab() {
        val state = content(buildPersonState(PersonRemote(person("Acting", acting = listOf(1, 2), crew = listOf(3)), isLoading = false), null))
        assertEquals(PersonTab.ACTING, state.tab)
        assertEquals(listOf(1, 2), state.movies.map { it.id })
        assertTrue(state.hasBothTabs)
    }

    @Test
    fun aDirectorStartsOnTheCrewTab() {
        val state = content(buildPersonState(PersonRemote(person("Directing", acting = listOf(1), crew = listOf(3, 4)), isLoading = false), null))
        assertEquals(PersonTab.CREW, state.tab)
        assertEquals(listOf(3, 4), state.movies.map { it.id })
    }

    @Test
    fun theUsersTabChoiceCountsWhenThereAreTwoTabs() {
        val remote = PersonRemote(person("Acting", acting = listOf(1), crew = listOf(3)), isLoading = false)
        val state = content(buildPersonState(remote, PersonTab.CREW))
        assertEquals(PersonTab.CREW, state.tab)
        assertEquals(listOf(3), state.movies.map { it.id })
    }

    @Test
    fun withOnlyOneListThereIsNoChoiceAndThatListIsShown() {
        val onlyCrew = content(buildPersonState(PersonRemote(person("Acting", crew = listOf(3)), isLoading = false), PersonTab.ACTING))
        assertFalse(onlyCrew.hasBothTabs)
        assertEquals(PersonTab.CREW, onlyCrew.tab) // an actor with no acting credits: the crew list is all there is
        assertEquals(listOf(3), onlyCrew.movies.map { it.id })

        val onlyActing = content(buildPersonState(PersonRemote(person("Directing", acting = listOf(1)), isLoading = false), PersonTab.CREW))
        assertFalse(onlyActing.hasBothTabs)
        assertEquals(PersonTab.ACTING, onlyActing.tab)
    }

    @Test
    fun aPersonWithNoMoviesIsStillShownWithAnEmptyList() {
        val state = content(buildPersonState(PersonRemote(person("Acting"), isLoading = false), null))
        assertTrue(state.movies.isEmpty())
        assertFalse(state.hasBothTabs)
    }

    @Test
    fun aRetryThatFailsKeepsWhatWasAlreadyThere() {
        // PersonViewModel keeps the old details when a new try fails: the state is still the content
        val remote = PersonRemote(details = person("Acting", acting = listOf(1)), error = ApiError.NO_INTERNET, isLoading = false)
        assertTrue(buildPersonState(remote, null) is PersonState.Content)
    }
}
