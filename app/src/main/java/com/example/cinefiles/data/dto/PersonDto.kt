package com.example.cinefiles.data.dto

import com.google.gson.annotations.SerializedName

/*
 * TMDB's answer of GET /person/{id}?append_to_response=movie_credits, exactly as TMDB sends it.
 * Like every DTO, all fields are nullable with a default. It becomes a PersonDetails in data/PersonDetails.kt.
 */

data class PersonDto(
    val id: Int? = null,
    val name: String? = null,
    val biography: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("known_for_department") val knownForDepartment: String? = null,
    @SerializedName("movie_credits") val movieCredits: PersonCreditsDto? = null
)

/** The movies of the person: the ones they acted in ([cast]) and the ones they worked on behind the camera ([crew]). */
data class PersonCreditsDto(
    val cast: List<PersonCreditDto>? = null,
    val crew: List<PersonCreditDto>? = null
)

/**
 * One movie of a person's filmography. It looks like a movie of a list answer, plus [popularity] (which decides
 * the order on the Person screen) and the role: [character] for an actor, [job] for a crew member.
 */
data class PersonCreditDto(
    val id: Int? = null,
    val title: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("vote_count") val voteCount: Int? = null,
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int>? = null,
    val popularity: Double? = null,
    val adult: Boolean? = null,
    val character: String? = null,
    val job: String? = null
) {
    /** The same movie as a [MovieDto], so it goes through the ONE place that decides what a usable movie is. */
    fun asMovieDto() = MovieDto(
        id = id, title = title, originalTitle = originalTitle, posterPath = posterPath, backdropPath = backdropPath,
        overview = overview, releaseDate = releaseDate, voteAverage = voteAverage, voteCount = voteCount,
        originalLanguage = originalLanguage, genreIds = genreIds, adult = adult
    )
}
