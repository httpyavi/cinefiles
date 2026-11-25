package com.example.cinefiles.data

/**
 * The movies Your Picks may choose from (see MovieRepository.picksCandidates).
 *
 * [movies] has no repeats, no poster-less movies and no movie the user already has a state for.
 * [similarTo] covers the movies that came from TMDB's "similar" list of a swiped-right movie:
 * candidate id -> the id of that swiped-right movie. The engine uses it to say "Similar to ...".
 */
data class PicksPool(val movies: List<Movie>, val similarTo: Map<Int, Int> = emptyMap())
