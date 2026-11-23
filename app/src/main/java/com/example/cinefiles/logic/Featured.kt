package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie

/** The big movie at the top of Home is chosen among this many of the first trending movies. */
const val FEATURED_CANDIDATES = 5

/**
 * Chooses the featured movie for Home: it changes from day to day, so Home does not look the same every morning.
 *
 * Only movies with a backdrop can be featured (the hero is a wide picture). From the first five of those,
 * the day of the year picks one: day 1 takes the first, day 2 the second, ... and day 6 starts over.
 * [dayOfYear] is 1 to 366 (Calendar.DAY_OF_YEAR). Returns null when no trending movie has a backdrop.
 */
fun pickFeatured(trending: List<Movie>, dayOfYear: Int): Movie? {
    val candidates = trending.filter { it.backdropPath != null }.take(FEATURED_CANDIDATES)
    if (candidates.isEmpty()) return null
    return candidates[Math.floorMod(dayOfYear, candidates.size)]
}
