package com.example.cinefiles.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.ItemPosterRowBinding
import com.example.cinefiles.ui.components.PosterView
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.loadImage

/**
 * One row of posters. Home uses two of these (Trending and Popular).
 * Hand it a new list with submitList(...); DiffUtil works out what changed, so only the
 * posters that are really new are redrawn.
 */
class PosterRowAdapter(
    private val onMovieClick: (Movie) -> Unit
) : ListAdapter<Movie, PosterRowAdapter.PosterViewHolder>(MovieDiffCallback) {

    class PosterViewHolder(val poster: PosterView) : RecyclerView.ViewHolder(poster)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val binding = ItemPosterRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PosterViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        val movie = getItem(position)
        holder.poster.contentDescription = movie.title // read out by screen readers
        holder.poster.imageView.loadImage(ImageUrls.poster(movie.posterPath))
        holder.poster.setOnClickListener { onMovieClick(movie) }
    }

    override fun onViewRecycled(holder: PosterViewHolder) {
        // The view is about to show another movie: stop its old download so a late picture cannot land on it
        Glide.with(holder.poster).clear(holder.poster.imageView)
    }

    /** Tells DiffUtil when two list entries are the same movie, and when that movie changed. */
    private object MovieDiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Movie, newItem: Movie) = oldItem == newItem
    }
}
