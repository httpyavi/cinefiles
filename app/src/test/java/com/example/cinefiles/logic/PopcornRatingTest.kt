package com.example.cinefiles.logic

import com.example.cinefiles.logic.PopcornRating.IconState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The math behind the popcorn rating row: halves, taps, drags and how each bucket is drawn. */
class PopcornRatingTest {

    // The row used below: buckets 40 wide with a gap of 10, so bucket n starts at 50 * n
    private fun at(x: Float) = PopcornRating.ratingAt(x, iconSize = 40f, gap = 10f)

    // ---------- which rating a touch means ----------

    @Test
    fun theLeftHalfOfABucketIsAHalfAndTheRightHalfIsTheWholeBucket() {
        assertEquals(0.5f, at(5f))     // left half of the first bucket
        assertEquals(1.0f, at(30f))    // right half of the first bucket
        assertEquals(1.5f, at(55f))    // left half of the second
        assertEquals(2.0f, at(90f))    // right half of the second
        assertEquals(4.5f, at(205f))   // left half of the fifth
        assertEquals(5.0f, at(235f))   // right half of the fifth
    }

    @Test
    fun theMiddleOfABucketBelongsToTheLeftHalf() {
        assertEquals(0.5f, at(20f))
        assertEquals(1.0f, at(20.5f))
    }

    @Test
    fun theGapAfterABucketCountsAsItsWholeBucket() {
        assertEquals(1.0f, at(45f)) // between the first and the second bucket
        assertEquals(4.0f, at(195f))
    }

    @Test
    fun pastTheEndOfTheRowIsFiveAndOffTheStartIsNoRating() {
        assertEquals(5.0f, at(900f))
        assertNull(at(-0.5f))
        assertNull(at(-80f))
        assertEquals(0.5f, at(0f)) // the very first pixel is still on the first bucket
    }

    // ---------- tap = set, tap again = clear ----------

    @Test
    fun tappingTheSameRatingAgainClearsIt() {
        assertEquals(3.5f, PopcornRating.afterTap(current = null, tapped = 3.5f))
        assertEquals(4.0f, PopcornRating.afterTap(current = 3.5f, tapped = 4.0f))
        assertNull(PopcornRating.afterTap(current = 4.0f, tapped = 4.0f))
        assertNull(PopcornRating.afterTap(current = 4.0f, tapped = null))
    }

    // ---------- the value ----------

    @Test
    fun aRatingIsRoundedToTheNearestHalfAndKeptBetweenHalfAndFive() {
        assertEquals(3.5f, PopcornRating.normalize(3.5f))
        assertEquals(3.5f, PopcornRating.normalize(3.6f))
        assertEquals(4.0f, PopcornRating.normalize(3.8f))
        assertEquals(5.0f, PopcornRating.normalize(7f))
        assertEquals(0.5f, PopcornRating.normalize(0.3f))
    }

    @Test
    fun nothingOrZeroIsNoRating() {
        assertNull(PopcornRating.normalize(null))
        assertNull(PopcornRating.normalize(0f))
        assertNull(PopcornRating.normalize(0.2f))
        assertNull(PopcornRating.normalize(-1f))
        assertNull(PopcornRating.normalize(Float.NaN))
    }

    // ---------- how a bucket is drawn ----------

    @Test
    fun eachBucketIsEmptyHalfOrFullForTheRating() {
        val states = (0 until 5).map { PopcornRating.iconState(it, 3.5f) }
        assertEquals(listOf(IconState.FULL, IconState.FULL, IconState.FULL, IconState.HALF, IconState.EMPTY), states)
        assertEquals(IconState.HALF, PopcornRating.iconState(0, 0.5f))
        assertEquals(IconState.FULL, PopcornRating.iconState(4, 5.0f))
    }

    @Test
    fun noRatingMeansFiveEmptyBuckets() {
        assertEquals(List(5) { IconState.EMPTY }, (0 until 5).map { PopcornRating.iconState(it, null) })
    }

    @Test
    fun theNumberIsShownWithOneDecimalAndADot() {
        assertEquals("3.5", PopcornRating.format(3.5f))
        assertEquals("4.0", PopcornRating.format(4f))
    }
}
