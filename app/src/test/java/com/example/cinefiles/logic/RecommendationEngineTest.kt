package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three rules of "Your Picks". The tests check what the rules MEAN (liked genres rank higher, passed ones lower,
 * excluded movies never appear...), not the exact weights, so a small change of a weight does not break them.
 */
class RecommendationEngineTest {

    private val engine = RecommendationEngine()

    private val comedy = 35
    private val drama = 18
    private val horror = 27
    private val romance = 10749
    private val action = 28

    private val happy = Moods.all.first { it.id == "happy" }       // comedy
    private val romantic = Moods.all.first { it.id == "romantic" } // romance
    private val surprise = Moods.all.first { it.isSurprise }       // no genres at all

    private fun movie(
        id: Int,
        genres: List<Int> = listOf(comedy),
        rating: Double = 7.0,
        year: Int = 2010,
        title: String = "Movie $id",
        poster: String? = "/p$id.jpg"
    ) = Movie(
        id = id, title = title, posterPath = poster, backdropPath = null, voteAverage = rating,
        releaseYear = year, genreIds = genres, voteCount = 500
    )

    private fun ids(picks: List<Recommendation>) = picks.map { it.movie.id }

    // ---------- the genre profile ----------

    @Test
    fun aMoodsGenresCountAndTheBestOneIsOne() {
        val profile = engine.genreProfile(listOf(happy, romantic), liked = emptyList(), passed = emptyList())

        assertEquals(setOf(comedy, romance), profile.keys)
        assertTrue(profile.values.all { it in 0.0..1.0 })
        assertEquals(1.0, profile.values.maxOrNull()!!, 1e-9)
    }

    @Test
    fun likedGenresRaiseTheProfile() {
        val noLikes = engine.genreProfile(listOf(happy, romantic), emptyList(), emptyList())
        val likedAComedy = engine.genreProfile(listOf(happy, romantic), liked = listOf(movie(1, listOf(comedy))), passed = emptyList())

        // Both moods weigh the same, until the user likes a comedy: then comedy leads
        assertEquals(noLikes.getValue(comedy), noLikes.getValue(romance), 1e-9)
        assertTrue(likedAComedy.getValue(comedy) > likedAComedy.getValue(romance))
    }

    @Test
    fun passedGenresLowerTheProfile() {
        val passedAComedy = engine.genreProfile(listOf(happy, romantic), liked = emptyList(), passed = listOf(movie(1, listOf(comedy))))

        assertTrue(passedAComedy.getValue(comedy) < passedAComedy.getValue(romance))
        assertTrue(passedAComedy.getValue(comedy) > 0.0) // lowered, not erased: the mood still asked for it
    }

    @Test
    fun aGenreThatIsOnlyPassedNeverGoesBelowZero() {
        val profile = engine.genreProfile(listOf(happy), emptyList(), passed = listOf(movie(1, listOf(horror)), movie(2, listOf(horror))))

        assertFalse(horror in profile) // "not at all", not a punishment for movies of that genre
        assertTrue(profile.values.all { it >= 0.0 })
    }

    @Test
    fun withNothingToGoOnTheProfileIsEmpty() {
        assertTrue(engine.genreProfile(emptyList(), emptyList(), emptyList()).isEmpty())
        assertTrue(engine.genreProfile(listOf(surprise), emptyList(), emptyList()).isEmpty()) // Surprise Me has no genres
        assertTrue(engine.genreProfile(emptyList(), emptyList(), listOf(movie(1))).isEmpty()) // only passes: nothing positive
    }

    @Test
    fun theBestGenresComeFirstAndTiesGoToTheSmallerId() {
        val profile = mapOf(comedy to 1.0, drama to 1.0, romance to 0.5, horror to 0.2, action to 0.1)

        assertEquals(listOf(drama, comedy, romance), engine.topGenres(profile)) // 18 before 35: the tie
        assertEquals(listOf(drama), engine.topGenres(profile, count = 1))
        assertTrue(engine.topGenres(emptyMap()).isEmpty())
    }

    // ---------- the score ----------

    @Test
    fun aMovieOfALikedGenreScoresHigherThanOneOfAnotherGenre() {
        val profile = engine.genreProfile(emptyList(), liked = listOf(movie(1, listOf(horror))), passed = emptyList())

        val horrorScore = engine.score(movie(2, listOf(horror)), profile, averageLikedYear = 2010.0)
        val comedyScore = engine.score(movie(3, listOf(comedy)), profile, averageLikedYear = 2010.0)

        assertTrue(horrorScore > comedyScore)
    }

    @Test
    fun aBetterRatedMovieScoresHigherAllElseEqual() {
        val profile = mapOf(comedy to 1.0)
        assertTrue(
            engine.score(movie(1, rating = 8.5), profile, null) > engine.score(movie(2, rating = 6.0), profile, null)
        )
    }

    @Test
    fun aMovieCloserToTheYearOfYourLikesScoresHigher() {
        val profile = mapOf(comedy to 1.0)
        val near = engine.score(movie(1, year = 1996), profile, averageLikedYear = 1995.0)
        val far = engine.score(movie(2, year = 2022), profile, averageLikedYear = 1995.0)

        assertTrue(near > far)
    }

    @Test
    fun withoutLikesTheEraDoesNotFavourAnyYear() {
        val profile = mapOf(comedy to 1.0)
        val old = engine.score(movie(1, year = 1970), profile, averageLikedYear = null)
        val recent = engine.score(movie(2, year = 2023), profile, averageLikedYear = null)

        assertEquals(old, recent, 1e-9)
    }

    @Test
    fun theScoreStaysBetweenZeroAndOne() {
        val profile = mapOf(comedy to 1.0)

        val best = engine.score(movie(1, rating = 10.0, year = 2000), profile, averageLikedYear = 2000.0)
        val worst = engine.score(movie(2, genres = listOf(horror), rating = 0.0, year = 1900), profile, averageLikedYear = 2020.0)
        val unknown = engine.score(movie(3, genres = emptyList()).copy(voteAverage = null, releaseYear = null), profile, 2000.0)

        assertEquals(1.0, best, 1e-9)
        assertTrue(worst in 0.0..1.0)
        assertTrue(unknown in 0.0..1.0)
    }

    @Test
    fun theMatchPercentIsTheScoreAsAPercentAndStaysInRange() {
        val any = movie(1)
        assertEquals(92, Recommendation(any, 0.921, PickReason.Generic).matchPercent)
        assertEquals(0, Recommendation(any, 0.0, PickReason.Generic).matchPercent)
        assertEquals(100, Recommendation(any, 1.4, PickReason.Generic).matchPercent)
        assertEquals(0, Recommendation(any, -0.2, PickReason.Generic).matchPercent)
    }

    // ---------- the picks ----------

    @Test
    fun theMoodDecidesWhenNothingWasLiked() {
        val candidates = listOf(movie(1, listOf(horror), rating = 9.0), movie(2, listOf(comedy), rating = 6.0))

        val picks = engine.recommend(candidates, listOf(happy), liked = emptyList(), passed = emptyList())

        assertEquals(listOf(2, 1), ids(picks)) // the comedy fits the Happy mood, even with the lower rating
    }

    @Test
    fun likedGenresRankMoviesOfThatGenreFirst() {
        val candidates = listOf(movie(1, listOf(comedy), rating = 7.5), movie(2, listOf(horror), rating = 7.0))

        val picks = engine.recommend(candidates, moods = emptyList(), liked = listOf(movie(100, listOf(horror))), passed = emptyList())

        assertEquals(2, picks.first().movie.id)
    }

    @Test
    fun passedGenresRankLower() {
        val candidates = listOf(movie(1, listOf(comedy)), movie(2, listOf(romance)))
        val moods = listOf(happy, romantic)

        val withoutPass = engine.recommend(candidates, moods, emptyList(), emptyList())
        val afterPassingAComedy = engine.recommend(candidates, moods, emptyList(), passed = listOf(movie(100, listOf(comedy))))

        assertEquals(1, withoutPass.first().movie.id)        // an even tie: the smaller id
        assertEquals(2, afterPassingAComedy.first().movie.id) // the romance now leads
    }

    @Test
    fun excludedMoviesNeverAppear() {
        val candidates = (1..15).map { movie(it) }

        val picks = engine.recommend(candidates, listOf(happy), emptyList(), emptyList(), excludeIds = setOf(1, 2, 3))

        assertTrue(ids(picks).none { it in setOf(1, 2, 3) })
    }

    @Test
    fun moviesYouLikedOrPassedInThisSittingNeverAppear() {
        val liked = movie(1)
        val passed = movie(2)

        val picks = engine.recommend(listOf(liked, passed, movie(3)), listOf(happy), listOf(liked), listOf(passed))

        assertEquals(listOf(3), ids(picks))
    }

    @Test
    fun moviesWithoutAPosterNeverAppear() {
        val picks = engine.recommend(listOf(movie(1, poster = null), movie(2)), listOf(happy), emptyList(), emptyList())
        assertEquals(listOf(2), ids(picks))
    }

    @Test
    fun aMovieThatIsInTheListTwiceAppearsOnce() {
        val picks = engine.recommend(listOf(movie(1), movie(1), movie(2)), listOf(happy), emptyList(), emptyList())
        assertEquals(1, ids(picks).count { it == 1 })
    }

    @Test
    fun atMostFourPicksShareTheSameFirstGenre() {
        val comedies = (1..12).map { movie(it, listOf(comedy), rating = 8.0) }
        val dramas = (21..23).map { movie(it, listOf(drama), rating = 5.0) }

        val picks = engine.recommend(comedies + dramas, listOf(happy), emptyList(), emptyList())

        assertEquals(4, picks.count { it.movie.genreIds.first() == comedy })
        assertEquals(3, picks.count { it.movie.genreIds.first() == drama }) // the lower scores still make it in
        assertEquals(7, picks.size)
    }

    @Test
    fun neverMoreThanTenPicks() {
        val genres = listOf(comedy, drama, horror, romance, action)
        val candidates = (1..40).map { movie(it, listOf(genres[it % genres.size])) }

        val picks = engine.recommend(candidates, listOf(happy), emptyList(), emptyList())

        assertEquals(10, picks.size)
        assertTrue(genres.all { genre -> picks.count { it.movie.genreIds.first() == genre } <= 4 })
    }

    @Test
    fun nothingToChooseFromGivesNoPicks() {
        assertTrue(engine.recommend(emptyList(), listOf(happy), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun withNoMoodAndNoLikesTheBestRatedComeFirst() {
        val candidates = listOf(movie(1, rating = 6.0), movie(2, rating = 9.0), movie(3, rating = 7.5))

        val picks = engine.recommend(candidates, emptyList(), emptyList(), emptyList())

        assertEquals(listOf(2, 3, 1), ids(picks))
        assertTrue(picks.all { it.reason == PickReason.Generic })
    }

    @Test
    fun theOrderIsTheSameEveryTimeWhateverOrderTheCandidatesComeIn() {
        // Three movies with exactly the same score: only the id can tell them apart
        val candidates = listOf(movie(5), movie(3), movie(9), movie(1, rating = 6.0))

        val first = engine.recommend(candidates, listOf(happy), emptyList(), emptyList())
        val again = engine.recommend(candidates, listOf(happy), emptyList(), emptyList())
        val shuffled = engine.recommend(candidates.reversed(), listOf(happy), emptyList(), emptyList())

        assertEquals(listOf(3, 5, 9, 1), ids(first))
        assertEquals(first, again)
        assertEquals(first, shuffled)
    }

    @Test
    fun moviesThatMatchOnlyInTheEraBreakTheirTieByTheEra() {
        val candidates = listOf(movie(1, year = 2022), movie(2, year = 1996))

        val picks = engine.recommend(candidates, emptyList(), liked = listOf(movie(100, listOf(drama), year = 1995)), passed = emptyList())

        assertEquals(2, picks.first().movie.id) // 1996 is next to 1995
    }

    // ---------- the reason ----------

    @Test
    fun theReasonNamesTheLikedMovieItIsMostSimilarTo() {
        val liked = listOf(
            movie(100, listOf(horror), title = "Scary One"),
            movie(101, listOf(comedy, romance), title = "Funny Love")
        )

        val picks = engine.recommend(listOf(movie(1, listOf(romance, comedy))), listOf(happy), liked, emptyList())

        assertEquals(PickReason.SimilarTo("Funny Love"), picks.single().reason) // shares two genres, not just none
    }

    @Test
    fun withNoLikesTheReasonIsTheMoodThatFitsBest() {
        val picks = engine.recommend(listOf(movie(1, listOf(romance))), listOf(happy, romantic), emptyList(), emptyList())

        assertEquals(PickReason.MoodMatch("romantic"), picks.single().reason)
    }

    @Test
    fun aMoodWithoutGenresStillNamesItselfAsTheReason() {
        val picks = engine.recommend(listOf(movie(1)), listOf(surprise), emptyList(), emptyList())

        assertEquals(PickReason.MoodMatch(surprise.id), picks.single().reason)
    }

    @Test
    fun everyPickHasAReason() {
        val picks = engine.recommend((1..8).map { movie(it, listOf(it + 1)) }, listOf(happy), listOf(movie(100, listOf(2))), emptyList())
        assertTrue(picks.isNotEmpty())
        picks.forEach { assertNotNull(it.reason) }
    }
}
