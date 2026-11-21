package com.example.cinefiles.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.cinefiles.R
import kotlin.math.max

/**
 * A dark stage with soft colored light on it. It is the BACKGROUND of the Mood and Swipe screens.
 *
 *  - A big glow at the top and a smaller one at the bottom, in one [tint] color. Change the tint with
 *    [animateTint] and the light slowly turns into the new color.
 *  - Two glows at the left and right edges ([setEdge]): a warm red one for "like" (right edge) and a cool
 *    slate one for "pass" (left edge). The swipe screen turns them up while a card is dragged.
 *
 * It only draws, and it never takes touches. Put it as the first (bottom) view of a screen.
 */
class AmbientGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundColor = ContextCompat.getColor(context, R.color.background)
    private val likeColor = ContextCompat.getColor(context, R.color.like)
    private val passColor = ContextCompat.getColor(context, R.color.pass)

    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val likeEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val passEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var tint = ContextCompat.getColor(context, R.color.accent)
    private var tintAnimator: ValueAnimator? = null
    private var edgeAnimator: ValueAnimator? = null

    /** How strong the tinted light is: 0 = none, 1 = full. The Swipe screen keeps it lower so posters stay the hero. */
    var strength = 1f
        set(value) {
            field = value
            rebuildTintShaders()
            invalidate()
        }

    /** Where the big glow is centered, from the top (0f) to the bottom (1f) of the screen. */
    var focusY = 0.16f
        set(value) {
            field = value
            rebuildTintShaders()
            invalidate()
        }

    /** -1 = the pass glow at the left edge is at full strength, +1 = the like glow at the right edge, 0 = none. */
    private var edge = 0f

    init {
        // Never blocks touches, and is not read out by screen readers
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Sets the color of the light at once. */
    fun setTint(color: Int) {
        tintAnimator?.cancel()
        tint = color
        rebuildTintShaders()
        invalidate()
    }

    /** The light slowly turns into [color]. */
    fun animateTint(color: Int, durationMs: Long = TINT_CHANGE_MS) {
        tintAnimator?.cancel()
        val from = tint
        if (from == color) return
        tintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener {
                tint = ColorUtils.blendARGB(from, color, it.animatedFraction)
                rebuildTintShaders()
                invalidate()
            }
            start()
        }
    }

    /** Turns the edge glows up or down while a card is dragged: -1 (pass) ... 0 ... +1 (like). */
    fun setEdge(amount: Float) {
        val clamped = amount.coerceIn(-1f, 1f)
        if (clamped == edge) return
        edge = clamped
        invalidate()
    }

    /** The edge glow smoothly fades out (the finger let go and the card is going back). */
    fun fadeEdge(durationMs: Long = EDGE_FADE_MS) {
        if (edge == 0f) return
        edgeAnimator?.cancel()
        val from = edge
        edgeAnimator = ValueAnimator.ofFloat(from, 0f).apply {
            duration = durationMs
            addUpdateListener { setEdge(it.animatedValue as Float) }
            start()
        }
    }

    /**
     * A card is leaving. If it was dragged by a finger the edge glow is already on, so it fades out;
     * if it was sent by a button the glow flashes once. [direction] is +1 for like, -1 for pass.
     */
    fun releaseEdge(direction: Int) {
        if (edge != 0f) fadeEdge(RELEASE_FADE_MS) else pulseEdge(direction)
    }

    /** A short flash of the like glow (or of the pass glow, for -1): used when a card is liked with a button. */
    fun pulseEdge(direction: Int) {
        edgeAnimator?.cancel()
        edgeAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = PULSE_MS
            addUpdateListener { setEdge(direction * it.animatedValue as Float) }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildTintShaders()

        // The edge glows never change color, so they are made only when the size changes
        val reach = h * EDGE_REACH
        likeEdgePaint.shader = RadialGradient(
            w * 1.05f, h * 0.55f, reach, intArrayOf(likeColor, ColorUtils.setAlphaComponent(likeColor, 0)), null, Shader.TileMode.CLAMP
        )
        passEdgePaint.shader = RadialGradient(
            -w * 0.05f, h * 0.55f, reach, intArrayOf(passColor, ColorUtils.setAlphaComponent(passColor, 0)), null, Shader.TileMode.CLAMP
        )
    }

    private fun rebuildTintShaders() {
        if (width == 0 || height == 0) return
        val reach = max(width, height).toFloat()
        topPaint.shader = RadialGradient(
            width * 0.5f, height * focusY, reach * GLOW_REACH,
            intArrayOf(withStrength(tint, TOP_ALPHA), withStrength(tint, 0f)), null, Shader.TileMode.CLAMP
        )
        bottomPaint.shader = RadialGradient(
            width * 0.5f, height * 1.02f, reach * 0.7f,
            intArrayOf(withStrength(tint, BOTTOM_ALPHA), withStrength(tint, 0f)), null, Shader.TileMode.CLAMP
        )
    }

    /** [color] with this much opacity (0..1), made weaker by [strength]. */
    private fun withStrength(color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha * strength * 255f).toInt().coerceIn(0, 255))

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), topPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bottomPaint)

        if (edge > 0f) {
            likeEdgePaint.alpha = (edge * EDGE_ALPHA).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), likeEdgePaint)
        } else if (edge < 0f) {
            passEdgePaint.alpha = (-edge * EDGE_ALPHA).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), passEdgePaint)
        }
    }

    override fun onDetachedFromWindow() {
        tintAnimator?.cancel()
        edgeAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TINT_CHANGE_MS = 450L
        const val PULSE_MS = 420L
        const val EDGE_FADE_MS = 260L
        const val RELEASE_FADE_MS = 420L
        const val TOP_ALPHA = 0.42f      // opacity of the big glow
        const val BOTTOM_ALPHA = 0.10f   // opacity of the glow at the bottom
        const val GLOW_REACH = 0.62f     // how far the big glow reaches, as a part of the longer side
        const val EDGE_ALPHA = 150f      // the strongest an edge glow gets (out of 255)
        const val EDGE_REACH = 0.62f     // how far an edge glow reaches, as a part of the height
    }
}
