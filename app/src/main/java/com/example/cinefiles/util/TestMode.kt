package com.example.cinefiles.util

import androidx.annotation.StringRes
import com.example.cinefiles.R

// TEMP: ways to force a screen into each of its states, so loading / error / empty can be tested
// on a phone. Used by Home and by the swipe screen: long-press the name "Movie Lover" on Home, or the
// mood caption at the top of the swipe screen, to step to the next mode.
// Removed in Batch 5, when those screens have real loading and errors (see TEMP.md).
enum class TestMode(@StringRes val label: Int) {
    NORMAL(R.string.temp_test_normal),
    LOADING_FOREVER(R.string.temp_test_loading),
    FAIL_ONCE(R.string.temp_test_error), // fails the next load; Retry then works
    EMPTY(R.string.temp_test_empty)
}
