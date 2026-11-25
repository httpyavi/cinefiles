package com.example.cinefiles.logic

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.example.cinefiles.R

/*
 * THE LOOK OF A DIARY PAGE: its font and its background theme (plain Kotlin, no screens).
 * [DiaryFont.textScale] is how much bigger or smaller than normal a font looks right (a serif reads smaller, a display font bigger).
 * Each page remembers its choice as the NAME of one of these (DiaryEntry.fontStyle / backgroundTheme).
 * An unknown or missing name (a page from a newer version, say) falls back to the first one, so a page always opens.
 */

/** The four built-in writing styles. DiaryTypefaces (ui/diary) turns them into real typefaces. */
enum class DiaryFont(@StringRes val label: Int, val textScale: Float) {
    SANS(R.string.diary_font_sans, 1f),            // Sans / Modern: the app's own font
    SERIF(R.string.diary_font_serif, 1.06f),       // Serif / Editorial: like a magazine
    MONO(R.string.diary_font_mono, 0.94f),         // Typewriter: monospaced, like a script
    DISPLAY(R.string.diary_font_display, 1.12f);   // Bold Display: big, heavy and condensed, for short lines

    companion object {
        fun of(name: String?): DiaryFont = values().firstOrNull { it.name == name } ?: SANS
    }
}

/**
 * Five dark, cinematic backgrounds. Every one keeps light text on a dark page, so the words stay readable.
 * [background] is the screen behind the page, [card] the cells (blocks) on it, [text] and [secondaryText] the words,
 * [accent] the little details (the cell's label, the cursor), all from values/colors.xml.
 */
enum class DiaryTheme(
    @StringRes val label: Int,
    @ColorRes val background: Int,
    @ColorRes val card: Int,
    @ColorRes val text: Int,
    @ColorRes val secondaryText: Int,
    @ColorRes val accent: Int
) {
    MIDNIGHT(
        R.string.diary_theme_midnight, R.color.diary_midnight_bg, R.color.diary_midnight_card,
        R.color.diary_midnight_text, R.color.diary_midnight_text_2, R.color.diary_midnight_accent
    ),
    CRIMSON(
        R.string.diary_theme_crimson, R.color.diary_crimson_bg, R.color.diary_crimson_card,
        R.color.diary_crimson_text, R.color.diary_crimson_text_2, R.color.diary_crimson_accent
    ),
    PARCHMENT(
        R.string.diary_theme_parchment, R.color.diary_parchment_bg, R.color.diary_parchment_card,
        R.color.diary_parchment_text, R.color.diary_parchment_text_2, R.color.diary_parchment_accent
    ),
    SLATE(
        R.string.diary_theme_slate, R.color.diary_slate_bg, R.color.diary_slate_card,
        R.color.diary_slate_text, R.color.diary_slate_text_2, R.color.diary_slate_accent
    ),
    SEPIA(
        R.string.diary_theme_sepia, R.color.diary_sepia_bg, R.color.diary_sepia_card,
        R.color.diary_sepia_text, R.color.diary_sepia_text_2, R.color.diary_sepia_accent
    );

    companion object {
        fun of(name: String?): DiaryTheme = values().firstOrNull { it.name == name } ?: MIDNIGHT
    }
}
