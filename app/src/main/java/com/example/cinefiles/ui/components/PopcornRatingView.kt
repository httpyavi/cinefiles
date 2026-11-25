package com.example.cinefiles.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.cinefiles.R
import com.example.cinefiles.logic.PopcornRating
import com.example.cinefiles.logic.PopcornRating.IconState
import kotlin.math.abs

/**
 * A Letterboxd-style rating row with five popcorn buckets. It shows a rating from 0.5 to 5.0 in half steps (a half bucket
 * is half full) and, unless it is [isInteractive] = false, lets the user set it:
 *  - TAP the left half of a bucket for a half, the right half for the whole bucket; tap the rating you already have to clear it;
 *  - DRAG across the row: the buckets follow the finger, and the rating is set where the finger lifts (dragging off the
 *    left end clears it).
 * The math (which half, what a tap does) is in logic/PopcornRating.kt, where it is unit-tested; this class draws and
 * listens. [onRatingChanged] is called once per finished touch with the new rating (null = cleared).
 *
 * While [isLocked] (the movie is not watched yet, THE WATCH RULE) the row is dimmed and a touch changes nothing: it
 * calls [onLockedTouch] instead, so the screen can shake it and say "Mark as watched first".
 * Screen readers read "Your rating: 3.5 out of 5" and can raise or lower the rating by half a bucket.
 *
 * In XML: app:popcornSize, app:popcornGap and app:interactive. A small, non-interactive one is a rating badge.
 */
class PopcornRatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** The saved rating (0.5 to 5.0) or null. Setting it from code never calls [onRatingChanged]. */
    var rating: Float? = null
        set(value) {
            val normalized = PopcornRating.normalize(value)
            if (field == normalized && draft == null) return
            field = normalized
            draft = null
            invalidate()
            updateDescription()
        }

    /** Called when the user finishes a touch: the new rating, or null when it was cleared. */
    var onRatingChanged: ((Float?) -> Unit)? = null

    /** Called when the user touches the row while it is [isLocked]. */
    var onLockedTouch: (() -> Unit)? = null

    /** Dimmed and not changeable (the movie is not watched). */
    var isLocked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            updateDescription()
        }

    /** False for a badge that only shows a rating. */
    var isInteractive: Boolean = true
        set(value) {
            field = value
            isClickable = value
            isFocusable = value
        }

    private val iconSize: Int
    private val gap: Int

    private val empty: Drawable = loadIcon(R.drawable.ic_popcorn_empty)
    private val half: Drawable = loadIcon(R.drawable.ic_popcorn_half)
    private val full: Drawable = loadIcon(R.drawable.ic_popcorn_full)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // What the finger shows while dragging (null: nothing is being dragged)
    private var draft: Float? = null
    private var hasDraft = false
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PopcornRatingView, defStyleAttr, 0)
        iconSize = a.getDimensionPixelSize(R.styleable.PopcornRatingView_popcornSize, resources.getDimensionPixelSize(R.dimen.popcorn_size))
        gap = a.getDimensionPixelSize(R.styleable.PopcornRatingView_popcornGap, resources.getDimensionPixelSize(R.dimen.popcorn_gap))
        isInteractive = a.getBoolean(R.styleable.PopcornRatingView_interactive, true)
        a.recycle()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateDescription()
    }

    private fun loadIcon(res: Int): Drawable = checkNotNull(ContextCompat.getDrawable(context, res)).mutate()

    // ---------- Size and drawing ----------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = PopcornRating.COUNT * iconSize + (PopcornRating.COUNT - 1) * gap + paddingLeft + paddingRight
        val height = iconSize + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val shown = if (hasDraft) draft else rating
        val alpha = if (isLocked) LOCKED_ALPHA else FULL_ALPHA
        for (index in 0 until PopcornRating.COUNT) {
            val icon = when (PopcornRating.iconState(index, shown)) {
                IconState.EMPTY -> empty
                IconState.HALF -> half
                IconState.FULL -> full
            }
            val left = paddingLeft + index * (iconSize + gap)
            icon.setBounds(left, paddingTop, left + iconSize, paddingTop + iconSize)
            icon.alpha = alpha
            icon.draw(canvas)
        }
    }

    // ---------- Touch: tap and drag ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive || !isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLocked) return true
                if (!dragging) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dy > touchSlop && dy > dx) return false // a vertical scroll: let the page have it
                    if (dx > touchSlop) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true) // a drag across the row is not a page scroll
                    }
                }
                if (dragging) showDraft(valueAt(event.x))
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isLocked) {
                    performClick()
                    onLockedTouch?.invoke()
                } else if (dragging) {
                    commit(PopcornRating.normalize(valueAt(event.x)))
                } else {
                    performClick()
                    commit(PopcornRating.afterTap(rating, valueAt(event.x)))
                }
                dragging = false
                hasDraft = false
                draft = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                hasDraft = false
                draft = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun valueAt(x: Float): Float? = PopcornRating.ratingAt(x - paddingLeft, iconSize.toFloat(), gap.toFloat())

    /** Shows [value] under the finger without saving it, with a small tick each time it moves to another half step. */
    private fun showDraft(value: Float?) {
        val normalized = PopcornRating.normalize(value)
        if (hasDraft && draft == normalized) return
        if (hasDraft) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        hasDraft = true
        draft = normalized
        invalidate()
    }

    private fun commit(value: Float?) {
        if (value == rating) return
        rating = value
        onRatingChanged?.invoke(value)
    }

    // ---------- Accessibility ----------

    private fun updateDescription() {
        val current = rating
        contentDescription = when {
            isLocked -> context.getString(R.string.rating_locked_description)
            current == null -> context.getString(R.string.rating_none_description)
            else -> context.getString(R.string.rating_description, PopcornRating.format(current))
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (!isInteractive) return
        AccessibilityNodeInfoCompat.wrap(info).rangeInfo = AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
            AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT, 0f, PopcornRating.MAX, rating ?: 0f
        )
        if (!isLocked) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (isInteractive && !isLocked) {
            val current = rating ?: 0f
            when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
                    commit(PopcornRating.normalize((current + PopcornRating.MIN).coerceAtMost(PopcornRating.MAX)))
                    return true
                }
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                    commit(PopcornRating.normalize(current - PopcornRating.MIN))
                    return true
                }
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        const val FULL_ALPHA = 255
        const val LOCKED_ALPHA = 90
    }
}
