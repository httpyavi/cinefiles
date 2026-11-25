package com.example.cinefiles.ui.swipe

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.ViewSwipeCardBinding
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.genresText
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.ratingText
import com.example.cinefiles.util.yearAndGenres

/**
 * ONE movie card: a big rounded poster with the title, rating, year and genres. While it is dragged, a see-through veil and a big popcorn (swipe right) or X (pass) show on it.
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
        // A hair-thin light edge, drawn over the poster: the card looks like a sheet of glass
        foreground = ContextCompat.getDrawable(context, R.drawable.fg_card_edge)

        setFeedback(like = 0f, pass = 0f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The card turns around a point in its lower part, so dragging it feels like swinging it by the top
        pivotX = w / 2f
        pivotY = h * PIVOT_HEIGHT_FRACTION
    }

    /** Shows [movie] on this card. [eyebrow] is the small line above the title, "Top rated" for example (or null). */
    fun bind(movie: Movie, eyebrow: String? = null) {
        movieId = movie.id
        isVisible = true

        binding.cardEyebrow.text = eyebrow
        binding.cardEyebrow.isVisible = !eyebrow.isNullOrEmpty()
        binding.cardTitle.text = movie.title
        binding.cardPoster.loadImage(ImageUrls.bigPoster(movie.posterPath))

        val rating = movie.ratingText()
        binding.cardRatingPill.isVisible = rating != null
        binding.cardRating.text = rating

        // "1994 · Drama, Crime" (the year and up to two genres; anything unknown is left out)
        val meta = movie.yearAndGenres(MAX_GENRES_SHOWN)
        binding.cardMeta.text = meta
        binding.cardMeta.isVisible = meta.isNotEmpty()

        // What a screen reader says about this card
        contentDescription = listOfNotNull(
            movie.title,
            movie.releaseYear?.toString(),
            rating?.let { context.getString(R.string.swipe_rating_description, it) },
            movie.genresText(MAX_GENRES_SHOWN).ifEmpty { null }
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

    /**
     * Sets the like / pass feedback: 0 = nothing (the poster is completely clear), 1 = fully shown.
     * The veil gets stronger and the popcorn or X bigger the larger the amount, so it follows the finger smoothly.
     */
    fun setFeedback(like: Float, pass: Float) {
        applyFeedback(binding.overlayLike, binding.iconLike, like)
        applyFeedback(binding.overlayPass, binding.iconPass, pass)
    }

    private fun applyFeedback(veil: View, icon: View, amount: Float) {
        veil.alpha = amount * MAX_VEIL_ALPHA
        icon.alpha = (amount * ICON_FADE_SPEED).coerceAtMost(1f) // the icon shows a little sooner than the veil is strong
        val scale = ICON_MIN_SCALE + (ICON_MAX_SCALE - ICON_MIN_SCALE) * amount
        icon.scaleX = scale
        icon.scaleY = scale
    }

    /** The card is flying away: the feedback grows to full in [durationMs] (a swipe by finger is already close to it). */
    fun showFullFeedback(like: Boolean, durationMs: Long) {
        val veil = if (like) binding.overlayLike else binding.overlayPass
        val icon = if (like) binding.iconLike else binding.iconPass
        veil.animate().alpha(MAX_VEIL_ALPHA).setDuration(durationMs)
        icon.animate().alpha(1f).scaleX(ICON_MAX_SCALE).scaleY(ICON_MAX_SCALE).setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
    }

    /** The feedback fades away (the card is going back to the middle, or is coming back with Undo). */
    fun fadeOutFeedback(durationMs: Long) {
        binding.overlayLike.animate().alpha(0f).setDuration(durationMs)
        binding.iconLike.animate().alpha(0f).setDuration(durationMs)
        binding.overlayPass.animate().alpha(0f).setDuration(durationMs)
        binding.iconPass.animate().alpha(0f).setDuration(durationMs)
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

    /** Stops the feedback and cover animations (the card's own animation is stopped with animate().cancel()). */
    fun cancelChildAnimations() {
        binding.overlayLike.animate().cancel()
        binding.iconLike.animate().cancel()
        binding.overlayPass.animate().cancel()
        binding.iconPass.animate().cancel()
        binding.cardDim.animate().cancel()
    }

    private companion object {
        const val PIVOT_HEIGHT_FRACTION = 0.75f
        const val MAX_GENRES_SHOWN = 2
        const val MAX_VEIL_ALPHA = 0.5f      // the veil never gets more than half opaque, so the poster always shows through
        const val ICON_FADE_SPEED = 1.8f     // the icon is fully visible when the veil is at about 55%
        const val ICON_MIN_SCALE = 0.45f
        const val ICON_MAX_SCALE = 1f
    }
}
