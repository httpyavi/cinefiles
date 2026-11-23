package com.example.cinefiles.data

/**
 * One page of a TMDB list, and how many pages the list has. A long list (Discover) asks for page after page
 * and stops when [hasMore] is false. [movies] can be shorter than TMDB's 20 because movies without a poster are
 * left out (even empty, while [hasMore] is still true), so never stop just because a page came back short.
 */
data class MoviePage(val movies: List<Movie>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}
