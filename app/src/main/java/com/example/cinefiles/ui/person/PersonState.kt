package com.example.cinefiles.ui.person

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.PersonDetails
import com.example.cinefiles.logic.MovieSorter
import com.example.cinefiles.logic.PersonRole
import com.example.cinefiles.logic.SortField
import com.example.cinefiles.logic.SortOption
import com.example.cinefiles.logic.withStateOf

/** One role tab of the Person screen: the role and how many movies the person has in it ("Director (8)"). */
data class PersonTab(val role: PersonRole, val count: Int)

/** Everything the Person screen can be showing. PersonFragment draws whichever one it is given. */
sealed class PersonState {

    /** TMDB has not answered yet. */
    object Loading : PersonState()

    /** TMDB could not be reached (no internet, no key...). The screen offers Retry. */
    data class Error(val error: ApiError) : PersonState()

    /**
     * The screen can be drawn. [tabs] are the roles the person really has, the primary one first (logic/PersonRoles.kt);
     * [role] is the selected one and [movies] its movies (one poster per movie) in the chosen [sort] (logic/MovieSorter.kt); [sortFields] are the
     * fields the Sort sheet should offer for them. With no movie at all, [tabs] is empty,
     * [role] is null and the screen says so. A single role needs no choice, so the screen only shows the tabs when
     * there are at least two ([showTabs]).
     */
    data class Content(
        val person: PersonDetails,
        val role: PersonRole?,
        val movies: List<Movie>,
        val tabs: List<PersonTab>,
        val sort: SortOption = DEFAULT_SORT,
        val sortFields: List<SortField> = MovieSorter.PERSON_FIELDS
    ) : PersonState() {
        val showTabs: Boolean get() = tabs.size > 1
    }
}

/** The usual order of a person's works: TMDB's popularity order, the most popular first. */
val DEFAULT_SORT: SortOption = MovieSorter.defaultOf(MovieSorter.PERSON_FIELDS)

/** What TMDB has told us so far about the person. */
data class PersonRemote(
    val details: PersonDetails? = null,
    val error: ApiError? = null,
    val isLoading: Boolean = true
)

/**
 * Puts the answer and the user's tab choice together into one [PersonState]. Plain Kotlin, so it can be tested.
 * [chosenRole] is what the user tapped (null: nothing yet, or a role this person does not have, then the first tab).
 * [sort] is the order chosen with the Sort button; it applies to whichever role tab is shown.
 * [saved] are the movies the user has a state for (by id): a movie of the person that is one of them wears its state, so it shows the user's own
 * popcorn rating and can be sorted by it.
 */
fun buildPersonState(
    remote: PersonRemote,
    chosenRole: PersonRole?,
    sort: SortOption = DEFAULT_SORT,
    saved: Map<Int, Movie> = emptyMap()
): PersonState {
    val person = remote.details
        ?: return if (remote.isLoading) PersonState.Loading else PersonState.Error(remote.error ?: ApiError.UNKNOWN)

    val tabs = person.roles.map { PersonTab(it, person.credits.getValue(it).size) }
    val role = chosenRole?.takeIf { it in person.roles } ?: person.roles.firstOrNull()
    val credits = role?.let { person.credits[it] }.orEmpty().map { it.withStateOf(saved[it.id]) }
    return PersonState.Content(
        person, role, MovieSorter.sort(credits, sort), tabs, sort,
        sortFields = MovieSorter.availableFields(credits, MovieSorter.PERSON_FIELDS)
    )
}
