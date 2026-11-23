package com.example.cinefiles.logic

/** A list asks for its next page when this many items (or fewer) are left below the last one on screen. */
const val ITEMS_LEFT_BEFORE_NEXT_PAGE = 6

/**
 * Should a list load its next page now?
 *
 * @param totalItems          how many items the list holds
 * @param lastVisiblePosition position of the last item you can see (0 = the first item), or -1 if none
 * @param itemsLeft           how many items below the visible ones are left before we ask for more
 *
 * Example: 20 items, you see up to item 14 (position 14): 5 are left, which is under 6, so load more.
 */
fun shouldLoadNextPage(
    totalItems: Int,
    lastVisiblePosition: Int,
    itemsLeft: Int = ITEMS_LEFT_BEFORE_NEXT_PAGE
): Boolean {
    if (totalItems == 0 || lastVisiblePosition < 0) return false
    val itemsBelow = totalItems - 1 - lastVisiblePosition
    return itemsBelow < itemsLeft
}
