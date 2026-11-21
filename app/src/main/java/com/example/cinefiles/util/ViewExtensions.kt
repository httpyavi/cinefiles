package com.example.cinefiles.util

import android.animation.ObjectAnimator
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * The app draws edge-to-edge (behind the status and navigation bars).
 * Call this on a screen's root view so its content is pushed away from the bars.
 *
 * It ADDS the bar heights to the padding the view already has in its XML.
 * Pass top = false for a screen that wants its image to run under the status bar.
 */
fun View.applySystemBarsPadding(top: Boolean = true, bottom: Boolean = true) {
    val paddingTopFromXml = paddingTop
    val paddingBottomFromXml = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(
            top = paddingTopFromXml + if (top) bars.top else 0,
            bottom = paddingBottomFromXml + if (bottom) bars.bottom else 0
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

private const val SHAKE_DISTANCE_DP = 8f
private const val SHAKE_DURATION_MS = 320L
