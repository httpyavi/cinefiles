package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fonts, sizes, alignments and canvas themes of a diary page: what the page remembers, and what happens to a name nobody knows. */
class DiaryStylesTest {

    @Test
    fun thereAreFiveFontsThreeSizesFourAlignmentsAndFiveThemes() {
        assertEquals(5, DiaryFont.values().size)
        assertEquals(3, DiaryTextSize.values().size)
        assertEquals(4, TextAlign.values().size)
        assertEquals(5, DiaryTheme.values().size)
    }

    @Test
    fun aPageRemembersItsStyleAsTheNameOfTheChoice() {
        for (font in DiaryFont.values()) assertEquals(font, DiaryFont.of(font.name))
        for (size in DiaryTextSize.values()) assertEquals(size, DiaryTextSize.of(size.name))
        for (align in TextAlign.values()) assertEquals(align, TextAlign.of(align.name))
        for (theme in DiaryTheme.values()) assertEquals(theme, DiaryTheme.of(theme.name))
    }

    @Test
    fun anUnknownOrMissingNameFallsBackToTheDefaultSoAPageAlwaysOpens() {
        assertEquals(DiaryFont.SANS, DiaryFont.of(null))
        assertEquals(DiaryFont.SANS, DiaryFont.of("COMIC_SANS"))
        assertEquals(DiaryTextSize.STANDARD, DiaryTextSize.of(null))
        assertEquals(DiaryTextSize.STANDARD, DiaryTextSize.of("HUGE"))
        assertEquals(TextAlign.LEFT, TextAlign.of(null))
        assertEquals(TextAlign.LEFT, TextAlign.of("DIAGONAL"))
        assertEquals(DiaryTheme.MIDNIGHT, DiaryTheme.of(null))
        assertEquals(DiaryTheme.MIDNIGHT, DiaryTheme.of("neon"))
    }

    @Test
    fun theTwoThemesOfTheFirstDiaryKeepWorkingUnderTheirNewNames() {
        assertEquals(DiaryTheme.CHARCOAL, DiaryTheme.of("PARCHMENT"))
        assertEquals(DiaryTheme.NAVY, DiaryTheme.of("SLATE"))
    }

    @Test
    fun everyChoiceHasItsOwnLabelAndColors() {
        assertEquals(DiaryFont.values().size, DiaryFont.values().map { it.label }.toSet().size)
        assertEquals(DiaryTextSize.values().size, DiaryTextSize.values().map { it.label }.toSet().size)
        assertEquals(DiaryTheme.values().size, DiaryTheme.values().map { it.label }.toSet().size)
        assertEquals(DiaryTheme.values().size, DiaryTheme.values().map { it.background }.toSet().size)
        assertEquals(DiaryTheme.values().size, DiaryTheme.values().map { it.divider }.toSet().size)
    }

    @Test
    fun aFontNeverShrinksTheTextToUnreadableOrBlowsItUp() {
        assertTrue(DiaryFont.values().all { it.textScale in 0.85f..1.25f })
    }

    @Test
    fun textSizesGrowFromCompactToLargeAndTheHeadlineIsAlwaysBigger() {
        val sizes = DiaryTextSize.values().toList()
        assertEquals(sizes.sortedBy { it.bodySp }, sizes)
        assertTrue(sizes.all { it.headlineSp > it.bodySp })
    }
}
