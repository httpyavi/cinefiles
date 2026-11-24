package com.example.cinefiles.ui.components

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * A quiet, dark cinematic skeleton shimmer drawable.
 *
 * Runs a soft highlight gradient across a rounded rectangle.
 * Implements [Animatable] so Glide and view animations can start and stop it automatically.
 */
class ShimmerDrawable(
    private val cornerRadius: Float = 24f,
    private val baseColor: Int = 0xFF141419.toInt(),
    private val highlightColor: Int = 0xFF23232E.toInt(),
    startImmediately: Boolean = false
) : Drawable(), Animatable {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1300L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidateSelf()
        }
    }

    init {
        if (startImmediately) {
            animator.start()
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rectF.set(b)

        // Base background
        paint.shader = null
        paint.color = baseColor
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

        // Shimmer sweep
        val width = b.width().toFloat()
        val shimmerWidth = (width * 0.9f).coerceAtLeast(120f)
        val totalSpan = width + shimmerWidth * 2
        val startX = b.left - shimmerWidth + totalSpan * progress

        paint.shader = LinearGradient(
            startX,
            0f,
            startX + shimmerWidth,
            0f,
            intArrayOf(baseColor, highlightColor, baseColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun start() {
        if (!animator.isStarted) {
            animator.start()
        }
    }

    override fun stop() {
        if (animator.isStarted) {
            animator.cancel()
        }
    }

    override fun isRunning(): Boolean = animator.isRunning

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) {
            if (restart || !animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
        return changed
    }
}
