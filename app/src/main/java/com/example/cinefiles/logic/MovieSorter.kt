package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie

/*
 * HOW A LIST OF POSTERS IS SORTED (plain Kotlin, no screens: easy to test).
 *
 * One sorting system for every poster grid that has a Sort button: My Collection (the Liked / Watchlist / Watched tab on screen) and the Person
 * screen (the role tab on screen). The bottom sheet (ui/components/SortSheet) lets the user pick a [SortField] and a [SortDirection]; this
 * file does the sorting. A movie that does not have the value being sorted on (no rating yet, no runtime known) always goes LAST, whichever
 * direction is chosen; movies that are equal keep a fixed order (by name, then id), so a list never shuffles by chance.
 */

enum class SortDirection { ASCENDING, DESCENDING }

/**
 * What a list can be sorted on. Each field has two directions, worded the way people say them ([firstLabel] / [secondLabel]: "A to Z" / "Z to A",
 * "Newest" / "Earliest"...). [firstDirection] is the direction of the first wording, and the first one is what a fresh choice of the field uses.
 */
enum class SortField(
    @StringRes val label: Int,
    @StringRes val firstLabel: Int,
    @StringRes val secondLabel: Int,
    val firstDirection: SortDirection
) {
    /** When the movie got into the tab: liked, put on the watchlist, or marked watched. Only My Collection has it. */
    WHEN_ADDED(R.string.sort_when_added, R.string.sort_newest, R.string.sort_earliest, SortDirection.DESCENDING),

    /** TMDB's own order for a person's movies: the most popular first. Only the Person screen has it. (Reversed = the least popular first.) */
    POPULARITY(R.string.sort_popularity, R.string.sort_highest, R.string.sort_lowest, SortDirection.DESCENDING),

    NAME(R.string.sort_name, R.string.sort_a_z, R.string.sort_z_a, SortDirection.ASCENDING),
    RELEASE_DATE(R.string.sort_release_date, R.string.sort_newest, R.string.sort_earliest, SortDirection.DESCENDING),
    USER_RATING(R.string.sort_user_rating, R.string.sort_highest, R.string.sort_lowest, SortDirection.DESCENDING),
    RUNTIME(R.string.sort_runtime, R.string.sort_longest, R.string.sort_shortest, SortDirection.DESCENDING),
    TMDB_RATING(R.string.sort_tmdb_rating, R.string.sort_highest, R.string.sort_lowest, SortDirection.DESCENDING);

    /** The wording of [direction] for this field. */
    @StringRes
    fun labelOf(direction: SortDirection): Int = if (direction == firstDirection) firstLabel else secondLabel

    /** The other direction. */
    fun opposite(direction: SortDirection): SortDirection =
        if (direction == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
}

/** One choice of the Sort sheet: a field and a direction. */
data class SortOption(val field: SortField, val direction: SortDirection) {
    /** The same field in the direction of its first wording ("Newest", "A to Z"...). */
    companion object {
        fun firstOf(field: SortField) = SortOption(field, field.firstDirection)
    }
}

object MovieSorter {

    /** What My Collection can sort on, the default (newest first) at the top. */
    val COLLECTION_FIELDS = listOf(
        SortField.WHEN_ADDED, SortField.NAME, SortField.RELEASE_DATE, SortField.USER_RATING, SortField.RUNTIME, SortField.TMDB_RATING
    )

    /** What a person's works can be sorted on, TMDB's popularity order (the default) at the top. */
    val PERSON_FIELDS = listOf(
        SortField.POPULARITY, SortField.NAME, SortField.RELEASE_DATE, SortField.USER_RATING, SortField.RUNTIME, SortField.TMDB_RATING
    )

    /** The sort a screen starts with: the first field, in the direction of its first wording. */
    fun defaultOf(fields: List<SortField>): SortOption = SortOption.firstOf(fields.first())

    /**
     * The fields worth offering for these [movies]: the first one (the default), the name, the release date, and any other field at least
     * one movie has a value for. Sorting a list of movies without a single rating by "Your rating" would change nothing, so it is not offered.
     */
    fun availableFields(movies: List<Movie>, fields: List<SortField>): List<SortField> = fields.filter { field ->
        when (field) {
            SortField.USER_RATING -> movies.any { it.userRating != null }
            SortField.RUNTIME -> movies.any { it.runtimeMinutes != null }
            SortField.TMDB_RATING -> movies.any { it.voteAverage != null }
            else -> true
        }
    }

    /**
     * [movies] in the order of [option]. [addedAt] tells the time a movie got into the tab being sorted (the sort "When added" uses it);
     * [SortField.POPULARITY] keeps the order the list already has (TMDB's, most popular first) or reverses it.
     */
    fun sort(movies: List<Movie>, option: SortOption, addedAt: (Movie) -> Long = { 0L }): List<Movie> {
        val direction = option.direction
        val comparator: Comparator<Movie> = when (option.field) {
            SortField.POPULARITY -> return if (direction == SortDirection.DESCENDING) movies else movies.asReversed().toList()
            SortField.WHEN_ADDED -> by(direction) { addedAt(it).takeIf { time -> time > 0L } }
            SortField.NAME -> by(direction) { nameKey(it.title) }
            SortField.RELEASE_DATE -> by(direction) { releaseKey(it) }
            SortField.USER_RATING -> by(direction) { it.userRating }
            SortField.RUNTIME -> by(direction) { it.runtimeMinutes }
            SortField.TMDB_RATING -> by(direction) { it.voteAverage }
        }
        return movies.sortedWith(comparator.thenBy { nameKey(it.title) }.thenBy { it.id })
    }

    /** Compares by a key. A movie without the key is last in both directions. */
    private fun <T : Comparable<T>> by(direction: SortDirection, key: (Movie) -> T?): Comparator<Movie> = Comparator { a, b ->
        val x = key(a)
        val y = key(b)
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            direction == SortDirection.ASCENDING -> x.compareTo(y)
            else -> y.compareTo(x)
        }
    }

    private val LEADING_ARTICLE = Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE)

    /** The title as a library would file it: lower case, without a leading "The", "A" or "An" ("The Godfather" sits under G). */
    fun nameKey(title: String): String {
        val trimmed = title.trim()
        val withoutArticle = trimmed.replace(LEADING_ARTICLE, "")
        return (withoutArticle.ifEmpty { trimmed }).lowercase()
    }

    /** "2014-11-05" sorts like a date. A movie with only a year sorts at the start of that year. */
    private fun releaseKey(movie: Movie): String? =
        movie.releaseDate?.takeIf { it.isNotBlank() } ?: movie.releaseYear?.let { "%04d-00-00".format(it) }
}
