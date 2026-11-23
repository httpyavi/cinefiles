package com.example.cinefiles.logic

/**
 * TMDB's 19 movie genres, written down once, so the app never has to ask TMDB's genre list.
 * (Moods.kt chooses WHICH genres a mood wants; this file only knows what each genre number is called.)
 */
object Genres {

    private val names: Map<Int, String> = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Science Fiction",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western"
    )

    /** How many genres TMDB has for movies. */
    val count: Int get() = names.size

    /** All genres as (id, name), in alphabetical order of the name: the genre chips of the Discover screen. */
    val all: List<Pair<Int, String>> by lazy { names.entries.map { it.key to it.value }.sortedBy { it.second } }

    /** The name of one genre, or null for a number we do not know. */
    fun nameOf(id: Int): String? = names[id]

    /** The names of these genres in the same order; numbers we do not know are left out. */
    fun namesOf(ids: List<Int>): List<String> = ids.mapNotNull { names[it] }
}
