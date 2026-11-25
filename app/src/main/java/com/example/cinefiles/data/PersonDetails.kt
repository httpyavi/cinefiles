package com.example.cinefiles.data

import com.example.cinefiles.data.dto.PersonCreditDto
import com.example.cinefiles.data.dto.PersonDto
import com.example.cinefiles.data.dto.toMovie

/**
 * Everything the Person screen shows about one actor, director or crew member.
 *
 * [acting] are the movies they acted in and [crew] the movies they worked on behind the camera (directing, writing...).
 * Both are one entry per movie, the best known first, and only movies that can be shown as a poster.
 * [knownFor] is TMDB's "known for department" ("Acting", "Directing"...), null when TMDB has none.
 */
data class PersonDetails(
    val id: Int,
    val name: String,
    val knownFor: String?,
    val biography: String,
    val profilePath: String?,
    val acting: List<Movie>,
    val crew: List<Movie>
)

/** Builds [PersonDetails] from TMDB's person answer (fetched with movie_credits). Null when there is no id or name. */
fun PersonDto.toPersonDetails(): PersonDetails? {
    val personId = id ?: return null
    val personName = name?.trim().orEmpty()
    if (personName.isEmpty()) return null

    return PersonDetails(
        id = personId,
        name = personName,
        knownFor = knownForDepartment?.trim()?.ifBlank { null },
        biography = biography?.trim().orEmpty(),
        profilePath = profilePath?.ifBlank { null },
        acting = movieCredits?.cast.orEmpty().toFilmography(),
        crew = movieCredits?.crew.orEmpty().toFilmography()
    )
}

/**
 * One poster per movie (a person can have several credits in one movie: writer AND producer), the most popular
 * movie first and, between equally popular ones, the newest. Movies that cannot be shown (no poster, explicit) are dropped.
 */
private fun List<PersonCreditDto>.toFilmography(): List<Movie> =
    filter { !it.asMovieDto().posterPath.isNullOrBlank() }
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<PersonCreditDto> { it.popularity ?: 0.0 }
                .thenByDescending { it.releaseDate.orEmpty() } // "2014-11-05" sorts like a date
                .thenBy { it.id ?: 0 }
        )
        .mapNotNull { it.asMovieDto().toMovie() }
