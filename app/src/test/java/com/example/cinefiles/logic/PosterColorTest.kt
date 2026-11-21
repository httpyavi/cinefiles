package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterColorTest {

    private fun solid(argb: Int, count: Int = 100) = IntArray(count) { argb }

    private fun hueOf(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        val spread = high - low
        val sector = when (high) {
            r -> ((g - b) / spread) % 6f
            g -> (b - r) / spread + 2f
            else -> (r - g) / spread + 4f
        }
        val degrees = sector * 60f
        return if (degrees < 0f) degrees + 360f else degrees
    }

    @Test
    fun aRedPosterGivesARedGlow() {
        val glow = PosterColor.glowFor(solid(0xFFCC2020.toInt()))
        val hue = hueOf(glow)
        assertTrue("hue $hue", hue < 10f || hue > 350f)
    }

    @Test
    fun aBluePosterGivesABlueGlow() {
        val hue = hueOf(PosterColor.glowFor(solid(0xFF2040CC.toInt())))
        assertEquals(228f, hue, 8f)
    }

    @Test
    fun anOrangeAndTealPosterLeansToTheMoreColorfulHalf() {
        // 70 bright orange pixels and 30 dim teal ones: orange wins
        val pixels = solid(0xFFFF8020.toInt(), 70) + solid(0xFF206060.toInt(), 30)
        val hue = hueOf(PosterColor.glowFor(pixels))
        assertTrue("hue $hue", hue in 10f..45f)
    }

    @Test
    fun hueAveragingGoesAroundTheCircle() {
        // 350 degrees and 10 degrees are both red: the average must be red, not green (180)
        val pixels = solid(PosterColor.colorOf(350f, 0.8f, 0.8f), 50) + solid(PosterColor.colorOf(10f, 0.8f, 0.8f), 50)
        val hue = hueOf(PosterColor.glowFor(pixels))
        assertTrue("hue $hue", hue < 10f || hue > 350f)
    }

    @Test
    fun blackAndWhiteAndGrayPostersGetTheNeutralColor() {
        assertEquals(PosterColor.NEUTRAL, PosterColor.glowFor(solid(0xFF000000.toInt())))
        assertEquals(PosterColor.NEUTRAL, PosterColor.glowFor(solid(0xFF808080.toInt())))
        assertEquals(PosterColor.NEUTRAL, PosterColor.glowFor(solid(0xFFFFFFFF.toInt())))
    }

    @Test
    fun theGlowIsAlwaysRichButNotBlinding() {
        for (color in listOf(0xFFFFFF00.toInt(), 0xFF110000.toInt(), 0xFF33FFAA.toInt())) {
            val glow = PosterColor.glowFor(solid(color))
            val r = (glow shr 16 and 0xFF) / 255f
            val g = (glow shr 8 and 0xFF) / 255f
            val b = (glow and 0xFF) / 255f
            val value = maxOf(r, g, b)
            assertTrue("value $value", value in 0.54f..0.91f)
        }
    }
}
