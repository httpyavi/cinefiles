package com.example.cinefiles.ui.mood

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.cinefiles.R
import com.example.cinefiles.util.animationsEnabled
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The mood slider: a full-color track painted in all the mood colors, with a big round handle you drag.
 *
 *  - The track shows the whole spectrum. Behind the handle (the part you have passed) it is bright; ahead of the
 *    handle it is dimmed, so the chosen place is obvious at a glance.
 *  - The handle is a white disc with a dot of the mood's own color, a soft shadow under it (depth) and a glow of the
 *    mood color around it. It is the same color as the character above, so the two read as one thing. When idle, the
 *    glow breathes very slowly.
 *  - While you drag, [Listener.onValueChanged] reports the exact place (0f..8f), so the character can follow.
 *  - Each time the handle passes the middle between two moods, [Listener.onIndexChanged] is called
 *    (the screen answers with a small haptic tick).
 *  - When you let go the handle springs to the nearest mood and [Listener.onSettled] is called.
 *  - Tapping the track jumps to that mood.
 *
 * It also works with a keyboard (left / right arrows) and with TalkBack (swipe up / down).
 */
class MoodSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        /** The handle moved. [value] is 0f for the first mood ... (count - 1) for the last. */
        fun onValueChanged(value: Float)

        /** The handle now is nearest to a different mood. */
        fun onIndexChanged(index: Int)

        /** The handle came to rest on a mood. */
        fun onSettled(index: Int)
    }

    var listener: Listener? = null

    private val colors = MoodSpectrum.colors(context)
    private val count = colors.size

    /** The handle's place: 0f..(count - 1). Use [moveTo] to change it. */
    var value = 0f
        private set

    /** The mood the handle is nearest to. */
    val index: Int get() = value.roundToInt().coerceIn(0, count - 1)

    // ---- Sizes (pixels, worked out once) ----
    private val density = resources.displayMetrics.density
    private val handleRadius = resources.getDimension(R.dimen.mood_slider_handle_radius)
    private val trackThickness = resources.getDimension(R.dimen.mood_slider_track_thickness)
    private val tickRadius = resources.getDimension(R.dimen.mood_slider_tick_radius)
    // Room at both ends for the handle AND its glow, so the glow is never cut off by the edge of the view
    private val sidePadding = handleRadius * 2f + 4f * density

    // ---- Paints (made once) ----
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.background)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.text_primary) }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var handleScale = 1f           // grows a little while you hold it
    private var breathe = 0f               // -1..1, the slow idle pulse of the glow
    private var lastIndex = 0
    private var moveAnimator: ValueAnimator? = null
    private var breatheAnimator: ValueAnimator? = null
    private var dragging = false

    init {
        isFocusable = true
        isClickable = true
        trackPaint.strokeWidth = trackThickness
        dimPaint.strokeWidth = trackThickness
    }

    // =====================================================================================
    //  What the screen can ask for
    // =====================================================================================

    /** Puts the handle on a place at once (no animation, no callbacks). Use it to restore a saved place. */
    fun jumpTo(newValue: Float) {
        moveAnimator?.cancel()
        value = newValue.coerceIn(0f, (count - 1).toFloat())
        lastIndex = index
        invalidate()
    }

    /** Slides the handle to [target] (a mood number, or any place between) and reports every step. */
    fun moveTo(target: Float, durationMs: Long = MOVE_MS, springy: Boolean = true) {
        moveAnimator?.cancel()
        val from = value
        val to = target.coerceIn(0f, (count - 1).toFloat())
        moveAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            interpolator = if (springy) OvershootInterpolator(SPRING) else DecelerateInterpolator()
            addUpdateListener { setValueInternal(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && !dragging) listener?.onSettled(index)
                }
            })
            start()
        }
    }

    // =====================================================================================
    //  The finger
    // =====================================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true) // the whole drag belongs to the slider
                moveAnimator?.cancel()
                dragging = true
                growHandle(true)
                val touched = valueAt(event.x)
                // A touch on the handle just grabs it; a touch elsewhere on the track slides the handle there
                if (abs(touched - value) * trackWidth() / (count - 1) > handleRadius * 1.6f) {
                    dragging = false
                    moveTo(touched, TAP_MOVE_MS, springy = false)
                    dragging = true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (moveAnimator?.isRunning == true) return true // let a tap-slide finish first
                setValueInternal(valueAt(event.x))
                return true
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                growHandle(false)
                performClick()
                moveTo(nearestMoodTo(valueAt(event.x)).toFloat())
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                growHandle(false)
                moveTo(index.toFloat())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun nearestMoodTo(place: Float) = place.roundToInt().coerceIn(0, count - 1)

    private fun trackWidth() = (width - 2f * sidePadding).coerceAtLeast(1f)

    /** The place on the slider (0f..count-1) under an x position on the screen. */
    private fun valueAt(x: Float): Float =
        ((x - sidePadding) / trackWidth() * (count - 1)).coerceIn(0f, (count - 1).toFloat())

    private fun xOf(place: Float): Float = sidePadding + place / (count - 1) * trackWidth()

    /** The track is in the middle of the view's height (the Cozy / Wild captions sit below it). */
    private fun trackY() = height * TRACK_HEIGHT_FRACTION

    private fun setValueInternal(newValue: Float) {
        value = newValue.coerceIn(0f, (count - 1).toFloat())
        listener?.onValueChanged(value)
        if (index != lastIndex) {
            lastIndex = index
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            listener?.onIndexChanged(index)
        }
        invalidate()
    }

    private fun growHandle(grow: Boolean) {
        ValueAnimator.ofFloat(handleScale, if (grow) HANDLE_HELD_SCALE else 1f).apply {
            duration = HANDLE_GROW_MS
            addUpdateListener { handleScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // =====================================================================================
    //  Keyboard and screen readers
    // =====================================================================================

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { step(-1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { step(+1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun step(direction: Int) {
        moveTo((index + direction).coerceIn(0, count - 1).toFloat(), TAP_MOVE_MS, springy = false)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        AccessibilityNodeInfoCompat.wrap(info).rangeInfo = AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
            AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_INT, 0f, (count - 1).toFloat(), index.toFloat()
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { step(+1); return true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { step(-1); return true }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    // =====================================================================================
    //  Drawing
    // =====================================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The track is painted with all nine mood colors, evenly spread from end to end
        val positions = FloatArray(count) { it / (count - 1).toFloat() }
        trackPaint.shader = LinearGradient(sidePadding, 0f, w - sidePadding, 0f, colors, positions, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val y = trackY()
        val handleX = xOf(value)
        val current = MoodSpectrum.colorAt(colors, value)

        // The whole spectrum, bright. Ahead of the handle it is dimmed, so you can see how far you have come.
        canvas.drawLine(sidePadding, y, width - sidePadding, y, trackPaint)
        dimPaint.alpha = DIM_ALPHA
        canvas.drawLine(handleX, y, width - sidePadding, y, dimPaint)

        // One small dot for each mood (brighter for the ones you have passed)
        for (i in 0 until count) {
            tickPaint.alpha = if (i <= value + 0.01f) TICK_ALPHA_ON else TICK_ALPHA_OFF
            canvas.drawCircle(xOf(i.toFloat()), y, tickRadius, tickPaint)
        }

        val r = handleRadius * handleScale

        // The glow of the mood color around the handle: a few circles on top of each other, each fainter than
        // the last one. (No gradient object is made here, so dragging does not create garbage 60 times a second.)
        val glowRadius = min(r * GLOW_REACH * (1f + 0.07f * breathe), y) // never wider than the view, or its edge would show
        dotPaint.color = current
        dotPaint.alpha = GLOW_RING_ALPHA
        for (ring in GLOW_RINGS downTo 1) {
            canvas.drawCircle(handleX, y, glowRadius * ring / GLOW_RINGS, dotPaint)
        }

        // Depth: a soft dark shadow just under the handle
        shadowPaint.alpha = SHADOW_ALPHA
        canvas.drawCircle(handleX, y + SHADOW_OFFSET * density, r * 1.04f, shadowPaint)

        // The handle: a bright disc with a dot of the mood color in it
        canvas.drawCircle(handleX, y, r, handlePaint)
        dotPaint.color = current
        dotPaint.alpha = 255
        canvas.drawCircle(handleX, y, r * DOT_FRACTION, dotPaint)
    }

    // =====================================================================================
    //  Life: the glow breathes very slowly while nothing is happening
    // =====================================================================================

    private val idleMotionOn: Boolean
        get() = context.animationsEnabled()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBreathing()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startBreathing() else stopBreathing()
    }

    private fun startBreathing() {
        if (breatheAnimator != null || !idleMotionOn) return
        breatheAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BREATHE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                breathe = sin(it.animatedFraction * 2f * Math.PI.toFloat())
                invalidate()
            }
            start()
        }
    }

    private fun stopBreathing() {
        breatheAnimator?.cancel()
        breatheAnimator = null
        breathe = 0f
    }

    override fun onDetachedFromWindow() {
        moveAnimator?.cancel()
        stopBreathing()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MOVE_MS = 300L
        const val TAP_MOVE_MS = 220L
        const val SPRING = 1.8f
        const val HANDLE_HELD_SCALE = 1.14f
        const val HANDLE_GROW_MS = 140L
        const val TRACK_HEIGHT_FRACTION = 0.5f
        const val DIM_ALPHA = 150          // how much the part of the track ahead of the handle is darkened (out of 255)
        const val TICK_ALPHA_ON = 230
        const val TICK_ALPHA_OFF = 120
        const val GLOW_REACH = 2.1f        // the glow reaches this many handle radii
        const val GLOW_RING_ALPHA = 20     // each of the rings is faint; together they glow brightest in the middle
        const val GLOW_RINGS = 14
        const val SHADOW_ALPHA = 70
        const val SHADOW_OFFSET = 2.5f     // dp
        const val DOT_FRACTION = 0.58f     // the colored dot is this part of the handle's radius
        const val BREATHE_MS = 3600L
    }
}
