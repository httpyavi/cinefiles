package com.example.cinefiles.util

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions
import com.example.cinefiles.R

/**
 * Every screen change in the app goes through the three functions below, so all
 * navigation looks and behaves the same way. Fragments never build NavOptions themselves.
 */

/** The key under which a movie id travels to the Details screen. Must match nav_graph.xml. */
const val ARG_MOVIE_ID = "movieId"

/** The short "fade through" animation (old screen fades out, new one fades in). */
private fun NavOptionsBuilder.fadeThrough() {
    anim {
        enter = R.anim.fade_through_enter
        exit = R.anim.fade_through_exit
        popEnter = R.anim.fade_through_enter
        popExit = R.anim.fade_through_exit
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
        fadeThrough()
    })
}

/**
 * Opens a normal screen on top of the current one (Mood, Details...). Back returns to the previous screen.
 * launchSingleTop means a double tap cannot open the same screen twice.
 */
fun NavController.openScreen(destinationId: Int, args: Bundle? = null) {
    navigate(destinationId, args, navOptions {
        launchSingleTop = true
        fadeThrough()
    })
}

/** Opens the Details screen for one movie. */
fun NavController.openMovieDetails(movieId: Int) {
    openScreen(R.id.detailsFragment, bundleOf(ARG_MOVIE_ID to movieId))
}
