package com.example.cinefiles.util

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions
import com.example.cinefiles.R

/**
 * Every screen change in the app goes through the functions below, so all
 * navigation looks and behaves the same way. Fragments never build NavOptions themselves.
 */

/** The key under which a movie id travels to the Details screen. Must match nav_graph.xml. */
const val ARG_MOVIE_ID = "movieId"

/** The key under which a person id travels to the Person screen. Must match nav_graph.xml. */
const val ARG_PERSON_ID = "personId"

/** The key under which a diary page id travels to the Diary editor (0 = a new page). Must match nav_graph.xml. */
const val ARG_DIARY_ENTRY_ID = "entryId"

/**
 * How a screen change moves. FADE_THROUGH (old screen fades out, new one fades in and grows a little) is for tabs,
 * Details and every other change. SLIDE (the next step comes in from the right, Back goes the other way) is only for
 * the steps of the "watch something" flow: Mood, Preferences, Swipe, Your Picks.
 */
private enum class Motion { FADE_THROUGH, SLIDE }

/** Sets the motion of a screen change. With the phone's "Remove animations" on, there is none: the screen just changes. */
private fun NavOptionsBuilder.motion(controller: NavController, motion: Motion) {
    if (!controller.context.animationsEnabled()) return
    anim {
        when (motion) {
            Motion.FADE_THROUGH -> {
                enter = R.anim.fade_through_enter
                exit = R.anim.fade_through_exit
                popEnter = R.anim.fade_through_enter
                popExit = R.anim.fade_through_exit
            }
            Motion.SLIDE -> {
                enter = R.anim.slide_in_right
                exit = R.anim.slide_out_left
                popEnter = R.anim.slide_in_left
                popExit = R.anim.slide_out_right
            }
        }
    }
}

/**
 * Switches to one of the four tabs (Home, Discover, Collection, Profile).
 *
 * - Each tab keeps its own state (scroll position and so on) while you visit the other tabs.
 * - Back from any tab goes to Home; back from Home leaves the app.
 */
fun NavController.openTab(destinationId: Int) {
    if (currentDestination?.id == destinationId) return // already on this tab

    navigate(destinationId, null, navOptions {
        launchSingleTop = true
        restoreState = true
        // Go back to Home first, and remember (save) the tab we are leaving
        popUpTo(graph.startDestinationId) { saveState = true }
        motion(this@openTab, Motion.FADE_THROUGH)
    })
}

/**
 * Opens a normal screen on top of the current one (Mood, Details...). Back returns to the previous screen.
 * launchSingleTop (the default) means a double tap cannot open the same screen twice. But it also means a screen
 * that is already on top is REPLACED instead of stacked, so a screen that must stack on itself passes singleTop = false.
 * The steps of the "watch something" flow pass slide = true to slide in instead of fading.
 */
fun NavController.openScreen(
    destinationId: Int,
    args: Bundle? = null,
    singleTop: Boolean = true,
    slide: Boolean = false
) {
    navigate(destinationId, args, navOptions {
        launchSingleTop = singleTop
        motion(this@openScreen, if (slide) Motion.SLIDE else Motion.FADE_THROUGH)
    })
}

/**
 * Opens the Details screen for one movie.
 * A different movie opens ON TOP of the one you are looking at (tap a similar movie, then Back returns to the
 * first one). Tapping the SAME movie again while its Details is already open does nothing, so a double tap
 * cannot open it twice. With [returnIfOpenBelow] (the Diary's movie link) a Details of the same movie that is already further down the
 * back stack is returned to, not opened again.
 */
fun NavController.openMovieDetails(movieId: Int, returnIfOpenBelow: Boolean = false) {
    val alreadyOpen = currentDestination?.id == R.id.detailsFragment &&
        currentBackStackEntry?.arguments?.getInt(ARG_MOVIE_ID) == movieId
    if (alreadyOpen) return

    // The Diary reaches Details from a page that Details itself opened (Details -> Diary -> Details): go BACK to that Details instead of
    // stacking a second copy of it, so Back never walks through the same movie twice
    if (returnIfOpenBelow && hasMovieDetailsBelow(movieId) && popBackStack(R.id.detailsFragment, false)) return

    openScreen(R.id.detailsFragment, bundleOf(ARG_MOVIE_ID to movieId), singleTop = false)
}

/** Is the nearest Details screen in the back stack the one of [movieId]? (False when there is no Details at all.) */
private fun NavController.hasMovieDetailsBelow(movieId: Int): Boolean =
    runCatching { getBackStackEntry(R.id.detailsFragment) }.getOrNull()?.arguments?.getInt(ARG_MOVIE_ID) == movieId

/**
 * Opens the Person screen (an actor, a director or another crew member) for one TMDB person id.
 * Like Details, a different person opens ON TOP (movie -> person -> movie -> another person...), and tapping the SAME
 * person again while that screen is already open does nothing.
 */
fun NavController.openPerson(personId: Int) {
    val alreadyOpen = currentDestination?.id == R.id.personFragment &&
        currentBackStackEntry?.arguments?.getInt(ARG_PERSON_ID) == personId
    if (alreadyOpen) return
    openScreen(R.id.personFragment, bundleOf(ARG_PERSON_ID to personId), singleTop = false)
}

/**
 * Opens the Diary list. With a [movieId] it lists only the pages about that movie (and offers "New page" for it);
 * with 0 it is "My Diary", every page.
 */
fun NavController.openDiaryList(movieId: Int = 0) {
    openScreen(R.id.diaryListFragment, bundleOf(ARG_MOVIE_ID to movieId))
}

/**
 * Opens the Diary editor: the page [entryId] (a saved page), or a NEW page about [movieId] when [entryId] is 0.
 */
fun NavController.openDiaryEditor(entryId: Long = 0L, movieId: Int = 0) {
    openScreen(R.id.diaryEditorFragment, bundleOf(ARG_DIARY_ENTRY_ID to entryId, ARG_MOVIE_ID to movieId))
}
