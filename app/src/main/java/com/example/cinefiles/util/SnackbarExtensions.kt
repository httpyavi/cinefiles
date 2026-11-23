package com.example.cinefiles.util

import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.google.android.material.snackbar.Snackbar

/*
 * Every short message at the bottom of a screen goes through these helpers, so they look and behave the same:
 * the same length, and on the four tab screens they sit ABOVE the floating tab bar instead of behind it.
 */

/**
 * What an action did, so that Undo can take it back: the line to show ("Added to your watchlist") and the movie as
 * it was BEFORE (every Undo puts that saved state back, see MovieRepository.restoreState).
 */
class UndoInfo(@StringRes val message: Int, val before: Movie)

/** Shows [message]. Do [aboveTabBar] on a tab screen. Nothing happens when the screen has no view any more. */
fun Fragment.showMessage(@StringRes message: Int, aboveTabBar: Boolean = false) =
    show(Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG), aboveTabBar)

/** Shows [message] (text made by the screen, for example with a movie title in it). */
fun Fragment.showMessage(message: CharSequence, aboveTabBar: Boolean = false) =
    show(Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG), aboveTabBar)

/** Shows the line of [info] with an Undo button; [onUndo] gets the movie as it was before. */
fun Fragment.showUndo(info: UndoInfo, aboveTabBar: Boolean = false, onUndo: (Movie) -> Unit) =
    show(
        Snackbar.make(requireView(), info.message, Snackbar.LENGTH_LONG).setAction(R.string.action_undo) { onUndo(info.before) },
        aboveTabBar
    )

/** Shows [message] with a button that does [onAction] (Retry, for example). */
fun Fragment.showMessage(@StringRes message: Int, @StringRes actionLabel: Int, aboveTabBar: Boolean = false, onAction: () -> Unit) =
    show(Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).setAction(actionLabel) { onAction() }, aboveTabBar)

/**
 * Shows [message] on the whole window instead of on this screen, above the tab bar. For a message that must stay
 * after the screen has gone (Clear my data sends you Home and says it was done there).
 */
fun Fragment.showWindowMessage(@StringRes message: Int) =
    show(Snackbar.make(requireActivity().findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG), aboveTabBar = true)

private fun Fragment.show(snackbar: Snackbar, aboveTabBar: Boolean) {
    if (aboveTabBar) snackbar.setAnchorView(requireActivity().findViewById<View>(R.id.bottomNav))
    snackbar.show()
}
