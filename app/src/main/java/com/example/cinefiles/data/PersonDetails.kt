package com.example.cinefiles.data

import com.example.cinefiles.data.dto.PersonCreditDto
import com.example.cinefiles.data.dto.PersonDto
import com.example.cinefiles.data.dto.toMovie
import com.example.cinefiles.logic.PersonRole
import com.example.cinefiles.logic.PersonRoles

/**
 * Everything the Person screen shows about one actor, director or crew member.
 *
 * [credits] holds one list of movies per [PersonRole] the person really has (Actor from TMDB's cast, and Director,
 * Writer, Producer, Cinematographer, Composer, Editor or Crew from the jobs of TMDB's crew). A role with no movie is not in
 * the map. Each list has one entry per movie (a person can have several credits in one movie), the best known first,
 * and only movies that can be shown as a poster. A movie can be in several lists (a writer who also directed).
 * [roles] are the tabs: the roles that have movies, the person's primary role first (see [PersonRoles.order]).
 * [knownFor] is TMDB's "known for department" ("Acting", "Directing"...), null when TMDB has none.
 */
data class PersonDetails(
    val id: Int,
    val name: String,
    val knownFor: String?,
    val biography: String,
    val profilePath: String?,
    val credits: Map<PersonRole, List<Movie>>
) {
    val roles: List<PersonRole> = PersonRoles.order(credits.mapValues { it.value.size }, knownFor)

    /** Every movie of the person, once (to remember their titles and posters for Details). */
    val allMovies: List<Movie> get() = credits.values.flatten().distinctBy { it.id }
}

/** Builds [PersonDetails] from TMDB's person answer (fetched with movie_credits). Null when there is no id or name. */
fun PersonDto.toPersonDetails(): PersonDetails? {
    val personId = id ?: return null
    val personName = name?.trim().orEmpty()
    if (personName.isEmpty()) return null

    val byRole = LinkedHashMap<PersonRole, List<Movie>>()
    movieCredits?.cast.orEmpty().toFilmography().takeIf { it.isNotEmpty() }?.let { byRole[PersonRole.ACTOR] = it }
    movieCredits?.crew.orEmpty()
        .groupBy { PersonRoles.roleOfJob(it.job) }
        .forEach { (role, credits) -> credits.toFilmography().takeIf { it.isNotEmpty() }?.let { byRole[role] = it } }

    return PersonDetails(
        id = personId,
        name = personName,
        knownFor = knownForDepartment?.trim()?.ifBlank { null },
        biography = biography?.trim().orEmpty(),
        profilePath = profilePath?.ifBlank { null },
        credits = byRole
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
