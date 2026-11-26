package com.example.cinefiles.ui.diary

import android.graphics.Typeface
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.text.HtmlCompat
import kotlin.math.max
import kotlin.math.min

/**
 * The rich text of a diary page, with no library: words are written with Android's own spans and saved as HTML with `HtmlCompat.toHtml`
 * (read back with `HtmlCompat.fromHtml`). Only a handful of things are ever written, so the HTML stays tiny and reads back the same:
 *
 *  - INLINE: bold ([Typeface.BOLD]), italic ([Typeface.ITALIC]) and [UNDERLINE]: `<b>`, `<i>`, `<u>`;
 *  - one KIND per paragraph ([Block]): a plain paragraph `<p>`, a HEADING (a bold, larger paragraph: `<h2>`) or a PULL-QUOTE (a bar and an
 *    indent: `<blockquote>`). toHtml of the framework does not know a heading, so every paragraph is written on its own and given its tag here.
 *
 * Toggling works like any editor: with words selected, the style is switched on (or off, when all of them already have it); with only a cursor,
 * it is switched for what is typed next (a zero-length span at the cursor grows as letters are typed into it).
 */
object RichText {

    /** The "style" of the underline, next to [Typeface.BOLD] and [Typeface.ITALIC] (they are all passed to [toggle] and [isActive]). */
    const val UNDERLINE = 100

    /** The kind of a paragraph. */
    enum class Block { BODY, HEADING, QUOTE }

    /** Per-paragraph alignment (each paragraph is aligned independently). */
    enum class ParagraphAlign { LEFT, CENTER, RIGHT }

    private const val HEADING_SIZE = 1.4f      // what an <h2> is in HtmlCompat.fromHtml
    private const val HEADING_MIN_SIZE = 1.2f  // a paragraph covered by a bigger span than this is a heading
    private const val INLINE_START_END = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE

    private val OPEN_PARAGRAPH = Regex("^<p[^>]*>")
    private const val CLOSE_PARAGRAPH = "</p>"
    private const val EMPTY_PARAGRAPH = "<p dir=\"ltr\"><br></p>"

    // ---------- Saving and loading ----------

    /**
     * What an editor shows for [html]: styled text. The plain blue [QuoteSpan] that fromHtml makes for a `<blockquote>` becomes a [PullQuoteSpan]
     * in [quoteColor], and the spans are tidied (one bold span per heading, no twin spans) so that saving and loading again never changes them.
     */
    fun toSpanned(html: String, quoteColor: Int, quoteBar: Int, quoteGap: Int): SpannableStringBuilder {
        val spanned = SpannableStringBuilder(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT))
        // fromHtml ends every paragraph with a line break: the last one is not the user's
        while (spanned.isNotEmpty() && spanned[spanned.length - 1] == '\n') spanned.delete(spanned.length - 1, spanned.length)
        tidy(spanned)
        restyleQuotes(spanned, quoteColor, quoteBar, quoteGap)
        return spanned
    }

    /** The HTML to save for [text]: one tag per paragraph, only bold, italic and underline inside (an input method may leave its own spans). */
    fun toHtml(text: Spanned): String {
        val whole = text.toString()
        val ranges = ArrayList<IntRange>()
        var start = 0
        while (true) {
            val newline = whole.indexOf('\n', start)
            val end = if (newline < 0) whole.length else newline
            ranges += start until end
            if (newline < 0) break
            start = newline + 1
        }
        while (ranges.isNotEmpty() && ranges.last().isEmpty()) ranges.removeAt(ranges.lastIndex) // trailing blank lines are not worth keeping
        return ranges.joinToString("") { paragraphHtml(text, it.first, it.last + 1) }
    }

    private fun paragraphHtml(text: Spanned, start: Int, end: Int): String {
        val align = alignmentOf(text, start, end)
        val alignAttr = when (align) {
            ParagraphAlign.CENTER -> " style=\"text-align:center;\""
            ParagraphAlign.RIGHT -> " style=\"text-align:right;\""
            ParagraphAlign.LEFT -> ""
        }
        if (start == end) {
            return if (alignAttr.isEmpty()) EMPTY_PARAGRAPH else "<p dir=\"ltr\"$alignAttr><br></p>"
        }
        val block = blockOf(text, start, end)
        val slice = SpannableStringBuilder(text.subSequence(start, end)) // the spans are cut to the paragraph
        val clean = SpannableStringBuilder(slice.toString())
        for (span in slice.getSpans(0, slice.length, Any::class.java)) {
            val s = slice.getSpanStart(span)
            val e = slice.getSpanEnd(span)
            if (e <= s) continue
            when {
                span is StyleSpan && span.style in INLINE_STYLES -> {
                    // a heading is bold by itself
                    val style = if (block == Block.HEADING) span.style and Typeface.BOLD.inv() else span.style
                    if (style != Typeface.NORMAL) clean.setSpan(StyleSpan(style), s, e, INLINE_START_END)
                }
                span is UnderlineSpan && !isComposing(slice, span) -> clean.setSpan(UnderlineSpan(), s, e, INLINE_START_END)
            }
        }
        val inner = HtmlCompat.toHtml(clean, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE).trim()
            .replace(OPEN_PARAGRAPH, "").removeSuffix(CLOSE_PARAGRAPH)
        return when (block) {
            Block.BODY -> "<p dir=\"ltr\"$alignAttr>$inner</p>"
            Block.HEADING -> "<h2$alignAttr>$inner</h2>"
            Block.QUOTE -> "<blockquote$alignAttr><p dir=\"ltr\"$alignAttr>$inner</p></blockquote>"
        }
    }

    // ---------- Asking ----------

    /**
     * Does [style] ([Typeface.BOLD], [Typeface.ITALIC] or [UNDERLINE]) apply to the selection from [start] to [end]? For words: all of them
     * have it. For a cursor ([start] == [end]): the next letter typed will have it.
     */
    fun isActive(text: Spanned, style: Int, start: Int, end: Int): Boolean {
        val from = min(start, end)
        val to = max(start, end)
        val spans = spansOf(text, style)
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

    /** The kind of the paragraph that has the cursor (or the one the selection starts in). */
    fun blockAt(text: Spanned, start: Int): Block {
        val from = paragraphStart(text, start)
        return blockOf(text, from, paragraphEnd(text, start))
    }

    /** The alignment of the paragraph that has the cursor (or the one the selection starts in). */
    fun alignmentAt(text: Spanned, position: Int): ParagraphAlign {
        val from = paragraphStart(text, position)
        return alignmentOf(text, from, paragraphEnd(text, position))
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
            text.setSpan(newSpan(style), from, to, INLINE_START_END)
        }
    }

    /**
     * Makes the paragraphs of the selection from [start] to [end] a [target] paragraph. When the paragraph with the cursor already is one,
     * they all go back to plain paragraphs (so the same button switches it on and off).
     */
    fun toggleBlock(text: Editable, target: Block, start: Int, end: Int, quoteColor: Int, quoteBar: Int, quoteGap: Int) {
        val from = paragraphStart(text, min(start, end))
        val to = paragraphEnd(text, max(start, end))
        val result = if (blockAt(text, min(start, end)) == target) Block.BODY else target

        removeBlocks(text, from, to)
        when (result) {
            Block.HEADING -> {
                text.setSpan(RelativeSizeSpan(HEADING_SIZE), from, to, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                text.setSpan(StyleSpan(Typeface.BOLD), from, to, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            }
            Block.QUOTE -> splitIntoQuotes(text, from, to, quoteColor, quoteBar, quoteGap)
            Block.BODY -> Unit
        }
    }

    /** Sets independent [align] on the paragraph(s) overlapping [start]..[end] without changing other paragraphs. */
    fun setAlignment(text: Editable, align: ParagraphAlign, start: Int, end: Int) {
        val from = paragraphStart(text, min(start, end))
        val to = paragraphEnd(text, max(start, end))
        removeAlignmentSpans(text, from, to)
        if (align != ParagraphAlign.LEFT) {
            splitAlignmentSpans(text, from, to, align)
        }
    }

    /** Gives every pull-quote the bar of [color] (the page's accent changes with its theme). */
    fun restyleQuotes(text: Editable, color: Int, bar: Int, gap: Int) {
        for (span in text.getSpans(0, text.length, QuoteSpan::class.java)) {
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            text.removeSpan(span)
            setQuote(text, s, e, color, bar, gap)
        }
    }

    // ---------- Inside: inline styles ----------

    private val INLINE_STYLES = intArrayOf(Typeface.BOLD, Typeface.ITALIC, Typeface.BOLD_ITALIC)

    private fun newSpan(style: Int): Any = if (style == UNDERLINE) UnderlineSpan() else StyleSpan(style)

    private fun spansOf(text: Spanned, style: Int): List<Any> =
        if (style == UNDERLINE) {
            text.getSpans(0, text.length, UnderlineSpan::class.java).filter { !isComposing(text, it) }
        } else {
            text.getSpans(0, text.length, StyleSpan::class.java).filter { it.style == style }
        }

    /** The keyboard underlines the word it is composing with an UnderlineSpan flagged "composing": that one is not the user's. */
    private fun isComposing(text: Spanned, span: Any) = text.getSpanFlags(span) and Spanned.SPAN_COMPOSING != 0

    private fun toggleAtCursor(text: Editable, style: Int, position: Int) {
        val spans = spansOf(text, style)
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
            text.setSpan(newSpan(style), s, position, INLINE_START_END)
            text.setSpan(newSpan(style), position, e, INLINE_START_END)
            return
        }
        // Switched on: an empty span at the cursor that grows with every letter typed into it
        text.setSpan(newSpan(style), position, position, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
    }

    /** Takes [style] away from [from] to [to], keeping it on the parts of the spans that stick out. */
    private fun removeStyle(text: Editable, style: Int, from: Int, to: Int) {
        for (span in spansOf(text, style)) {
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            if (e <= from || s >= to) continue
            text.removeSpan(span)
            if (s < from) text.setSpan(newSpan(style), s, from, INLINE_START_END)
            if (e > to) text.setSpan(newSpan(style), to, e, INLINE_START_END)
        }
    }

    // ---------- Inside: paragraphs ----------

    private fun paragraphStart(text: CharSequence, position: Int): Int {
        val before = min(position, text.length)
        return if (before == 0) 0 else text.lastIndexOf('\n', before - 1) + 1
    }

    private fun paragraphEnd(text: CharSequence, position: Int): Int {
        val newline = text.indexOf('\n', min(position, text.length))
        return if (newline < 0) text.length else newline
    }

    private fun blockOf(text: Spanned, start: Int, end: Int): Block {
        val quoted = text.getSpans(0, text.length, QuoteSpan::class.java).any { span ->
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            s <= start && (e > start || (end == start && s == start && e == start))
        }
        if (quoted) return Block.QUOTE
        val heading = text.getSpans(start, end, RelativeSizeSpan::class.java).any { span ->
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            span.sizeChange >= HEADING_MIN_SIZE && s <= start && e >= end && (end > start || s == start)
        }
        return if (heading) Block.HEADING else Block.BODY
    }

    /** Takes the heading and the pull-quote away from the paragraphs between [from] and [to], keeping them on the parts that stick out. */
    private fun removeBlocks(text: Editable, from: Int, to: Int) {
        for (span in text.getSpans(from, to, RelativeSizeSpan::class.java)) {
            if (span.sizeChange < HEADING_MIN_SIZE) continue
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            text.removeSpan(span)
            if (s < from) text.setSpan(RelativeSizeSpan(span.sizeChange), s, from, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            if (e > to) text.setSpan(RelativeSizeSpan(span.sizeChange), to, e, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        }
        // A heading is bold by itself, so its bold goes with it
        if (blockAt(text, from) != Block.QUOTE) removeStyle(text, Typeface.BOLD, from, to)
        for (span in text.getSpans(0, text.length, PullQuoteSpan::class.java)) {
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            if (e < from || s > to) continue
            text.removeSpan(span)
            if (s < from) setQuote(text, s, from, span.color, span.barWidth, span.gap)
            if (e > to + 1) setQuote(text, to + 1, e, span.color, span.barWidth, span.gap)
        }
    }

    private fun splitIntoQuotes(text: Editable, from: Int, to: Int, color: Int, bar: Int, gap: Int) {
        // One quote span per paragraph: the paragraph's own text, and its line break when it has one
        var start = from
        while (start <= to) {
            val end = paragraphEnd(text, start)
            setQuote(text, start, min(end + 1, text.length), color, bar, gap)
            if (end >= to) break
            start = end + 1
        }
    }

    /** A pull-quote from [start] to [end]; the ends are moved to the borders of their paragraphs (a paragraph span needs that). */
    private fun setQuote(text: Editable, start: Int, end: Int, color: Int, bar: Int, gap: Int) {
        val from = paragraphStart(text, start)
        var to = if (end >= text.length) text.length else paragraphEnd(text, max(end - 1, 0)).let { min(it + 1, text.length) }
        if (to < from) to = from
        // An empty last line has nothing yet for a paragraph span to hold on to: it grows with what is typed there instead
        val flags = if (from == to && to == text.length) Spannable.SPAN_INCLUSIVE_INCLUSIVE else Spanned.SPAN_PARAGRAPH
        text.setSpan(PullQuoteSpan(color, bar, gap), from, to, flags)
    }

    private fun alignmentOf(text: Spanned, start: Int, end: Int): ParagraphAlign {
        val span = text.getSpans(0, text.length, AlignmentSpan::class.java).firstOrNull { span ->
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            s <= start && (e > start || (end == start && s == start && e == start))
        } ?: return ParagraphAlign.LEFT
        return toParagraphAlign(span.alignment)
    }

    private fun toParagraphAlign(alignment: Layout.Alignment): ParagraphAlign = when (alignment) {
        Layout.Alignment.ALIGN_CENTER -> ParagraphAlign.CENTER
        Layout.Alignment.ALIGN_OPPOSITE -> ParagraphAlign.RIGHT
        Layout.Alignment.ALIGN_NORMAL -> ParagraphAlign.LEFT
    }

    private fun toLayoutAlignment(align: ParagraphAlign): Layout.Alignment = when (align) {
        ParagraphAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        ParagraphAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        ParagraphAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
    }

    private fun removeAlignmentSpans(text: Editable, from: Int, to: Int) {
        for (span in text.getSpans(0, text.length, AlignmentSpan::class.java)) {
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            if ((e <= from && !(s == from && e == from)) || s > to) continue
            val align = toParagraphAlign(span.alignment)
            text.removeSpan(span)
            if (align != ParagraphAlign.LEFT) {
                if (s < from) splitAlignmentSpans(text, s, max(s, from - 1), align)
                if (e > to + 1) splitAlignmentSpans(text, to + 1, e, align)
            }
        }
    }

    private fun splitAlignmentSpans(text: Editable, from: Int, to: Int, align: ParagraphAlign) {
        var start = paragraphStart(text, from)
        val endLimit = paragraphEnd(text, to)
        while (start <= endLimit) {
            val end = paragraphEnd(text, start)
            setParagraphAlignment(text, start, min(end + 1, text.length), align)
            if (end >= endLimit) break
            start = end + 1
        }
    }

    private fun setParagraphAlignment(text: Editable, start: Int, end: Int, align: ParagraphAlign) {
        val from = paragraphStart(text, start)
        var to = if (end >= text.length) text.length else paragraphEnd(text, max(end - 1, 0)).let { min(it + 1, text.length) }
        if (to < from) to = from
        val flags = if (from == to && to == text.length) Spannable.SPAN_INCLUSIVE_INCLUSIVE else Spanned.SPAN_PARAGRAPH
        text.setSpan(AlignmentSpan.Standard(toLayoutAlignment(align)), from, to, flags)
    }

    /** Keeps one bold span per heading and no twins: what fromHtml made of `<h2><b>..</b></h2>` would otherwise grow with every save. */
    private fun tidy(text: Editable) {
        val seen = HashSet<Triple<Int, Int, Int>>()
        for (span in text.getSpans(0, text.length, StyleSpan::class.java)) {
            val key = Triple(span.style, text.getSpanStart(span), text.getSpanEnd(span))
            if (!seen.add(key)) text.removeSpan(span)
        }
        // Headings and their bold type like the user's own (a new letter at either end stays in the heading)
        for (span in text.getSpans(0, text.length, RelativeSizeSpan::class.java)) {
            if (span.sizeChange < HEADING_MIN_SIZE) continue
            val s = text.getSpanStart(span)
            val e = text.getSpanEnd(span)
            text.setSpan(span, s, e, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            for (bold in text.getSpans(s, e, StyleSpan::class.java)) {
                if (bold.style == Typeface.BOLD && text.getSpanStart(bold) == s && text.getSpanEnd(bold) == e) {
                    text.setSpan(bold, s, e, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                }
            }
        }
        // Normalize paragraph alignment spans so each paragraph owns its own independent span
        val existingAlignments = text.getSpans(0, text.length, AlignmentSpan::class.java)
            .map { Triple(text.getSpanStart(it), text.getSpanEnd(it), toParagraphAlign(it.alignment)) }
            .also { spans -> text.getSpans(0, text.length, AlignmentSpan::class.java).forEach { text.removeSpan(it) } }
        for ((s, e, align) in existingAlignments) {
            if (align != ParagraphAlign.LEFT && s <= text.length) {
                splitAlignmentSpans(text, s, max(s, e - 1), align)
            }
        }
    }

}
