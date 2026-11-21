package com.example.cinefiles.logic

import com.example.cinefiles.data.Movie

/** The two things you can do with a card. Swiping right (or the green button) = LIKE, left = PASS. */
enum class SwipeDirection { LIKE, PASS }

/** One finished swipe: which movie, and what the user decided. */
data class SwipeRecord(val movie: Movie, val direction: SwipeDirection)

/**
 * The deck of swipe cards and everything the user has done with it.
 *
 * It never changes: [swipe], [undo] and [withMoreCards] return a NEW deck. The important trick is that
 * only two things are stored, the list of [cards] and the [history] of swipes. Everything else
 * (how far we are, what is on top, who was liked) is worked out from those two. So progress, the
 * liked list and the cards can never disagree with each other.
 *
 * The first swipe in the history belongs to the first card, the second to the second card, and so on.
 * That is why [position] is simply the number of swipes in the history.
 */
data class SwipeDeck(
    val cards: List<Movie>,
    val history: List<SwipeRecord> = emptyList()
) {
    /** How many cards the deck has. */
    val total: Int get() = cards.size

    /** How many cards are already done (0 = nothing swiped yet). */
    val position: Int get() = history.size

    /** The cards waiting under the user's thumb: the one on top, and the next two behind it. */
    val top: Movie? get() = cards.getOrNull(position)
    val second: Movie? get() = cards.getOrNull(position + 1)
    val third: Movie? get() = cards.getOrNull(position + 2)

    /** True when every card has been swiped. */
    val isFinished: Boolean get() = position >= total

    /** The number in "7 of 20": the card you are looking at. It never goes above [total]. */
    val shownPosition: Int get() = minOf(position + 1, total)

    val canUndo: Boolean get() = history.isNotEmpty()

    /** The movies liked so far, each one once (even if it showed up twice in the deck). */
    val likedMovies: List<Movie>
        get() = history.filter { it.direction == SwipeDirection.LIKE }.map { it.movie }.distinctBy { it.id }

    val passedMovies: List<Movie>
        get() = history.filter { it.direction == SwipeDirection.PASS }.map { it.movie }.distinctBy { it.id }

    /** The top card is liked or passed. Nothing happens when the deck is already finished. */
    fun swipe(direction: SwipeDirection): SwipeDeck {
        val movie = top ?: return this
        return copy(history = history + SwipeRecord(movie, direction))
    }

    /** Takes the last swipe back, so that card is on top again. Nothing happens with no history. */
    fun undo(): SwipeDeck {
        if (history.isEmpty()) return this
        return copy(history = history.dropLast(1))
    }

    /** Adds more cards at the end ("Keep swiping"). The history stays, so the count keeps going up. */
    fun withMoreCards(more: List<Movie>): SwipeDeck = copy(cards = cards + more)
}
