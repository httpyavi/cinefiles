package com.example.cinefiles.ui.mood

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.example.cinefiles.R
import com.example.cinefiles.util.animationsEnabled
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the mood characters using the exact vector artwork provided.
 * Transitions between moods seamlessly using scale, position interpolation,
 * and alpha crossfade as the user interacts with the mood slider.
 */
class MoodFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colors = MoodSpectrum.colors(context)
    private var position = DEFAULT_POSITION

    /** The color of the character right now (blends from mood to mood while sliding). */
    var faceColor: Int = colors[DEFAULT_POSITION.toInt()]
        private set

    // Exact vector drawable resources in spectrum order:
    // 0: relaxed, 1: happy, 2: romantic, 3: emotional, 4: scared,
    // 5: mind_bending (mysterious), 6: escape, 7: excited, 8: surprise
    private val drawableResIds = listOf(
        R.drawable.relaxed,
        R.drawable.happy,
        R.drawable.romantic,
        R.drawable.emotional,
        R.drawable.scared,
        R.drawable.mysterious,
        R.drawable.escape,
        R.drawable.excited,
        R.drawable.surprise
    )

    private val drawables: List<Drawable> = drawableResIds.mapNotNull { id ->
        ContextCompat.getDrawable(context, id)?.mutate()
    }

    private val drawableBounds = Array(drawables.size) { Rect() }

    // Idle floating/breathing animation
    private var time = 0f
    private var timeAnimator: ValueAnimator? = null

    // Pop squish/bounce animation
    private var popScaleX = 1f
    private var popScaleY = 1f

    init {
        updateFaceColor()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTimeAnimator()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTimeAnimator()
    }

    private fun startTimeAnimator() {
        if (!context.animationsEnabled()) return
        if (timeAnimator != null) return
        timeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = IDLE_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                time = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopTimeAnimator() {
        timeAnimator?.cancel()
        timeAnimator = null
    }

    /** Shows the character for this position on the slider (0f to 8f). */
    fun setPosition(newPosition: Float) {
        position = newPosition.coerceIn(0f, (drawables.size - 1).toFloat())
        updateFaceColor()
        invalidate()
    }

    private fun updateFaceColor() {
        faceColor = MoodSpectrum.colorAt(colors, position)
    }

    /** Squish and spring-back bounce feedback when selected. */
    fun pop() {
        animate().cancel()
        animate()
            .scaleX(POP_SQUISH)
            .scaleY(POP_SQUISH)
            .setDuration(POP_IN_MS)
            .withEndAction {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(POP_OUT_MS)
                    .setInterpolator(OvershootInterpolator(POP_OVERSHOOT))
                    .start()
            }
            .start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateBounds(w, h)
    }

    private fun recalculateBounds(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val availableW = w * 0.78f
        val availableH = h * 0.78f

        drawables.forEachIndexed { index, drawable ->
            val intrinsicW = max(1, drawable.intrinsicWidth)
            val intrinsicH = max(1, drawable.intrinsicHeight)
            val aspect = intrinsicW.toFloat() / intrinsicH.toFloat()

            var targetW = availableW
            var targetH = targetW / aspect
            if (targetH > availableH) {
                targetH = availableH
                targetW = targetH * aspect
            }

            val left = ((w - targetW) / 2f).toInt()
            val top = ((h - targetH) / 2f).toInt()
            val right = (left + targetW).toInt()
            val bottom = (top + targetH).toInt()

            drawableBounds[index].set(left, top, right, bottom)
            drawable.bounds = drawableBounds[index]
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawables.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val from = position.toInt().coerceIn(0, drawables.size - 1)
        val fraction = (position - from).coerceIn(0f, 1f)
        val eased = fraction * fraction * (3f - 2f * fraction)

        // Subtle idle floating offset and breathing scale
        val floatY = if (context.animationsEnabled()) {
            sin(time * 2.0 * PI).toFloat() * (h * 0.015f)
        } else 0f

        val breathScale = if (context.animationsEnabled()) {
            1f + sin(time * 2.0 * PI).toFloat() * 0.012f
        } else 1f

        val centerX = w / 2f
        val centerY = h / 2f

        if (fraction <= 0.001f || from >= drawables.size - 1) {
            // Single character
            val drawable = drawables[from]
            canvas.save()
            canvas.translate(0f, floatY)
            canvas.scale(breathScale, breathScale, centerX, centerY)
            drawable.alpha = 255
            drawable.draw(canvas)
            canvas.restore()
        } else {
            // Smooth transition between Character A and Character B
            val to = (from + 1).coerceAtMost(drawables.size - 1)
            val drawableA = drawables[from]
            val drawableB = drawables[to]

            // Character A transitions out: fades, scales down slightly, shifts left
            val alphaA = ((1f - eased) * 255).toInt().coerceIn(0, 255)
            val scaleA = breathScale * (1f - 0.12f * eased)
            val shiftXA = -eased * (w * 0.12f)

            canvas.save()
            canvas.translate(shiftXA, floatY)
            canvas.scale(scaleA, scaleA, centerX, centerY)
            drawableA.alpha = alphaA
            drawableA.draw(canvas)
            canvas.restore()

            // Character B transitions in: fades in, scales up to 1.0, shifts into center
            val alphaB = (eased * 255).toInt().coerceIn(0, 255)
            val scaleB = breathScale * (0.88f + 0.12f * eased)
            val shiftXB = (1f - eased) * (w * 0.12f)

            canvas.save()
            canvas.translate(shiftXB, floatY)
            canvas.scale(scaleB, scaleB, centerX, centerY)
            drawableB.alpha = alphaB
            drawableB.draw(canvas)
            canvas.restore()
        }
    }

    companion object {
        const val DEFAULT_POSITION = 1f // Happy by default
        private const val POP_SQUISH = 0.88f
        private const val POP_IN_MS = 100L
        private const val POP_OUT_MS = 280L
        private const val POP_OVERSHOOT = 2.4f
        private const val IDLE_CYCLE_MS = 3600L
        val poses: Array<FloatArray> = Array(9) { FloatArray(26) }
    }
}
