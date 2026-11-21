package com.example.cinefiles.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.PosterColor

/**
 * The glow color of each poster, worked out once and remembered (in memory) for the rest of the session.
 *
 * It asks Glide (already in the app) for a TINY copy of the poster, so this costs almost nothing, and
 * logic/PosterColor.kt turns its pixels into one color. No extra library.
 */
object PosterTint {

    private const val WIDTH = 48
    private const val HEIGHT = 72

    private val colors = HashMap<Int, Int>()
    private val pixels = IntArray(WIDTH * HEIGHT) // shared by every request: only the main thread uses it

    /** The color of this movie's poster, if it has been worked out already. */
    fun cached(movieId: Int): Int? = colors[movieId]

    /**
     * Works out the color of [movie]'s poster and calls [onReady] with it (on the main thread).
     * If it is already known, [onReady] is called right away. A poster that cannot be loaded gives no call.
     */
    fun load(context: Context, movie: Movie, onReady: (Int) -> Unit) {
        colors[movie.id]?.let { onReady(it); return }
        val url = ImageUrls.poster(movie.posterPath) ?: return

        Glide.with(context.applicationContext)
            .asBitmap()
            .load(url)
            .override(WIDTH, HEIGHT)
            .disallowHardwareConfig() // we read its pixels, which a hardware bitmap does not allow
            .into(object : CustomTarget<Bitmap>(WIDTH, HEIGHT) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val small = if (resource.width == WIDTH && resource.height == HEIGHT) {
                        resource
                    } else {
                        Bitmap.createScaledBitmap(resource, WIDTH, HEIGHT, true)
                    }
                    small.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                    val color = PosterColor.glowFor(pixels)
                    colors[movie.id] = color
                    onReady(color)
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
    }
}
