package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie

/**
 * Multi-layer filter to exclude sexually explicit films, erotica, and pornography
 * while strictly preserving mainstream romance, mature drama, and non-explicit storylines.
 */
object ContentFilter {

    /**
     * TMDB Production Company IDs that primarily distribute erotic/softcore adult releases (e.g. Vivamax).
     */
    const val EXCLUDED_COMPANIES = "149142,173083"

    /**
     * TMDB Keyword IDs for erotic, softcore, and adult sexual classifications.
     */
    const val EXCLUDED_KEYWORDS =
        "321739,256466,155477,190370,343572,207767,11512,323690,155691,302868,298666,226010,240530,382870,180544,6054"

    /**
     * Matches explicit sexual content, erotica, and pornography without matching
     * mainstream romance, kissing, mature relationships, or non-explicit nudity.
     */
    private val EXPLICIT_PATTERN = Regex(
        "\\b(erotic\\s+film|erotic\\s+movie|erotic\\s+thriller|erotic\\s+drama|erotic\\s+romance|erotica|softcore|sexfilm|threesome|foursome|sexcapade|creampie|sexually\\s+explicit|hardcore\\s+sex|adult\\s+video|pornographic|pornography|porno)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isExplicit(title: String?, originalTitle: String?, overview: String?): Boolean {
        if (!title.isNullOrBlank() && EXPLICIT_PATTERN.containsMatchIn(title)) return true
        if (!originalTitle.isNullOrBlank() && EXPLICIT_PATTERN.containsMatchIn(originalTitle)) return true
        if (!overview.isNullOrBlank() && EXPLICIT_PATTERN.containsMatchIn(overview)) return true
        return false
    }

    fun isExplicit(movie: Movie): Boolean =
        isExplicit(movie.title, null, movie.overview)
}

fun Movie.isExplicit(): Boolean = ContentFilter.isExplicit(this)
