package com.example.cinefiles.ui.diary

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.cinefiles.R
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryTheme

/*
 * Turns the look of a diary page (logic/DiaryStyles.kt: a font and a theme, just names and colors) into real typefaces and
 * colors on real views. The editor, the list of pages and the style sheet all use these, so a page looks the same everywhere.
 */

/** The typeface of a diary font. All four are built in (Poppins is the app's own font): nothing is downloaded. */
fun DiaryFont.typeface(context: Context): Typeface = when (this) {
    DiaryFont.SANS -> ResourcesCompat.getFont(context, R.font.poppins_regular) ?: Typeface.SANS_SERIF
    DiaryFont.SERIF -> Typeface.create("serif", Typeface.NORMAL)
    DiaryFont.MONO -> Typeface.MONOSPACE
    DiaryFont.DISPLAY -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
}

/** A theme color as a real color (the theme stores the resource ids). */
fun Context.themeColor(color: Int): Int = ContextCompat.getColor(this, color)

/**
 * Writes this text in a diary [font] at [baseSp] (scaled for the font) and in the theme's [color].
 * The text, the hint and the cursor all follow the theme.
 */
fun TextView.applyDiaryText(font: DiaryFont, theme: DiaryTheme, baseSp: Float, secondary: Boolean = false) {
    typeface = font.typeface(context)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * font.textScale)
    setTextColor(context.themeColor(if (secondary) theme.secondaryText else theme.text))
    setHintTextColor(context.themeColor(theme.secondaryText))
}
