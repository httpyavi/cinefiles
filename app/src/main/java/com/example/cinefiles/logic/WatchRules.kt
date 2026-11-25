package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction

/*
 * THE WATCH RULE (plain Kotlin, no screens: easy to test).
 *
 * A movie can only be LIKED, put in the FAVORITES, RATED (popcorn rating) or written about in the MOVIE DIARY (a new page) while it is
 * WATCHED. You cannot love, rank, score or review a movie you have not seen. So:
 *
 *  - Like, Favorite and Rate are refused on a movie that is not watched (the Watchlist counts as not watched).
 *    MovieRepository enforces it on the SAVED copy of the movie; the screens use the same functions to show the
 *    controls as unavailable (dimmed, they shake and say why when tapped).
 *  - Taking "watched" away (Mark as not watched, or Move back to watchlist) also takes away the like, the rating and
 *    the favorite, in the same database change, so the database can never hold a liked, rated or favorited movie that
 *    is not watched. The Snackbar that follows offers Undo, which puts all of it back.
 *  - Rating does NOT mark the movie as watched for you (we chose "block with a hint" over "do it silently"): it is
 *    the same answer for all three actions, "Mark as watched first".
 */

/** The Favorites on the Profile hold at most this many movies. */
const val MAX_FAVORITES = 4

/** Can this movie be liked? [movie] is the SAVED copy (null = not saved yet, which means not watched). */
fun canLike(movie: Movie?): Boolean = movie?.isWatched == true

/** Can this movie be put in the Favorites? (Only watched movies; the limit of 4 is a separate rule.) */
fun canFavorite(movie: Movie?): Boolean = movie?.isWatched == true

/** Can this movie be rated with popcorn? */
fun canRate(movie: Movie?): Boolean = movie?.isWatched == true

/**
 * Can a NEW diary page be started about this movie? Pages that were already written stay (they are the user's own words, un-watching a
 * movie never deletes them) and can still be opened from My Diary; only starting a new page needs a watched movie.
 */
fun canWriteDiary(movie: Movie?): Boolean = movie?.isWatched == true

/** Is something stored on this movie that only a watched movie may have (a like, a rating or a favorite)? */
fun Movie.hasWatchDependents(): Boolean = reaction == Reaction.LIKED || userRating != null || isFavorite

/**
 * The movie after "watched" has been taken away: not watched, and without the like, the rating and the favorite.
 * (A "passed" reaction is not a like, so it stays.) This is what the database queries `unmarkWatched` and
 * `addToWatchlist` do, written once in Kotlin so the in-memory test database and the tests agree with them.
 */
fun Movie.withoutWatched(): Movie = copy(
    isWatched = false,
    watchedAt = 0L,
    reaction = if (reaction == Reaction.LIKED) Reaction.NONE else reaction,
    reactedAt = if (reaction == Reaction.LIKED) 0L else reactedAt,
    userRating = null,
    ratedAt = 0L,
    isFavorite = false,
    favoritedAt = 0L,
    favoriteOrder = 0
)

/** What a screen should say when a refused action needs explaining. */
enum class WatchRuleBlock { LIKE, FAVORITE, RATE, DIARY }
