package com.example.cinefiles.logic

/*
 * THE WORDS AND THE COVER OF A DIARY PAGE (plain Kotlin, no screens: easy to test).
 *
 * A diary page is ONE piece of writing, like a Substack post: a headline (plain text), a body (the editor's HTML: bold, italic, underline,
 * headings and pull-quotes, made and read with HtmlCompat in ui/diary/RichText.kt) and an optional cover picture.
 * The cover is kept as one short text, [DiaryCover.encode]: the picture's ADDRESS, never its bytes:
 *   - "tmdb:/abc123.jpg"  a still of the movie from TMDB (its TMDB path);
 *   - "local:1700_ab12.jpg"  a picture from the phone's gallery: the NAME of the small copy the app keeps in its own storage
 *     (data/DiaryImageStore.kt), so the page never breaks when the gallery changes.
 */

enum class ImageSource(val prefix: String) { LOCAL("local"), TMDB("tmdb") }

/** The cover picture of a page: where it comes from and its address ([ref]). */
data class DiaryCover(val source: ImageSource, val ref: String) {

    /** The text a page keeps in `DiaryEntry.coverImageUri`. */
    fun encode(): String = "${source.prefix}:$ref"

    companion object {
        fun local(name: String) = DiaryCover(ImageSource.LOCAL, name)

        fun tmdb(path: String) = DiaryCover(ImageSource.TMDB, path)

        /** The cover written as [value], or null for nothing, a blank text or an address nobody knows (never a crash). */
        fun parse(value: String?): DiaryCover? = parseList(value).firstOrNull()

        /** Parses one or more encoded attachments stored in `DiaryEntry.coverImageUri`. */
        fun parseList(value: String?): List<DiaryCover> {
            if (value.isNullOrBlank()) return emptyList()
            return value.split('\n', '|').mapNotNull { parseSingle(it) }
        }

        /** Encodes a list of attachments for storage in `DiaryEntry.coverImageUri`. */
        fun encodeList(covers: List<DiaryCover>): String? =
            covers.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.encode() }

        private fun parseSingle(value: String?): DiaryCover? {
            if (value.isNullOrBlank()) return null
            val trimmed = value.trim()
            val colon = trimmed.indexOf(':')
            if (colon <= 0) return null
            val source = ImageSource.values().firstOrNull { it.prefix == trimmed.substring(0, colon) } ?: return null
            val ref = trimmed.substring(colon + 1)
            return if (ref.isBlank()) null else DiaryCover(source, ref)
        }
    }
}

/** Turns the little bit of HTML the editor writes back into plain words, without any Android class, so it can be tested. */
object HtmlText {

    private val LINE_BREAK = Regex("<br\\s*/?>|</p>|</div>|</h[1-6]>|</blockquote>", RegexOption.IGNORE_CASE)
    private val BLANK_LINE = Regex("<br\\s*/?>\\s*</p>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")

    fun toPlain(html: String): String {
        // A paragraph that is only a line break (a blank line) is one line break, not two
        val withBreaks = LINE_BREAK.replace(BLANK_LINE.replace(html, "</p>"), "\n")
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

    /** Plain words as HTML paragraphs (a blank line is an empty paragraph), the other way round. Used to move an old plain note over. */
    fun fromPlain(text: String): String =
        text.trim().lines().joinToString("") { line ->
            if (line.isBlank()) "<p dir=\"ltr\"><br></p>" else "<p dir=\"ltr\">${escape(line.trim())}</p>"
        }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c.code > ASCII_MAX -> append("&#").append(c.code).append(';') // like HtmlCompat.toHtml: numeric entities outside ASCII
                else -> append(c)
            }
        }
    }

    private const val ASCII_MAX = 126
}

/** Questions about the words of a page. */
object DiaryText {

    const val MAX_PHOTOS = 10

    /** The words of a page's body with no formatting marks. */
    fun plainBody(bodyHtml: String): String = HtmlText.toPlain(bodyHtml)

    /** A page with nothing on it (no headline, no words, no attachments) is not worth saving. */
    fun isBlank(headline: String, bodyHtml: String, cover: String?): Boolean =
        headline.isBlank() && plainBody(bodyHtml).isBlank() && DiaryCover.parseList(cover).isEmpty()

    /** The first words of the body on one line (blank lines and runs of spaces squeezed), or "" when it has no words. */
    fun preview(bodyHtml: String): String = plainBody(bodyHtml).replace(Regex("\\s+"), " ")
}
