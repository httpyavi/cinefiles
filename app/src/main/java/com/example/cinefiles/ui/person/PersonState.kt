package com.example.cinefiles.ui.person

import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.PersonDetails

/** The two lists of a person's movies: the ones they acted in, and the ones they worked on behind the camera. */
enum class PersonTab { ACTING, CREW }

/** Everything the Person screen can be showing. PersonFragment draws whichever one it is given. */
sealed class PersonState {

    /** TMDB has not answered yet. */
    object Loading : PersonState()

    /** TMDB could not be reached (no internet, no key...). The screen offers Retry. */
    data class Error(val error: ApiError) : PersonState()

    /**
     * The screen can be drawn. [movies] is the list of the selected [tab]; it can be empty (TMDB lists no movies
     * for the person), and then the screen says so. [hasBothTabs]: the person has acted AND worked behind the
     * camera, so the screen offers the two tabs.
     */
    data class Content(
        val person: PersonDetails,
        val tab: PersonTab,
        val movies: List<Movie>,
        val hasBothTabs: Boolean
    ) : PersonState()
}

/** What TMDB has told us so far about the person. */
data class PersonRemote(
    val details: PersonDetails? = null,
    val error: ApiError? = null,
    val isLoading: Boolean = true
)

/** The tab to show first: "Acting" for a person known for acting, otherwise the crew list (when it has movies). */
fun defaultTabOf(person: PersonDetails): PersonTab = when {
    person.knownFor.equals("Acting", ignoreCase = true) && person.acting.isNotEmpty() -> PersonTab.ACTING
    person.crew.isNotEmpty() -> PersonTab.CREW
    else -> PersonTab.ACTING
}

/**
 * Puts the answer and the user's tab choice together into one [PersonState]. Plain Kotlin, so it can be tested.
 * [chosenTab] is what the user tapped (null: nothing yet). It only counts when the person has both lists; with
 * just one list, that list is shown and there is nothing to choose.
 */
fun buildPersonState(remote: PersonRemote, chosenTab: PersonTab?): PersonState {
    val person = remote.details
        ?: return if (remote.isLoading) PersonState.Loading else PersonState.Error(remote.error ?: ApiError.UNKNOWN)

    val hasBoth = person.acting.isNotEmpty() && person.crew.isNotEmpty()
    val tab = if (hasBoth) chosenTab ?: defaultTabOf(person) else defaultTabOf(person)
    val movies = if (tab == PersonTab.ACTING) person.acting else person.crew
    return PersonState.Content(person, tab, movies, hasBoth)
}
