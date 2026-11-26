package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words and the cover of a diary page: what counts as an empty page, the preview and the cover's address. */
class DiaryTextTest {

    // ---------- The cover ----------

    @Test
    fun aCoverIsKeptAsItsSourceAndAddress() {
        assertEquals("tmdb:/abc.jpg", DiaryCover.tmdb("/abc.jpg").encode())
        assertEquals("local:1700_ab12.jpg", DiaryCover.local("1700_ab12.jpg").encode())
    }

    @Test
    fun aCoverIsReadBackFromItsText() {
        assertEquals(DiaryCover.tmdb("/abc.jpg"), DiaryCover.parse("tmdb:/abc.jpg"))
        assertEquals(DiaryCover.local("1700_ab12.jpg"), DiaryCover.parse("local:1700_ab12.jpg"))
        assertEquals(DiaryCover.tmdb("/a:b.jpg"), DiaryCover.parse(DiaryCover.tmdb("/a:b.jpg").encode()))
    }

    @Test
    fun nothingBlankOrUnknownIsEverACover() {
        assertNull(DiaryCover.parse(null))
        assertNull(DiaryCover.parse(""))
        assertNull(DiaryCover.parse("   "))
        assertNull(DiaryCover.parse("tmdb:"))
        assertNull(DiaryCover.parse("tmdb:   "))
        assertNull(DiaryCover.parse("ftp:/abc.jpg"))
        assertNull(DiaryCover.parse("/abc.jpg"))
        assertNull(DiaryCover.parse(":abc"))
    }

    @Test
    fun multipleAttachmentsAreEncodedAndRestoredInOrder() {
        val covers = listOf(DiaryCover.local("one.jpg"), DiaryCover.tmdb("/two.jpg"))
        val encoded = DiaryCover.encodeList(covers)
        assertEquals(covers, DiaryCover.parseList(encoded))
        assertEquals(DiaryCover.local("one.jpg"), DiaryCover.parse(encoded))
        assertTrue(DiaryCover.parseList(null).isEmpty())
        assertNull(DiaryCover.encodeList(emptyList()))
    }

    // ---------- Words ----------

    @Test
    fun htmlBecomesPlainWordsWithTheirLineBreaks() {
        assertEquals("Hello world", HtmlText.toPlain("<p dir=\"ltr\">Hello <b>world</b></p>"))
        assertEquals("one\ntwo", HtmlText.toPlain("<p>one</p><p>two</p>"))
        assertEquals("Title\nwords", HtmlText.toPlain("<h2>Title</h2><p>words</p>"))
        assertEquals("quoted", HtmlText.toPlain("<blockquote><p>quoted</p></blockquote>"))
        assertEquals("Tom & Jerry <3", HtmlText.toPlain("<p>Tom &amp; Jerry &lt;3</p>"))
        assertEquals("café", HtmlText.toPlain("<p>caf&#233;</p>"))
    }

    @Test
    fun plainWordsMakeParagraphsThatReadBackTheSame() {
        val html = HtmlText.fromPlain("First line\n\nSecond <line> & moreé")
        assertEquals("First line\n\nSecond <line> & moreé", HtmlText.toPlain(html))
        assertTrue(html.contains("<p dir=\"ltr\">First line</p>"))
        assertTrue(html.contains("&lt;line&gt; &amp; more&#233;"))
    }

    // ---------- An empty page ----------

    @Test
    fun aPageWithNothingOnItIsBlank() {
        assertTrue(DiaryText.isBlank("", "", null))
        assertTrue(DiaryText.isBlank("   ", "<p dir=\"ltr\"><br></p>", null))
        assertTrue(DiaryText.isBlank("", "<p>  </p>", "tmdb:"))
    }

    @Test
    fun aHeadlineWordsOrACoverEachMakeAPageWorthSaving() {
        assertFalse(DiaryText.isBlank("A headline", "", null))
        assertFalse(DiaryText.isBlank("", "<p>Words</p>", null))
        assertFalse(DiaryText.isBlank("", "", "local:cover.jpg"))
    }

    @Test
    fun theFormattingAloneIsNotAnythingToSave() {
        assertTrue(DiaryText.isBlank("", "<h2></h2><blockquote><p></p></blockquote><p><b></b></p>", null))
    }

    @Test
    fun thePreviewIsTheFirstWordsOnOneLine() {
        assertEquals("I loved it a lot", DiaryText.preview("<p>I loved it</p><p>  a   lot </p>"))
        assertEquals("", DiaryText.preview("<p><br></p>"))
    }
}
