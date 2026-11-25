package com.example.cinefiles.util

import android.animation.ObjectAnimator
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import com.example.cinefiles.R

/**
 * The app draws edge-to-edge (behind the status and navigation bars).
 * Call this on a screen's root view so its content is pushed away from the bars.
 *
 * It ADDS the bar heights to the padding the view already has in its XML.
 * Pass top = false for a screen that wants its image to run under the status bar.
 *
 * The bottom padding is also at least the height of the on-screen KEYBOARD: an edge-to-edge window is not resized by
 * the keyboard (adjustResize does nothing there), so a bar at the bottom (the Diary editor's formatting strip and the
 * "Start swiping" footer) would otherwise be hidden behind it while the user types.
 */
fun View.applySystemBarsPadding(top: Boolean = true, bottom: Boolean = true) {
    val paddingTopFromXml = paddingTop
    val paddingBottomFromXml = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        view.updatePadding(
            top = paddingTopFromXml + if (top) bars.top else 0,
            bottom = paddingBottomFromXml + if (bottom) maxOf(bars.bottom, keyboardHeight) else 0
        )
        insets // not consumed, so other views can still read them
    }
    ViewCompat.requestApplyInsets(this)
}

/** A quick left-right wiggle that means "no, you cannot do that" (about a third of a second). */
fun View.shake() {
    val distance = SHAKE_DISTANCE_DP * resources.displayMetrics.density
    ObjectAnimator.ofFloat(this, View.TRANSLATION_X, 0f, -distance, distance, -distance, distance, 0f)
        .setDuration(SHAKE_DURATION_MS)
        .start()
}

/** Puts a small gold star in front of this text (the rating of a movie), or takes it away. */
fun TextView.showRatingStar(show: Boolean) {
    setCompoundDrawablesRelativeWithIntrinsicBounds(if (show) R.drawable.ic_star_meta else 0, 0, 0, 0)
    TextViewCompat.setCompoundDrawableTintList(this, ContextCompat.getColorStateList(context, R.color.star))
    compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.space_4)
}

private const val SHAKE_DISTANCE_DP = 8f
private const val SHAKE_DURATION_MS = 320L
