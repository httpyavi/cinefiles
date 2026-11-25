package com.example.cinefiles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pouring a diary page of the first version (a list of blocks) into the new shape (a body and a cover), which database migration 3 -> 4 does. */
class LegacyDiaryTest {

    private fun text(body: String, html: Boolean = false, align: String? = null): String {
        val parts = mutableListOf("\"type\":\"TEXT\"", "\"text\":${quote(body)}")
        if (html) parts += "\"html\":true"
        if (align != null) parts += "\"align\":\"$align\""
        return "{${parts.joinToString(",")}}"
    }

    private fun image(source: String, ref: String) = "{\"type\":\"IMAGE\",\"source\":\"$source\",\"ref\":\"$ref\"}"

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun json(vararg blocks: String) = "[${blocks.joinToString(",")}]"

    @Test
    fun textBlocksBecomeTheBodyOneAfterTheOther() {
        val page = LegacyDiary.convert(json(text("<p dir=\"ltr\">First</p>", html = true), text("Second\nline")))
        assertEquals("<p dir=\"ltr\">First</p><p dir=\"ltr\">Second</p><p dir=\"ltr\">line</p>", page.bodyHtml)
        assertNull(page.cover)
        assertEquals("LEFT", page.alignment)
    }

    @Test
    fun theFirstPictureBecomesTheCoverAndTheOthersAreLeftOut() {
        val page = LegacyDiary.convert(json(image("TMDB", "/first.jpg"), text("Words"), image("LOCAL", "mine.jpg")))
        assertEquals("tmdb:/first.jpg", page.cover)
    }

    @Test
    fun aGalleryPictureKeepsItsFileName() {
        val page = LegacyDiary.convert(json(text("Words"), image("LOCAL", "1700_ab12.jpg")))
        assertEquals("local:1700_ab12.jpg", page.cover)
    }

    @Test
    fun theFirstCenteredOrRightAlignedBlockDecidesThePageAlignment() {
        assertEquals("CENTER", LegacyDiary.convert(json(text("a", align = "LEFT"), text("b", align = "CENTER"), text("c", align = "RIGHT"))).alignment)
        assertEquals("RIGHT", LegacyDiary.convert(json(text("a"), text("c", align = "RIGHT"))).alignment)
        assertEquals("LEFT", LegacyDiary.convert(json(text("a", align = "LEFT"))).alignment)
    }

    @Test
    fun blankBlocksAreLeftOut() {
        val page = LegacyDiary.convert(json(text("   "), text("<p dir=\"ltr\"><br></p>", html = true), text("Real")))
        assertEquals("<p dir=\"ltr\">Real</p>", page.bodyHtml)
    }

    @Test
    fun brokenOrEmptyJsonGivesAnEmptyPageAndNeverACrash() {
        for (bad in listOf(null, "", "   ", "not json", "{\"a\":1}", "[1,2,3]", "[{\"type\":\"WEIRD\"}]", "[null]")) {
            val page = LegacyDiary.convert(bad)
            assertEquals("for $bad", "", page.bodyHtml)
            assertNull(page.cover)
            assertEquals("LEFT", page.alignment)
        }
    }

    @Test
    fun aPictureWithoutAnAddressIsNoCover() {
        assertNull(LegacyDiary.convert(json(image("LOCAL", ""))).cover)
    }

    @Test
    fun theNoteOfTheVeryFirstVersionBecomesOnePlainTextBlock() {
        val blocks = LegacyDiary.plainNoteBlocks("My \"first\" note\nsecond line")
        val page = LegacyDiary.convert(blocks)
        assertTrue(page.bodyHtml.contains("My &quot;first&quot; note") || page.bodyHtml.contains("My \"first\" note"))
        assertEquals(
            "My \"first\" note\nsecond line",
            com.example.cinefiles.logic.HtmlText.toPlain(page.bodyHtml)
        )
    }
}
