package com.example.cinefiles.logic

import kotlin.math.floor

/*
 * THE MATH OF THE POPCORN RATING (plain Kotlin, no screens: easy to test).
 *
 * A rating is 0.5 to 5.0 in steps of 0.5 (five popcorn buckets, each one can be half), or null for "not rated".
 * Like Letterboxd's half stars: touch the LEFT half of a bucket for a half, the RIGHT half for the whole one.
 * Tapping the rating you already have clears it. PopcornRatingView (ui/components) only draws and passes touches here.
 */
object PopcornRating {

    const val COUNT = 5
    const val MIN = 0.5f
    const val MAX = 5.0f

    /** How one of the five buckets is drawn. */
    enum class IconState { EMPTY, HALF, FULL }

    /**
     * The nearest valid rating: rounded to the nearest half, never above 5. Null (and anything under a quarter, which
     * rounds to zero) means "no rating".
     */
    fun normalize(value: Float?): Float? {
        if (value == null || value.isNaN() || value < MIN / 2) return null
        return (Math.round(value * 2f) / 2f).coerceIn(MIN, MAX)
    }

    /** How bucket number [index] (0 = the first) looks for [rating]. */
    fun iconState(index: Int, rating: Float?): IconState {
        val value = rating ?: return IconState.EMPTY
        return when {
            value >= index + 1 -> IconState.FULL
            value >= index + 0.5f -> IconState.HALF
            else -> IconState.EMPTY
        }
    }

    /**
     * The rating that a touch [x] pixels from the left edge of the row means. Each bucket is [iconSize] wide, followed by
     * [gap]. Left half of a bucket = a half, right half (and the gap after it) = the whole bucket; past the end = 5.0;
     * left of the first bucket = null (the finger was dragged off the start: no rating).
     */
    fun ratingAt(x: Float, iconSize: Float, gap: Float): Float? {
        if (x < 0f) return null
        val slot = iconSize + gap
        val index = floor(x / slot).toInt().coerceAtMost(COUNT - 1)
        val withinIcon = (x - index * slot) / iconSize
        val value = index + if (withinIcon <= 0.5f) 0.5f else 1f
        return value.coerceIn(MIN, MAX)
    }

    /** What the rating becomes after a TAP on [tapped]: tapping the rating you already have clears it. */
    fun afterTap(current: Float?, tapped: Float?): Float? = if (tapped == null || tapped == current) null else tapped

    /** The number as shown next to the buckets: "3.5", "4.0" (always one decimal, a dot in every language). */
    fun format(rating: Float): String = String.format(java.util.Locale.US, "%.1f", rating)
}
