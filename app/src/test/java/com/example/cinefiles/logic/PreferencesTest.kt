package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesTest {

    @Test
    fun everyChoiceStartsAtAny() {
        val preferences = Preferences()
        assertEquals(ReleasePeriod.ANY, preferences.releasePeriod)
        assertEquals(MinRating.ANY, preferences.minRating)
        assertEquals(Length.ANY, preferences.length)
        assertEquals(Language.ANY, preferences.language)
    }

    @Test
    fun anyIsTheFirstChipOfEveryGroup() {
        assertEquals(ReleasePeriod.ANY, ReleasePeriod.values().first())
        assertEquals(MinRating.ANY, MinRating.values().first())
        assertEquals(Length.ANY, Length.values().first())
        assertEquals(Language.ANY, Language.values().first())
    }

    @Test
    fun groupsHaveTheChipsFromTheBrief() {
        assertEquals(4, ReleasePeriod.values().size) // Any, Before 2000, 2000-2014, 2015+
        assertEquals(4, MinRating.values().size)     // Any, 6+, 7+, 8+
        assertEquals(4, Length.values().size)        // Any, Under 90 min, Under 2 hrs, 2 hrs+
        assertEquals(5, Language.values().size)      // Any, English, Filipino, Korean, Japanese
    }

    @Test
    fun changingOneChoiceKeepsTheOthers() {
        val changed = Preferences().copy(minRating = MinRating.SEVEN_PLUS)
        assertEquals(MinRating.SEVEN_PLUS, changed.minRating)
        assertEquals(ReleasePeriod.ANY, changed.releasePeriod)
        assertEquals(Language.ANY, changed.language)
    }
}
