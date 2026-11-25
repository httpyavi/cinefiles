package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R

// The choices on the "Make it yours" screen. Every group starts at ANY ("no preference").
// Each enum is also the list of chips shown for that group, in this order.
// logic/Discovery.kt turns these choices into filters when it builds the swipe deck.

enum class ReleasePeriod(@StringRes val label: Int) {
    ANY(R.string.pref_any),
    BEFORE_2000(R.string.pref_period_before_2000),
    FROM_2000_TO_2014(R.string.pref_period_2000_2014),
    FROM_2015(R.string.pref_period_2015_plus)
}

enum class MinRating(@StringRes val label: Int) {
    ANY(R.string.pref_any),
    SIX_PLUS(R.string.pref_rating_6),
    SEVEN_PLUS(R.string.pref_rating_7),
    EIGHT_PLUS(R.string.pref_rating_8)
}

enum class Length(@StringRes val label: Int) {
    ANY(R.string.pref_any),
    UNDER_90_MIN(R.string.pref_length_under_90),
    UNDER_2_HOURS(R.string.pref_length_under_2h),
    OVER_2_HOURS(R.string.pref_length_over_2h)
}

/**
 * Everything the user chose on the Preferences screen. The default is "Any" everywhere.
 * [language] is a TMDB language code ("ko", "en"...: see logic/Languages.kt, which the screen's search field picks from), or null for any language.
 * These choices only belong to the "Let's watch something" flow (the swipe deck and Your Picks): the Discover tab never uses them.
 */
data class Preferences(
    val releasePeriod: ReleasePeriod = ReleasePeriod.ANY,
    val minRating: MinRating = MinRating.ANY,
    val length: Length = Length.ANY,
    val language: String? = null
)
