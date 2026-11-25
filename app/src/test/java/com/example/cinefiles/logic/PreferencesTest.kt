package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesTest {

    @Test
    fun everyChoiceStartsAtAny() {
        val preferences = Preferences()
        assertEquals(ReleasePeriod.ANY, preferences.releasePeriod)
        assertEquals(MinRating.ANY, preferences.minRating)
        assertEquals(Length.ANY, preferences.length)
        assertNull(preferences.language) // any language
    }

    @Test
    fun anyIsTheFirstChipOfEveryGroup() {
        assertEquals(ReleasePeriod.ANY, ReleasePeriod.values().first())
        assertEquals(MinRating.ANY, MinRating.values().first())
        assertEquals(Length.ANY, Length.values().first())
    }

    @Test
    fun groupsHaveTheChipsFromTheBrief() {
        // (the language is not a group of chips any more: it is a search field, see LanguagesTest)
        assertEquals(4, ReleasePeriod.values().size) // Any, Before 2000, 2000-2014, 2015+
        assertEquals(4, MinRating.values().size)     // Any, 6+, 7+, 8+
        assertEquals(4, Length.values().size)        // Any, Under 90 min, Under 2 hrs, 2 hrs+
    }

    @Test
    fun changingOneChoiceKeepsTheOthers() {
        val changed = Preferences().copy(minRating = MinRating.SEVEN_PLUS)
        assertEquals(MinRating.SEVEN_PLUS, changed.minRating)
        assertEquals(ReleasePeriod.ANY, changed.releasePeriod)
        assertNull(changed.language)

        val korean = changed.copy(language = "ko")
        assertEquals("ko", korean.language)
        assertEquals(MinRating.SEVEN_PLUS, korean.minRating)
    }
}
