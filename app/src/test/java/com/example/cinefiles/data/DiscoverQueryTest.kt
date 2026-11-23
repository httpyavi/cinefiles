package com.example.cinefiles.data

import com.example.cinefiles.logic.Discovery
import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource
import com.example.cinefiles.logic.Language
import com.example.cinefiles.logic.Length
import com.example.cinefiles.logic.MinRating
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.ReleasePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Preferences screen's choices and the mood, turned into TMDB /discover parameters. */
class DiscoverQueryTest {

    private val comedy = 35
    private val action = 28

    /** The first question the deck asks (the "fits your mood" one) for these choices, as TMDB parameters. */
    private fun moodQuery(moods: List<Int>, preferences: Preferences = Preferences(), relax: Int = 0) =
        Discovery.plan(moods, preferences, relax).first { it.source == DiscoverySource.MOOD_MATCH || it.source == DiscoverySource.EXPLORE }
            .toDiscoverQuery()

    private fun query(source: DiscoverySource, preferences: Preferences = Preferences()) =
        Discovery.plan(listOf(comedy), preferences, 0).first { it.source == source }.toDiscoverQuery()

    // ---------- Mood ----------

    @Test
    fun moodGenresAreJoinedWithAPipeWhichMeansOr() {
        assertEquals("35|28", moodQuery(listOf(comedy, action)).withGenres)
        assertEquals("35", moodQuery(listOf(comedy)).withGenres)
    }

    @Test
    fun noMoodMeansNoGenreFilter() {
        assertNull(moodQuery(emptyList()).withGenres)
    }

    // ---------- Preferences ----------

    @Test
    fun noPreferencesMeansNoLimitsExceptTheVoteFloor() {
        val q = moodQuery(listOf(comedy))
        assertNull(q.releasedFrom)
        assertNull(q.releasedTo)
        assertNull(q.minRating)
        assertNull(q.minRuntime)
        assertNull(q.maxRuntime)
        assertNull(q.originalLanguage)
        assertNull(q.maxVotes)
        assertEquals(200, q.minVotes)
    }

    @Test
    fun eraBecomesAReleaseDateRange() {
        val before2000 = moodQuery(listOf(comedy), Preferences(releasePeriod = ReleasePeriod.BEFORE_2000))
        assertNull(before2000.releasedFrom)
        assertEquals("1999-12-31", before2000.releasedTo)

        val middle = moodQuery(listOf(comedy), Preferences(releasePeriod = ReleasePeriod.FROM_2000_TO_2014))
        assertEquals("2000-01-01", middle.releasedFrom)
        assertEquals("2014-12-31", middle.releasedTo)

        val recent = moodQuery(listOf(comedy), Preferences(releasePeriod = ReleasePeriod.FROM_2015))
        assertEquals("2015-01-01", recent.releasedFrom)
        assertNull(recent.releasedTo)
    }

    @Test
    fun ratingBecomesAMinimumVoteAverage() {
        assertEquals(6.0, moodQuery(listOf(comedy), Preferences(minRating = MinRating.SIX_PLUS)).minRating)
        assertEquals(7.0, moodQuery(listOf(comedy), Preferences(minRating = MinRating.SEVEN_PLUS)).minRating)
        assertEquals(8.0, moodQuery(listOf(comedy), Preferences(minRating = MinRating.EIGHT_PLUS)).minRating)
    }

    @Test
    fun lengthBecomesRuntimeLimits() {
        val short = moodQuery(listOf(comedy), Preferences(length = Length.UNDER_90_MIN))
        assertNull(short.minRuntime)
        assertEquals(89, short.maxRuntime)

        val medium = moodQuery(listOf(comedy), Preferences(length = Length.UNDER_2_HOURS))
        assertEquals(119, medium.maxRuntime)

        val long = moodQuery(listOf(comedy), Preferences(length = Length.OVER_2_HOURS))
        assertEquals(120, long.minRuntime)
        assertNull(long.maxRuntime)
    }

    @Test
    fun languageBecomesTheOriginalLanguageCode() {
        fun code(language: Language) = moodQuery(listOf(comedy), Preferences(language = language)).originalLanguage
        assertEquals("en", code(Language.ENGLISH))
        assertEquals("tl", code(Language.FILIPINO))
        assertEquals("ko", code(Language.KOREAN))
        assertEquals("ja", code(Language.JAPANESE))
        assertNull(code(Language.ANY))
    }

    // ---------- The kinds of movie ----------

    @Test
    fun topRatedAsksForTheBestRatedFirst() {
        val q = query(DiscoverySource.TOP_RATED)
        assertEquals("vote_average.desc", q.sortBy)
        assertEquals(7.5, q.minRating)
    }

    @Test
    fun hiddenGemsAskForAGoodRatingAndABoundedNumberOfVotes() {
        val q = query(DiscoverySource.HIDDEN_GEM)
        assertEquals("vote_average.desc", q.sortBy)
        assertEquals(7.0, q.minRating)
        assertEquals(200, q.minVotes)
        assertEquals(5000, q.maxVotes)
    }

    @Test
    fun theMoodMatchAsksForTheBestKnownFirst() {
        assertEquals("popularity.desc", query(DiscoverySource.MOOD_MATCH).sortBy)
    }

    // ---------- Loosening ----------

    @Test
    fun theLastRelaxStepDropsTheGenres() {
        val prefs = Preferences(releasePeriod = ReleasePeriod.FROM_2015, language = Language.KOREAN)
        val q = moodQuery(listOf(comedy), prefs, relax = 5)
        assertNull(q.withGenres)
        assertNull(q.releasedFrom)
        assertNull(q.originalLanguage)
    }

    @Test
    fun aRequestWithNoLimitsAtAllIsStillAValidQuery() {
        val q = DiscoveryRequest(DiscoverySource.EXPLORE).toDiscoverQuery()
        assertNull(q.withGenres)
        assertEquals(200, q.minVotes)
        assertEquals("popularity.desc", q.sortBy)
    }
}
