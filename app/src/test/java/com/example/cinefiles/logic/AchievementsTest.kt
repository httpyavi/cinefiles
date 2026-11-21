package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private fun movie(id: Int, vararg genres: String) =
        Movie(id, "Movie $id", null, null, genreNames = genres.toList())

    private fun achievement(id: AchievementId) = Achievements.all.first { it.id == id }

    private fun swipe(
        stats: AchievementStats, movie: Movie, direction: SwipeDirection,
        hour: Int = 15, session: Int = 1, emotional: Boolean = false
    ) = Achievements.afterSwipe(stats, movie, direction, hour, session, emotional)

    @Test
    fun everyAchievementHasAGoalAndNoneIsMissing() {
        assertEquals(AchievementId.values().size, Achievements.all.size)
        assertEquals(AchievementId.values().toSet(), Achievements.all.map { it.id }.toSet())
        assertTrue(Achievements.all.all { it.goal > 0 })
    }

    @Test
    fun firstLikeUnlocksFirstFrame() {
        val first = achievement(AchievementId.FIRST_FRAME)
        val afterPass = swipe(AchievementStats(), movie(1, "Drama"), SwipeDirection.PASS)
        assertFalse(Achievements.isUnlocked(first, afterPass))

        val afterLike = swipe(afterPass, movie(2, "Drama"), SwipeDirection.LIKE)
        assertTrue(Achievements.isUnlocked(first, afterLike))
    }

    @Test
    fun likesAndPassesAreCountedSeparately() {
        var stats = AchievementStats()
        repeat(3) { stats = swipe(stats, movie(it, "Drama"), SwipeDirection.LIKE) }
        repeat(2) { stats = swipe(stats, movie(10 + it, "Drama"), SwipeDirection.PASS) }
        assertEquals(5, stats.swiped)
        assertEquals(3, stats.liked)
        assertEquals(2, stats.passed)
    }

    @Test
    fun progressNeverGoesAboveTheGoal() {
        var stats = AchievementStats()
        repeat(15) { stats = swipe(stats, movie(it, "Drama"), SwipeDirection.PASS) }
        val explorer = achievement(AchievementId.SCENE_EXPLORER)
        assertEquals(10, Achievements.progress(explorer, stats))
        assertTrue(Achievements.isUnlocked(explorer, stats))
    }

    @Test
    fun genreCollectorCountsDifferentLikedGenresNotPasses() {
        val collector = achievement(AchievementId.GENRE_COLLECTOR)
        var stats = AchievementStats()
        for ((i, genre) in listOf("Drama", "Comedy", "Action", "Horror").withIndex()) {
            stats = swipe(stats, movie(i, genre), SwipeDirection.LIKE)
        }
        stats = swipe(stats, movie(50, "Romance"), SwipeDirection.PASS) // a pass does not count
        assertEquals(4, Achievements.progress(collector, stats))
        assertFalse(Achievements.isUnlocked(collector, stats))

        stats = swipe(stats, movie(51, "Crime", "Drama"), SwipeDirection.LIKE)
        assertEquals(5, Achievements.progress(collector, stats))
        assertTrue(Achievements.isUnlocked(collector, stats))
    }

    @Test
    fun acrossTheGenresCountsEveryGenreSeenEvenOnPasses() {
        var stats = AchievementStats()
        val genres = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        for ((i, g) in genres.withIndex()) stats = swipe(stats, movie(i, g), SwipeDirection.PASS)
        assertTrue(Achievements.isUnlocked(achievement(AchievementId.ACROSS_THE_GENRES), stats))
    }

    @Test
    fun nightOwlOnlyCountsLateNightSwipes() {
        val owl = achievement(AchievementId.NIGHT_OWL)
        var stats = AchievementStats()
        for (hour in listOf(12, 18, 22, 5)) stats = swipe(stats, movie(hour, "Drama"), SwipeDirection.PASS, hour = hour)
        assertEquals(0, stats.nightSwipes)
        for (hour in listOf(23, 0, 2, 4, 4)) stats = swipe(stats, movie(100 + hour, "Drama"), SwipeDirection.PASS, hour = hour)
        assertEquals(5, stats.nightSwipes)
        assertTrue(Achievements.isUnlocked(owl, stats))
    }

    @Test
    fun directorsCutNeedsTwentyCardsInOneSitting() {
        val cut = achievement(AchievementId.DIRECTORS_CUT)
        var stats = AchievementStats()
        // two sittings of 15 cards are not one sitting of 30
        for (n in 1..15) stats = swipe(stats, movie(n, "Drama"), SwipeDirection.PASS, session = n)
        for (n in 1..15) stats = swipe(stats, movie(100 + n, "Drama"), SwipeDirection.PASS, session = n)
        assertEquals(15, stats.bestSession)
        assertFalse(Achievements.isUnlocked(cut, stats))

        for (n in 1..20) stats = swipe(stats, movie(200 + n, "Drama"), SwipeDirection.PASS, session = n)
        assertTrue(Achievements.isUnlocked(cut, stats))
    }

    @Test
    fun emotionalDamageOnlyCountsWhileEmotionalIsChosen() {
        var stats = AchievementStats()
        repeat(4) { stats = swipe(stats, movie(it, "Drama"), SwipeDirection.PASS, emotional = false) }
        assertEquals(0, stats.emotionalSwipes)
        repeat(5) { stats = swipe(stats, movie(10 + it, "Drama"), SwipeDirection.PASS, emotional = true) }
        assertTrue(Achievements.isUnlocked(achievement(AchievementId.EMOTIONAL_DAMAGE), stats))
    }

    @Test
    fun undoTakesTheLikeBackSoLikingAgainDoesNotCountTwice() {
        val film = movie(1, "Drama", "Crime")
        val liked = swipe(AchievementStats(), film, SwipeDirection.LIKE)
        assertEquals(1, liked.liked)

        val undone = Achievements.afterUndo(liked, film, SwipeDirection.LIKE, hourOfDay = 15, emotionalSession = false)
        assertEquals(0, undone.liked)
        assertEquals(0, undone.swiped)
        assertEquals(0, undone.likedGenres.count { it.value > 0 })

        val likedAgain = swipe(undone, film, SwipeDirection.LIKE)
        assertEquals(1, likedAgain.liked)
        assertEquals(2, likedAgain.likedGenres.count { it.value > 0 })
    }

    @Test
    fun undoNeverGoesBelowZeroAndGenresSeenStaySeen() {
        val film = movie(1, "Drama")
        val passed = swipe(AchievementStats(), film, SwipeDirection.PASS)
        val undone = Achievements.afterUndo(passed, film, SwipeDirection.PASS, 15, false)
        assertEquals(0, undone.passed)
        assertTrue("Drama" in undone.seenGenres)

        val again = Achievements.afterUndo(undone, film, SwipeDirection.PASS, 15, false)
        assertEquals(0, again.passed)
        assertEquals(0, again.swiped)
    }
}
