package com.example.cinefiles.data

import com.example.cinefiles.data.dto.MovieDto
import com.example.cinefiles.data.dto.toMovie

/** One actor of a movie. */
data class CastMember(val name: String, val character: String?, val profilePath: String?)

/**
 * Everything the Details screen will need about one movie (the screen itself arrives in a later batch;
 * this is only the data for it).
 *
 * trailerKey is a YouTube video id, or null when TMDB lists no trailer.
 */
data class MovieDetails(
    val movie: Movie,
    val cast: List<CastMember>,
    val directors: List<String>,
    val trailerKey: String?
)

private const val MAX_CAST = 10

/** Builds [MovieDetails] from TMDB's details answer (fetched with credits and videos). Null when the movie is unusable. */
fun MovieDto.toDetails(): MovieDetails? {
    val movie = toMovie() ?: return null

    // TMDB lists the cast in billing order, so the first ones are the stars
    val cast = credits?.cast.orEmpty()
        .filter { !it.name.isNullOrBlank() }
        .take(MAX_CAST)
        .map { CastMember(it.name.orEmpty(), it.character?.ifBlank { null }, it.profilePath?.ifBlank { null }) }

    val directors = credits?.crew.orEmpty()
        .filter { it.job == "Director" && !it.name.isNullOrBlank() }
        .map { it.name.orEmpty() }
        .distinct()

    val trailerKey = videos?.results.orEmpty()
        .firstOrNull { it.site == "YouTube" && it.type == "Trailer" && !it.key.isNullOrBlank() }
        ?.key

    return MovieDetails(movie, cast, directors, trailerKey)
}
