package com.example.cinefiles.util

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.cinefiles.logic.ITEMS_LEFT_BEFORE_NEXT_PAGE
import com.example.cinefiles.logic.shouldLoadNextPage

/**
 * Asks for the next page when the user scrolls close to the end of a list (about 6 items left).
 * The Discover tab uses it. To use it:
 *
 *     list.addOnScrollListener(PaginationScrollListener { viewModel.loadNextPage() })
 *
 * It may call [onLoadMore] again and again while the user stays near the end, so the ViewModel must ignore the
 * call when a page is already loading or there are no more pages. (Works with LinearLayoutManager,
 * GridLayoutManager and StaggeredGridLayoutManager, vertical or horizontal.)
 */
class PaginationScrollListener(
    private val itemsLeft: Int = ITEMS_LEFT_BEFORE_NEXT_PAGE,
    private val onLoadMore: () -> Unit
) : RecyclerView.OnScrollListener() {

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        // Only when scrolling forward (down or to the right)
        if (dx <= 0 && dy <= 0) return

        val manager = recyclerView.layoutManager ?: return
        val lastVisible = when (manager) {
            is LinearLayoutManager -> manager.findLastVisibleItemPosition() // also covers GridLayoutManager
            is StaggeredGridLayoutManager -> manager.findLastVisibleItemPositions(null).maxOrNull() ?: -1
            else -> -1
        }
        if (shouldLoadNextPage(manager.itemCount, lastVisible, itemsLeft)) onLoadMore()
    }
}
