package com.example.cinefiles.ui.mood

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.cinefiles.R
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The big animated face on the Mood screen.
 *
 * It is drawn with plain Canvas shapes (no images, no library). The face is made of separate parts:
 * two eyes, two eyebrows, a mouth, blush, tears, a sweat drop, sparkles and heart eyes. Each part is
 * described by a few numbers, and every mood has one list of numbers (a "pose", see [poses]).
 *
 * [setPosition] takes a place on the slider (0 = first mood ... 8 = last). Between two moods the numbers are
 * blended, so the eyes open, the brows lift and the mouth bends smoothly while you drag: nothing is swapped.
 * The color of the face blends through the mood colors in the same way.
 *
 * On top of that the face is alive: it floats and breathes, blinks every few seconds, tears roll,
 * sparkles twinkle and hearts pulse. (If the phone's "remove animations" is on, it stays still.)
 *
 * All sizes are in "u" = one tenth of the face radius, so the face looks the same at any size.
 */
class MoodFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- Where we are on the slider, and the blended pose for that place ----
    private val colors = MoodSpectrum.colors(context)
    private val pose = FloatArray(POSE_SIZE)
    private var position = DEFAULT_POSITION

    /** The color of the face right now (it blends from mood to mood while you slide). */
    var faceColor: Int = colors[DEFAULT_POSITION.toInt()]
        private set

    // ---- Time: one number that goes 0 -> 12 seconds and starts over. Every idle motion is a whole
    //      number of cycles in 12 seconds, so nothing jumps when it starts over. ----
    private var time = 0f
    private var blink = 0f
    private var timeAnimator: ValueAnimator? = null

    // ---- Colors, paints and paths are made once (onDraw must not create objects) ----
    private val ink = ContextCompat.getColor(context, R.color.face_ink)
    private val white = ContextCompat.getColor(context, R.color.face_white)
    private val tearColor = ContextCompat.getColor(context, R.color.face_tear)
    private val heartColor = ContextCompat.getColor(context, R.color.face_heart)
    private val blushColor = ContextCompat.getColor(context, R.color.face_blush)
    private val tongueColor = ContextCompat.getColor(context, R.color.accent_soft)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ink
    }
    private val path = Path()
    private val eyePath = Path()
    private val mouthPath = Path()

    // The disc and glow gradients only change when the color or the size changes
    private var shaderColor = 0
    private var shaderRadius = 0f

    init {
        updatePose()
    }

    // =====================================================================================
    //  What the screen can ask for
    // =====================================================================================

    /** Shows the face for this place on the slider. 0f = first mood, 1f = second mood, 0.5f = halfway between. */
    fun setPosition(newPosition: Float) {
        position = newPosition.coerceIn(0f, (poses.size - 1).toFloat())
        updatePose()
        invalidate()
    }

    /** A little squish and spring-back: "you chose me". */
    fun pop() {
        animate().cancel()
        animate().scaleX(POP_SQUISH).scaleY(POP_SQUISH).setDuration(POP_IN_MS).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(POP_OUT_MS)
                .setInterpolator(OvershootInterpolator(POP_OVERSHOOT)).start()
        }.start()
    }

    private fun updatePose() {
        // Which two moods are we between, and how far along?
        val from = position.toInt().coerceAtMost(poses.size - 2)
        val along = position - from
        val eased = along * along * (3f - 2f * along) // slow near a mood, quick in between: each expression "holds"

        for (i in pose.indices) {
            pose[i] = poses[from][i] + (poses[from + 1][i] - poses[from][i]) * eased
        }
        faceColor = MoodSpectrum.colorAt(colors, position)
    }

    // =====================================================================================
    //  Drawing
    // =====================================================================================

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val radius = min(w, h) * FACE_SIZE_FRACTION
        val u = radius / 10f
        makeShadersIfNeeded(radius)

        // Float up and down, and breathe a very little
        val cycle = time * TWO_PI / FLOAT_SECONDS
        val bob = sin(cycle) * u * 0.35f
        val breathe = 1f + 0.012f * sin(cycle + 1f)

        canvas.save()
        canvas.translate(w / 2f, h / 2f + bob)
        canvas.scale(breathe, breathe)

        canvas.drawCircle(0f, 0f, radius * GLOW_FRACTION, glowPaint)
        canvas.drawCircle(0f, 0f, radius, discPaint)
        rimPaint.strokeWidth = u * 0.14f
        rimPaint.alpha = 46
        canvas.drawCircle(0f, 0f, radius - rimPaint.strokeWidth / 2f, rimPaint)

        drawBlush(canvas, u)
        drawEyes(canvas, u)
        drawBrows(canvas, u)
        drawMouth(canvas, u)
        drawSweat(canvas, u)
        drawSparkles(canvas, u)

        canvas.restore()
    }

    private fun makeShadersIfNeeded(radius: Float) {
        if (shaderColor == faceColor && shaderRadius == radius) return
        shaderColor = faceColor
        shaderRadius = radius

        val lighter = ColorUtils.blendARGB(faceColor, Color.WHITE, 0.30f)
        val darker = ColorUtils.blendARGB(faceColor, Color.BLACK, 0.24f)
        // A light source up on the left: the face looks like a soft glossy ball
        discPaint.shader = RadialGradient(
            -radius * 0.28f, -radius * 0.38f, radius * 1.45f,
            intArrayOf(lighter, faceColor, darker), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            0f, 0f, radius * GLOW_FRACTION,
            intArrayOf(ColorUtils.setAlphaComponent(faceColor, 120), ColorUtils.setAlphaComponent(faceColor, 0)),
            null, Shader.TileMode.CLAMP
        )
    }

    private fun drawBlush(canvas: Canvas, u: Float) {
        val amount = pose[BLUSH]
        if (amount < 0.01f) return
        fillPaint.shader = null
        fillPaint.color = blushColor
        // Two ovals on top of each other (a big faint one, a smaller stronger one) make a soft cheek
        for (step in 0..1) {
            val grow = if (step == 0) 0.45f else 0f
            fillPaint.alpha = (amount * (if (step == 0) 34f else 52f)).toInt()
            canvas.drawOval(-7.3f * u - grow * u, 0.7f * u - grow * u, -3.9f * u + grow * u, 2.9f * u + grow * u, fillPaint)
            canvas.drawOval(3.9f * u - grow * u, 0.7f * u - grow * u, 7.3f * u + grow * u, 2.9f * u + grow * u, fillPaint)
        }
    }

    private fun drawEyes(canvas: Canvas, u: Float) {
        val eyeScale = pose[EYE_SCALE]
        val halfWidth = 1.55f * u * eyeScale
        val halfHeight = 1.4f * u * eyeScale
        val sclera = pose[SCLERA]
        val heart = pose[HEART]
        val eyeY = -1.3f * u

        for (side in 0..1) {
            val sign = if (side == 0) -1f else 1f
            val eyeX = sign * 3.4f * u
            var top = if (side == 0) pose[EYE_TOP_L] else pose[EYE_TOP_R]
            var bottom = if (side == 0) pose[EYE_BOT_L] else pose[EYE_BOT_R]

            // A blink brings the lids together in the middle
            val middle = (top + bottom) / 2f
            top += (middle - top) * blink
            bottom += (middle - bottom) * blink

            // The shape of the eye: an upper curve and a lower curve between two corners.
            // top = 1 and bottom = -1 is a round eye; top = 1 and bottom = 0.6 is a thin happy "^" arc.
            // A round eye leaves its corners straight up; a thin one leans them in, so it ends in soft points.
            val thickness = ((top - bottom) / 2f).coerceIn(0f, 1.4f)
            val lean = halfWidth * (1f - thickness).coerceIn(0f, 1f) * 0.8f
            eyePath.reset()
            eyePath.moveTo(eyeX - halfWidth, eyeY)
            eyePath.cubicTo(
                eyeX - halfWidth + lean, eyeY - CURVE * top * halfHeight,
                eyeX + halfWidth - lean, eyeY - CURVE * top * halfHeight, eyeX + halfWidth, eyeY
            )
            eyePath.cubicTo(
                eyeX + halfWidth - lean, eyeY - CURVE * bottom * halfHeight,
                eyeX - halfWidth + lean, eyeY - CURVE * bottom * halfHeight, eyeX - halfWidth, eyeY
            )
            eyePath.close()

            val eyeAlpha = 1f - heart // heart eyes replace the normal eyes

            // The whites of the eye with a pupil in them (wide, scared, dreamy eyes)...
            if (sclera > 0.01f && eyeAlpha > 0.01f) {
                fillPaint.shader = null
                fillPaint.color = white
                fillPaint.alpha = (sclera * eyeAlpha * 255f).toInt()
                canvas.drawPath(eyePath, fillPaint)

                canvas.save()
                canvas.clipPath(eyePath)
                fillPaint.color = ink
                fillPaint.alpha = (sclera * eyeAlpha * 255f).toInt()
                canvas.drawCircle(
                    eyeX + pose[LOOK_X] * halfWidth * 0.5f,
                    eyeY + pose[LOOK_Y] * halfHeight * 0.5f,
                    pose[PUPIL] * halfHeight, fillPaint
                )
                canvas.restore()
            }

            // ...or the solid dark eye (happy, relaxed, emotional...). The two fade into each other.
            fillPaint.color = ink
            fillPaint.alpha = ((1f - sclera) * eyeAlpha * 255f).toInt()
            canvas.drawPath(eyePath, fillPaint)

            // A thin dark edge so a white eye is always clearly drawn
            linePaint.strokeWidth = 0.22f * u
            linePaint.alpha = (sclera * eyeAlpha * 255f).toInt()
            canvas.drawPath(eyePath, linePaint)

            // The little light dot in a dark eye makes it look wet and alive
            val open = (((top - bottom) / 2f - 0.55f) / 0.35f).coerceIn(0f, 1f) // only in an eye that is open
            val catchlight = (1f - sclera) * eyeAlpha * open
            if (catchlight > 0.05f) {
                fillPaint.color = white
                fillPaint.alpha = (catchlight * 235f).toInt()
                val size = 0.34f * u * (1f + 0.9f * pose[TEAR])
                canvas.drawCircle(eyeX + halfWidth * 0.34f, eyeY - halfHeight * 0.36f, size, fillPaint)
            }

            if (heart > 0.01f) {
                val pulse = 1f + 0.1f * sin(time * TWO_PI / HEART_SECONDS)
                heartPath(eyeX, eyeY, 1.75f * u * pulse)
                fillPaint.color = heartColor
                fillPaint.alpha = (heart * 255f).toInt()
                canvas.drawPath(path, fillPaint)
            }

            // A glint in the eye (excited, dreamy, playful)
            val sparkle = pose[SPARKLE]
            if (sparkle > 0.01f) {
                starPath(eyeX + halfWidth * 0.42f, eyeY - halfHeight * 0.42f, 0.95f * u)
                fillPaint.color = white
                fillPaint.alpha = (sparkle * twinkle(side * 0.37f) * 255f).toInt()
                canvas.drawPath(path, fillPaint)
            }

            drawTear(canvas, u, eyeX, eyeY + halfHeight, side)
        }
    }

    /** A tear rolls from the bottom of the eye, fades, and the next one starts. */
    private fun drawTear(canvas: Canvas, u: Float, x: Float, y: Float, side: Int) {
        val amount = pose[TEAR]
        if (amount < 0.01f) return
        // 0 -> 1 over each 1.5 seconds. The two eyes are a little out of step.
        val along = fractionOf(time / TEAR_SECONDS + side * 0.37f)
        val drop = 0.55f * u * (0.6f + 0.4f * along)
        dropPath(x, y + 0.2f * u + along * 4.4f * u, drop)
        fillPaint.color = tearColor
        fillPaint.alpha = (amount * (1f - along * along) * 235f).toInt()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawBrows(canvas: Canvas, u: Float) {
        val alpha = pose[BROW_ALPHA]
        if (alpha < 0.01f) return

        linePaint.strokeWidth = 0.5f * u
        linePaint.alpha = (alpha * 255f).toInt()
        val tilt = pose[BROW_TILT] * 0.5f * u
        val arch = pose[BROW_ARCH] * u

        for (side in 0..1) {
            val sign = if (side == 0) -1f else 1f
            val raise = if (side == 0) pose[BROW_L] else pose[BROW_R]
            val x = sign * 3.4f * u
            val y = -4.4f * u - raise * 0.9f * u

            // The inner end (nearest the nose) goes up for a worried brow, down for an angry one
            val innerY = y - tilt
            val outerY = y + tilt
            val startY = if (side == 0) outerY else innerY
            val endY = if (side == 0) innerY else outerY

            path.reset()
            path.moveTo(x - 1.9f * u, startY)
            path.quadTo(x, (startY + endY) / 2f - arch * 1.4f, x + 1.9f * u, endY)
            canvas.drawPath(path, linePaint)
        }
    }

    private fun drawMouth(canvas: Canvas, u: Float) {
        val halfWidth = 3.0f * u * pose[MOUTH_W]
        val centerY = 3.9f * u
        val skew = pose[MOUTH_SKEW] * 0.7f * u
        val leftY = centerY + skew
        val rightY = centerY - skew
        val topControl = centerY + pose[MOUTH_TOP] * 8f * u
        val bottomControl = centerY + pose[MOUTH_BOT] * 8f * u

        // One curve along the top lip, one along the bottom lip. When they are the same it is just a line.
        mouthPath.reset()
        mouthPath.moveTo(-halfWidth, leftY)
        mouthPath.quadTo(0f, topControl, halfWidth, rightY)
        mouthPath.quadTo(0f, bottomControl, -halfWidth, leftY)
        mouthPath.close()

        // The inside of the mouth shows only when the lips are apart
        val openness = ((pose[MOUTH_BOT] - pose[MOUTH_TOP]) * 8f).coerceIn(0f, 1f)
        if (openness > 0.01f) {
            fillPaint.shader = null
            fillPaint.color = ink
            fillPaint.alpha = (openness * 255f).toInt()
            canvas.drawPath(mouthPath, fillPaint)

            val tongue = pose[TONGUE]
            if (tongue > 0.01f) {
                canvas.save()
                canvas.clipPath(mouthPath)
                fillPaint.color = tongueColor
                fillPaint.alpha = (tongue * openness * 255f).toInt()
                val tongueY = centerY + pose[MOUTH_BOT] * 4f * u
                canvas.drawOval(-1.6f * u, tongueY - 0.5f * u, 1.6f * u, tongueY + 2.6f * u, fillPaint)
                canvas.restore()
            }
        }

        linePaint.strokeWidth = 0.5f * u
        linePaint.alpha = 255
        canvas.drawPath(mouthPath, linePaint)
    }

    private fun drawSweat(canvas: Canvas, u: Float) {
        val amount = pose[SWEAT]
        if (amount < 0.01f) return
        // A cold sweat drop on the forehead that slides a little
        val slide = 0.5f * u * (0.5f + 0.5f * sin(time * TWO_PI / FLOAT_SECONDS * 2f))
        dropPath(6.1f * u, -5.3f * u + slide, 0.75f * u)
        fillPaint.color = tearColor
        fillPaint.alpha = (amount * 235f).toInt()
        canvas.drawPath(path, fillPaint)
    }

    /** Little stars floating around the head (excited, dreamy, playful moods). */
    private fun drawSparkles(canvas: Canvas, u: Float) {
        val amount = pose[SPARKLE]
        if (amount < 0.01f) return
        fillPaint.color = white
        starPath(-8.6f * u, -6.6f * u, 1.1f * u)
        fillPaint.alpha = (amount * twinkle(0f) * 230f).toInt()
        canvas.drawPath(path, fillPaint)
        starPath(8.9f * u, -4.4f * u, 1.5f * u)
        fillPaint.alpha = (amount * twinkle(0.33f) * 230f).toInt()
        canvas.drawPath(path, fillPaint)
        starPath(7.2f * u, 7.6f * u, 0.9f * u)
        fillPaint.alpha = (amount * twinkle(0.66f) * 230f).toInt()
        canvas.drawPath(path, fillPaint)
    }

    // ---------- Shapes ----------

    /** A heart centered on (x, y). The result is in [path]. */
    private fun heartPath(x: Float, y: Float, size: Float) {
        path.reset()
        path.moveTo(x, y + size * 0.95f)
        path.cubicTo(x - size * 1.7f, y - size * 0.1f, x - size * 0.85f, y - size * 1.35f, x, y - size * 0.5f)
        path.cubicTo(x + size * 0.85f, y - size * 1.35f, x + size * 1.7f, y - size * 0.1f, x, y + size * 0.95f)
        path.close()
    }

    /** A four-pointed star centered on (x, y). The result is in [path]. */
    private fun starPath(x: Float, y: Float, radius: Float) {
        val pinch = radius * 0.14f
        path.reset()
        path.moveTo(x, y - radius)
        path.quadTo(x + pinch, y - pinch, x + radius, y)
        path.quadTo(x + pinch, y + pinch, x, y + radius)
        path.quadTo(x - pinch, y + pinch, x - radius, y)
        path.quadTo(x - pinch, y - pinch, x, y - radius)
        path.close()
    }

    /** A water drop with its tip at the top, centered near (x, y). The result is in [path]. */
    private fun dropPath(x: Float, y: Float, size: Float) {
        path.reset()
        path.moveTo(x, y - size * 1.5f)
        path.cubicTo(x + size * 0.2f, y - size * 0.7f, x + size, y - size * 0.2f, x + size, y + size * 0.35f)
        path.cubicTo(x + size, y + size, x - size, y + size, x - size, y + size * 0.35f)
        path.cubicTo(x - size, y - size * 0.2f, x - size * 0.2f, y - size * 0.7f, x, y - size * 1.5f)
        path.close()
    }

    /** 0..1, repeating: the part after the decimal point. */
    private fun fractionOf(value: Float): Float = value - kotlin.math.floor(value)

    /** A star's brightness: it fades in and out, a bit out of step with the others. */
    private fun twinkle(offset: Float): Float {
        if (timeAnimator == null) return 1f // not animating: stars just stay lit
        return 0.65f + 0.35f * sin((time / TWINKLE_SECONDS + offset) * TWO_PI)
    }

    // =====================================================================================
    //  Life: floating, breathing, blinking
    // =====================================================================================

    private val idleMotionOn: Boolean
        get() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    private val blinkRunnable = object : Runnable {
        override fun run() {
            ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                duration = BLINK_MS
                addUpdateListener { blink = it.animatedValue as Float; invalidate() }
                start()
            }
            postDelayed(this, Random.nextLong(BLINK_MIN_GAP_MS, BLINK_MAX_GAP_MS))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startIdleMotion()
    }

    override fun onDetachedFromWindow() {
        stopIdleMotion()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // No drawing at 60 frames a second while the screen is not showing
        if (visibility == VISIBLE) startIdleMotion() else stopIdleMotion()
    }

    private fun startIdleMotion() {
        if (timeAnimator != null || !idleMotionOn) return
        timeAnimator = ValueAnimator.ofFloat(0f, CYCLE_SECONDS).apply {
            duration = (CYCLE_SECONDS * 1000f).toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { time = it.animatedValue as Float; invalidate() }
            start()
        }
        postDelayed(blinkRunnable, Random.nextLong(BLINK_MIN_GAP_MS, BLINK_MAX_GAP_MS))
    }

    private fun stopIdleMotion() {
        timeAnimator?.cancel()
        timeAnimator = null
        removeCallbacks(blinkRunnable)
        blink = 0f
    }

    // =====================================================================================
    //  The poses: one list of numbers per mood, in slider order
    // =====================================================================================

    private companion object {
        const val DEFAULT_POSITION = 1f          // the face starts on the second mood (Happy)
        const val FACE_SIZE_FRACTION = 0.36f     // the face radius, as a part of the smaller side of this view
        const val GLOW_FRACTION = 1.25f
        const val CURVE = 4f / 3f                // turns "how high the eye is" into a Bezier control point
        const val TWO_PI = (2.0 * Math.PI).toFloat()

        const val CYCLE_SECONDS = 12f
        const val FLOAT_SECONDS = 3f             // 4 float cycles in 12 s
        const val HEART_SECONDS = 0.75f
        const val TEAR_SECONDS = 1.5f
        const val TWINKLE_SECONDS = 1.5f
        const val BLINK_MS = 170L
        const val BLINK_MIN_GAP_MS = 2400L
        const val BLINK_MAX_GAP_MS = 5200L

        const val POP_SQUISH = 0.92f
        const val POP_IN_MS = 70L
        const val POP_OUT_MS = 340L
        const val POP_OVERSHOOT = 3f

        // Where each number lives in a pose
        const val EYE_TOP_L = 0     // upper edge of the left eye: 1 = round, 0 = half shut. (bigger = more open)
        const val EYE_BOT_L = 1     // lower edge of the left eye: -1 = round. Above the top = a "^" shape
        const val EYE_TOP_R = 2
        const val EYE_BOT_R = 3
        const val EYE_SCALE = 4     // size of both eyes
        const val SCLERA = 5        // 0 = a solid dark eye, 1 = a white eye with a pupil
        const val PUPIL = 6         // size of the pupil (when the eye is white)
        const val LOOK_X = 7        // where the pupil looks: -1 left ... 1 right
        const val LOOK_Y = 8        // -1 up ... 1 down
        const val HEART = 9         // 1 = heart-shaped eyes
        const val SPARKLE = 10      // glints in the eyes and little stars around the head
        const val BROW_ALPHA = 11   // 0 = no eyebrows
        const val BROW_L = 12       // how high the left eyebrow sits
        const val BROW_R = 13
        const val BROW_TILT = 14    // > 0 worried (inner ends up), < 0 angry
        const val BROW_ARCH = 15    // how much the eyebrows bend
        const val MOUTH_W = 16      // width of the mouth
        const val MOUTH_TOP = 17    // top lip: > 0 smile, < 0 frown
        const val MOUTH_BOT = 18    // bottom lip: further below the top lip = a more open mouth
        const val MOUTH_SKEW = 19   // > 0 the right side is higher (a smirk)
        const val TONGUE = 20
        const val BLUSH = 21
        const val TEAR = 22
        const val SWEAT = 23
        const val POSE_SIZE = 24

        /** Builds one pose. Anything you do not name is "neutral". */
        private fun pose(
            eyeTop: Float = 1f, eyeBot: Float = -1f, eyeTopR: Float = eyeTop, eyeBotR: Float = eyeBot,
            eyeScale: Float = 1f, sclera: Float = 0f, pupil: Float = 0.5f, lookX: Float = 0f, lookY: Float = 0f,
            heart: Float = 0f, sparkle: Float = 0f,
            browAlpha: Float = 0f, browL: Float = 0f, browR: Float = 0f, browTilt: Float = 0f, browArch: Float = 0f,
            mouthW: Float = 0.8f, mouthTop: Float = 0.2f, mouthBot: Float = 0.2f, mouthSkew: Float = 0f,
            tongue: Float = 0f, blush: Float = 0f, tear: Float = 0f, sweat: Float = 0f
        ) = floatArrayOf(
            eyeTop, eyeBot, eyeTopR, eyeBotR, eyeScale, sclera, pupil, lookX, lookY, heart, sparkle,
            browAlpha, browL, browR, browTilt, browArch,
            mouthW, mouthTop, mouthBot, mouthSkew, tongue, blush, tear, sweat
        )

        /** In slider order: relaxed, happy, romantic, emotional, scared, mind-bending, escape, excited, surprise. */
        val poses: Array<FloatArray> = arrayOf(
            // Relaxed: calm, half-closed eyes and a small easy smile
            pose(eyeTop = 0.05f, eyeBot = -0.85f, mouthW = 0.55f, mouthTop = 0.22f, mouthBot = 0.22f, blush = 0.2f),
            // Happy: bright "^" eyes, a big open smile, rosy cheeks
            pose(eyeTop = 1.1f, eyeBot = 0.55f, eyeScale = 1.1f, mouthW = 1.05f, mouthTop = 0.15f, mouthBot = 1f, blush = 0.7f),
            // Romantic: heart eyes, a soft smile, deep blush, gently lifted brows
            pose(heart = 1f, browAlpha = 0.8f, browL = 0.5f, browR = 0.5f, browTilt = 0.4f, browArch = 0.6f,
                mouthW = 0.75f, mouthTop = 0.3f, mouthBot = 0.5f, blush = 1f),
            // Emotional: big wet eyes, worried brows, a small frown and rolling tears
            pose(eyeTop = 0.95f, eyeBot = -0.95f, eyeScale = 1.1f, browAlpha = 1f, browL = 0.5f, browR = 0.5f, browTilt = 2.4f,
                mouthW = 0.6f, mouthTop = -0.38f, mouthBot = -0.38f, blush = 0.4f, tear = 1f),
            // Scared: very wide white eyes with tiny pupils, high worried brows, an "O" mouth, a cold sweat
            pose(eyeTop = 1.4f, eyeBot = -1.4f, eyeScale = 1.15f, sclera = 1f, pupil = 0.36f,
                browAlpha = 1f, browL = 1.2f, browR = 1.2f, browTilt = 2f, browArch = 0.6f,
                mouthW = 0.45f, mouthTop = -0.3f, mouthBot = 0.8f, sweat = 1f),
            // Mind-bending (curious): one eye bigger than the other, one brow way up, looking up and aside, a smirk
            pose(eyeTop = 1.1f, eyeBot = -1.1f, eyeTopR = 1.5f, eyeBotR = -1.5f, sclera = 1f, pupil = 0.5f,
                lookX = -0.6f, lookY = -0.6f, browAlpha = 1f, browL = 0.1f, browR = 2.3f, browTilt = -0.3f, browArch = 0.7f,
                mouthW = 0.6f, mouthTop = 0.05f, mouthBot = 0.05f, mouthSkew = 0.7f),
            // Escape (dreamy): big pupils gazing up, starry glints, a soft round mouth
            pose(eyeTop = 1.1f, eyeBot = -1f, sclera = 1f, pupil = 0.66f, lookX = 0.25f, lookY = -0.7f, sparkle = 1f,
                browAlpha = 0.8f, browL = 0.9f, browR = 0.9f, browTilt = 0.6f, browArch = 0.8f,
                mouthW = 0.5f, mouthTop = -0.05f, mouthBot = 0.4f, blush = 0.3f),
            // Excited (thrilled): wide eyes with glints, high arched brows, a huge open grin
            pose(eyeTop = 1.35f, eyeBot = -1.1f, eyeScale = 1.1f, sparkle = 1f,
                browAlpha = 1f, browL = 1.5f, browR = 1.5f, browTilt = -0.2f, browArch = 0.9f,
                mouthW = 1.15f, mouthTop = 0.1f, mouthBot = 1.25f, tongue = 0.4f),
            // Surprise Me: a wink, a lopsided grin and a tongue out
            pose(eyeTopR = 0.95f, eyeBotR = 0.6f, sparkle = 0.7f,
                browAlpha = 0.8f, browL = 0.2f, browR = 1.5f, browArch = 0.5f,
                mouthW = 0.95f, mouthTop = 0.1f, mouthBot = 0.95f, mouthSkew = -0.5f, tongue = 1f, blush = 0.3f)
        )
    }
}
