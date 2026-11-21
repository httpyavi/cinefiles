package com.example.cinefiles.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.cinefiles.R

/**
 * One achievement badge, drawn as a small medal.
 *
 *  - EARNED: a lit red medal with a soft glow and a white picture. [playUnlock] adds a quick pop and a
 *    band of light that sweeps across it, once, when the badge has just been earned.
 *  - NOT EARNED YET: a quiet glass medal with a dim picture, and a thin red ring around it that fills up
 *    as the goal comes closer.
 *
 * Only drawn shapes and a few numbers: no image files.
 */
class AchievementBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val accent = ContextCompat.getColor(context, R.color.accent)
    private val accentSoft = ContextCompat.getColor(context, R.color.accent_soft)
    private val accentDeep = ContextCompat.getColor(context, R.color.accent_deep)
    private val glassFill = ContextCompat.getColor(context, R.color.glass_fill_strong)
    private val glassStroke = ContextCompat.getColor(context, R.color.glass_stroke)
    private val iconOn = ContextCompat.getColor(context, R.color.on_accent)
    private val iconOff = ContextCompat.getColor(context, R.color.text_secondary)
    private val ringWidth = resources.getDimension(R.dimen.badge_ring_width)

    private var icon: Drawable? = null
    private var unlocked = false
    private var progress = 0f // 0..1, how close the goal is

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shineMatrix = Matrix()
    private val shineColors = intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 120), Color.TRANSPARENT)
    private val glowColors = intArrayOf(ColorUtils.setAlphaComponent(accent, 110), ColorUtils.setAlphaComponent(accent, 0))
    private var glowShader: Shader? = null     // made when the size is known: onDraw must not create objects
    private var medalShader: Shader? = null
    private var shineShader: LinearGradient? = null
    private val arc = RectF()
    private val clip = Path()

    private var shineAmount = -1f // -1 = no shine; 0..1 = where the band of light is
    private var shineAnimator: ValueAnimator? = null

    /** Shows this badge: its picture, whether it is earned, and (if not) how far along it is (0..1). */
    fun setBadge(picture: Int, earned: Boolean, progressFraction: Float) {
        icon = ContextCompat.getDrawable(context, picture)?.mutate()
        unlocked = earned
        progress = progressFraction.coerceIn(0f, 1f)
        invalidate()
    }

    /** The "you just earned this" moment: a small pop, and a band of light sweeping across the medal. */
    fun playUnlock() {
        shineAnimator?.cancel()
        animate().cancel()
        scaleX = POP_FROM
        scaleY = POP_FROM
        animate().scaleX(1f).scaleY(1f).setDuration(POP_MS).setInterpolator(OvershootInterpolator(POP_OVERSHOOT)).start()

        shineAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SHINE_MS
            startDelay = SHINE_DELAY_MS
            addUpdateListener { shineAmount = it.animatedFraction; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { shineAmount = -1f; invalidate() }
                override fun onAnimationCancel(animation: android.animation.Animator) { shineAmount = -1f }
            })
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val medal = minOf(w, h) * MEDAL_FRACTION
        // A soft red light around the medal
        glowShader = RadialGradient(cx, cy, medal * 1.55f, glowColors, null, Shader.TileMode.CLAMP)
        // The medal: lighter at the top-left, deeper at the bottom-right
        medalShader = LinearGradient(cx - medal, cy - medal, cx + medal, cy + medal, accentSoft, accentDeep, Shader.TileMode.CLAMP)
        // A band of light for the unlock animation
        shineShader = LinearGradient(0f, 0f, medal * 0.9f, 0f, shineColors, null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val medal = minOf(width, height) * MEDAL_FRACTION // the radius of the medal itself

        if (unlocked) {
            glow.shader = glowShader
            canvas.drawCircle(cx, cy, medal * 1.55f, glow)

            fill.shader = medalShader
            canvas.drawCircle(cx, cy, medal, fill)
            fill.shader = null
            ring.color = ColorUtils.setAlphaComponent(Color.WHITE, 90)
            ring.strokeWidth = ringWidth / 2f
            canvas.drawCircle(cx, cy, medal, ring)
        } else {
            fill.shader = null
            fill.color = glassFill
            canvas.drawCircle(cx, cy, medal, fill)

            // The ring around the medal: a faint full circle, and a red arc for the progress made
            val ringRadius = medal + ringWidth * 1.5f
            ring.strokeWidth = ringWidth
            ring.color = glassStroke
            canvas.drawCircle(cx, cy, ringRadius, ring)
            if (progress > 0f) {
                ring.color = accent
                arc.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
                canvas.drawArc(arc, -90f, 360f * progress, false, ring)
            }
        }

        // The picture in the middle
        icon?.let {
            val half = (medal * ICON_FRACTION).toInt()
            it.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            it.setTint(if (unlocked) iconOn else iconOff)
            it.draw(canvas)
        }

        // The band of light (only while it is sweeping)
        if (unlocked && shineAmount >= 0f) {
            clip.reset()
            clip.addCircle(cx, cy, medal, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            val travel = (shineAmount * 2f - 1f) * medal * 2.2f // from far left to far right
            shineMatrix.reset()
            shineMatrix.postRotate(SHINE_TILT)
            shineMatrix.postTranslate(cx - medal * 0.45f + travel, cy - medal)
            shineShader?.setLocalMatrix(shineMatrix)
            shine.shader = shineShader
            canvas.drawRect(cx - medal * 3f, cy - medal * 1.5f, cx + medal * 3f, cy + medal * 1.5f, shine)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        shineAnimator?.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MEDAL_FRACTION = 0.36f   // the medal's radius as a part of the view's size (room is left for the ring and glow)
        const val ICON_FRACTION = 0.5f
        const val POP_FROM = 0.7f
        const val POP_MS = 520L
        const val POP_OVERSHOOT = 2.4f
        const val SHINE_MS = 900L
        const val SHINE_DELAY_MS = 180L
        const val SHINE_TILT = 20f
    }
}
