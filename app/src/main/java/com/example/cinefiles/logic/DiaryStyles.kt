package com.example.cinefiles.logic

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.example.cinefiles.R

/*
 * THE LOOK OF A DIARY PAGE: its font, text size, alignment and canvas theme (plain Kotlin, no screens).
 * Each page remembers its choices as the NAME of one of these (DiaryEntry.fontStyle / textSize / textAlignment / canvasTheme).
 * An unknown or missing name (a page from a newer version, say) falls back to the default one, so a page always opens.
 */

/**
 * The five writing fonts. [textScale] is how much bigger or smaller than normal a font looks right (a serif reads smaller,
 * a display font bigger). DiaryTypefaces (ui/diary/DiaryStyleExtensions.kt) turns them into real typefaces.
 */
enum class DiaryFont(@StringRes val label: Int, val textScale: Float) {
    SERIF(R.string.diary_font_serif, 1.06f),       // Editorial Serif: like a magazine
    SANS(R.string.diary_font_sans, 1f),            // Modern Sans: the app's own font
    MONO(R.string.diary_font_mono, 0.94f),         // Classic Typewriter: monospaced, like a script
    DISPLAY(R.string.diary_font_display, 1.12f),   // Cinema Display: big, heavy and condensed, for short lines
    LIGHT(R.string.diary_font_light, 1f);          // Clean Light: thin and airy

    companion object {
        val DEFAULT = SANS

        fun of(name: String?): DiaryFont = values().firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** How big the writing is: [bodySp] for the paragraphs, [headlineSp] for the page's headline. */
enum class DiaryTextSize(@StringRes val label: Int, val bodySp: Float, val headlineSp: Float) {
    COMPACT(R.string.diary_size_compact, 15f, 24f),
    STANDARD(R.string.diary_size_standard, 17f, 28f),
    LARGE(R.string.diary_size_large, 20f, 33f);

    companion object {
        val DEFAULT = STANDARD

        fun of(name: String?): DiaryTextSize = values().firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** How the headline and the paragraphs are lined up on the page. */
enum class TextAlign {
    LEFT, CENTER, RIGHT, JUSTIFY;

    companion object {
        val DEFAULT = LEFT

        fun of(name: String?): TextAlign = values().firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Five dark, cinematic canvases. Every one keeps light text on a dark page, so the words stay readable.
 * [background] is the screen behind the page, [card] the page's card in the list, [text] and [secondaryText] the words,
 * [accent] the little details (the movie link, the cursor, the pull-quote bar) and [divider] the thin lines, all from values/colors.xml.
 */
enum class DiaryTheme(
    @StringRes val label: Int,
    @ColorRes val background: Int,
    @ColorRes val card: Int,
    @ColorRes val text: Int,
    @ColorRes val secondaryText: Int,
    @ColorRes val accent: Int,
    @ColorRes val divider: Int
) {
    MIDNIGHT(
        R.string.diary_theme_midnight, R.color.diary_midnight_bg, R.color.diary_midnight_card,
        R.color.diary_midnight_text, R.color.diary_midnight_text_2, R.color.diary_midnight_accent, R.color.diary_midnight_divider
    ),
    CHARCOAL(
        R.string.diary_theme_charcoal, R.color.diary_charcoal_bg, R.color.diary_charcoal_card,
        R.color.diary_charcoal_text, R.color.diary_charcoal_text_2, R.color.diary_charcoal_accent, R.color.diary_charcoal_divider
    ),
    NAVY(
        R.string.diary_theme_navy, R.color.diary_navy_bg, R.color.diary_navy_card,
        R.color.diary_navy_text, R.color.diary_navy_text_2, R.color.diary_navy_accent, R.color.diary_navy_divider
    ),
    CRIMSON(
        R.string.diary_theme_crimson, R.color.diary_crimson_bg, R.color.diary_crimson_card,
        R.color.diary_crimson_text, R.color.diary_crimson_text_2, R.color.diary_crimson_accent, R.color.diary_crimson_divider
    ),
    SEPIA(
        R.string.diary_theme_sepia, R.color.diary_sepia_bg, R.color.diary_sepia_card,
        R.color.diary_sepia_text, R.color.diary_sepia_text_2, R.color.diary_sepia_accent, R.color.diary_sepia_divider
    );

    companion object {
        val DEFAULT = MIDNIGHT

        /** The theme called [name]. The two themes of the first diary ("PARCHMENT" and "SLATE") became CHARCOAL and NAVY. */
        fun of(name: String?): DiaryTheme = when (name) {
            "PARCHMENT" -> CHARCOAL
            "SLATE" -> NAVY
            else -> values().firstOrNull { it.name == name } ?: DEFAULT
        }
    }
}
