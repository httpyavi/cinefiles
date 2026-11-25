package com.example.cinefiles.ui.diary

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.cinefiles.R
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryTheme
import com.example.cinefiles.logic.TextAlign

/*
 * Turns the look of a diary page (logic/DiaryStyles.kt: a font, a size, an alignment and a theme, just names and colors) into real
 * typefaces and colors on real views. The editor, the list of pages and the style sheet all use these, so a page looks the same everywhere.
 */

/** The typeface of a diary font. All five are built in (Poppins is the app's own font): nothing is downloaded. */
fun DiaryFont.typeface(context: Context): Typeface = when (this) {
    DiaryFont.SERIF -> Typeface.create("serif", Typeface.NORMAL)
    DiaryFont.SANS -> ResourcesCompat.getFont(context, R.font.poppins_regular) ?: Typeface.SANS_SERIF
    DiaryFont.MONO -> Typeface.MONOSPACE
    DiaryFont.DISPLAY -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
    DiaryFont.LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
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

/**
 * Lines this text up as [align]: left, center, right, or justified (every line the full width: Android 8 and newer, older phones show it on the left).
 */
fun TextView.applyAlignment(align: TextAlign) {
    val horizontal = when (align) {
        TextAlign.CENTER -> Gravity.CENTER_HORIZONTAL
        TextAlign.RIGHT -> Gravity.END
        TextAlign.LEFT, TextAlign.JUSTIFY -> Gravity.START
    }
    gravity = Gravity.TOP or horizontal
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        justificationMode = if (align == TextAlign.JUSTIFY) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE
    }
}
