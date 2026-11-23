package com.example.cinefiles.logic

/**
 * Remembers which page of TMDB's answer each deck question is on.
 *
 * Why: Discovery.buildDeck asks the same questions every time it is called ("keep swiping", or the deck
 * running low). Asking page 1 again would only bring back the movies we already have. So each question
 * moves on to its next page after every successful answer, and the deck keeps getting NEW movies.
 * (A question is a [DiscoveryRequest]: the kind of movie plus the mood and preferences, loosened or not.)
 */
class PageCounter {
    private val pages = HashMap<DiscoveryRequest, Int>()

    /** The page to ask for now (1 for a question that was never asked). */
    fun pageFor(request: DiscoveryRequest): Int = pages[request] ?: 1

    /** The answer for the current page arrived: the next time, ask for the page after it. */
    fun markAnswered(request: DiscoveryRequest) {
        pages[request] = pageFor(request) + 1
    }

    /** Start over from page 1 (a brand new deck). */
    fun reset() = pages.clear()
}
