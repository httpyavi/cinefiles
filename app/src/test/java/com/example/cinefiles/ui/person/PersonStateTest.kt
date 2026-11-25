package com.example.cinefiles.ui.person

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.PersonDetails
import com.example.cinefiles.logic.PersonRole
import com.example.cinefiles.logic.SortDirection
import com.example.cinefiles.logic.SortField
import com.example.cinefiles.logic.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the Person screen decides what to show from TMDB's answer and the role tab the user tapped. */
class PersonStateTest {

    private fun movie(id: Int) = Movie(id = id, title = "Movie $id", posterPath = "/p$id.jpg", backdropPath = null)

    private fun person(knownFor: String?, vararg credits: Pair<PersonRole, List<Int>>) = PersonDetails(
        id = 1, name = "Someone", knownFor = knownFor, biography = "", profilePath = null,
        credits = credits.associate { (role, ids) -> role to ids.map(::movie) }
    )

    private fun content(state: PersonState) = state as PersonState.Content

    private fun remote(person: PersonDetails) = PersonRemote(person, isLoading = false)

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

    // ---------- the role tabs ----------

    @Test
    fun theTabsCarryTheirMovieCountAndOnlyExistForRolesWithMovies() {
        val state = content(
            buildPersonState(
                remote(person("Acting", PersonRole.ACTOR to listOf(1, 2, 3), PersonRole.PRODUCER to listOf(4), PersonRole.DIRECTOR to emptyList())),
                null
            )
        )
        assertEquals(
            listOf(PersonTab(PersonRole.ACTOR, 3), PersonTab(PersonRole.PRODUCER, 1)),
            state.tabs
        )
    }

    @Test
    fun anActorStartsOnTheActorTabAndADirectorOnTheDirectorTab() {
        val actor = content(buildPersonState(remote(person("Acting", PersonRole.ACTOR to listOf(1, 2), PersonRole.DIRECTOR to listOf(3, 4, 5))), null))
        assertEquals(PersonRole.ACTOR, actor.role)
        assertEquals(listOf(1, 2), actor.movies.map { it.id })

        val director = content(buildPersonState(remote(person("Directing", PersonRole.ACTOR to listOf(1), PersonRole.DIRECTOR to listOf(3, 4))), null))
        assertEquals(PersonRole.DIRECTOR, director.role)
        assertEquals(listOf(3, 4), director.movies.map { it.id })
    }

    @Test
    fun withNoPrimaryRoleTheBiggestListComesFirst() {
        val state = content(buildPersonState(remote(person(null, PersonRole.ACTOR to listOf(1), PersonRole.PRODUCER to listOf(2, 3, 4))), null))
        assertEquals(listOf(PersonRole.PRODUCER, PersonRole.ACTOR), state.tabs.map { it.role })
        assertEquals(PersonRole.PRODUCER, state.role)
    }

    @Test
    fun theUsersTabChoiceCountsWhenThePersonHasThatRole() {
        val remote = remote(person("Acting", PersonRole.ACTOR to listOf(1), PersonRole.WRITER to listOf(3)))
        val state = content(buildPersonState(remote, PersonRole.WRITER))
        assertEquals(PersonRole.WRITER, state.role)
        assertEquals(listOf(3), state.movies.map { it.id })
    }

    @Test
    fun aChosenRoleThePersonDoesNotHaveFallsBackToTheFirstTab() {
        val remote = remote(person("Acting", PersonRole.ACTOR to listOf(1)))
        val state = content(buildPersonState(remote, PersonRole.COMPOSER))
        assertEquals(PersonRole.ACTOR, state.role)
    }

    @Test
    fun theTabsAreOnlyShownWhenThereIsAChoice() {
        assertTrue(content(buildPersonState(remote(person("Acting", PersonRole.ACTOR to listOf(1), PersonRole.CREW to listOf(2))), null)).showTabs)
        assertFalse(content(buildPersonState(remote(person("Acting", PersonRole.ACTOR to listOf(1))), null)).showTabs)
    }

    @Test
    fun aPersonWithNoMoviesIsStillShownWithAnEmptyList() {
        val state = content(buildPersonState(remote(person("Acting")), null))
        assertTrue(state.movies.isEmpty())
        assertTrue(state.tabs.isEmpty())
        assertNull(state.role)
        assertFalse(state.showTabs)
    }

    @Test
    fun aRetryThatFailsKeepsWhatWasAlreadyThere() {
        // PersonViewModel keeps the old details when a new try fails: the state is still the content
        val remote = PersonRemote(details = person("Acting", PersonRole.ACTOR to listOf(1)), error = ApiError.NO_INTERNET, isLoading = false)
        assertTrue(buildPersonState(remote, null) is PersonState.Content)
    }

    // ---------- the Sort button ----------

    private fun withTitle(id: Int, title: String, rating: Float? = null) =
        Movie(id = id, title = title, posterPath = "/p$id.jpg", backdropPath = null, userRating = rating)

    private fun personWith(vararg movies: Movie) = PersonDetails(
        id = 1, name = "Someone", knownFor = "Acting", biography = "", profilePath = null,
        credits = mapOf(PersonRole.ACTOR to movies.toList())
    )

    @Test
    fun theUsualOrderIsTmdbsPopularityOrder() {
        val state = content(buildPersonState(remote(personWith(withTitle(1, "B"), withTitle(2, "A"), withTitle(3, "C"))), null))
        assertEquals(DEFAULT_SORT, state.sort)
        assertEquals(listOf(1, 2, 3), state.movies.map { it.id }) // as TMDB sent them
    }

    @Test
    fun theChosenSortOrdersTheMoviesOfTheTabOnScreen() {
        val remote = remote(personWith(withTitle(1, "B"), withTitle(2, "A"), withTitle(3, "C")))

        val byName = content(buildPersonState(remote, null, SortOption(SortField.NAME, SortDirection.ASCENDING)))
        assertEquals(listOf(2, 1, 3), byName.movies.map { it.id })

        val zToA = content(buildPersonState(remote, null, SortOption(SortField.NAME, SortDirection.DESCENDING)))
        assertEquals(listOf(3, 1, 2), zToA.movies.map { it.id })

        val leastPopular = content(buildPersonState(remote, null, SortOption(SortField.POPULARITY, SortDirection.ASCENDING)))
        assertEquals(listOf(3, 2, 1), leastPopular.movies.map { it.id })
    }

    @Test
    fun theSortAppliesToWhicheverRoleTabIsShown() {
        val person = PersonDetails(
            id = 1, name = "Someone", knownFor = "Acting", biography = "", profilePath = null,
            credits = mapOf(
                PersonRole.ACTOR to listOf(withTitle(1, "B"), withTitle(2, "A")),
                PersonRole.WRITER to listOf(withTitle(3, "Z"), withTitle(4, "Y"))
            )
        )
        val sort = SortOption(SortField.NAME, SortDirection.ASCENDING)
        assertEquals(listOf(2, 1), content(buildPersonState(remote(person), PersonRole.ACTOR, sort)).movies.map { it.id })
        assertEquals(listOf(4, 3), content(buildPersonState(remote(person), PersonRole.WRITER, sort)).movies.map { it.id })
    }

    @Test
    fun aMovieTheUserRatedWearsItsRatingAndCanBeSortedByIt() {
        val remote = remote(personWith(withTitle(1, "A"), withTitle(2, "B"), withTitle(3, "C")))
        val saved = mapOf(2 to withTitle(2, "B", rating = 4.5f), 3 to withTitle(3, "C", rating = 2.0f))

        val state = content(buildPersonState(remote, null, SortOption(SortField.USER_RATING, SortDirection.DESCENDING), saved))

        assertEquals(listOf(2, 3, 1), state.movies.map { it.id })
        assertEquals(4.5f, state.movies.first().userRating)
        assertTrue(SortField.USER_RATING in state.sortFields)
    }

    @Test
    fun withNoRatedMoviesTheRatingSortIsNotOffered() {
        val state = content(buildPersonState(remote(personWith(withTitle(1, "A"), withTitle(2, "B"))), null))
        assertFalse(SortField.USER_RATING in state.sortFields)
        assertTrue(SortField.NAME in state.sortFields)
    }
}
