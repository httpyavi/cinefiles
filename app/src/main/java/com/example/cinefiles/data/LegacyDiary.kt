package com.example.cinefiles.data

import com.example.cinefiles.logic.DiaryCover
import com.example.cinefiles.logic.HtmlText
import com.example.cinefiles.logic.ImageSource
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * The diary of database versions 2 and 3, which only the migrations (data/Migrations.kt) still need to read.
 *
 * Version 3 kept a page as a list of BLOCKS in one piece of JSON text (`blocksJson`): text blocks (a run of paragraphs, plain or as the
 * editor's HTML, with an alignment) and picture blocks. Version 4 keeps a page as one headline, one body and one cover, so [convert]
 * pours the old blocks into that shape: the text blocks one after the other become the body, the FIRST picture becomes the cover (the
 * other pictures are not part of the new page; DiaryImageStore removes their now unused files at the next start) and the first
 * centered or right-aligned block decides the page's alignment.
 */
internal object LegacyDiary {

    /** A page in the new shape. [cover] is the text of [DiaryCover.encode] or null; [alignment] the name of a TextAlign. */
    data class Converted(val bodyHtml: String, val cover: String?, val alignment: String)

    /** The version 3 JSON for a page that holds one plain note (what the version 2 -> 3 migration writes). Never made by the app any more. */
    fun plainNoteBlocks(note: String): String {
        val block = JsonObject().apply {
            addProperty("id", java.util.UUID.randomUUID().toString())
            addProperty("type", "TEXT")
            addProperty("text", note)
        }
        return Gson().toJson(JsonArray().apply { add(block) })
    }

    /** Pours the version 3 blocks of [blocksJson] into a version 4 page. Broken, empty or unknown JSON gives an empty page, never a crash. */
    fun convert(blocksJson: String?): Converted {
        val blocks = parse(blocksJson)
        val body = StringBuilder()
        var cover: String? = null
        var alignment = "LEFT"

        for (block in blocks) {
            when (block.string("type")) {
                "TEXT" -> {
                    val text = block.string("text").orEmpty()
                    val isHtml = block.get("html")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
                    val words = if (isHtml) HtmlText.toPlain(text) else text
                    if (words.isBlank()) continue
                    body.append(if (isHtml) text.trim() else HtmlText.fromPlain(text))
                    val align = block.string("align")
                    if (alignment == "LEFT" && (align == "CENTER" || align == "RIGHT")) alignment = align
                }
                "IMAGE" -> {
                    val ref = block.string("ref").orEmpty()
                    val source = if (block.string("source") == "TMDB") ImageSource.TMDB else ImageSource.LOCAL
                    if (cover == null && ref.isNotBlank()) cover = DiaryCover(source, ref).encode()
                }
            }
        }
        return Converted(body.toString(), cover, alignment)
    }

    private fun parse(json: String?): List<JsonObject> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonArray) emptyList() else root.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }
        } catch (e: RuntimeException) { // JsonParseException and friends
            emptyList()
        }
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
