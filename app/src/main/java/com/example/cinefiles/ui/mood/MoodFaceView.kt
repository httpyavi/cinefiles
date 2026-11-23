package com.example.cinefiles.ui.mood

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.cinefiles.R
import com.example.cinefiles.logic.MoodShapes
import com.example.cinefiles.util.animationsEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The big character on the Mood screen: one flat, solid-colored shape that turns into a different shape and a
 * different expression for every mood. There are no outlines or strokes anywhere: the body, the eyes, the
 * eyebrows, the mouth, the blush and the tears are all FILLED shapes.
 *
 *  - THE BODY has its own silhouette for each mood (a cushion, a flower, a heart, a dome, a triangle, a hexagon,
 *    a circle, a crown and a wedge). Every silhouette is the same list of 72 points, so two of them can be
 *    blended point by point: the body flows from one shape into the next while you drag.
 *  - THE FACE is a list of numbers per mood (a "pose": eye shape, brows, mouth, blush, tears...). They are blended
 *    in the same way, so the eyes open, the mouth bends and the tear fades in smoothly. Nothing is swapped.
 *  - THE COLOR blends through the mood colors (see MoodSpectrum).
 *
 * It is alive too: it floats, squashes and stretches a very little like jelly, blinks, and the tears,
 * hearts and sparkles move. (With "remove animations" on, it stays still.)
 *
 * All sizes of the face are in "u" = one tenth of the body radius, so it looks the same at any size.
 */
class MoodFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- Where we are on the slider, and the blended pose and silhouette for that place ----
    private val colors = MoodSpectrum.colors(context)
    private val pose = FloatArray(POSE_SIZE)
    private val outline = FloatArray(MoodShapes.POINTS * 2) // x, y of every point, in body radii
    private var position = DEFAULT_POSITION

    /** The color of the character right now (it blends from mood to mood while you slide). */
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
    private val deepRed = ContextCompat.getColor(context, R.color.face_heart)
    private val tongueColor = ContextCompat.getColor(context, R.color.accent_soft)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG) // everything is drawn FILLED: there is no stroke paint at all
    private val bodyPath = Path()
    private val path = Path()
    private val eyePath = Path()
    private val mouthPath = Path()

    init {
        updatePose()
    }

    // =====================================================================================
    //  What the screen can ask for
    // =====================================================================================

    /** Shows the character for this place on the slider. 0f = first mood, 1f = second mood, 0.5f = halfway between. */
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
        val eased = along * along * (3f - 2f * along) // slow near a mood, quick in between: each look "holds"

        val a = poses[from]
        val b = poses[from + 1]
        for (i in pose.indices) pose[i] = a[i] + (b[i] - a[i]) * eased

        // The body flows from one silhouette into the next in the same way
        val shapeA = MoodShapes.all[from]
        val shapeB = MoodShapes.all[from + 1]
        for (i in outline.indices) outline[i] = shapeA[i] + (shapeB[i] - shapeA[i]) * eased

        faceColor = MoodSpectrum.colorAt(colors, position)
    }

    // =====================================================================================
    //  Drawing
    // =====================================================================================

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val radius = min(w, h) * BODY_SIZE_FRACTION
        val u = radius / 10f

        // Float up and down, and squash and stretch a very little, like jelly
        val cycle = time * TWO_PI / FLOAT_SECONDS
        val bob = sin(cycle) * u * 0.35f
        val jelly = 0.014f * sin(cycle * 2f + 1f)

        canvas.save()
        canvas.translate(w / 2f, h / 2f + bob)
        canvas.scale(1f + jelly, 1f - jelly)

        drawBody(canvas, radius)

        // The face sits a little higher or lower depending on the body (a triangle is wide at the bottom)
        canvas.save()
        canvas.translate(0f, pose[FACE_Y] * u)
        drawBlush(canvas, u)
        drawEyes(canvas, u)
        drawBrows(canvas, u)
        drawMouth(canvas, u)
        drawSweat(canvas, u)
        canvas.restore()

        drawSparkles(canvas, u)
        drawHearts(canvas, u)

        canvas.restore()
    }

    /** The body: a smooth closed curve through the 72 points of the (blended) silhouette. */
    private fun drawBody(canvas: Canvas, radius: Float) {
        val n = MoodShapes.POINTS
        bodyPath.reset()
        bodyPath.moveTo(outline[0] * radius, outline[1] * radius)
        for (i in 0 until n) {
            val i0 = (i - 1 + n) % n
            val i2 = (i + 1) % n
            val i3 = (i + 2) % n
            // Catmull-Rom: a smooth curve that passes through every point
            val c1x = outline[i * 2] + (outline[i2 * 2] - outline[i0 * 2]) / 6f
            val c1y = outline[i * 2 + 1] + (outline[i2 * 2 + 1] - outline[i0 * 2 + 1]) / 6f
            val c2x = outline[i2 * 2] - (outline[i3 * 2] - outline[i * 2]) / 6f
            val c2y = outline[i2 * 2 + 1] - (outline[i3 * 2 + 1] - outline[i * 2 + 1]) / 6f
            bodyPath.cubicTo(
                c1x * radius, c1y * radius, c2x * radius, c2y * radius,
                outline[i2 * 2] * radius, outline[i2 * 2 + 1] * radius
            )
        }
        bodyPath.close()
        fillPaint.color = faceColor
        canvas.drawPath(bodyPath, fillPaint)
    }

    private fun drawBlush(canvas: Canvas, u: Float) {
        val amount = pose[BLUSH]
        if (amount < 0.01f) return
        // A deeper, redder tone of the body itself, so it never looks like a sticker
        val blush = ColorUtils.blendARGB(faceColor, deepRed, 0.5f)
        fillPaint.color = ColorUtils.setAlphaComponent(blush, (amount * 150f).toInt().coerceIn(0, 255))
        val x = pose[EYE_GAP] + 2.3f
        canvas.drawOval(-(x + 1.7f) * u, 0.7f * u, -(x - 1.7f) * u, 2.9f * u, fillPaint)
        canvas.drawOval((x - 1.7f) * u, 0.7f * u, (x + 1.7f) * u, 2.9f * u, fillPaint)
    }

    private fun drawEyes(canvas: Canvas, u: Float) {
        val eyeScale = pose[EYE_SCALE]
        val halfWidth = 1.55f * u * eyeScale
        val halfHeight = 1.4f * u * eyeScale
        val sclera = pose[SCLERA]
        val eyeY = -1.3f * u

        for (side in 0..1) {
            val sign = if (side == 0) -1f else 1f
            val eyeX = sign * pose[EYE_GAP] * u
            var top = if (side == 0) pose[EYE_TOP_L] else pose[EYE_TOP_R]
            var bottom = if (side == 0) pose[EYE_BOT_L] else pose[EYE_BOT_R]

            // A blink brings the lids together in the middle
            val middle = (top + bottom) / 2f
            top += (middle - top) * blink
            bottom += (middle - bottom) * blink

            // The shape of the eye: an upper curve and a lower curve between two corners (always a FILLED shape).
            // top = 1 and bottom = -1 is a round eye; top = 1 and bottom = 0.6 is a thin happy arch;
            // top and bottom both below zero is a closed, content eye.
            // A round eye keeps its corners straight; a thin one leans them in, so it ends in soft points.
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

            // The white of the eye with a solid pupil in it (wide, scared, dreamy, curious eyes)...
            if (sclera > 0.01f) {
                fillPaint.color = ColorUtils.setAlphaComponent(white, (sclera * 255f).toInt())
                canvas.drawPath(eyePath, fillPaint)

                canvas.save()
                canvas.clipPath(eyePath)
                fillPaint.color = ColorUtils.setAlphaComponent(ink, (sclera * 255f).toInt())
                canvas.drawCircle(
                    eyeX + pose[LOOK_X] * halfWidth * 0.5f,
                    eyeY + pose[LOOK_Y] * halfHeight * 0.5f,
                    pose[PUPIL] * halfHeight, fillPaint
                )
                canvas.restore()
            }

            // ...or a solid dark eye. The two fade into each other.
            fillPaint.color = ColorUtils.setAlphaComponent(ink, ((1f - sclera) * 255f).toInt())
            canvas.drawPath(eyePath, fillPaint)

            // The little light dot in a dark eye makes it look wet and alive (only in a properly open eye)
            val open = (((top - bottom) / 2f - 0.55f) / 0.35f).coerceIn(0f, 1f)
            val catchlight = (1f - sclera) * open
            if (catchlight > 0.05f) {
                fillPaint.color = ColorUtils.setAlphaComponent(white, (catchlight * 235f).toInt())
                val size = 0.34f * u * (1f + 0.9f * pose[TEAR])
                canvas.drawCircle(eyeX + halfWidth * 0.34f, eyeY - halfHeight * 0.36f, size, fillPaint)
            }

            // A glint in the eye (excited, dreamy, in love, playful)
            val sparkle = pose[SPARKLE]
            if (sparkle > 0.01f) {
                starPath(eyeX + halfWidth * 0.42f, eyeY - halfHeight * 0.42f, 0.95f * u)
                fillPaint.color = ColorUtils.setAlphaComponent(white, (sparkle * twinkle(side * 0.37f) * 255f).toInt())
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
        fillPaint.color = ColorUtils.setAlphaComponent(tearColor, (amount * (1f - along * along) * 235f).toInt())
        canvas.drawPath(path, fillPaint)
    }

    private fun drawBrows(canvas: Canvas, u: Float) {
        val alpha = pose[BROW_ALPHA]
        if (alpha < 0.01f) return

        fillPaint.color = ColorUtils.setAlphaComponent(ink, (alpha * 255f).toInt())
        val tilt = pose[BROW_TILT] * 0.5f * u
        val arch = pose[BROW_ARCH] * u

        for (side in 0..1) {
            val sign = if (side == 0) -1f else 1f
            val raise = if (side == 0) pose[BROW_L] else pose[BROW_R]
            val x = sign * pose[EYE_GAP] * u
            val y = -4.4f * u - raise * 0.9f * u

            // The inner end (nearest the nose) goes up for a worried brow, down for an angry one
            val innerY = y - tilt
            val outerY = y + tilt
            val startY = if (side == 0) outerY else innerY
            val endY = if (side == 0) innerY else outerY

            fillBar(canvas, x - 1.9f * u, startY, x, (startY + endY) / 2f - arch * 1.4f, x + 1.9f * u, endY, 0.5f * u)
        }
    }

    /**
     * A filled bar along a curve (from x0,y0 through the bend cx,cy to x1,y1) with round ends: an eyebrow.
     * It is a solid shape, not a stroked line.
     */
    private fun fillBar(
        canvas: Canvas, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, thickness: Float
    ) {
        val half = thickness / 2f
        path.reset()
        // along the upper side, then back along the lower side
        for (pass in 0..1) {
            for (step in 0..BAR_STEPS) {
                val i = if (pass == 0) step else BAR_STEPS - step
                val t = i / BAR_STEPS.toFloat()
                val px = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * cx + t * t * x1
                val py = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * cy + t * t * y1
                var tx = 2 * (1 - t) * (cx - x0) + 2 * t * (x1 - cx)
                var ty = 2 * (1 - t) * (cy - y0) + 2 * t * (y1 - cy)
                val length = hypot(tx, ty)
                if (length < 0.0001f) { tx = x1 - x0; ty = y1 - y0 } else { tx /= length; ty /= length }
                val side = if (pass == 0) 1f else -1f
                val ox = -ty * half * side
                val oy = tx * half * side
                if (pass == 0 && step == 0) path.moveTo(px + ox, py + oy) else path.lineTo(px + ox, py + oy)
            }
        }
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawCircle(x0, y0, half, fillPaint)
        canvas.drawCircle(x1, y1, half, fillPaint)
    }

    private fun drawMouth(canvas: Canvas, u: Float) {
        val halfWidth = 3.0f * u * pose[MOUTH_W]
        val centerY = 3.9f * u
        val skew = pose[MOUTH_SKEW] * 0.7f * u
        val leftY = centerY + skew
        val rightY = centerY - skew
        val topControl = centerY + pose[MOUTH_TOP] * 8f * u
        // The lips are never closer than one "u" apart at the control point, so a closed mouth is a smooth,
        // solid curved bar (a filled crescent) and never a thin line.
        val bottomControl = max(centerY + pose[MOUTH_BOT] * 8f * u, topControl + MIN_LIP * u)

        mouthPath.reset()
        mouthPath.moveTo(-halfWidth, leftY)
        mouthPath.quadTo(0f, topControl, halfWidth, rightY)
        mouthPath.quadTo(0f, bottomControl, -halfWidth, leftY)
        mouthPath.close()

        fillPaint.color = ink
        canvas.drawPath(mouthPath, fillPaint)
        // Round ends, so a smile looks like a soft bar
        canvas.drawCircle(-halfWidth, leftY, 0.25f * u, fillPaint)
        canvas.drawCircle(halfWidth, rightY, 0.25f * u, fillPaint)

        // The tongue only shows when the mouth is open (the lips are far apart)
        val openness = (((bottomControl - topControl) / u - 2f) / 2f).coerceIn(0f, 1f)
        val tongue = pose[TONGUE] * openness
        if (tongue > 0.01f) {
            canvas.save()
            canvas.clipPath(mouthPath)
            fillPaint.color = ColorUtils.setAlphaComponent(tongueColor, (tongue * 255f).toInt())
            val tongueY = (topControl + bottomControl) / 2f + 0.3f * u
            canvas.drawOval(-1.6f * u, tongueY - 0.5f * u, 1.6f * u, tongueY + 2.6f * u, fillPaint)
            canvas.restore()
        }
    }

    private fun drawSweat(canvas: Canvas, u: Float) {
        val amount = pose[SWEAT]
        if (amount < 0.01f) return
        // A cold sweat drop that slides a little
        val slide = 0.5f * u * (0.5f + 0.5f * sin(time * TWO_PI / FLOAT_SECONDS * 2f))
        dropPath(pose[EYE_GAP] * u + 2.8f * u, -5.3f * u + slide, 0.75f * u)
        fillPaint.color = ColorUtils.setAlphaComponent(tearColor, (amount * 235f).toInt())
        canvas.drawPath(path, fillPaint)
    }

    /** Little stars floating around the body (excited, dreamy, playful moods). */
    private fun drawSparkles(canvas: Canvas, u: Float) {
        val amount = pose[SPARKLE]
        if (amount < 0.01f) return
        starPath(-8.9f * u, -7.2f * u, 1.1f * u)
        fillPaint.color = ColorUtils.setAlphaComponent(white, (amount * twinkle(0f) * 230f).toInt())
        canvas.drawPath(path, fillPaint)
        starPath(9.2f * u, -5.0f * u, 1.5f * u)
        fillPaint.color = ColorUtils.setAlphaComponent(white, (amount * twinkle(0.33f) * 230f).toInt())
        canvas.drawPath(path, fillPaint)
        starPath(8.4f * u, 8.0f * u, 0.9f * u)
        fillPaint.color = ColorUtils.setAlphaComponent(white, (amount * twinkle(0.66f) * 230f).toInt())
        canvas.drawPath(path, fillPaint)
    }

    /** Two little hearts that float up beside a character in love. */
    private fun drawHearts(canvas: Canvas, u: Float) {
        val amount = pose[HEARTS]
        if (amount < 0.01f) return
        for (i in 0..1) {
            val along = fractionOf(time / HEART_FLOAT_SECONDS + i * 0.5f) // 0 -> 1, then starts over
            val x = if (i == 0) 9.6f * u else -9.4f * u
            val y = (if (i == 0) -3.2f else -1.0f) * u - along * 5f * u
            heartPath(x, y, (0.7f + 0.5f * along) * u)
            val fade = sin(along * PI.toFloat()).coerceAtLeast(0f) // in and out
            fillPaint.color = ColorUtils.setAlphaComponent(white, (amount * fade * 235f).toInt())
            canvas.drawPath(path, fillPaint)
        }
    }

    // ---------- Small shapes (all filled) ----------

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
    private fun fractionOf(value: Float): Float = value - floor(value)

    /** A star's brightness: it fades in and out, a bit out of step with the others. */
    private fun twinkle(offset: Float): Float {
        if (timeAnimator == null) return 1f // not animating: stars just stay lit
        return 0.65f + 0.35f * sin((time / TWINKLE_SECONDS + offset) * TWO_PI)
    }

    // =====================================================================================
    //  Life: floating, jelly, blinking
    // =====================================================================================

    private val idleMotionOn: Boolean
        get() = context.animationsEnabled()

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
    //  The numbers: one pose and one silhouette per mood, in slider order
    // =====================================================================================

    private companion object {
        const val DEFAULT_POSITION = 1f          // starts on the second mood (Happy)
        const val BODY_SIZE_FRACTION = 0.34f     // the body radius, as a part of the smaller side of this view
        const val CURVE = 4f / 3f                // turns "how high the eye is" into a Bezier control point
        const val TWO_PI = (2.0 * Math.PI).toFloat()
        const val MIN_LIP = 1f                   // the thinnest a mouth can be: one "u" between the lips' control points
        const val BAR_STEPS = 10                 // how smooth an eyebrow is

        const val CYCLE_SECONDS = 12f
        const val FLOAT_SECONDS = 3f             // 4 float cycles in 12 s
        const val HEART_FLOAT_SECONDS = 3f
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
        const val EYE_TOP_L = 0     // upper edge of the left eye: 1 = round, 0 = half shut, < 0 = a closed "bowl"
        const val EYE_BOT_L = 1     // lower edge of the left eye: -1 = round. Above the top = a thin arch
        const val EYE_TOP_R = 2
        const val EYE_BOT_R = 3
        const val EYE_SCALE = 4     // size of both eyes
        const val SCLERA = 5        // 0 = a solid dark eye, 1 = a white eye with a solid pupil
        const val PUPIL = 6         // size of the pupil (when the eye is white)
        const val LOOK_X = 7        // where the pupil looks: -1 left ... 1 right
        const val LOOK_Y = 8        // -1 up ... 1 down
        const val SPARKLE = 9       // glints in the eyes and little stars around the body
        const val BROW_ALPHA = 10   // 0 = no eyebrows
        const val BROW_L = 11       // how high the left eyebrow sits
        const val BROW_R = 12
        const val BROW_TILT = 13    // > 0 worried (inner ends up), < 0 angry
        const val BROW_ARCH = 14    // how much the eyebrows bend
        const val MOUTH_W = 15      // width of the mouth
        const val MOUTH_TOP = 16    // top lip: > 0 smile, < 0 frown
        const val MOUTH_BOT = 17    // bottom lip: further below the top lip = a more open mouth
        const val MOUTH_SKEW = 18   // > 0 the right side is higher (a smirk)
        const val TONGUE = 19
        const val BLUSH = 20
        const val TEAR = 21
        const val SWEAT = 22
        const val FACE_Y = 23       // moves the whole face down (> 0) or up (< 0) on the body, in "u"
        const val EYE_GAP = 24      // how far each eye is from the middle, in "u"
        const val HEARTS = 25       // little hearts floating beside the body
        const val POSE_SIZE = 26

        /** Builds one pose. Anything you do not name is "neutral". */
        private fun pose(
            eyeTop: Float = 1f, eyeBot: Float = -1f, eyeTopR: Float = eyeTop, eyeBotR: Float = eyeBot,
            eyeScale: Float = 1f, sclera: Float = 0f, pupil: Float = 0.5f, lookX: Float = 0f, lookY: Float = 0f,
            sparkle: Float = 0f,
            browAlpha: Float = 0f, browL: Float = 0f, browR: Float = 0f, browTilt: Float = 0f, browArch: Float = 0f,
            mouthW: Float = 0.8f, mouthTop: Float = 0.2f, mouthBot: Float = 0.2f, mouthSkew: Float = 0f,
            tongue: Float = 0f, blush: Float = 0f, tear: Float = 0f, sweat: Float = 0f,
            faceY: Float = 0f, eyeGap: Float = 3.4f, hearts: Float = 0f
        ) = floatArrayOf(
            eyeTop, eyeBot, eyeTopR, eyeBotR, eyeScale, sclera, pupil, lookX, lookY, sparkle,
            browAlpha, browL, browR, browTilt, browArch,
            mouthW, mouthTop, mouthBot, mouthSkew, tongue, blush, tear, sweat,
            faceY, eyeGap, hearts
        )

        /** In slider order: relaxed, happy, romantic, emotional, scared, mind-bending, escape, excited, surprise. */
        val poses: Array<FloatArray> = arrayOf(
            // Relaxed (a soft cushion): two closed, content eyes and a small easy smile
            pose(eyeTop = -0.1f, eyeBot = -0.8f, eyeScale = 1.1f, eyeGap = 3.7f, faceY = 0.3f,
                mouthW = 0.6f, mouthTop = 0.2f, mouthBot = 0.2f, blush = 0.3f),
            // Happy (a flower): happy arch eyes, a big open smile, rosy cheeks
            pose(eyeTop = 1.1f, eyeBot = 0.55f, eyeScale = 1.1f, eyeGap = 3.2f,
                mouthW = 1f, mouthTop = 0.15f, mouthBot = 0.95f, blush = 0.6f),
            // Romantic (a heart): starry eyes, a soft smile, deep blush and little hearts floating by
            pose(sparkle = 0.8f, eyeGap = 3f, faceY = -0.9f, browAlpha = 0.8f, browL = 0.5f, browR = 0.5f,
                browTilt = 0.4f, browArch = 0.6f, mouthW = 0.55f, mouthTop = 0.25f, mouthBot = 0.45f,
                blush = 1f, hearts = 1f),
            // Emotional (a dome, like a little ghost): big wet eyes, worried brows, a small frown and rolling tears
            pose(eyeTop = 0.95f, eyeBot = -0.95f, eyeScale = 1.1f, eyeGap = 3.3f, faceY = 0.4f,
                browAlpha = 1f, browL = 0.5f, browR = 0.5f, browTilt = 2.4f,
                mouthW = 0.5f, mouthTop = -0.35f, mouthBot = -0.35f, blush = 0.4f, tear = 1f),
            // Scared (a triangle): wide white eyes with tiny pupils, high worried brows, an "O" mouth, a cold sweat
            pose(eyeTop = 1.4f, eyeBot = -1.4f, eyeScale = 1.05f, sclera = 1f, pupil = 0.36f, eyeGap = 2.7f, faceY = 1.4f,
                browAlpha = 1f, browL = 1.2f, browR = 1.2f, browTilt = 2f, browArch = 0.6f,
                mouthW = 0.4f, mouthTop = -0.3f, mouthBot = 0.7f, sweat = 1f),
            // Mind-bending (a hexagon): one eye bigger than the other, one brow way up, looking up and aside, a smirk
            pose(eyeTop = 1.1f, eyeBot = -1.1f, eyeTopR = 1.5f, eyeBotR = -1.5f, sclera = 1f, pupil = 0.5f,
                lookX = -0.6f, lookY = -0.6f, browAlpha = 1f, browL = 0.1f, browR = 2.3f, browTilt = -0.3f, browArch = 0.7f,
                mouthW = 0.6f, mouthTop = 0.05f, mouthBot = 0.05f, mouthSkew = 0.7f),
            // Escape (a circle): big dreamy white eyes gazing up, starry glints, a small round mouth
            pose(eyeTop = 1.1f, eyeBot = -1f, sclera = 1f, pupil = 0.66f, lookX = 0.25f, lookY = -0.7f, sparkle = 1f,
                browAlpha = 0.8f, browL = 0.9f, browR = 0.9f, browTilt = 0.6f, browArch = 0.8f,
                mouthW = 0.4f, mouthTop = -0.05f, mouthBot = 0.4f, blush = 0.25f),
            // Excited (a crown): wide eyes with glints, high arched brows, a huge open grin
            pose(eyeTop = 1.35f, eyeBot = -1.1f, eyeScale = 1.1f, sparkle = 1f, eyeGap = 3.3f, faceY = 0.6f,
                browAlpha = 1f, browL = 1.5f, browR = 1.5f, browTilt = -0.2f, browArch = 0.9f,
                mouthW = 1.1f, mouthTop = 0.1f, mouthBot = 1.15f, tongue = 0.4f),
            // Surprise Me (a wedge): a wink, a lopsided grin and a tongue out
            pose(eyeTopR = 1.1f, eyeBotR = 0.55f, sparkle = 0.7f, eyeGap = 3.2f, faceY = 0.5f,
                browAlpha = 0.9f, browL = 0.2f, browR = 1.5f, browArch = 0.5f,
                mouthW = 0.95f, mouthTop = 0.1f, mouthBot = 0.95f, mouthSkew = -0.5f, tongue = 1f, blush = 0.3f)
        )
    }
}
