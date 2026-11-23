package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction

/*
 * THE RULES OF MY COLLECTION AND OF THE DETAILS BUTTONS (plain Kotlin, no screens: easy to test).
 *
 * A movie can be liked, on the watchlist and watched. Liked is independent of the other two: liking never
 * changes the watchlist or watched. The watchlist and watched are linked: a movie is on the watchlist OR watched,
 * never both (marking it watched takes it off the watchlist; adding it to the watchlist un-watches it).
 */

/** The three things a movie can be in your collection, from the most important to the least. */
enum class MovieStatus { WATCHED, LIKED, WATCHLIST }

/** Does this movie have this status? (The watchlist only counts movies that are not watched.) */
fun Movie.hasStatus(status: MovieStatus): Boolean = when (status) {
    MovieStatus.WATCHED -> isWatched
    MovieStatus.LIKED -> reaction == Reaction.LIKED
    MovieStatus.WATCHLIST -> inWatchlist && !isWatched
}

/**
 * THE LIKE RULE: a movie cannot be liked while it is on the watchlist. (Remove it from the watchlist, or mark it
 * watched, and liking works again.) [movie] is the movie as the database has it, or null if it is not saved yet.
 * MovieRepository.like enforces this; the screens use it to show Like as unavailable.
 */
fun canLike(movie: Movie?): Boolean = movie == null || !movie.hasStatus(MovieStatus.WATCHLIST)

/** The movie with the user's state (liked, watchlist, watched) of its saved copy. TMDB's answers know nothing about it. */
fun Movie.withStateOf(saved: Movie?): Movie =
    if (saved == null) this else copy(
        reaction = saved.reaction,
        reactedAt = saved.reactedAt,
        inWatchlist = saved.inWatchlist,
        watchlistAddedAt = saved.watchlistAddedAt,
        isWatched = saved.isWatched,
        watchedAt = saved.watchedAt
    )

/** The three tabs of My Collection. Each tab is the list of movies that have its status. */
enum class CollectionTab(val status: MovieStatus) {
    LIKED(MovieStatus.LIKED),
    WATCHLIST(MovieStatus.WATCHLIST),
    WATCHED(MovieStatus.WATCHED)
}

/**
 * Which small badge a poster shows on a given tab, or null for none.
 *
 * The tab's own status is left out, because on the Liked tab every poster is liked, and a heart on all of them
 * would say nothing. Of the other statuses the movie has, the most important one wins (watched, then liked,
 * then watchlist). So on the Liked tab you see a check mark on a movie you also watched.
 */
fun badgeStatusFor(movie: Movie, tab: CollectionTab?): MovieStatus? =
    // With no tab (the Discover grid) nothing is redundant: the movie's most important status is shown
    MovieStatus.values().firstOrNull { it != tab?.status && movie.hasStatus(it) }

/**
 * One thing the long-press menu can do to a poster.
 * [label] is the menu text, [message] the line in the Snackbar that follows (which also offers Undo).
 */
enum class CollectionAction(@StringRes val label: Int, @StringRes val message: Int) {
    ADD_TO_WATCHLIST(R.string.action_add_watchlist, R.string.msg_added_watchlist),
    REMOVE_FROM_WATCHLIST(R.string.action_remove_watchlist, R.string.msg_removed_watchlist),
    MARK_WATCHED(R.string.action_mark_watched, R.string.msg_marked_watched),
    MARK_UNWATCHED(R.string.action_unmark_watched, R.string.msg_unmarked_watched),
    MOVE_BACK_TO_WATCHLIST(R.string.action_move_back_watchlist, R.string.msg_moved_back_watchlist),
    LIKE(R.string.action_like, R.string.msg_liked),
    UNLIKE(R.string.action_unlike, R.string.msg_unliked)
}

/**
 * The actions in the long-press menu of a poster on [tab], in the order they are listed.
 * The last one is always the one that removes the movie from the tab it is on.
 */
fun actionsFor(tab: CollectionTab, movie: Movie): List<CollectionAction> {
    // "Like" is left out while the movie is on the watchlist (see canLike); "Unlike" is always possible
    val likeToggle: CollectionAction? = when {
        movie.reaction == Reaction.LIKED -> CollectionAction.UNLIKE
        canLike(movie) -> CollectionAction.LIKE
        else -> null
    }
    return when (tab) {
        // Liked tab: manage the other two lists, and finish with "Unlike" (which removes it from this tab)
        CollectionTab.LIKED -> listOf(
            when {
                movie.isWatched -> CollectionAction.MOVE_BACK_TO_WATCHLIST
                movie.inWatchlist -> CollectionAction.REMOVE_FROM_WATCHLIST
                else -> CollectionAction.ADD_TO_WATCHLIST
            },
            if (movie.isWatched) CollectionAction.MARK_UNWATCHED else CollectionAction.MARK_WATCHED,
            CollectionAction.UNLIKE
        )
        CollectionTab.WATCHLIST -> listOfNotNull(
            CollectionAction.MARK_WATCHED,
            likeToggle,
            CollectionAction.REMOVE_FROM_WATCHLIST
        )
        CollectionTab.WATCHED -> listOfNotNull(
            CollectionAction.MOVE_BACK_TO_WATCHLIST,
            likeToggle,
            CollectionAction.MARK_UNWATCHED
        )
    }
}

/**
 * What the big button on the Details screen says and does:
 * ADD (not on the list: tap to add), IN_LIST (on the list: tap to remove) or MOVE_BACK (the movie is watched:
 * tap to move it back to the watchlist).
 */
enum class WatchlistButton(@StringRes val label: Int, val isOnList: Boolean) {
    ADD(R.string.action_add_watchlist, false),
    IN_LIST(R.string.detail_in_watchlist, true),
    MOVE_BACK(R.string.action_move_back_watchlist, false)
}

fun watchlistButtonFor(movie: Movie): WatchlistButton = when {
    movie.isWatched -> WatchlistButton.MOVE_BACK
    movie.inWatchlist -> WatchlistButton.IN_LIST
    else -> WatchlistButton.ADD
}

/** 169 minutes is 2 hours and 49 minutes. Returns (hours, minutes). */
fun splitRuntime(totalMinutes: Int): Pair<Int, Int> = Pair(totalMinutes / 60, totalMinutes % 60)
