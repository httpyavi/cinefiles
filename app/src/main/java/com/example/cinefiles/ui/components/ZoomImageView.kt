package com.example.cinefiles.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import com.example.cinefiles.util.animationsEnabled
import kotlin.math.min

/**
 * A picture you can look at closely, with only the platform's gesture detectors (no library):
 *  - it starts FITTED: the whole picture is visible, centered,
 *  - pinch to zoom in (up to [MAX_ZOOM] times) and out (never smaller than fitted),
 *  - drag to move around while zoomed (the picture never leaves the view),
 *  - double-tap to zoom in on the spot, or back to fitted,
 *  - a single tap is a normal click ([performClick]), which the poster viewer uses to close itself.
 *
 * The double-tap zoom animates, unless the phone's animations are off: then it jumps.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val pictureMatrix = Matrix()
    private val values = FloatArray(9)
    private val bounds = RectF()

    private var fitScale = 1f // the scale that makes the whole picture fit the view

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleDetector.isInProgress) return false // two fingers are zooming, not dragging
            pictureMatrix.postTranslate(-distanceX, -distanceY)
            applyMatrix()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (currentZoom() > 1.01f) 1f else DOUBLE_TAP_ZOOM
            zoomTo(target, e.x, e.y)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean = performClick()
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        fitToView()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitToView()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) return super.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    // ---------- The zoom ----------

    /** How far the picture is zoomed in: 1 = fitted, [MAX_ZOOM] = as big as it goes. */
    fun currentZoom(): Float {
        pictureMatrix.getValues(values)
        return if (fitScale == 0f) 1f else values[Matrix.MSCALE_X] / fitScale
    }

    /** Back to the whole picture, centered. */
    private fun fitToView() {
        val picture = drawable ?: return
        if (width == 0 || height == 0 || picture.intrinsicWidth <= 0 || picture.intrinsicHeight <= 0) return

        fitScale = min(width.toFloat() / picture.intrinsicWidth, height.toFloat() / picture.intrinsicHeight)
        pictureMatrix.reset()
        pictureMatrix.postScale(fitScale, fitScale)
        applyMatrix()
    }

    private fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val wanted = (currentZoom() * factor).coerceIn(1f, MAX_ZOOM)
        val real = wanted / currentZoom()
        pictureMatrix.postScale(real, real, focusX, focusY)
        applyMatrix()
    }

    private fun zoomTo(zoom: Float, focusX: Float, focusY: Float) {
        val from = currentZoom()
        if (!context.animationsEnabled()) {
            zoomBy(zoom / from, focusX, focusY)
            return
        }
        ValueAnimator.ofFloat(from, zoom).setDuration(ZOOM_ANIMATION_MS).apply {
            addUpdateListener { zoomBy((it.animatedValue as Float) / currentZoom(), focusX, focusY) }
            start()
        }
    }

    /** Puts the matrix on the picture, after moving it back inside the view (and centering it when it is smaller). */
    private fun applyMatrix() {
        val picture = drawable
        if (picture != null) {
            bounds.set(0f, 0f, picture.intrinsicWidth.toFloat(), picture.intrinsicHeight.toFloat())
            pictureMatrix.mapRect(bounds)
            pictureMatrix.postTranslate(keepInside(bounds.left, bounds.right, width), keepInside(bounds.top, bounds.bottom, height))
        }
        imageMatrix = pictureMatrix
    }

    /** How far to move a picture that spans [start]..[end] so it fills the view's [size], or sits in the middle. */
    private fun keepInside(start: Float, end: Float, size: Int): Float = when {
        end - start <= size -> (size - (end - start)) / 2f - start // smaller than the view: centered
        start > 0f -> -start                                        // a gap on the start side: close it
        end < size -> size - end                                    // a gap on the end side: close it
        else -> 0f
    }

    companion object {
        const val MAX_ZOOM = 4f
        private const val DOUBLE_TAP_ZOOM = 2.5f
        private const val ZOOM_ANIMATION_MS = 200L
    }
}
