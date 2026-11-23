package com.example.cinefiles.ui.components

import android.animation.AnimatorInflater
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.example.cinefiles.R
import com.example.cinefiles.databinding.ViewPosterBinding

/** The small status icon that can sit in the corner of a poster. */
enum class PosterBadge(
    @DrawableRes val icon: Int,
    @ColorRes val tint: Int,
    @StringRes val description: Int
) {
    LIKED(R.drawable.ic_heart_filled, R.color.like, R.string.status_liked),
    WATCHLIST(R.drawable.ic_bookmark_filled, R.color.accent_soft, R.string.badge_watchlist),
    WATCHED(R.drawable.ic_check_filled, R.color.text_primary, R.string.status_watched)
}

/**
 * A movie poster: rounded corners, always 2:3 (width:height), plus an optional status badge.
 *
 * In a layout, give it the width you want (for example match_parent inside a grid cell) and
 * wrap_content for the height; the height follows from the 2:3 shape.
 *
 * Later batches load the real TMDB picture into [imageView] with Glide.
 * Set [badge] to show a status icon, or null to hide it.
 */
class PosterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = ViewPosterBinding.inflate(LayoutInflater.from(context), this)

    /** The poster picture. */
    val imageView: ImageView
        get() = binding.posterImage

    /** The status icon in the top-right corner, or null for none. */
    var badge: PosterBadge? = null
        set(value) {
            field = value
            showBadge(value)
        }

    init {
        // Same small "press" shrink as the buttons. It only plays while the poster is clickable.
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.press_scale)
    }

    private fun showBadge(badge: PosterBadge?) {
        val badgeView = binding.posterBadge
        badgeView.isVisible = badge != null
        if (badge != null) {
            badgeView.setImageResource(badge.icon)
            ImageViewCompat.setImageTintList(
                badgeView,
                ContextCompat.getColorStateList(context, badge.tint)
            )
            badgeView.contentDescription = context.getString(badge.description)
        }
    }
}
