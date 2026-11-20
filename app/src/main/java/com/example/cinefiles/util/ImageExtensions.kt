package com.example.cinefiles.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.cinefiles.R

/**
 * Loads a picture from the web into this ImageView with Glide.
 *
 * - While it loads (or if it fails, or the url is null) a quiet surface-colored block is shown.
 * - When the picture arrives it fades in softly.
 * - Glide cancels the download by itself when the screen goes away.
 */
fun ImageView.loadImage(url: String?) {
    Glide.with(this)
        .load(url)
        .placeholder(R.color.surface)
        .error(R.color.surface)
        .centerCrop()
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(this)
}
