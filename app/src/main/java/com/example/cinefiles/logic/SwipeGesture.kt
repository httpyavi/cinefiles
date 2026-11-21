package com.example.cinefiles.logic

import kotlin.math.abs

/**
 * The rule for what happens when a finger LETS GO of a swipe card after dragging it.
 * It is plain math (no Android), so it can be tested on its own. SwipeDeckView asks it.
 */
object SwipeGesture {

    /** Dragged past this part of the card's width = a swipe, even when the finger was slow. */
    const val COMMIT_FRACTION = 0.35f

    /**
     * @param dx how far the finger is from where it touched down (pixels, + is right)
     * @param velocityX how fast the finger was moving sideways when it lifted (pixels per second, + is right)
     * @param cardWidth the width of the card (pixels)
     * @param flingVelocity the speed (pixels per second) from which a flick counts as a swipe
     * @return LIKE or PASS when the card should fly away, or null when it should spring back
     */
    fun decide(dx: Float, velocityX: Float, cardWidth: Float, flingVelocity: Float): SwipeDirection? {
        val isFling = abs(velocityX) > flingVelocity
        val farEnough = abs(dx) > cardWidth * COMMIT_FRACTION
        if (!isFling && !farEnough) return null

        // A fast flick decides the direction; otherwise the side the card is on does
        val toTheRight = if (isFling) velocityX > 0f else dx > 0f
        return if (toTheRight) SwipeDirection.LIKE else SwipeDirection.PASS
    }
}
