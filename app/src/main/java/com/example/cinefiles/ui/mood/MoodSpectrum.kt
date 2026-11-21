package com.example.cinefiles.ui.mood

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.cinefiles.logic.Moods

/**
 * The colors of the nine moods along the slider, and how to blend between them.
 *
 * The blend goes through the COLOR WHEEL (hue), not straight between the two RGB values. Straight RGB
 * blending turns teal -> amber into a muddy olive; going around the wheel stays bright and clean.
 * (Everything here runs on the main thread only, so the little work arrays are shared.)
 */
object MoodSpectrum {

    private val hslA = FloatArray(3)
    private val hslB = FloatArray(3)
    private val hslOut = FloatArray(3)

    /** The nine colors, in slider order (the same order as [Moods.spectrum]). */
    fun colors(context: Context): IntArray =
        IntArray(Moods.spectrum.size) { ContextCompat.getColor(context, Moods.spectrum[it].tint) }

    /** The color at a place on the slider: 0f = first mood, 1f = second mood, 0.5f = halfway between them. */
    fun colorAt(colors: IntArray, position: Float): Int {
        val clamped = position.coerceIn(0f, (colors.size - 1).toFloat())
        val index = clamped.toInt().coerceAtMost(colors.size - 2)
        return blend(colors[index], colors[index + 1], clamped - index)
    }

    /** Blends two colors through the color wheel. t = 0 gives [from], t = 1 gives [to]. */
    fun blend(from: Int, to: Int, t: Float): Int {
        ColorUtils.colorToHSL(from, hslA)
        ColorUtils.colorToHSL(to, hslB)

        // Take the SHORT way around the wheel (350 -> 10 degrees goes through 0, not through 180)
        var hueChange = hslB[0] - hslA[0]
        if (hueChange > 180f) hueChange -= 360f else if (hueChange < -180f) hueChange += 360f

        hslOut[0] = (hslA[0] + hueChange * t + 360f) % 360f
        hslOut[1] = hslA[1] + (hslB[1] - hslA[1]) * t
        hslOut[2] = hslA[2] + (hslB[2] - hslA[2]) * t
        return ColorUtils.HSLToColor(hslOut)
    }
}
