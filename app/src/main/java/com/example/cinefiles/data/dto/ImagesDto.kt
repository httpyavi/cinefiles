package com.example.cinefiles.data.dto

import com.google.gson.annotations.SerializedName

/*
 * TMDB's answer of GET /movie/{id}/images: the official pictures of a movie. The Diary uses the [backdrops] (wide stills
 * and artwork) as pictures a user can put on a page. Like every DTO, all fields are nullable with a default.
 */

data class MovieImagesDto(
    val backdrops: List<ImageFileDto>? = null,
    val posters: List<ImageFileDto>? = null
)

/** One picture. [language] is null for a picture without any text on it (the nicest kind for a diary page). */
data class ImageFileDto(
    @SerializedName("file_path") val filePath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("iso_639_1") val language: String? = null
)

/**
 * The paths of the usable backdrops ("/abc123.jpg"), best first: pictures without text before pictures with text
 * (TMDB's own order, which is by votes, is kept inside each group), no repeats, at most [limit].
 */
fun MovieImagesDto?.toStillPaths(limit: Int = 30): List<String> =
    this?.backdrops.orEmpty()
        .filter { !it.filePath.isNullOrBlank() }
        .sortedBy { it.language != null } // stable: false (no text) comes first
        .mapNotNull { it.filePath }
        .distinct()
        .take(limit)
