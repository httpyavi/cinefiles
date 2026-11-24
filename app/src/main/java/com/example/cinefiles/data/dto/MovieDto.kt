package com.example.cinefiles.data.dto

import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.ContentFilter
import com.example.cinefiles.logic.Genres
import com.google.gson.annotations.SerializedName

/*
 * DTOs = the shapes of TMDB's JSON answers, exactly as TMDB sends them.
 * TMDB leaves out fields whenever it likes, so EVERY field here is nullable with a default.
 * Nothing in the app uses these classes directly: they are turned into [Movie] right away (toMovie below).
 */

/** The answer of every list call (trending, popular, top rated, discover, similar, search). */
data class MovieListResponse(
    val page: Int? = null,
    val results: List<MovieDto>? = null,
    @SerializedName("total_pages") val totalPages: Int? = null
)

/**
 * One movie. The same class is used for a movie inside a list and for the full details answer:
 * the details answer simply fills more fields (genres, runtime, credits, videos).
 */
data class MovieDto(
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
    // Lists give genre NUMBERS...
    @SerializedName("genre_ids") val genreIds: List<Int>? = null,
    // ...the details answer gives genres with names, and the length
    val genres: List<GenreDto>? = null,
    val runtime: Int? = null,
    val credits: CreditsDto? = null,
    val videos: VideosDto? = null,
    val adult: Boolean? = null
)

data class GenreDto(val id: Int? = null, val name: String? = null)

data class CreditsDto(
    val cast: List<CastDto>? = null,
    val crew: List<CrewDto>? = null
)

data class CastDto(
    val name: String? = null,
    val character: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null
)

data class CrewDto(val name: String? = null, val job: String? = null)

data class VideosDto(val results: List<VideoDto>? = null)

data class VideoDto(
    val key: String? = null,
    val site: String? = null,
    val type: String? = null,
    val name: String? = null
)

// ---------- The ONE place where TMDB's answer becomes our Movie ----------

/**
 * Turns TMDB's movie into our [Movie]. Returns null when it is not usable: no id or no title.
 *
 * - The rating is unknown (null) while nobody has voted: TMDB writes 0.0 for those movies, and a "0.0"
 *   rating on a card would be a lie.
 * - releaseYear is the first four letters of the date ("2014-11-05" gives 2014).
 */
fun MovieDto.toMovie(): Movie? {
    val movieId = id ?: return null
    if (adult == true) return null
    val movieTitle = (title ?: originalTitle)?.trim().orEmpty()
    if (movieTitle.isEmpty()) return null
    if (ContentFilter.isExplicit(movieTitle, originalTitle, overview)) return null

    val votes = voteCount ?: 0
    // The details answer has genres with names; a list only has numbers (names come from logic/Genres.kt)
    val detailGenres = genres.orEmpty().filter { it.id != null }
    val ids = if (detailGenres.isNotEmpty()) detailGenres.map { it.id!! } else genreIds.orEmpty()
    val names = if (detailGenres.isNotEmpty()) {
        detailGenres.map { it.name ?: Genres.nameOf(it.id!!) }.filterNotNull()
    } else {
        Genres.namesOf(ids)
    }

    return Movie(
        id = movieId,
        title = movieTitle,
        posterPath = posterPath?.ifBlank { null },
        backdropPath = backdropPath?.ifBlank { null },
        voteAverage = voteAverage?.takeIf { votes > 0 },
        releaseYear = yearOf(releaseDate),
        genreNames = names,
        genreIds = ids,
        voteCount = votes,
        originalLanguage = originalLanguage?.ifBlank { null },
        runtimeMinutes = runtime?.takeIf { it > 0 }, // 0 means "unknown" in TMDB's answer
        overview = overview.orEmpty(),
        releaseDate = releaseDate?.ifBlank { null }
    )
}

/**
 * Turns a TMDB list into movies. Movies that cannot be used are dropped, and so are movies without a
 * poster when [requirePoster] is true: a poster row or a swipe card with a blank picture looks broken.
 */
fun List<MovieDto>?.toMovies(requirePoster: Boolean = true): List<Movie> =
    orEmpty().mapNotNull { it.toMovie() }.filter { !requirePoster || it.posterPath != null }

/** "2014-11-05" gives 2014. Anything that does not start with four digits gives null. */
internal fun yearOf(releaseDate: String?): Int? {
    val text = releaseDate?.trim() ?: return null
    if (text.length < 4 || !text.take(4).all { it.isDigit() }) return null
    return text.take(4).toInt().takeIf { it > 0 }
}
