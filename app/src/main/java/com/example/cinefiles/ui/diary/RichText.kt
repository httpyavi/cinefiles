package com.example.cinefiles.ui.diary

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.text.HtmlCompat
import com.example.cinefiles.logic.DiaryBlock
import kotlin.math.max
import kotlin.math.min

/**
 * The bold and italic of a diary paragraph, with no library: a bold word is a [StyleSpan] on the text, and a block saves its text as HTML with
 * `HtmlCompat.toHtml` (and reads it back with `HtmlCompat.fromHtml`). Only bold and italic are ever written, so the HTML stays tiny.
 *
 * Toggling works like any editor: with words selected, the style is switched on (or off, when all of them already have it); with only a cursor,
 * it is switched for what is typed next (a zero-length span at the cursor grows as letters are typed into it).
 */
object RichText {

    // ---------- Saving and loading ----------

    /** What an editor shows for [block]: styled for a rich block, plain words with their line breaks otherwise. */
    fun toSpanned(block: DiaryBlock): SpannableStringBuilder {
        if (!block.html) return SpannableStringBuilder(block.text)
        val spanned = SpannableStringBuilder(HtmlCompat.fromHtml(block.text, HtmlCompat.FROM_HTML_MODE_COMPACT))
        // fromHtml ends every paragraph with a line break: the last one is not the user's
        while (spanned.isNotEmpty() && spanned[spanned.length - 1] == '\n') spanned.delete(spanned.length - 1, spanned.length)
        return spanned
    }

    /** The HTML to save for what is typed: only the bold and italic spans are kept (an input method may leave its own spans in the text). */
    fun toHtml(text: Spanned): String {
        val clean = SpannableStringBuilder(text.toString())
        for (span in text.getSpans(0, text.length, StyleSpan::class.java)) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (end > start && (span.style == Typeface.BOLD || span.style == Typeface.ITALIC || span.style == Typeface.BOLD_ITALIC)) {
                clean.setSpan(StyleSpan(span.style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return HtmlCompat.toHtml(clean, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE).trim()
    }

    // ---------- Asking ----------

    /**
     * Does [style] ([Typeface.BOLD] or [Typeface.ITALIC]) apply to the selection from [start] to [end]? For words: all of them have it.
     * For a cursor ([start] == [end]): the next letter typed will have it.
     */
    fun isActive(text: Spanned, style: Int, start: Int, end: Int): Boolean {
        val from = min(start, end)
        val to = max(start, end)
        val spans = styleSpans(text, style)
        if (from == to) {
            return spans.any { span ->
                val s = text.getSpanStart(span)
                val e = text.getSpanEnd(span)
                (s == from && e == from) || (s < from && from < e)
            }
        }
        // Every character of the selection is inside some span of the style
        var reached = from
        for (span in spans.sortedBy { text.getSpanStart(it) }) {
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            if (e <= reached) continue
            if (s > reached) return false
            reached = e
            if (reached >= to) return true
        }
        return reached >= to
    }

    // ---------- Changing ----------

    /** Switches [style] for the selection from [start] to [end] (see the class comment). */
    fun toggle(text: Editable, style: Int, start: Int, end: Int) {
        val from = min(start, end)
        val to = max(start, end)
        if (from == to) {
            toggleAtCursor(text, style, from)
        } else if (isActive(text, style, from, to)) {
            removeStyle(text, style, from, to)
        } else {
            removeStyle(text, style, from, to) // one clean span instead of overlapping pieces
            text.setSpan(StyleSpan(style), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun toggleAtCursor(text: Editable, style: Int, position: Int) {
        val spans = styleSpans(text, style)
        val marker = spans.firstOrNull { text.getSpanStart(it) == position && text.getSpanEnd(it) == position }
        if (marker != null) {
            text.removeSpan(marker) // the style was switched on for the next letters: switch it off again
            return
        }
        val inside = spans.firstOrNull { text.getSpanStart(it) < position && position < text.getSpanEnd(it) }
        if (inside != null) {
            // Switched off in the middle of styled words: the words stay styled on both sides, what is typed here is not
            val s = text.getSpanStart(inside)
            val e = text.getSpanEnd(inside)
            text.removeSpan(inside)
            text.setSpan(StyleSpan(style), s, position, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(StyleSpan(style), position, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return
        }
        // Switched on: an empty span at the cursor that grows with every letter typed into it
        text.setSpan(StyleSpan(style), position, position, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
    }

    /** Takes [style] away from [from] to [to], keeping it on the parts of the spans that stick out. */
    private fun removeStyle(text: Editable, style: Int, from: Int, to: Int) {
        for (span in styleSpans(text, style)) {
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            if (e <= from || s >= to) continue
            text.removeSpan(span)
            if (s < from) text.setSpan(StyleSpan(style), s, from, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (e > to) text.setSpan(StyleSpan(style), to, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun styleSpans(text: Spanned, style: Int): List<StyleSpan> =
        text.getSpans(0, text.length, StyleSpan::class.java).filter { it.style == style }
}
