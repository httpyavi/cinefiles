package com.example.cinefiles.logic

/*
 * THE LANGUAGES THE USER CAN ASK FOR on the "Make it yours" screen (plain Kotlin, no screens: easy to test).
 *
 * TMDB describes the language a movie was made in with a short code (`original_language`: "en", "ko", "tl"...). The Preferences
 * screen used to offer four fixed choices; now the user types in a search field and picks from live suggestions, so any language in
 * this list works. [Preferences.language] holds the CODE (null = any language), and [Discovery] sends it to TMDB as is.
 */

/** One language: TMDB's [code], its English [name] (shown in the suggestions) and other names people may type ([aliases]). */
data class LanguageOption(val code: String, val name: String, val aliases: List<String> = emptyList()) {
    /** The suggestion list shows the name, and a picked suggestion becomes the field's text through this. */
    override fun toString(): String = name
}

object Languages {

    /** TMDB files Filipino movies under Tagalog's code. They have few votes, so Discovery relaxes its vote limits for them. */
    const val FILIPINO = "tl"

    /** Every language the picker knows, A to Z. Mandarin and Cantonese are separate codes on TMDB ("zh" and "cn"). */
    val all: List<LanguageOption> = listOf(
        LanguageOption("ar", "Arabic"),
        LanguageOption("bn", "Bengali", listOf("Bangla")),
        LanguageOption("bg", "Bulgarian"),
        LanguageOption("ca", "Catalan"),
        LanguageOption("cn", "Cantonese", listOf("Chinese")),
        LanguageOption("zh", "Mandarin", listOf("Chinese")),
        LanguageOption("hr", "Croatian"),
        LanguageOption("cs", "Czech"),
        LanguageOption("da", "Danish"),
        LanguageOption("nl", "Dutch"),
        LanguageOption("en", "English"),
        LanguageOption("et", "Estonian"),
        LanguageOption(FILIPINO, "Filipino", listOf("Tagalog")),
        LanguageOption("fi", "Finnish"),
        LanguageOption("fr", "French"),
        LanguageOption("de", "German"),
        LanguageOption("el", "Greek"),
        LanguageOption("he", "Hebrew"),
        LanguageOption("hi", "Hindi"),
        LanguageOption("hu", "Hungarian"),
        LanguageOption("is", "Icelandic"),
        LanguageOption("id", "Indonesian"),
        LanguageOption("it", "Italian"),
        LanguageOption("ja", "Japanese"),
        LanguageOption("kn", "Kannada"),
        LanguageOption("ko", "Korean"),
        LanguageOption("lv", "Latvian"),
        LanguageOption("lt", "Lithuanian"),
        LanguageOption("ms", "Malay"),
        LanguageOption("ml", "Malayalam"),
        LanguageOption("mr", "Marathi"),
        LanguageOption("no", "Norwegian"),
        LanguageOption("fa", "Persian", listOf("Farsi")),
        LanguageOption("pl", "Polish"),
        LanguageOption("pt", "Portuguese"),
        LanguageOption("pa", "Punjabi"),
        LanguageOption("ro", "Romanian"),
        LanguageOption("ru", "Russian"),
        LanguageOption("sr", "Serbian"),
        LanguageOption("sk", "Slovak"),
        LanguageOption("sl", "Slovenian"),
        LanguageOption("es", "Spanish"),
        LanguageOption("sv", "Swedish"),
        LanguageOption("ta", "Tamil"),
        LanguageOption("te", "Telugu"),
        LanguageOption("th", "Thai"),
        LanguageOption("tr", "Turkish"),
        LanguageOption("uk", "Ukrainian"),
        LanguageOption("ur", "Urdu"),
        LanguageOption("vi", "Vietnamese")
    ).sortedBy { it.name }

    /** The language with this TMDB [code], or null (also for null: "any language"). */
    fun byCode(code: String?): LanguageOption? = all.firstOrNull { it.code == code }

    /** Is a movie made in [movieLanguage] one the user asked for ([wanted])? TMDB sometimes writes Filipino as "fil" instead of "tl". */
    fun matches(wanted: String, movieLanguage: String): Boolean =
        if (wanted == FILIPINO) movieLanguage == FILIPINO || movieLanguage == "fil" else movieLanguage.equals(wanted, ignoreCase = true)

    /** The English name for a code, or null. */
    fun nameOf(code: String?): String? = byCode(code)?.name

    /** The language whose name or alias is exactly [text] (any case, spaces around ignored), or null. */
    fun exact(text: String): LanguageOption? {
        val wanted = text.trim()
        if (wanted.isEmpty()) return null
        return all.firstOrNull { option -> option.name.equals(wanted, ignoreCase = true) || option.aliases.any { it.equals(wanted, ignoreCase = true) } }
    }

    /**
     * The live suggestions for what the user has typed so far. Blank text suggests every language. Otherwise a language matches when
     * its name, an alias or its code starts with [query], then when a word of its name starts with it, then when it appears anywhere
     * in a name. Better matches come first, and inside a group the names are A to Z. Typing "E" gives English first.
     */
    fun search(query: String): List<LanguageOption> {
        val typed = query.trim().lowercase()
        if (typed.isEmpty()) return all
        return all
            .mapNotNull { option -> rankOf(option, typed)?.let { rank -> rank to option } }
            .sortedWith(compareBy({ it.first }, { it.second.name }))
            .map { it.second }
    }

    /** 0 = the best match; null = no match. */
    private fun rankOf(option: LanguageOption, typed: String): Int? {
        val name = option.name.lowercase()
        val aliases = option.aliases.map { it.lowercase() }
        return when {
            name.startsWith(typed) -> 0
            aliases.any { it.startsWith(typed) } || option.code == typed -> 1
            name.split(' ', '-', '/').any { it.startsWith(typed) } -> 2
            name.contains(typed) || aliases.any { it.contains(typed) } -> 3
            else -> null
        }
    }
}
