package com.example.cinefiles.ui.picks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.ItemPickBinding
import com.example.cinefiles.logic.Recommendation
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.loadImage

/**
 * The three-column "More for you" grid. Each pick is a poster with its match percent and a bookmark button.
 * Tap the poster: the quick view. Tap the bookmark: puts the movie on the watchlist (or takes it off).
 * [setWatchlistIds] tells it which movies are on the watchlist right now, so their bookmark is filled.
 */
class PicksAdapter(
    private val onPickClick: (Recommendation) -> Unit,
    private val onBookmarkClick: (Movie) -> Unit
) : ListAdapter<Recommendation, PicksAdapter.PickViewHolder>(PickDiffCallback) {

    class PickViewHolder(val binding: ItemPickBinding) : RecyclerView.ViewHolder(binding.root)

    private var watchlistIds: Set<Int> = emptySet()

    fun setWatchlistIds(ids: Set<Int>) {
        if (ids == watchlistIds) return
        watchlistIds = ids
        notifyItemRangeChanged(0, itemCount) // at most nine picks: no need to work out which ones changed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickViewHolder =
        PickViewHolder(ItemPickBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PickViewHolder, position: Int) {
        val pick = getItem(position)
        val movie = pick.movie
        val context = holder.itemView.context
        val views = holder.binding

        views.pickPoster.contentDescription = context.getString(R.string.picks_quick_view_description, movie.title)
        views.pickPoster.imageView.loadImage(ImageUrls.poster(movie.posterPath))
        views.pickPoster.setOnClickListener { onPickClick(pick) }

        views.pickMatch.text = context.getString(R.string.picks_match_short, pick.matchPercent)

        val onList = movie.id in watchlistIds
        views.pickBookmark.setImageResource(if (onList) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        views.pickBookmark.contentDescription = context.getString(
            if (onList) R.string.picks_bookmark_remove else R.string.picks_bookmark_add, movie.title
        )
        views.pickBookmark.setOnClickListener { onBookmarkClick(movie) }
    }

    override fun onViewRecycled(holder: PickViewHolder) {
        Glide.with(holder.itemView).clear(holder.binding.pickPoster.imageView)
    }

    private object PickDiffCallback : DiffUtil.ItemCallback<Recommendation>() {
        override fun areItemsTheSame(oldItem: Recommendation, newItem: Recommendation) = oldItem.movie.id == newItem.movie.id
        override fun areContentsTheSame(oldItem: Recommendation, newItem: Recommendation) = oldItem == newItem
    }
}
