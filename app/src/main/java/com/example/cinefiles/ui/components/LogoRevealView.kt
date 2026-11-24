package com.example.cinefiles.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.cinefiles.R
import com.example.cinefiles.util.animationsEnabled

/**
 * A cinematic progressive reveal view for the CineFiles logo on launch.
 *
 * Reveals the letters (C - I - N - E - F - I - L - E - S) progressively from left to right
 * with a luminous projector/marquee beam edge and subtle settling scale over 1.15 seconds.
 */
class LogoRevealView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val logoDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.cinefiles_logo_white)
    private val logoBounds = Rect()

    private var revealProgress = 0f
    private var isRevealed = false

    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val beamWidthPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics
    )

    private var revealAnimator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        // cinefiles_logo_white viewport: 2204 x 1726 (aspect ratio ~1.277)
        val aspect = 2204f / 1726f
        var drawW = w.toFloat()
        var drawH = drawW / aspect
        if (drawH > h) {
            drawH = h.toFloat()
            drawW = drawH * aspect
        }

        val left = ((w - drawW) / 2f).toInt()
        val top = ((h - drawH) / 2f).toInt()
        logoBounds.set(left, top, (left + drawW).toInt(), (top + drawH).toInt())
    }

    /**
     * Starts the progressive letter-by-letter reveal animation from left to right.
     */
    fun startReveal(onComplete: () -> Unit) {
        revealAnimator?.cancel()

        if (!context.animationsEnabled()) {
            revealProgress = 1f
            isRevealed = true
            scaleX = 1f
            scaleY = 1f
            invalidate()
            onComplete()
            return
        }

        revealProgress = 0f
        isRevealed = false
        scaleX = START_SCALE
        scaleY = START_SCALE
        alpha = 1f
        invalidate()

        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = REVEAL_DURATION_MS
            interpolator = DecelerateInterpolator(1.25f)
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                revealProgress = progress
                val currentScale = START_SCALE + (1f - START_SCALE) * progress
                scaleX = currentScale
                scaleY = currentScale
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    revealProgress = 1f
                    isRevealed = true
                    scaleX = 1f
                    scaleY = 1f
                    invalidate()
                    onComplete()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = logoDrawable ?: return
        if (revealProgress <= 0f) return

        drawable.bounds = logoBounds

        if (isRevealed || revealProgress >= 1f) {
            drawable.draw(canvas)
            return
        }

        val leftF = logoBounds.left.toFloat()
        val topF = logoBounds.top.toFloat()
        val bottomF = logoBounds.bottom.toFloat()
        val widthF = logoBounds.width().toFloat()
        val revealX = leftF + widthF * revealProgress

        // 1. Clip and draw revealed portion of logo
        canvas.save()
        canvas.clipRect(leftF, topF, revealX, bottomF)
        drawable.draw(canvas)
        canvas.restore()

        // 2. Draw soft luminous beam at the leading sweep edge
        val beamLeft = revealX - beamWidthPx * 0.75f
        val beamRight = revealX + beamWidthPx * 0.25f
        beamPaint.shader = LinearGradient(
            beamLeft, 0f, beamRight, 0f,
            intArrayOf(0x00FFFFFF, 0xDDFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(beamLeft, topF, beamRight, bottomF, beamPaint)
    }

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        revealAnimator = null
        super.onDetachedFromWindow()
    }

    companion object {
        const val REVEAL_DURATION_MS = 1150L
        private const val START_SCALE = 0.94f
    }
}
