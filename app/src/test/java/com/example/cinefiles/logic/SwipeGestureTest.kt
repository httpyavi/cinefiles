package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeGestureTest {

    // A card 1000 px wide: the swipe line is at 350 px. A flick counts from 1500 px/s.
    private fun decide(dx: Float, velocityX: Float) =
        SwipeGesture.decide(dx = dx, velocityX = velocityX, cardWidth = 1000f, flingVelocity = 1500f)

    @Test
    fun slowShortDragSpringsBack() {
        assertNull(decide(dx = 200f, velocityX = 100f))
        assertNull(decide(dx = -200f, velocityX = -100f))
    }

    @Test
    fun slowDragPastTheLineCommitsToTheSideItIsOn() {
        assertEquals(SwipeDirection.LIKE, decide(dx = 400f, velocityX = 0f))
        assertEquals(SwipeDirection.PASS, decide(dx = -400f, velocityX = 0f))
    }

    @Test
    fun justBeforeTheLineSpringsBack() {
        assertNull(decide(dx = 350f, velocityX = 0f))
        assertNull(decide(dx = -349f, velocityX = 0f))
    }

    @Test
    fun fastShortFlickCommits() {
        assertEquals(SwipeDirection.LIKE, decide(dx = 60f, velocityX = 2500f))
        assertEquals(SwipeDirection.PASS, decide(dx = -60f, velocityX = -2500f))
    }

    @Test
    fun flickBelowTheSpeedLimitDoesNotCommit() {
        assertNull(decide(dx = 60f, velocityX = 1400f))
    }

    @Test
    fun theFlickDecidesTheDirectionWhenItDisagreesWithTheSide() {
        // Dragged left past the line, then flicked hard to the right: the flick wins
        assertEquals(SwipeDirection.LIKE, decide(dx = -400f, velocityX = 3000f))
        // Dragged right a bit, then flicked hard to the left
        assertEquals(SwipeDirection.PASS, decide(dx = 100f, velocityX = -3000f))
    }
}
