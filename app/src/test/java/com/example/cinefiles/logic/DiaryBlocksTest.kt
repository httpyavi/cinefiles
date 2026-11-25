package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The blocks of a diary page: saving them as JSON text, reading them back, rich text and alignment, and moving, deleting and previewing them. */
class DiaryBlocksTest {

    private fun text(id: String, text: String) = DiaryBlock(id, BlockType.TEXT, text = text)
    private fun image(id: String, source: ImageSource, ref: String) = DiaryBlock(id, BlockType.IMAGE, imageSource = source, imageRef = ref)

    private val page = listOf(
        text("a", "I cried at the docking scene."),
        image("b", ImageSource.TMDB, "/still.jpg"),
        image("c", ImageSource.LOCAL, "1700_abcd.jpg"),
        text("d", "Quote: \"Do not go gentle\" ✨\nnew line")
    )

    // ---------- JSON ----------

    @Test
    fun aPageSurvivesBeingSavedAndLoaded() {
        assertEquals(page, DiaryBlocks.fromJson(DiaryBlocks.toJson(page)))
    }

    @Test
    fun theJsonHoldsOnlyWordsAndAddressesNeverPictureBytes() {
        val json = DiaryBlocks.toJson(page)
        assertTrue("/still.jpg" in json)
        assertTrue("1700_abcd.jpg" in json) // the NAME of the app's own copy
    }

    @Test
    fun anEmptyPageIsAnEmptyList() {
        assertEquals("[]", DiaryBlocks.toJson(emptyList()))
        assertTrue(DiaryBlocks.fromJson("[]").isEmpty())
    }

    @Test
    fun brokenOrMissingJsonGivesAnEmptyPageAndNeverACrash() {
        assertTrue(DiaryBlocks.fromJson(null).isEmpty())
        assertTrue(DiaryBlocks.fromJson("").isEmpty())
        assertTrue(DiaryBlocks.fromJson("   ").isEmpty())
        assertTrue(DiaryBlocks.fromJson("{not json").isEmpty())
        assertTrue(DiaryBlocks.fromJson("{\"a\": 1}").isEmpty()) // an object, not a list
    }

    @Test
    fun unknownKindsAreSkippedAndMissingFieldsGetDefaults() {
        val blocks = DiaryBlocks.fromJson("""[{"type":"VIDEO","ref":"x"},{"type":"TEXT"},{"type":"IMAGE","ref":"/p.jpg"},null]""")
        assertEquals(2, blocks.size)
        assertEquals("", blocks[0].text)
        assertTrue(blocks[0].id.isNotBlank()) // a missing id is made up
        assertEquals(BlockType.IMAGE, blocks[1].type)
        assertEquals(ImageSource.LOCAL, blocks[1].imageSource) // an unknown source is the safe one
    }

    // ---------- rich text and alignment ----------

    @Test
    fun aRichBlockKeepsItsHtmlAndAlignmentThroughSavingAndLoading() {
        val rich = DiaryBlock("r", BlockType.TEXT, text = "<p dir=\"ltr\">Hello <b>world</b></p>", html = true, align = TextAlign.CENTER)
        val loaded = DiaryBlocks.fromJson(DiaryBlocks.toJson(listOf(rich))).single()
        assertEquals(rich, loaded)
        assertTrue(loaded.html)
        assertEquals(TextAlign.CENTER, loaded.align)
    }

    @Test
    fun aPlainLeftAlignedBlockWritesNoExtraFields() {
        val json = DiaryBlocks.toJson(listOf(text("a", "words")))
        assertFalse("html" in json)
        assertFalse("align" in json)
        val loaded = DiaryBlocks.fromJson(json).single()
        assertFalse(loaded.html)
        assertEquals(TextAlign.LEFT, loaded.align)
    }

    @Test
    fun anOlderPageWithoutTheNewFieldsStillOpensAsPlainLeftText() {
        val loaded = DiaryBlocks.fromJson("""[{"id":"x","type":"TEXT","text":"line one\nline two"}]""").single()
        assertEquals("line one\nline two", loaded.plainText)
        assertFalse(loaded.html)
        assertEquals(TextAlign.LEFT, loaded.align)
        assertEquals(TextAlign.LEFT, DiaryBlocks.fromJson("""[{"type":"TEXT","align":"DIAGONAL"}]""").single().align)
    }

    @Test
    fun theWordsOfARichBlockAreItsTextWithoutTheMarks() {
        val rich = DiaryBlock("r", BlockType.TEXT, text = "<p dir=\"ltr\">Caf&#233; <b>very</b> &lt;good&gt; &amp; <i>fun</i></p>\n", html = true)
        assertEquals("Café very <good> & fun", rich.plainText)
    }

    @Test
    fun aRichBlockWithNoLettersIsEmptyEvenThoughItsHtmlIsNot() {
        val empty = DiaryBlock("r", BlockType.TEXT, text = "<p dir=\"ltr\"></p>\n", html = true)
        assertTrue(empty.isBlank)
        assertTrue(DiaryBlocks.isEmpty(listOf(empty)))
        assertEquals(1, DiaryBlocks.withoutBlank(listOf(empty, text("a", "real"))).size)
    }

    @Test
    fun thePreviewOfARichPageIsItsPlainWords() {
        val rich = DiaryBlock("r", BlockType.TEXT, text = "<p dir=\"ltr\">I <b>loved</b> it</p>", html = true)
        assertEquals("I loved it", DiaryBlocks.preview(listOf(rich)))
    }

    @Test
    fun htmlTextHandlesLineBreaksEntitiesAndOddInput() {
        assertEquals("a\nb", HtmlText.toPlain("<p>a<br>b</p>"))
        assertEquals("a & b", HtmlText.toPlain("a &amp; b"))
        assertEquals("&lt;", HtmlText.toPlain("&amp;lt;")) // decoded once only
        assertEquals("😀", HtmlText.toPlain("&#128512;")) // a code point above the basic plane
        assertEquals("&#99999999999;", HtmlText.toPlain("&#99999999999;")) // not a number we can turn into a letter: left alone
        assertEquals("", HtmlText.toPlain(""))
    }

    @Test
    fun newTextBlocksCanBeRichAndAligned() {
        val block = DiaryBlocks.newText("<p>x</p>", html = true, align = TextAlign.RIGHT)
        assertTrue(block.html)
        assertEquals(TextAlign.RIGHT, block.align)
        assertFalse(DiaryBlocks.newText("plain").html)
    }

    // ---------- editing ----------

    @Test
    fun aBlockMovesToAnotherPlaceAndTheOthersShift() {
        assertEquals(listOf("b", "c", "a", "d"), DiaryBlocks.move(page, 0, 2).map { it.id })
        assertEquals(listOf("d", "a", "b", "c"), DiaryBlocks.move(page, 3, 0).map { it.id })
    }

    @Test
    fun moveUpAndDownMoveOnePlace() {
        assertEquals(listOf("b", "a", "c", "d"), DiaryBlocks.moveDown(page, 0).map { it.id })
        assertEquals(listOf("a", "c", "b", "d"), DiaryBlocks.moveUp(page, 2).map { it.id })
    }

    @Test
    fun movingOutsideThePageChangesNothing() {
        assertEquals(page, DiaryBlocks.moveUp(page, 0))
        assertEquals(page, DiaryBlocks.moveDown(page, 3))
        assertEquals(page, DiaryBlocks.move(page, 1, 1))
        assertEquals(page, DiaryBlocks.move(page, 9, 0))
    }

    @Test
    fun aBlockIsRemovedById() {
        assertEquals(listOf("a", "c", "d"), DiaryBlocks.remove(page, "b").map { it.id })
        assertEquals(page, DiaryBlocks.remove(page, "zzz"))
    }

    @Test
    fun newBlocksGetTheirOwnIds() {
        assertNotEquals(DiaryBlocks.newText().id, DiaryBlocks.newText().id)
        val picture = DiaryBlocks.newImage(ImageSource.TMDB, "/x.jpg")
        assertEquals(BlockType.IMAGE, picture.type)
        assertEquals("/x.jpg", picture.imageRef)
    }

    // ---------- what is worth saving ----------

    @Test
    fun emptyCellsAreLeftOutWhenSaving() {
        val blocks = listOf(text("a", "  \n "), text("b", "Real words"), image("c", ImageSource.LOCAL, ""))
        assertEquals(listOf("b"), DiaryBlocks.withoutBlank(blocks).map { it.id })
    }

    @Test
    fun aPageWithoutWordsOrPicturesIsEmpty() {
        assertTrue(DiaryBlocks.isEmpty(emptyList()))
        assertTrue(DiaryBlocks.isEmpty(listOf(text("a", ""), text("b", "   "))))
        assertFalse(DiaryBlocks.isEmpty(listOf(text("a", ""), image("b", ImageSource.TMDB, "/p.jpg"))))
    }

    // ---------- reading a page ----------

    @Test
    fun thePreviewIsTheFirstTextOnOneLine() {
        assertEquals("I cried at the docking scene.", DiaryBlocks.preview(page))
        assertEquals("one two three", DiaryBlocks.preview(listOf(text("a", "  one\n\n two   three "))))
        assertEquals("", DiaryBlocks.preview(listOf(image("a", ImageSource.TMDB, "/p.jpg"))))
        assertEquals("second", DiaryBlocks.preview(listOf(text("a", " "), text("b", "second"))))
    }

    @Test
    fun theCoverIsTheFirstPicture() {
        assertEquals("b", DiaryBlocks.firstImage(page)?.id)
        assertNull(DiaryBlocks.firstImage(listOf(text("a", "words"))))
    }

    @Test
    fun onlyGalleryPicturesAreKeptInTheAppsStorage() {
        assertEquals(setOf("1700_abcd.jpg"), DiaryBlocks.localImages(page))
        assertTrue(DiaryBlocks.localImages(emptyList()).isEmpty())
    }
}
