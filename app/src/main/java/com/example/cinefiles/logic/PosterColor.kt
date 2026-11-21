package com.example.cinefiles.logic

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Picks ONE color that feels like a poster, to tint the light behind the swipe cards.
 *
 * No library: it looks at a small copy of the poster (a list of pixel colors) and averages the hue of the
 * pixels that are colorful and bright enough, giving the most colorful pixels the most say. Dark and gray pixels
 * are ignored, because they say nothing about the poster's color. The answer is then made rich but not
 * too bright, so it works as a glow on a dark screen.
 *
 * Plain Kotlin on purpose (no android.graphics), so it can be tested.
 */
object PosterColor {

    /** What a poster without any color (black and white, very dark) gets: a calm blue-gray. */
    const val NEUTRAL = 0xFF6F7C99.toInt()

    /** [pixels] are ARGB colors, for example from a bitmap scaled down to 48 x 72. */
    fun glowFor(pixels: IntArray): Int {
        var hueX = 0.0
        var hueY = 0.0
        var weightSum = 0.0
        var saturationSum = 0.0
        var valueSum = 0.0

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255f
            val g = (pixel shr 8 and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            val high = max(r, max(g, b))
            val low = min(r, min(g, b))
            val value = high
            val saturation = if (high == 0f) 0f else (high - low) / high

            // Dark or gray pixels do not tell us anything about color
            if (value < MIN_VALUE || saturation < MIN_SATURATION) continue

            val weight = (saturation * value).toDouble() // the more colorful and bright, the more it counts
            val radians = hueOf(r, g, b, high, low) * PI / 180.0
            hueX += cos(radians) * weight   // hue is a circle: average it as a direction, not as a number
            hueY += sin(radians) * weight
            saturationSum += saturation * weight
            valueSum += value * weight
            weightSum += weight
        }

        // Hardly any colorful pixels: a black-and-white poster
        if (weightSum < pixels.size * ENOUGH_COLOR) return NEUTRAL

        var hue = atan2(hueY, hueX) * 180.0 / PI
        if (hue < 0) hue += 360.0
        val saturation = (saturationSum / weightSum).toFloat().coerceIn(GLOW_MIN_SATURATION, GLOW_MAX_SATURATION)
        val value = (valueSum / weightSum).toFloat().coerceIn(GLOW_MIN_VALUE, GLOW_MAX_VALUE)
        return colorOf(hue.toFloat(), saturation, value)
    }

    /** The hue (0..360 degrees) of a color whose largest channel is [high] and smallest is [low]. */
    private fun hueOf(r: Float, g: Float, b: Float, high: Float, low: Float): Float {
        val spread = high - low
        if (spread == 0f) return 0f
        val sector = when (high) {
            r -> ((g - b) / spread) % 6f
            g -> (b - r) / spread + 2f
            else -> (r - g) / spread + 4f
        }
        val degrees = sector * 60f
        return if (degrees < 0f) degrees + 360f else degrees
    }

    /** An opaque ARGB color from hue (0..360), saturation and value (0..1). */
    fun colorOf(hue: Float, saturation: Float, value: Float): Int {
        val sector = hue / 60f
        val whole = floor(sector).toInt() % 6
        val fraction = sector - floor(sector)
        val p = value * (1f - saturation)
        val q = value * (1f - saturation * fraction)
        val t = value * (1f - saturation * (1f - fraction))
        val (r, g, b) = when (whole) {
            0 -> Triple(value, t, p)
            1 -> Triple(q, value, p)
            2 -> Triple(p, value, t)
            3 -> Triple(p, q, value)
            4 -> Triple(t, p, value)
            else -> Triple(value, p, q)
        }
        return (0xFF shl 24) or ((r * 255f + 0.5f).toInt() shl 16) or ((g * 255f + 0.5f).toInt() shl 8) or (b * 255f + 0.5f).toInt()
    }

    private const val MIN_VALUE = 0.18f
    private const val MIN_SATURATION = 0.15f
    private const val ENOUGH_COLOR = 0.01       // at least this much of the poster must have color
    private const val GLOW_MIN_SATURATION = 0.45f
    private const val GLOW_MAX_SATURATION = 0.9f
    private const val GLOW_MIN_VALUE = 0.55f
    private const val GLOW_MAX_VALUE = 0.9f
}
