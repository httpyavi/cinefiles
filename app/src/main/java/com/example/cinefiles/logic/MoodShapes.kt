package com.example.cinefiles.logic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The nine body shapes of the Mood screen's character, one per mood in slider order (relaxed, happy, romantic,
 * emotional, scared, mind-bending, escape, excited, surprise): a cushion, a flower, a heart, a dome, a triangle,
 * a hexagon, a circle, a crown and a wedge.
 *
 * Every shape is the SAME kind of list: 72 points, as x and y in "body radii". That is what makes the character
 * morph: the points of two neighbouring shapes can be blended one by one (see MoodFaceView) and the body flows
 * from one silhouette into the next. Every shape has the area of a circle of radius 0.92 and its box is centered
 * on (0, 0), so no mood looks bigger or off to one side.
 *
 * Plain Kotlin (no Android), so it is unit-tested.
 */
object MoodShapes {

    const val POINTS = 72
    private const val SHAPE_AREA = 0.92 * 0.92 * Math.PI // every body has the same area as a circle of radius 0.92

    /** One list of x, y points (in body radii) for each mood, in slider order. */
    val all: Array<FloatArray> by lazy {
        arrayOf(
            bodyOf(smooth = 2) { squircle(it, 1.1, 0.86, 3.2) },                    // relaxed: a wide cushion
            bodyOf(smooth = 2) { 0.62 + 0.38 * ((1 + cos(4 * (it - PI / 4))) / 2).pow(0.6) }, // happy: a four-petal flower (broad petals, narrow notches)
            heartBody(),                                                             // romantic: a heart
            bodyOf(smooth = 2) { if (sin(it) > 0) min(1.0, 0.55 / sin(it)) else 1.0 },       // emotional: a dome (a disc with a flat bottom)
            bodyOf(smooth = 5) { polygon(it, 3, 0.6) },                              // scared: a triangle
            bodyOf(smooth = 3) { polygon(it, 6, 0.9) },                              // mind-bending: a hexagon
            bodyOf(smooth = 0) { 1.0 },                                              // escape: a circle
            bodyOf(smooth = 5) { crown(it) },                                        // excited: a crown with scallops
            wedgeBody()                                                              // surprise: a quarter-circle wedge
        )
    }

    /** A "squircle": between a rectangle and an ellipse. [n] = 2 is an ellipse, bigger is more square. */
    private fun squircle(theta: Double, a: Double, b: Double, n: Double): Double {
        val c = abs(cos(theta)) / a
        val s = abs(sin(theta)) / b
        return (c.pow(n) + s.pow(n)).pow(-1.0 / n)
    }

    /** A regular polygon with a flat edge at the bottom (so a triangle points up). [apothem] = distance to an edge. */
    private fun polygon(theta: Double, sides: Int, apothem: Double): Double {
        val sector = 2 * PI / sides
        var d = (theta - PI / 2 + sector / 2) % sector
        if (d < 0) d += sector
        return apothem / cos(d - sector / 2)
    }

    /** A rounded square with three scallops across the top. */
    private fun crown(theta: Double): Double {
        val base = squircle(theta, 1.0, 1.0, 5.0)
        val up = atan2(sin(theta + PI / 2), cos(theta + PI / 2)) // 0 = straight up
        val window = ((1.1 - abs(up)) / 0.3).coerceIn(0.0, 1.0)
        return base * (1.0 + 0.15 * cos(9 * up) * window)
    }

    /** Makes a body from a rule "how far is the edge in this direction" (angle 0 = right, +90 degrees = down). */
    private fun bodyOf(smooth: Int, radiusAt: (Double) -> Double): FloatArray {
        val radii = DoubleArray(POINTS) { radiusAt(it * 2 * PI / POINTS) }
        return finish(radii, smooth, 0.0, 0.0)
    }

    /** A heart, from the classic heart curve. */
    private fun heartBody(): FloatArray {
        val count = 720
        val xs = DoubleArray(count)
        val ys = DoubleArray(count)
        for (i in 0 until count) {
            val t = i * 2 * PI / count
            xs[i] = 16 * sin(t).pow(3)
            ys[i] = -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)) // minus: the screen's y goes down
        }
        return finish(radiiFromBoundary(xs, ys), 1, 0.0, 0.0)
    }

    /** A quarter of a disc, with the right angle at the bottom left. */
    private fun wedgeBody(): FloatArray {
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (i in 0..200) { xs.add(i / 200.0); ys.add(1.0) }                                          // bottom edge
        for (i in 0..300) { val a = i * PI / 2 / 300; xs.add(cos(a)); ys.add(1.0 - sin(a)) }          // the curved side
        for (i in 0..200) { xs.add(0.0); ys.add(i / 200.0) }                                          // left edge
        return finish(radiiFromBoundary(xs.toDoubleArray(), ys.toDoubleArray()), 4, 0.0, 0.0)
    }

    /** For a boundary drawn as many points: how far the edge is from the middle, in each of the 72 directions. */
    private fun radiiFromBoundary(xs: DoubleArray, ys: DoubleArray): DoubleArray {
        // the middle of the shape (the center of its area)
        var area = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in xs.indices) {
            val j = (i + 1) % xs.size
            val cross = xs[i] * ys[j] - xs[j] * ys[i]
            area += cross
            cx += (xs[i] + xs[j]) * cross
            cy += (ys[i] + ys[j]) * cross
        }
        cx /= 3 * area
        cy /= 3 * area

        val radii = DoubleArray(POINTS)
        for (i in xs.indices) {
            val dx = xs[i] - cx
            val dy = ys[i] - cy
            var angle = atan2(dy, dx)
            if (angle < 0) angle += 2 * PI
            val bin = (angle / (2 * PI) * POINTS).roundToInt() % POINTS
            radii[bin] = max(radii[bin], hypot(dx, dy))
        }
        for (i in radii.indices) { // a direction nobody hit: take the average of its neighbours
            if (radii[i] == 0.0) radii[i] = (radii[(i + POINTS - 1) % POINTS] + radii[(i + 1) % POINTS]) / 2
        }
        return radii
    }

    /**
     * Rounds the corners ([smooth] times, by averaging each point with its neighbours), turns the radii into
     * points, gives the body the standard size and puts the middle of its box at (0, 0).
     */
    private fun finish(start: DoubleArray, smooth: Int, shiftX: Double, shiftY: Double): FloatArray {
        var radii = start
        repeat(smooth) {
            val next = DoubleArray(POINTS)
            for (i in radii.indices) {
                next[i] = 0.25 * radii[(i + POINTS - 1) % POINTS] + 0.5 * radii[i] + 0.25 * radii[(i + 1) % POINTS]
            }
            radii = next
        }
        val xs = DoubleArray(POINTS) { radii[it] * cos(it * 2 * PI / POINTS) }
        val ys = DoubleArray(POINTS) { radii[it] * sin(it * 2 * PI / POINTS) }

        // the same area for every body, so none looks bigger than the others
        var area = 0.0
        for (i in xs.indices) {
            val j = (i + 1) % xs.size
            area += xs[i] * ys[j] - xs[j] * ys[i]
        }
        val scale = sqrt(SHAPE_AREA / abs(area / 2))
        val centerX = (xs.max() + xs.min()) / 2 * scale
        val centerY = (ys.max() + ys.min()) / 2 * scale

        val points = FloatArray(POINTS * 2)
        for (i in 0 until POINTS) {
            points[i * 2] = (xs[i] * scale - centerX + shiftX).toFloat()
            points[i * 2 + 1] = (ys[i] * scale - centerY + shiftY).toFloat()
        }
        return points
    }
}
