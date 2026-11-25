package com.example.cinefiles.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.cinefiles.R
import com.example.cinefiles.data.Avatar
import com.example.cinefiles.util.loadUserAvatar

/**
 * The round avatar of the user: their profile photo, or the first letter of their name until they pick one (Home's top corner and the
 * Profile screen use this same view, so the two always agree). [show] draws it. The red ring is the view's own background (bg_avatar);
 * the photo sits inside the ring. The size of the view and its text style (`app:avatarTextAppearance`) come from the layout.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val initial = TextView(context).apply {
        gravity = Gravity.CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val photo = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        setBackgroundResource(R.drawable.bg_avatar)
        addView(initial, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // The ring is drawn 4 dp inside the view: the photo sits just inside the ring
        val inset = resources.getDimensionPixelSize(R.dimen.space_4) + resources.getDimensionPixelSize(R.dimen.glass_stroke_width)
        addView(photo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { setMargins(inset, inset, inset, inset) })

        context.obtainStyledAttributes(attrs, R.styleable.AvatarView).use { array ->
            val appearance = array.getResourceId(R.styleable.AvatarView_avatarTextAppearance, 0)
            if (appearance != 0) initial.setTextAppearance(appearance)
        }
    }

    /** Shows the photo when there is one, otherwise [letter] (the first letter of the name). */
    fun show(letter: String, avatar: Avatar?) {
        initial.text = letter
        initial.isVisible = true
        photo.visibility = View.VISIBLE
        photo.loadUserAvatar(avatar) { loaded -> initial.isVisible = !loaded }
    }
}
