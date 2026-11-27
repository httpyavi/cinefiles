package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.PicksPool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of "Your Picks". The tests check what the rules MEAN (acclaimed movies beat noisy ones, liked genres rank
 * higher, passed ones lower, excluded movies never appear...), not the exact weights, so a small change of a weight
 * does not break them.
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
        poster: String? = "/p$id.jpg",
        votes: Int = 500,
        language: String = "en",
        minutes: Int? = null
    ) = Movie(
        id = id, title = title, posterPath = poster, backdropPath = null, voteAverage = rating,
        releaseYear = year, genreIds = genres, voteCount = votes, originalLanguage = language, runtimeMinutes = minutes
    )

    private fun ids(picks: List<Recommendation>) = picks.map { it.movie.id }

    private fun breakdown(candidate: Movie, moods: List<Mood> = emptyList(), liked: List<Movie> = emptyList(), passed: List<Movie> = emptyList()) =
        engine.breakdown(candidate, engine.tasteOf(moods, liked, passed))

    // ---------- the genre profile ----------

    @Test
    fun aMoodsGenresCountAndTheBestOneIsOne() {
        val profile = engine.genreProfile(listOf(happy, romantic), liked = emptyList(), passed = emptyList())

        assertEquals(setOf(comedy, romance), profile.keys)
        assertTrue(profile.values.all { it in 0.0..1.0 })
        assertEquals(1.0, profile.values.maxOrNull()!!, 1e-9)
    }

    @Test
    fun aSwipedRightMovieCountsDoubleWhatAMoodDoes() {
        val noSwipes = engine.genreProfile(listOf(happy, romantic), emptyList(), emptyList())
        val swipedAComedy = engine.genreProfile(listOf(happy, romantic), liked = listOf(movie(1, listOf(comedy))), passed = emptyList())

        // Both moods weigh the same, until the user swipes right on a comedy: then comedy (1 + 2) leads romance (1)
        assertEquals(noSwipes.getValue(comedy), noSwipes.getValue(romance), 1e-9)
        assertEquals(1.0, swipedAComedy.getValue(comedy), 1e-9)
        assertEquals(1.0 / 3.0, swipedAComedy.getValue(romance), 1e-9)
    }

    @Test
    fun aPassedMovieTakesBackWhatAMoodGaveItsGenre() {
        // comedy: +1 (mood) -1 (passed) = 0, romance: +1
        val profile = engine.genreProfile(listOf(happy, romantic), liked = emptyList(), passed = listOf(movie(1, listOf(comedy))))

        assertFalse(comedy in profile)
        assertEquals(1.0, profile.getValue(romance), 1e-9)
    }

    @Test
    fun aSwipeRightOutweighsOnePass() {
        val profile = engine.genreProfile(
            listOf(happy, romantic), liked = listOf(movie(1, listOf(comedy))), passed = listOf(movie(2, listOf(comedy)))
        )
        // comedy: +1 +2 -1 = 2, romance: +1
        assertEquals(1.0, profile.getValue(comedy), 1e-9)
        assertEquals(0.5, profile.getValue(romance), 1e-9)
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

    @Test
    fun theLatestThreeSwipedRightMoviesAreAskedForSimilarOnesNewestFirst() {
        val liked = (1..5).map { movie(it) }
        assertEquals(listOf(5, 4, 3), engine.similarSeeds(liked).map { it.id })
        assertEquals(listOf(2, 1), engine.similarSeeds(liked.take(2)).map { it.id })
        assertTrue(engine.similarSeeds(emptyList()).isEmpty())
        assertEquals(listOf(1), engine.similarSeeds(listOf(movie(1), movie(1))).map { it.id }) // a movie once
    }

    // ---------- the quality: IMDb's weighted rating ----------

    @Test
    fun theWeightedRatingIsPulledTowardTheAverageMovieWhenFewPeopleVoted() {
        assertEquals(6.8, engine.weightedRating(9.4, 0), 1e-9)               // nobody voted: the average movie
        assertEquals(7.9, engine.weightedRating(9.0, 500), 1e-9)            // as many votes as the weight: halfway
        assertEquals(8.0, engine.weightedRating(8.0, 1_000_000), 0.01)      // lots of votes: the rating itself
        assertTrue(engine.weightedRating(9.4, 12) < engine.weightedRating(8.2, 12_000))
    }

    @Test
    fun theQualityScoreStaysBetweenZeroAndOneAndRisesWithTheRating() {
        val best = engine.quality(movie(1, rating = 10.0, votes = 1_000_000))
        val worst = engine.quality(movie(2, rating = 1.0, votes = 1_000_000))
        val unrated = engine.quality(movie(3).copy(voteAverage = null, voteCount = 0))

        assertEquals(1.0, best, 1e-9)
        assertEquals(0.0, worst, 1e-9)
        assertTrue(unrated in 0.0..1.0)
        assertTrue(engine.quality(movie(4, rating = 8.0, votes = 5000)) > engine.quality(movie(5, rating = 7.0, votes = 5000)))
    }

    @Test
    fun aNoisyHighRatingIsWorthLessThanASolidOne() {
        assertTrue(engine.quality(movie(1, rating = 8.2, votes = 12_000)) > engine.quality(movie(2, rating = 9.4, votes = 12)))
    }

    @Test
    fun credibilityGrowsWithTheNumberOfVotes() {
        assertEquals(0.0, engine.credibility(movie(1, votes = 0)), 1e-9)
        assertTrue(engine.credibility(movie(2, votes = 300)) < engine.credibility(movie(3, votes = 3000)))
        assertEquals(1.0, engine.credibility(movie(4, votes = 500_000)), 1e-9)
    }

    // ---------- the genre match ----------

    @Test
    fun aMovieOfALikedGenreMatchesBetterThanOneOfAnotherGenre() {
        val liked = listOf(movie(1, listOf(horror)))

        val horrorMatch = breakdown(movie(2, listOf(horror)), liked = liked).genreMatch
        val comedyMatch = breakdown(movie(3, listOf(comedy)), liked = liked).genreMatch

        assertEquals(1.0, horrorMatch, 1e-9)
        assertEquals(0.0, comedyMatch, 1e-9)
    }

    @Test
    fun matchingMoreOfTheProfileMatchesBetter() {
        val liked = listOf(movie(1, listOf(comedy, romance)))

        val both = breakdown(movie(2, listOf(comedy, romance)), liked = liked).genreMatch
        val one = breakdown(movie(3, listOf(comedy)), liked = liked).genreMatch
        val oneAmongMany = breakdown(movie(4, listOf(comedy, action, horror, drama)), liked = liked).genreMatch

        assertTrue(both > one)
        assertTrue(one > oneAmongMany)
    }

    @Test
    fun withNoProfileEveryGenreIsNeutral() {
        assertEquals(0.5, breakdown(movie(1, listOf(horror))).genreMatch, 1e-9)
        assertEquals(0.5, breakdown(movie(2, listOf(comedy)), moods = listOf(surprise)).genreMatch, 1e-9)
    }

    // ---------- the era and the language ----------

    @Test
    fun aMovieOfTheSameDecadeAsYourLikesMatchesBetterThanAFarOneAndTheNextDecadeIsHalfway() {
        val liked = listOf(movie(100, year = 1995))

        val same = breakdown(movie(1, year = 1999), liked = liked).eraAndLanguage
        val next = breakdown(movie(2, year = 2004), liked = liked).eraAndLanguage
        val far = breakdown(movie(3, year = 2022), liked = liked).eraAndLanguage

        assertTrue(same > next)
        assertTrue(next > far)
    }

    @Test
    fun aMovieInTheLanguageOfYourLikesMatchesBetter() {
        val liked = listOf(movie(100, language = "ko"), movie(101, language = "ko"))

        val korean = breakdown(movie(1, language = "ko"), liked = liked).eraAndLanguage
        val english = breakdown(movie(2, language = "en"), liked = liked).eraAndLanguage

        assertTrue(korean > english)
    }

    @Test
    fun withoutSwipedRightMoviesEraAndLanguageAreNeutral() {
        assertEquals(0.5, breakdown(movie(1, year = 1970, language = "ja")).eraAndLanguage, 1e-9)
        assertEquals(0.5, breakdown(movie(2, year = 2023, language = "en")).eraAndLanguage, 1e-9)
    }

    @Test
    fun anUnknownYearOrLanguageIsNeutralToo() {
        val liked = listOf(movie(100, year = 1995, language = "ko"))
        val unknown = movie(1).copy(releaseYear = null, originalLanguage = null)
        assertEquals(0.5, breakdown(unknown, liked = liked).eraAndLanguage, 1e-9)
    }

    // ---------- the pass penalty ----------

    @Test
    fun aGenrePassedClearlyMoreOftenThanLikedPenalisesMoviesThatStartWithIt() {
        val passed = listOf(movie(100, listOf(horror)), movie(101, listOf(horror, drama)))

        assertEquals(0.10, breakdown(movie(1, listOf(horror)), passed = passed).passPenalty, 1e-9)
        assertEquals(0.0, breakdown(movie(2, listOf(drama, horror)), passed = passed).passPenalty, 1e-9) // its FIRST genre is drama
    }

    @Test
    fun theBiggerTheNetPassesTheBiggerThePenaltyUpToAMaximum() {
        fun penaltyAfter(passes: Int) =
            breakdown(movie(1, listOf(horror)), passed = (1..passes).map { movie(100 + it, listOf(horror)) }).passPenalty

        assertEquals(0.0, penaltyAfter(1), 1e-9) // one pass is not "heavy"
        assertTrue(penaltyAfter(3) > penaltyAfter(2))
        assertEquals(0.20, penaltyAfter(4), 1e-9)
        assertEquals(0.20, penaltyAfter(9), 1e-9)
    }

    @Test
    fun likesOfTheSameGenreCancelPasses() {
        val passed = listOf(movie(100, listOf(horror)), movie(101, listOf(horror)))
        val liked = listOf(movie(102, listOf(horror)))
        assertEquals(0.0, breakdown(movie(1, listOf(horror)), liked = liked, passed = passed).passPenalty, 1e-9)
    }

    @Test
    fun thePenaltyIsTakenOffTheScore() {
        val candidate = movie(1, listOf(horror, comedy))
        val passed = listOf(movie(100, listOf(horror)), movie(101, listOf(horror)))
        val parts = breakdown(candidate, moods = listOf(happy), passed = passed)
        val without = ScoreBreakdown(parts.genreMatch, parts.quality, parts.credibility, parts.eraAndLanguage, passPenalty = 0.0)

        assertEquals(without.total - 0.10, parts.total, 1e-9)
    }

    // ---------- the score ----------

    @Test
    fun theScoreIsMadeOfTheFourPartsWithTheirWeights() {
        val parts = ScoreBreakdown(genreMatch = 1.0, quality = 0.5, credibility = 0.2, eraAndLanguage = 0.0, passPenalty = 0.0)
        assertEquals(0.40 * 1.0 + 0.35 * 0.5 + 0.15 * 0.2, parts.total, 1e-9)
    }

    @Test
    fun theScoreStaysBetweenZeroAndOne() {
        val liked = listOf(movie(100, listOf(comedy), year = 2012))
        val taste = engine.tasteOf(listOf(happy), liked, emptyList())

        val best = engine.score(movie(1, rating = 10.0, votes = 50_000, year = 2014), taste)
        val worst = engine.score(movie(2, genres = listOf(horror), rating = 0.0, votes = 0, year = 1900, language = "ja"), taste)
        val unknown = engine.score(movie(3, genres = emptyList()).copy(voteAverage = null, releaseYear = null, voteCount = 0), taste)

        assertEquals(1.0, best, 1e-9)
        assertTrue(worst in 0.0..1.0)
        assertTrue(unknown in 0.0..1.0)
    }

    @Test
    fun aPenaltyBiggerThanTheScoreStopsAtZero() {
        assertEquals(0.0, ScoreBreakdown(0.0, 0.0, 0.0, 0.0, passPenalty = 0.2).total, 1e-9)
    }

    @Test
    fun theMatchPercentIsTheScoreAsAPercentAndStaysInRange() {
        val any = movie(1)
        assertEquals(92, Recommendation(any, 0.921, PickReason.Generic).matchPercent)
        assertEquals(0, Recommendation(any, 0.0, PickReason.Generic).matchPercent)
        assertEquals(100, Recommendation(any, 1.4, PickReason.Generic).matchPercent)
        assertEquals(0, Recommendation(any, -0.2, PickReason.Generic).matchPercent)
    }

    // ---------- the picks: quality wins when the genres are the same ----------

    @Test
    fun acclaimedWellVotedMoviesOutrankLowRatedOrLowVoteMoviesOfTheSameGenres() {
        val solid = movie(1, rating = 8.2, votes = 12_000)
        val decent = movie(2, rating = 6.9, votes = 600)
        val noisy = movie(3, rating = 9.4, votes = 12)   // a great rating from twelve people
        val poor = movie(4, rating = 5.1, votes = 80)

        // All four are comedies: the "three of a kind" limit would hide the last one, and here the whole order is the point
        val picks = RecommendationEngine(maxPerFirstGenre = 10)
            .recommend(listOf(poor, noisy, decent, solid), listOf(happy), liked = listOf(movie(100)), passed = emptyList())

        assertEquals(1, picks.first().movie.id)
        assertTrue(ids(picks).indexOf(1) < ids(picks).indexOf(2))
        assertTrue(ids(picks).indexOf(2) < ids(picks).indexOf(3)) // the noisy 9.4 is not worth the decent 6.9
        assertTrue(ids(picks).indexOf(1) < ids(picks).indexOf(4))
    }

    @Test
    fun aHighVoteCountAloneDoesNotBeatARealRatingDifference() {
        val loved = movie(1, rating = 8.6, votes = 8_000)
        val famousButMediocre = movie(2, rating = 6.3, votes = 30_000)

        val picks = engine.recommend(listOf(famousButMediocre, loved), listOf(happy), emptyList(), emptyList())

        assertEquals(listOf(1, 2), ids(picks))
    }

    @Test
    fun theStrongMatchOfYourSwipesComesBeforeTheWeakOneOfTheSameGenres() {
        val liked = listOf(movie(100, listOf(drama), year = 1995, language = "ko"))
        val passed = listOf(movie(101, listOf(horror)), movie(102, listOf(horror)))
        val strong = movie(1, listOf(drama), rating = 8.3, votes = 9_000, year = 1992, language = "ko")
        val weak = movie(2, listOf(drama), rating = 5.4, votes = 90, year = 2021, language = "en")
        val otherGenre = movie(3, listOf(horror), rating = 8.5, votes = 20_000, year = 1994, language = "ko")

        val picks = engine.recommend(listOf(weak, otherGenre, strong), moods = emptyList(), liked = liked, passed = passed)

        assertEquals(1, picks.first().movie.id)
        assertTrue(ids(picks).indexOf(1) < ids(picks).indexOf(2))
        assertTrue(ids(picks).indexOf(1) < ids(picks).indexOf(3))
    }

    @Test
    fun theMoodDecidesWhenNothingWasLikedAndTheQualityIsTheSame() {
        val candidates = listOf(movie(1, listOf(horror)), movie(2, listOf(comedy)))

        val picks = engine.recommend(candidates, listOf(happy), liked = emptyList(), passed = emptyList())

        assertEquals(listOf(2, 1), ids(picks)) // the comedy fits the Happy mood
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
        val afterPassingTwoComedies = engine.recommend(
            candidates, moods, emptyList(), passed = listOf(movie(100, listOf(comedy)), movie(101, listOf(comedy)))
        )

        assertEquals(1, withoutPass.first().movie.id)               // an even tie: the smaller id
        assertEquals(2, afterPassingTwoComedies.first().movie.id)   // the romance now leads
    }

    // ---------- the list ----------

    @Test
    fun excludedMoviesNeverAppear() {
        val candidates = (1..15).map { movie(it, genres = listOf(it)) }

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
    fun atMostThreePicksShareTheSameFirstGenre() {
        val comedies = (1..12).map { movie(it, listOf(comedy), rating = 8.0) }
        val dramas = (21..23).map { movie(it, listOf(drama), rating = 5.0) }
        val actions = (31..32).map { movie(it, listOf(action), rating = 5.0) }

        val picks = engine.recommend(comedies + dramas + actions, listOf(happy), emptyList(), emptyList())

        assertEquals(3, picks.count { it.movie.genreIds.first() == comedy })
        assertEquals(3, picks.count { it.movie.genreIds.first() == drama }) // the lower scores still make it in
        assertEquals(2, picks.count { it.movie.genreIds.first() == action })
        assertEquals(8, picks.size)
    }

    @Test
    fun neverMoreThanTenPicks() {
        val genres = listOf(comedy, drama, horror, romance, action)
        val candidates = (1..40).map { movie(it, listOf(genres[it % genres.size])) }

        val picks = engine.recommend(candidates, listOf(happy), emptyList(), emptyList())

        assertEquals(10, picks.size)
        assertTrue(genres.all { genre -> picks.count { it.movie.genreIds.first() == genre } <= 3 })
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
    }

    @Test
    fun theOrderIsTheSameEveryTimeWhateverOrderTheCandidatesComeIn() {
        // Three movies with exactly the same score: only the id can tell them apart
        val candidates = listOf(movie(5), movie(3), movie(9), movie(1, rating = 6.0))

        val all = RecommendationEngine(maxPerFirstGenre = 10) // the limit of three comedies would cut the fourth
        val first = all.recommend(candidates, listOf(happy), emptyList(), emptyList())
        val again = all.recommend(candidates, listOf(happy), emptyList(), emptyList())
        val shuffled = all.recommend(candidates.reversed(), listOf(happy), emptyList(), emptyList())

        assertEquals(listOf(3, 5, 9, 1), ids(first))
        assertEquals(first, again)
        assertEquals(first, shuffled)
    }

    @Test
    fun moviesThatMatchOnlyInTheEraBreakTheirTieByTheEra() {
        val candidates = listOf(movie(1, year = 2022), movie(2, year = 1996))

        val picks = engine.recommend(candidates, emptyList(), liked = listOf(movie(100, listOf(drama), year = 1995)), passed = emptyList())

        assertEquals(2, picks.first().movie.id) // 1996 is in the decade of 1995
    }

    // ---------- the reason ----------

    @Test
    fun aMovieTmdbCallsSimilarNamesTheMovieItIsSimilarTo() {
        val liked = listOf(movie(100, listOf(horror), title = "Scary One"), movie(101, listOf(drama), title = "Quiet One"))

        val picks = engine.recommend(
            listOf(movie(1, listOf(comedy))), listOf(happy), liked, emptyList(), similarTo = mapOf(1 to 101)
        )

        assertEquals(PickReason.SimilarTo("Quiet One"), picks.single().reason) // no shared genre needed: TMDB said so
    }

    @Test
    fun theReasonNamesTheLikedMovieThatSharesMostOfItsGenres() {
        val liked = listOf(
            movie(100, listOf(horror), title = "Scary One"),
            movie(101, listOf(comedy, romance), title = "Funny Love")
        )

        val picks = engine.recommend(listOf(movie(1, listOf(romance, comedy))), listOf(happy), liked, emptyList())

        assertEquals(PickReason.SimilarTo("Funny Love"), picks.single().reason)
    }

    @Test
    fun aLikedMovieWithOnlyASmallOverlapOfGenresIsNotCalledSimilar() {
        val liked = listOf(movie(100, listOf(comedy), title = "Funny"))

        // shares 1 of 4 genres: not "similar"
        val picks = engine.recommend(listOf(movie(1, listOf(drama, comedy, action, horror), rating = 7.0)), listOf(happy), liked, emptyList())

        assertEquals(PickReason.MoodMatch("happy"), picks.single().reason)
    }

    @Test
    fun aVeryWellRatedMoodMatchIsATopRatedPickOfThatMood() {
        val picks = engine.recommend(listOf(movie(1, listOf(romance), rating = 8.5, votes = 20_000)), listOf(happy, romantic), emptyList(), emptyList())

        assertEquals(PickReason.TopRated("romantic"), picks.single().reason)
    }

    @Test
    fun aVeryWellRatedMovieWithNoMoodIsJustATopRatedPick() {
        val picks = engine.recommend(listOf(movie(1, rating = 8.5, votes = 20_000)), emptyList(), emptyList(), emptyList())

        assertEquals(PickReason.TopRated(null), picks.single().reason)
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
    fun anOrdinaryMovieWithNothingToGoOnIsJustPickedForYou() {
        val picks = engine.recommend(listOf(movie(1)), emptyList(), emptyList(), emptyList())

        assertEquals(PickReason.Generic, picks.single().reason)
    }

    @Test
    fun everyPickHasAReason() {
        val picks = engine.recommend((1..8).map { movie(it, listOf(it + 1)) }, listOf(happy), listOf(movie(100, listOf(2))), emptyList())
        assertTrue(picks.isNotEmpty())
        picks.forEach { assertNotNull(it.reason) }
    }

    // ---------- the moods and the preferences are hard limits ----------

    /** Korean, from 2015 on, rated 8 or more, under 90 minutes. */
    private val strict = Preferences(ReleasePeriod.FROM_2015, MinRating.EIGHT_PLUS, Length.UNDER_90_MIN, "ko")

    /** A movie that fits Happy and [strict]: each test breaks one thing of it. */
    private fun fitting(id: Int, rating: Double = 8.4, minutes: Int? = 85) =
        movie(id, listOf(comedy), rating = rating, year = 2019, votes = 3000, language = "ko", minutes = minutes)

    private val anyGenre = RecommendationEngine(maxPerFirstGenre = 10) // the "three of a kind" limit is not the point here

    @Test
    fun noPickEverMissesTheMoodsGenresOrBreaksAPreference() {
        val good = (1..4).map { fitting(it) }
        val bad = listOf(
            fitting(10).copy(genreIds = listOf(horror)),                    // not a Happy genre (even though a horror was liked)
            fitting(11).copy(originalLanguage = "en"),                      // language
            fitting(12).copy(originalLanguage = null),                      // unknown language
            fitting(13).copy(voteAverage = 6.9, voteCount = 50_000),        // rating: famous, but under 8
            fitting(14).copy(releaseYear = 2012),                           // release period
            fitting(15).copy(releaseYear = null),                           // unknown year
            fitting(16, minutes = 140)                                      // length
        )

        val picks = anyGenre.recommend(
            bad + good, listOf(happy), liked = listOf(movie(100, listOf(horror))), passed = emptyList(), preferences = strict
        )

        assertEquals((1..4).toSet(), ids(picks).toSet())
    }

    @Test
    fun everyChosenMoodsGenreIsAllowedAndNoOther() {
        val candidates = listOf(fitting(1), fitting(2).copy(genreIds = listOf(romance)), fitting(3).copy(genreIds = listOf(drama)))

        val picks = anyGenre.recommend(candidates, listOf(happy, romantic), emptyList(), emptyList(), preferences = strict)

        assertEquals(setOf(1, 2), ids(picks).toSet())
    }

    @Test
    fun surpriseMeLimitsNoGenreButEveryPreferenceStillCounts() {
        val candidates = listOf(fitting(1).copy(genreIds = listOf(horror)), fitting(2).copy(originalLanguage = "ja"))

        val picks = engine.recommend(candidates, listOf(surprise), emptyList(), emptyList(), preferences = strict)

        assertEquals(listOf(1), ids(picks))
    }

    @Test
    fun theRuntimeOfEveryPickIsCheckedAndTheNextBestTakesTheGap() = runBlocking {
        // TMDB's list answers have no runtime. The two best rated are too long, and TMDB does not know the third's.
        val realRuntimes = mapOf(1 to 150, 2 to 130, 3 to null, 4 to 85, 5 to 80, 6 to 88, 7 to 70)
        val candidates = realRuntimes.keys.map { id -> fitting(id, rating = 9.0 - id * 0.1, minutes = null) }
        val lookedUp = ArrayList<Int>()

        val picks = anyGenre.recommendStrictly(
            candidates, listOf(happy), emptyList(), emptyList(), strict,
            lookUpRuntimes = { movies ->
                lookedUp += movies.map { it.id }
                movies.mapNotNull { movie -> realRuntimes[movie.id]?.let { movie.copy(runtimeMinutes = it) } }
            }
        )

        assertEquals(listOf(4, 5, 6, 7), ids(picks))
        assertTrue(picks.all { (it.movie.runtimeMinutes ?: Int.MAX_VALUE) <= 89 })
        assertEquals("a runtime is looked up once", lookedUp.size, lookedUp.toSet().size)
    }

    @Test
    fun overTwoHoursNeedsAKnownRuntimeOfAtLeastTwoHours() = runBlocking {
        val long = strict.copy(length = Length.OVER_2_HOURS)
        val candidates = listOf(fitting(1, minutes = 150), fitting(2, minutes = 100), fitting(3, minutes = null))

        val picks = anyGenre.recommendStrictly(candidates, listOf(happy), emptyList(), emptyList(), long, lookUpRuntimes = { emptyList() })

        assertEquals(listOf(1), ids(picks))
    }

    @Test
    fun withoutALengthNoRuntimeIsLookedUp() = runBlocking {
        var lookUps = 0

        val picks = engine.recommendStrictly(
            listOf(fitting(1, minutes = null)), listOf(happy), emptyList(), emptyList(), strict.copy(length = Length.ANY),
            lookUpRuntimes = { lookUps++; it }
        )

        assertEquals(listOf(1), ids(picks))
        assertEquals(0, lookUps)
    }

    @Test
    fun tooFewFittingMoviesGiveFewerPicksNeverLooserOnes() = runBlocking {
        // Twenty famous movies just under the 8.0 the user asked for, and only one that fits
        val almost = (20..40).map { fitting(it, rating = 7.9).copy(voteCount = 40_000) }

        val picks = engine.recommendStrictly(almost + fitting(1), listOf(happy), emptyList(), emptyList(), strict, lookUpRuntimes = { it })

        assertEquals(listOf(1), ids(picks))
    }

    // ---------- never empty: the closest picks complete the list ----------

    /** A pretend TMDB for the picks: every catalog movie that fits the limits asked for, minus the ids left out. */
    private fun candidatesFrom(
        catalog: List<Movie>,
        asked: MutableList<DiscoveryRequest> = ArrayList()
    ): suspend (DiscoveryRequest, Set<Int>) -> PicksPool = { limits, exclude ->
        asked += limits
        PicksPool(catalog.filter { it.id !in exclude && limits.matches(it) })
    }

    @Test
    fun whenNothingFitsEveryChoiceTheClosestPicksAreShownNearestFirstAndSaySo() = runBlocking {
        // Happy, Korean, 8+, under 90 minutes, 2015 on: no movie fits all of it
        val nearest = (1..4).map { fitting(it, rating = 7.5, minutes = 100) }                         // a point lower, 10 minutes longer
        val further = (11..20).map { fitting(it, rating = 6.0, minutes = 150).copy(releaseYear = 2001) } // much further off
        val horrorComedy = fitting(30).copy(genreIds = listOf(comedy, horror))

        val picks = anyGenre.recommendOrClosest(
            anyGenre.limitsFor(listOf(happy), strict), listOf(happy), emptyList(), emptyList(),
            candidatesFrom(nearest + further + horrorComedy), lookUpRuntimes = { it }
        )

        assertEquals(10, picks.size)
        assertTrue(picks.all { it.reason == PickReason.CloseMatch })
        assertTrue(ids(picks).containsAll((1..4).toList()))          // the nearest are all chosen...
        assertEquals(6, ids(picks).count { it in 11..20 })          // ...and the further ones only fill the rest
        assertEquals(picks.sortedByDescending { it.score }, picks)  // shown best match first
        assertFalse(30 in ids(picks)) // still never a horror film for Happy
    }

    @Test
    fun exactPicksComeFirstAndTheClosestOnlyFillTheRest() = runBlocking {
        val fits = (1..3).map { fitting(it) }
        val almost = (11..30).map { fitting(it, rating = 7.4) }

        val picks = anyGenre.recommendOrClosest(
            anyGenre.limitsFor(listOf(happy), strict), listOf(happy), emptyList(), emptyList(), candidatesFrom(fits + almost), { it }
        )

        assertEquals(10, picks.size)
        assertEquals((1..3).toSet(), ids(picks).take(3).toSet())
        assertTrue(picks.take(3).none { it.reason == PickReason.CloseMatch })
        assertTrue(picks.drop(3).all { it.reason == PickReason.CloseMatch && it.movie.voteAverage == 7.4 })
    }

    @Test
    fun withTenExactPicksNothingCloserIsLookedFor() = runBlocking {
        val asked = ArrayList<DiscoveryRequest>()

        val picks = anyGenre.recommendOrClosest(
            anyGenre.limitsFor(listOf(happy), strict), listOf(happy), emptyList(), emptyList(),
            candidatesFrom((1..15).map { fitting(it) }, asked), { it }
        )

        assertEquals(10, picks.size)
        assertEquals(1, asked.size)
        assertTrue(picks.none { it.reason == PickReason.CloseMatch })
    }

    @Test
    fun theMoodsLimitsLeaveOutTheGenresTheyAvoid() {
        val scared = Moods.all.first { it.id == "scared" }
        assertEquals(listOf(horror), engine.limitsFor(listOf(happy), Preferences()).withoutGenreIds)
        assertTrue(engine.limitsFor(listOf(happy, scared), Preferences()).withoutGenreIds.isEmpty())
    }
}
