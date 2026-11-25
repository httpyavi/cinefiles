package com.example.cinefiles.logic

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.UUID

/*
 * THE BLOCKS OF A DIARY PAGE (plain Kotlin, no screens: easy to test).
 *
 * A diary page is a list of blocks: a TEXT block (a run of paragraphs) or an IMAGE block (a picture). The page keeps them as one piece of
 * JSON text (DiaryEntry.blocksJson), written with Gson. The JSON holds only words and picture ADDRESSES, never the picture itself:
 *   - an image from TMDB (a still or backdrop of the movie): [ImageSource.TMDB] and its TMDB path "/abc123.jpg";
 *   - an image from the phone's gallery: [ImageSource.LOCAL] and the NAME of the small copy the app keeps in its own
 *     storage (data/DiaryImageStore.kt), so the picture never breaks when the gallery changes.
 * The page reads like one article (no boxes): a TEXT block is a run of paragraphs, an IMAGE block sits between two runs. A text block can be
 * RICH ([DiaryBlock.html]): its [DiaryBlock.text] is then HTML made by the editor (bold and italic, nothing else), and it is read back with
 * HtmlCompat. A block that is not rich (a note moved over from an older version) is plain text with its line breaks.
 */

enum class BlockType { TEXT, IMAGE }

enum class ImageSource { LOCAL, TMDB }

/** How a text block is lined up on the page. */
enum class TextAlign { LEFT, CENTER, RIGHT }

/**
 * One block. [text], [html] and [align] are used by TEXT blocks; [imageSource] and [imageRef] by IMAGE blocks.
 * [id] tells blocks apart while editing.
 */
data class DiaryBlock(
    val id: String,
    val type: BlockType,
    val text: String = "",
    val imageSource: ImageSource = ImageSource.LOCAL,
    val imageRef: String = "",
    val html: Boolean = false,
    val align: TextAlign = TextAlign.LEFT
) {
    /** The words of a text block with no formatting marks (what a preview and the "is it empty" question look at). */
    val plainText: String get() = if (html) HtmlText.toPlain(text) else text

    /** A block with nothing in it: a text block with no letters, or a picture block without a picture. */
    val isBlank: Boolean
        get() = when (type) {
            BlockType.TEXT -> plainText.isBlank()
            BlockType.IMAGE -> imageRef.isBlank()
        }
}

/**
 * Turns the little bit of HTML the editor writes (paragraphs, line breaks, bold, italic and numeric entities for letters outside ASCII)
 * back into plain words, without any Android class, so it can be tested. It is only for "what does this block say".
 */
object HtmlText {

    private val LINE_BREAK = Regex("<br\\s*/?>|</p>|</div>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")

    fun toPlain(html: String): String {
        val withBreaks = LINE_BREAK.replace(html, "\n")
        val noTags = TAG.replace(withBreaks, "")
        val numbers = NUMERIC_ENTITY.replace(noTags) { match ->
            val code = match.groupValues[1]
            val value = if (code.startsWith("x", ignoreCase = true)) code.substring(1).toIntOrNull(16) else code.toIntOrNull()
            if (value != null && Character.isValidCodePoint(value)) String(Character.toChars(value)) else match.value
        }
        return numbers
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&") // last, so "&amp;lt;" stays "&lt;"
            .trim()
    }
}

object DiaryBlocks {

    private val gson = Gson()

    /** The JSON shape of one block. Everything is nullable: a file written by an older or hand-edited version must still load. */
    private data class BlockJson(
        val id: String? = null,
        val type: String? = null,
        val text: String? = null,
        val html: Boolean? = null,
        val align: String? = null,
        val source: String? = null,
        val ref: String? = null
    )

    fun newId(): String = UUID.randomUUID().toString()

    /** A new text block. [html] says [text] is the editor's HTML (see [DiaryBlock]); false = plain words. */
    fun newText(text: String = "", html: Boolean = false, align: TextAlign = TextAlign.LEFT): DiaryBlock =
        DiaryBlock(newId(), BlockType.TEXT, text = text, html = html, align = align)

    fun newImage(source: ImageSource, ref: String): DiaryBlock =
        DiaryBlock(newId(), BlockType.IMAGE, imageSource = source, imageRef = ref)

    // ---------- Saving and loading ----------

    fun toJson(blocks: List<DiaryBlock>): String = gson.toJson(
        blocks.map { block ->
            when (block.type) {
                BlockType.TEXT -> BlockJson(
                    block.id, block.type.name, text = block.text,
                    html = block.html.takeIf { it },
                    align = block.align.name.takeIf { block.align != TextAlign.LEFT }
                )
                BlockType.IMAGE -> BlockJson(block.id, block.type.name, source = block.imageSource.name, ref = block.imageRef)
            }
        }
    )

    /** The blocks of [json]. Broken or empty JSON gives an empty list (never a crash); unknown blocks are skipped. */
    fun fromJson(json: String?): List<DiaryBlock> {
        if (json.isNullOrBlank()) return emptyList()
        val raw: Array<BlockJson?> = try {
            gson.fromJson(json, Array<BlockJson?>::class.java) ?: return emptyList()
        } catch (e: JsonSyntaxException) {
            return emptyList()
        } catch (e: RuntimeException) {
            return emptyList()
        }
        return raw.filterNotNull().mapNotNull { item ->
            val type = BlockType.values().firstOrNull { it.name == item.type } ?: return@mapNotNull null
            DiaryBlock(
                id = item.id?.ifBlank { null } ?: newId(),
                type = type,
                text = item.text.orEmpty(),
                imageSource = ImageSource.values().firstOrNull { it.name == item.source } ?: ImageSource.LOCAL,
                imageRef = item.ref.orEmpty(),
                html = item.html == true,
                align = TextAlign.values().firstOrNull { it.name == item.align } ?: TextAlign.LEFT
            )
        }
    }

    // ---------- Editing ----------

    /** Moves the block at [from] to place [to] (both must be inside the list, otherwise nothing changes). */
    fun move(blocks: List<DiaryBlock>, from: Int, to: Int): List<DiaryBlock> {
        if (from !in blocks.indices || to !in blocks.indices || from == to) return blocks
        val result = blocks.toMutableList()
        result.add(to, result.removeAt(from))
        return result
    }

    fun moveUp(blocks: List<DiaryBlock>, index: Int): List<DiaryBlock> = move(blocks, index, index - 1)

    fun moveDown(blocks: List<DiaryBlock>, index: Int): List<DiaryBlock> = move(blocks, index, index + 1)

    fun remove(blocks: List<DiaryBlock>, id: String): List<DiaryBlock> = blocks.filterNot { it.id == id }

    // ---------- Reading a page ----------

    /** Blocks worth saving: the empty ones (a text block nobody typed in) are left out. */
    fun withoutBlank(blocks: List<DiaryBlock>): List<DiaryBlock> = blocks.filterNot { it.isBlank }

    /** A page with nothing on it (no words, no picture) is not worth saving. */
    fun isEmpty(blocks: List<DiaryBlock>): Boolean = blocks.all { it.isBlank }

    /** The first words of the page on one line (blank lines and runs of spaces squeezed), or "" when it has no text. */
    fun preview(blocks: List<DiaryBlock>): String =
        blocks.firstOrNull { it.type == BlockType.TEXT && !it.isBlank }
            ?.plainText?.trim()?.replace(Regex("\\s+"), " ")
            .orEmpty()

    /** The first picture of the page (for the cover of a page in the list), or null. */
    fun firstImage(blocks: List<DiaryBlock>): DiaryBlock? =
        blocks.firstOrNull { it.type == BlockType.IMAGE && it.imageRef.isNotBlank() }

    /** The names of the gallery pictures this page keeps in the app's storage. */
    fun localImages(blocks: List<DiaryBlock>): Set<String> =
        blocks.filter { it.type == BlockType.IMAGE && it.imageSource == ImageSource.LOCAL && it.imageRef.isNotBlank() }
            .map { it.imageRef }
            .toSet()
}
