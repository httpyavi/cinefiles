package com.example.cinefiles.data

import com.example.cinefiles.data.dto.MovieDto
import com.example.cinefiles.data.dto.toMovie
import com.example.cinefiles.logic.Credits
import com.example.cinefiles.logic.CrewRole

/** One actor of a movie. [id] is the person's TMDB id: the Person screen opens with it. */
data class CastMember(val id: Int, val name: String, val character: String?, val profilePath: String?)

/** One crew member of a movie, with the [job] TMDB lists for them (for example "Director"). */
data class CrewMember(val id: Int, val name: String, val job: String, val profilePath: String?)

/**
 * Everything the Details screen needs about one movie that the lists do not tell.
 *
 * trailerKey is a YouTube video id, or null when TMDB lists no trailer.
 * directors are shown next to the title; crew holds the other groups (writers, producers, cinematography, composers),
 * see logic/Credits.kt. fullCrew is every group (the director, the editors, the designers too) with more names each, for
 * the "See all crew" sheet and for the short Crew section made from it. tagline, status ("Released") and productionCountries are null / empty when TMDB has none.
 */
data class MovieDetails(
    val movie: Movie,
    val cast: List<CastMember>,
    val directors: List<CrewMember>,
    val trailerKey: String?,
    val crew: Map<CrewRole, List<CrewMember>> = emptyMap(),
    val fullCrew: Map<CrewRole, List<CrewMember>> = emptyMap(),
    val tagline: String? = null,
    val status: String? = null,
    val productionCountries: List<String> = emptyList()
)

private const val MAX_CAST = 20

/** Builds [MovieDetails] from TMDB's details answer (fetched with credits and videos). Null when the movie is unusable. */
fun MovieDto.toDetails(): MovieDetails? {
    val movie = toMovie() ?: return null

    // TMDB lists the cast in billing order, so the first ones are the stars. Someone without an id cannot be opened.
    val cast = credits?.cast.orEmpty()
        .filter { it.id != null && !it.name.isNullOrBlank() }
        .take(MAX_CAST)
        .map { CastMember(it.id!!, it.name.orEmpty(), it.character?.ifBlank { null }, it.profilePath?.ifBlank { null }) }

    val crew = credits?.crew.orEmpty()
        .filter { it.id != null && !it.name.isNullOrBlank() && !it.job.isNullOrBlank() }
        .map { CrewMember(it.id!!, it.name.orEmpty(), it.job.orEmpty(), it.profilePath?.ifBlank { null }) }
    val groups = Credits.group(crew)
    val fullGroups = Credits.group(crew, Credits.MAX_FULL_PER_ROLE)

    val trailerKey = videos?.results.orEmpty()
        .firstOrNull { it.site == "YouTube" && it.type == "Trailer" && !it.key.isNullOrBlank() }
        ?.key

    return MovieDetails(
        movie = movie,
        cast = cast,
        directors = groups[CrewRole.DIRECTOR].orEmpty(),
        trailerKey = trailerKey,
        crew = groups - CrewRole.DIRECTOR,
        fullCrew = fullGroups,
        tagline = tagline?.trim()?.ifBlank { null },
        status = status?.trim()?.ifBlank { null },
        productionCountries = productionCountries.orEmpty().mapNotNull { it.name?.trim()?.ifBlank { null } }
    )
}
