package com.example.cinefiles.ui.diary

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.QuoteSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The rich text of a diary page on a real phone (it needs Android's Html and Spannable classes): what the editor writes is read back the
 * same, again and again, and the buttons (bold, italic, underline, heading, pull-quote) switch on and off like in any editor.
 */
@RunWith(AndroidJUnit4::class)
class RichTextTest {

    private val color = 0xFF112233.toInt()

    private fun spanned(html: String) = RichText.toSpanned(html, color, 6, 24)

    private fun edit(text: String) = SpannableStringBuilder(text)

    private fun toggleBlock(text: SpannableStringBuilder, block: RichText.Block, start: Int, end: Int = start) =
        RichText.toggleBlock(text, block, start, end, color, 6, 24)

    /** Saving then loading, three times: the words and the HTML never change (so a page never grows or loses a line by being opened). */
    private fun assertStable(text: SpannableStringBuilder) {
        val first = RichText.toHtml(text)
        var html = first
        repeat(3) {
            val loaded = spanned(html)
            html = RichText.toHtml(loaded)
        }
        assertEquals("the HTML after three round trips", first, html)
        assertEquals("the words after a round trip", text.toString(), spanned(first).toString())
    }

    // ---------- Inline ----------

    @Test
    fun boldItalicAndUnderlineSurviveAndStayStable() {
        val text = edit("Great film about trust")
        RichText.toggle(text, Typeface.BOLD, 0, 5)
        RichText.toggle(text, Typeface.ITALIC, 6, 10)
        RichText.toggle(text, RichText.UNDERLINE, 11, 16)

        val html = RichText.toHtml(text)
        assertTrue(html, html.contains("<b>Great</b>"))
        assertTrue(html, html.contains("<i>film</i>"))
        assertTrue(html, html.contains("<u>about</u>"))
        assertStable(text)

        val loaded = spanned(html)
        assertTrue(RichText.isActive(loaded, Typeface.BOLD, 0, 5))
        assertTrue(RichText.isActive(loaded, Typeface.ITALIC, 6, 10))
        assertTrue(RichText.isActive(loaded, RichText.UNDERLINE, 11, 16))
        assertFalse(RichText.isActive(loaded, Typeface.BOLD, 6, 10))
    }

    @Test
    fun togglingTwiceTakesTheStyleAwayAgain() {
        val text = edit("one two three")
        RichText.toggle(text, RichText.UNDERLINE, 4, 7)
        assertTrue(RichText.isActive(text, RichText.UNDERLINE, 4, 7))
        RichText.toggle(text, RichText.UNDERLINE, 4, 7)
        assertFalse(RichText.isActive(text, RichText.UNDERLINE, 4, 7))
        assertEquals(0, text.getSpans(0, text.length, UnderlineSpan::class.java).size)
    }

    @Test
    fun aStyleSwitchedOnAtTheCursorGrowsAsLettersAreTyped() {
        val text = edit("abc")
        RichText.toggle(text, Typeface.BOLD, 3, 3)
        assertTrue(RichText.isActive(text, Typeface.BOLD, 3, 3))
        text.insert(3, "def")
        assertTrue(RichText.isActive(text, Typeface.BOLD, 3, 6))
        assertFalse(RichText.isActive(text, Typeface.BOLD, 0, 3))
    }

    @Test
    fun theKeyboardsOwnComposingUnderlineIsNotTheUsers() {
        val text = edit("typing")
        text.setSpan(UnderlineSpan(), 0, 6, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or android.text.Spanned.SPAN_COMPOSING)
        assertFalse(RichText.isActive(text, RichText.UNDERLINE, 0, 6))
        assertFalse(RichText.toHtml(text).contains("<u>"))
    }

    // ---------- Paragraphs ----------

    @Test
    fun aHeadingIsWrittenAsAnH2AndReadBackAsAHeading() {
        val text = edit("Title\nSome words")
        toggleBlock(text, RichText.Block.HEADING, 2)

        val html = RichText.toHtml(text)
        assertTrue(html, html.startsWith("<h2>Title</h2>"))
        assertTrue(html, html.contains("<p dir=\"ltr\">Some words</p>"))
        assertStable(text)

        val loaded = spanned(html)
        assertEquals(RichText.Block.HEADING, RichText.blockAt(loaded, 2))
        assertEquals(RichText.Block.BODY, RichText.blockAt(loaded, 9))
    }

    @Test
    fun aHeadingIsNotWrittenAsBoldWords() {
        val text = edit("Title")
        toggleBlock(text, RichText.Block.HEADING, 0, 5)
        assertEquals("<h2>Title</h2>", RichText.toHtml(text))
    }

    @Test
    fun aPullQuoteIsWrittenAsABlockquoteAndReadBackWithTheAccentBar() {
        val text = edit("Intro\nA line worth quoting\nOutro")
        toggleBlock(text, RichText.Block.QUOTE, 10)

        val html = RichText.toHtml(text)
        assertTrue(html, html.contains("<blockquote><p dir=\"ltr\">A line worth quoting</p></blockquote>"))
        assertStable(text)

        val loaded = spanned(html)
        assertEquals(RichText.Block.QUOTE, RichText.blockAt(loaded, 10))
        assertEquals(RichText.Block.BODY, RichText.blockAt(loaded, 0))
        assertEquals(RichText.Block.BODY, RichText.blockAt(loaded, loaded.length))
        val bars = loaded.getSpans(0, loaded.length, QuoteSpan::class.java)
        assertEquals(1, bars.size)
        assertTrue("the plain blue bar of fromHtml was replaced", bars[0] is PullQuoteSpan)
        assertEquals(color, bars[0].color)
    }

    @Test
    fun theSameButtonSwitchesAParagraphKindOnAndOff() {
        val text = edit("only")
        toggleBlock(text, RichText.Block.HEADING, 0)
        assertEquals(RichText.Block.HEADING, RichText.blockAt(text, 0))
        toggleBlock(text, RichText.Block.HEADING, 0)
        assertEquals(RichText.Block.BODY, RichText.blockAt(text, 0))
        assertEquals("<p dir=\"ltr\">only</p>", RichText.toHtml(text))

        toggleBlock(text, RichText.Block.QUOTE, 0)
        assertEquals(RichText.Block.QUOTE, RichText.blockAt(text, 0))
        toggleBlock(text, RichText.Block.QUOTE, 0)
        assertEquals(RichText.Block.BODY, RichText.blockAt(text, 0))
        assertEquals("<p dir=\"ltr\">only</p>", RichText.toHtml(text))
    }

    @Test
    fun aQuoteChangesIntoAHeadingAndBack() {
        val text = edit("words")
        toggleBlock(text, RichText.Block.QUOTE, 0)
        toggleBlock(text, RichText.Block.HEADING, 0)
        assertEquals(RichText.Block.HEADING, RichText.blockAt(text, 0))
        assertEquals(0, text.getSpans(0, text.length, QuoteSpan::class.java).size)
        assertEquals("<h2>words</h2>", RichText.toHtml(text))
    }

    @Test
    fun takingTheQuoteAwayFromOneParagraphLeavesItsNeighbours() {
        val text = edit("a\nb\nc")
        toggleBlock(text, RichText.Block.QUOTE, 0, text.length) // all three
        assertEquals(RichText.Block.QUOTE, RichText.blockAt(text, 2))
        toggleBlock(text, RichText.Block.QUOTE, 2) // only the middle one
        assertEquals(RichText.Block.QUOTE, RichText.blockAt(text, 0))
        assertEquals(RichText.Block.BODY, RichText.blockAt(text, 2))
        assertEquals(RichText.Block.QUOTE, RichText.blockAt(text, 4))
        assertStable(text)
    }

    @Test
    fun aQuoteOnTheEmptyLastLineGrowsWithWhatIsTypedInIt() {
        val text = edit("first\n")
        toggleBlock(text, RichText.Block.QUOTE, text.length)
        text.insert(text.length, "typed")
        assertEquals(RichText.Block.QUOTE, RichText.blockAt(text, text.length))
        assertEquals(RichText.Block.BODY, RichText.blockAt(text, 0))
    }

    @Test
    fun aQuoteOnTheLastLineGrowsWithWhatIsTypedAtItsEnd() {
        val text = edit("first
last")
        toggleBlock(text, RichText.Block.QUOTE, 8)
        text.insert(text.length, " more")
        assertEquals(RichText.Block.QUOTE, RichText.blockAt(text, text.length))
        assertEquals("<p dir=\"ltr\">first</p><blockquote><p dir=\"ltr\">last more</p></blockquote>", RichText.toHtml(text))
    }

    // ---------- Blank lines, entities, odd input ----------

    @Test
    fun blankLinesBetweenParagraphsAreKeptAndNeverGrow() {
        assertStable(edit("one\n\ntwo\n\n\nthree"))
        assertEquals("one\n\ntwo", spanned(RichText.toHtml(edit("one\n\ntwo"))).toString())
    }

    @Test
    fun trailingBlankLinesAreNotSavedAndAnEmptyPageIsEmpty() {
        assertEquals(RichText.toHtml(edit("words")), RichText.toHtml(edit("words\n\n\n")))
        assertEquals("", RichText.toHtml(edit("")))
        assertEquals("", spanned("").toString())
    }

    @Test
    fun specialLettersAndSymbolsComeBackTheSame() {
        assertStable(edit("Café <b>not bold</b> & \"quotes\" — 你好 🍿"))
    }

    @Test
    fun aMixOfEverythingIsStable() {
        val text = edit("Headline here\nA body with bold, italic and underline.\nA quote\n\nThe end")
        toggleBlock(text, RichText.Block.HEADING, 0)
        RichText.toggle(text, Typeface.BOLD, 20, 24)
        RichText.toggle(text, Typeface.ITALIC, 26, 32)
        RichText.toggle(text, RichText.UNDERLINE, 37, 46)
        toggleBlock(text, RichText.Block.QUOTE, 55)
        assertStable(text)
        assertTrue(text.getSpans(0, text.length, StyleSpan::class.java).isNotEmpty())
    }
}
