package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The language picker of "Make it yours": the list, the live suggestions and the matching of a movie's language. */
class LanguagesTest {

    private fun names(query: String) = Languages.search(query).map { it.name }

    // ---------- the list ----------

    @Test
    fun theListIsMuchLongerThanTheOldFourChips() {
        assertTrue(Languages.all.size >= 40)
    }

    @Test
    fun theLanguagesOfTheBriefAreAllThere() {
        for (name in listOf(
            "English", "Korean", "Japanese", "Filipino", "Spanish", "French", "Hindi", "Mandarin", "Cantonese",
            "German", "Italian", "Thai", "Indonesian", "Portuguese"
        )) {
            assertTrue("$name is missing", Languages.all.any { it.name == name })
        }
    }

    @Test
    fun theListIsAToZWithUniqueCodes() {
        assertEquals(Languages.all.map { it.name }.sorted(), Languages.all.map { it.name })
        assertEquals(Languages.all.size, Languages.all.map { it.code }.toSet().size)
    }

    @Test
    fun tmdbsCodesAreUsed() {
        assertEquals("en", Languages.all.first { it.name == "English" }.code)
        assertEquals("tl", Languages.all.first { it.name == "Filipino" }.code)
        assertEquals("zh", Languages.all.first { it.name == "Mandarin" }.code)
        assertEquals("cn", Languages.all.first { it.name == "Cantonese" }.code) // TMDB's own code for Cantonese
    }

    @Test
    fun aCodeFindsItsLanguage() {
        assertEquals("Korean", Languages.nameOf("ko"))
        assertEquals("Thai", Languages.byCode("th")?.name)
        assertNull(Languages.byCode(null)) // any language
        assertNull(Languages.byCode("xx"))
    }

    // ---------- live suggestions ----------

    @Test
    fun typingEGivesEnglishFirst() {
        val found = names("E")
        assertEquals("English", found.first())
        assertTrue("Estonian" in found)
    }

    @Test
    fun suggestionsAreCaseAndSpaceInsensitive() {
        assertEquals("Korean", names("  KOR ").first())
        assertEquals("Japanese", names("ja").first())
    }

    @Test
    fun theStartOfTheNameBeatsAMatchInTheMiddle() {
        val found = names("s")
        val starting = found.takeWhile { it.lowercase().startsWith("s") }
        assertTrue(starting.containsAll(listOf("Serbian", "Spanish", "Swedish")))
        // Everything after them only has an "s" somewhere inside ("Russian", "Persian"...)
        assertTrue(found.drop(starting.size).none { it.lowercase().startsWith("s") })
        assertEquals(starting.sorted(), starting) // A to Z inside the group
    }

    @Test
    fun aliasesFindTheirLanguage() {
        assertEquals("Filipino", names("tag").first()) // Tagalog
        assertTrue("Mandarin" in names("chin") && "Cantonese" in names("chin"))
        assertEquals("Persian", names("farsi").first())
    }

    @Test
    fun aTmdbCodeFindsItsLanguage() {
        assertEquals("Korean", names("ko").first())
        assertEquals("Hindi", names("hi").first()) // the name "Hindi" starts with "hi" too
    }

    @Test
    fun aWordInTheMiddleOfAnyNameMatches() {
        assertTrue("Indonesian" in names("nes"))
    }

    @Test
    fun blankTextSuggestsEveryLanguageAndNonsenseNone() {
        assertEquals(Languages.all, Languages.search(""))
        assertEquals(Languages.all, Languages.search("   "))
        assertTrue(Languages.search("qqqq").isEmpty())
    }

    // ---------- picking by typed text ----------

    @Test
    fun anExactNameOrAliasIsAcceptedWhateverTheCase() {
        assertEquals("ko", Languages.exact("korean")?.code)
        assertEquals("tl", Languages.exact(" Tagalog ")?.code)
        assertNull(Languages.exact("Kor"))
        assertNull(Languages.exact(""))
    }

    // ---------- does a movie match the asked language ----------

    @Test
    fun aMovieMatchesItsOwnLanguageInAnyCase() {
        assertTrue(Languages.matches("ko", "ko"))
        assertTrue(Languages.matches("ko", "KO"))
        assertFalse(Languages.matches("ko", "ja"))
    }

    @Test
    fun filipinoAlsoMatchesTheCodeFil() {
        assertTrue(Languages.matches("tl", "tl"))
        assertTrue(Languages.matches("tl", "fil"))
        assertFalse(Languages.matches("tl", "en"))
    }
}
