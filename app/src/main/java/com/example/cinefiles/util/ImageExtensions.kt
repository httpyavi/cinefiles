package com.example.cinefiles.util

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.cinefiles.R
import com.example.cinefiles.ui.components.ShimmerDrawable

/**
 * Loads a picture from the web into this ImageView with Glide.
 *
 * - When the picture arrives it fades in softly.
 * - Glide cancels the download by itself when the screen goes away.
 * - While it loads, or if it fails, or the url is null:
 *     showSurfaceWhileMissing = true  -> a quiet surface-colored block is shown (posters)
 *     showSurfaceWhileMissing = false -> the ImageView stays EMPTY, so whatever is drawn behind
 *                                        it shows through (the mood cards' tinted fallback)
 * - onResult (optional) is told true when the picture arrived, or false when there is none
 *   (no url, or the download failed).
 */
fun ImageView.loadImage(
    url: String?,
    showSurfaceWhileMissing: Boolean = true,
    onResult: ((loaded: Boolean) -> Unit)? = null
) {
    var request = Glide.with(this)
        .load(url)
        .centerCrop()
        .transition(DrawableTransitionOptions.withCrossFade())

    if (showSurfaceWhileMissing) {
        val cornerRadius = resources.getDimension(R.dimen.radius_medium)
        val shimmer = ShimmerDrawable(cornerRadius = cornerRadius, startImmediately = true)
        request = request
            .placeholder(shimmer)
            .fallback(R.drawable.bg_poster_fallback)
            .error(R.drawable.bg_poster_fallback)
    }

    if (onResult != null) {
        request = request.listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
            ): Boolean {
                onResult(false)
                return false // false = let Glide do its normal work (show the error drawable)
            }

            override fun onResourceReady(
                resource: Drawable, model: Any, target: Target<Drawable>?,
                dataSource: DataSource, isFirstResource: Boolean
            ): Boolean {
                onResult(true)
                return false
            }
        })
    }
    request.into(this)
}
