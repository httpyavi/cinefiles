package com.example.cinefiles.ui.swipe

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.ViewSwipeCardBinding
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.loadImage
import java.text.NumberFormat

/**
 * ONE movie card: a big rounded poster with the title, rating, year, genres and the LIKE / PASS stamps.
 *
 * It only knows how to LOOK. Moving it with a finger is done by SwipeDeckView, which owns three of
 * these cards and reuses them (rebinding them to new movies) instead of making one per movie.
 */
class SwipeCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewSwipeCardBinding.inflate(LayoutInflater.from(context), this)

    // Writes 8.5 instead of 8.4999 (and 8,5 on phones set to a language that uses the comma)
    private val ratingFormat = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

    /** The id of the movie on this card, or null when the card is empty. */
    var movieId: Int? = null
        private set

    init {
        // Rounded corners (they also clip the poster). The same outline gives the card its soft shadow.
        val radius = resources.getDimension(R.dimen.radius_xlarge)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true

        // The two stamps lean a little, like a rubber stamp pressed by hand
        binding.stampLike.rotation = -STAMP_ROTATION_DEGREES
        binding.stampPass.rotation = STAMP_ROTATION_DEGREES
        setStamps(likeAlpha = 0f, passAlpha = 0f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The card turns around a point in its lower part, so dragging it feels like swinging it by the top
        pivotX = w / 2f
        pivotY = h * PIVOT_HEIGHT_FRACTION
    }

    /** Shows [movie] on this card. */
    fun bind(movie: Movie) {
        movieId = movie.id
        isVisible = true

        binding.cardTitle.text = movie.title
        binding.cardPoster.loadImage(ImageUrls.bigPoster(movie.posterPath))

        val ratingText = movie.voteAverage?.let { ratingFormat.format(it) }
        binding.cardStar.isVisible = ratingText != null
        binding.cardRating.isVisible = ratingText != null
        binding.cardRating.text = ratingText

        // "1994 · Drama, Crime" (the year and up to two genres; anything unknown is left out)
        val genres = movie.genreNames.take(MAX_GENRES_SHOWN).joinToString(", ").ifEmpty { null }
        val meta = listOfNotNull(movie.releaseYear?.toString(), genres).joinToString(" · ")
        binding.cardMeta.text = meta
        binding.cardMeta.isVisible = meta.isNotEmpty()

        // What a screen reader says about this card
        contentDescription = listOfNotNull(
            movie.title,
            movie.releaseYear?.toString(),
            ratingText?.let { context.getString(R.string.swipe_rating_description, it) },
            genres
        ).joinToString(", ")
    }

    /** Empties the card (there is no movie for it) and hides it. */
    fun clear() {
        movieId = null
        Glide.with(this).clear(binding.cardPoster)
        binding.cardPoster.setImageDrawable(null)
        contentDescription = null
        visibility = INVISIBLE // keeps its size, so nothing jumps around
    }

    /** Sets how visible the LIKE and PASS stamps are: 0 = invisible, 1 = fully visible. */
    fun setStamps(likeAlpha: Float, passAlpha: Float) {
        binding.stampLike.alpha = likeAlpha
        binding.stampPass.alpha = passAlpha
    }

    fun fadeOutStamps(durationMs: Long) {
        binding.stampLike.animate().alpha(0f).setDuration(durationMs)
        binding.stampPass.animate().alpha(0f).setDuration(durationMs)
    }

    /**
     * How much the card is covered by a flat surface-colored sheet: 1 = completely covered (a card waiting
     * behind the top one), 0 = the poster is fully visible (the top card).
     */
    fun setCover(amount: Float) {
        binding.cardDim.alpha = amount
    }

    fun animateCover(amount: Float, durationMs: Long) {
        binding.cardDim.animate().alpha(amount).setDuration(durationMs)
    }

    /** Stops the stamp and cover animations (the card's own animation is stopped with animate().cancel()). */
    fun cancelChildAnimations() {
        binding.stampLike.animate().cancel()
        binding.stampPass.animate().cancel()
        binding.cardDim.animate().cancel()
    }

    private companion object {
        const val STAMP_ROTATION_DEGREES = 12f
        const val PIVOT_HEIGHT_FRACTION = 0.75f
        const val MAX_GENRES_SHOWN = 2
    }
}
