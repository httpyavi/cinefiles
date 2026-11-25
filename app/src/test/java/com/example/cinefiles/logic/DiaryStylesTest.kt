package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fonts and themes of a diary page: what the page remembers, and what happens to a name nobody knows. */
class DiaryStylesTest {

    @Test
    fun thereAreFourFontsAndFiveThemes() {
        assertEquals(4, DiaryFont.values().size)
        assertEquals(5, DiaryTheme.values().size)
    }

    @Test
    fun aPageRemembersItsStyleAsTheNameOfTheChoice() {
        for (font in DiaryFont.values()) assertEquals(font, DiaryFont.of(font.name))
        for (theme in DiaryTheme.values()) assertEquals(theme, DiaryTheme.of(theme.name))
    }

    @Test
    fun anUnknownOrMissingNameFallsBackToTheFirstChoiceSoAPageAlwaysOpens() {
        assertEquals(DiaryFont.SANS, DiaryFont.of(null))
        assertEquals(DiaryFont.SANS, DiaryFont.of("COMIC_SANS"))
        assertEquals(DiaryTheme.MIDNIGHT, DiaryTheme.of(null))
        assertEquals(DiaryTheme.MIDNIGHT, DiaryTheme.of("neon"))
    }

    @Test
    fun everyChoiceHasItsOwnLabelAndColors() {
        assertEquals(DiaryFont.values().size, DiaryFont.values().map { it.label }.toSet().size)
        assertEquals(DiaryTheme.values().size, DiaryTheme.values().map { it.label }.toSet().size)
        assertEquals(DiaryTheme.values().size, DiaryTheme.values().map { it.background }.toSet().size)
    }

    @Test
    fun aFontNeverShrinksTheTextToUnreadableOrBlowsItUp() {
        assertTrue(DiaryFont.values().all { it.textScale in 0.85f..1.25f })
    }
}
