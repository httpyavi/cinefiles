package com.example.cinefiles.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.CollectionTab
import com.example.cinefiles.logic.MovieStatus
import com.example.cinefiles.logic.badgeStatusFor
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.loadImage

/** The two places posters are shown, and the layout of one poster in each. */
enum class PosterStyle(@LayoutRes val layout: Int) {
    /** A horizontal row (Home, Similar movies, "You liked"): posters of a fixed width. */
    ROW(R.layout.item_poster_row),

    /** A grid cell (My Collection, Discover): the width is the cell's. */
    GRID(R.layout.item_poster_grid)
}

/**
 * THE poster list of the app: one adapter for every row and grid of posters. Tap = [onMovieClick]; a long press
 * calls [onMovieLongClick] when it is given (My Collection's menu).
 *
 * With [showBadges] a poster wears a small status badge (liked, on the watchlist, watched). Which one depends on the
 * Collection tab being shown (see logic/CollectionRules.kt: badgeStatusFor), or, with no tab, on the movie's most
 * important status.
 *
 * Hand it a list with [submit]; DiffUtil works out what changed, so only changed posters are redrawn.
 */
class PosterAdapter(
    private val style: PosterStyle,
    private val onMovieClick: (Movie) -> Unit,
    private val onMovieLongClick: ((Movie, View) -> Unit)? = null,
    private val showBadges: Boolean = false
) : ListAdapter<Movie, PosterAdapter.PosterViewHolder>(MovieDiffCallback) {

    class PosterViewHolder(val poster: PosterView) : RecyclerView.ViewHolder(poster)

    private var tab: CollectionTab? = null

    /** Shows [movies]. [tab] is the Collection tab they are the list of (null: not a Collection tab). */
    fun submit(tab: CollectionTab?, movies: List<Movie>) {
        if (tab == this.tab) {
            submitList(movies)
        } else {
            // A different tab wears different badges, even on a poster that is in both lists
            // (DiffUtil would think it did not change), so every poster is redrawn once the list is in.
            this.tab = tab
            submitList(movies) { notifyItemRangeChanged(0, itemCount) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val poster = LayoutInflater.from(parent.context).inflate(style.layout, parent, false) as PosterView
        return PosterViewHolder(poster)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        val movie = getItem(position)
        val poster = holder.poster
        poster.contentDescription = movie.title // read out by screen readers
        poster.imageView.loadImage(ImageUrls.poster(movie.posterPath))
        poster.badge = if (showBadges) badgeStatusFor(movie, tab)?.toBadge() else null
        poster.setOnClickListener { onMovieClick(movie) }

        if (onMovieLongClick != null) {
            poster.setOnLongClickListener {
                onMovieLongClick.invoke(movie, poster)
                true
            }
        } else {
            poster.setOnLongClickListener(null)
            poster.isLongClickable = false
        }
    }

    override fun onViewRecycled(holder: PosterViewHolder) {
        // The view is about to show another movie: stop its old download so a late picture cannot land on it
        Glide.with(holder.poster).clear(holder.poster.imageView)
    }

    private fun MovieStatus.toBadge(): PosterBadge = when (this) {
        MovieStatus.WATCHED -> PosterBadge.WATCHED
        MovieStatus.LIKED -> PosterBadge.LIKED
        MovieStatus.WATCHLIST -> PosterBadge.WATCHLIST
    }
}

/** Tells DiffUtil when two list entries are the same movie, and when that movie changed. */
object MovieDiffCallback : DiffUtil.ItemCallback<Movie>() {
    override fun areItemsTheSame(oldItem: Movie, newItem: Movie) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Movie, newItem: Movie) = oldItem == newItem
}
