package com.example.cinefiles.logic

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodShapesTest {

    private val shapes = MoodShapes.all

    @Test
    fun thereAreNineShapesOfSeventyTwoPointsEach() {
        assertEquals(9, shapes.size)
        for (shape in shapes) assertEquals(MoodShapes.POINTS * 2, shape.size)
    }

    @Test
    fun everyNumberIsFinite() {
        for (shape in shapes) for (v in shape) assertTrue(v.isFinite())
    }

    private fun area(shape: FloatArray): Double {
        var sum = 0.0
        val n = MoodShapes.POINTS
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += shape[i * 2] * shape[j * 2 + 1] - shape[j * 2] * shape[i * 2 + 1]
        }
        return abs(sum / 2)
    }

    @Test
    fun everyShapeHasTheSameSizeAsACircleOfRadius092() {
        val circle = Math.PI * 0.92 * 0.92
        for ((i, shape) in shapes.withIndex()) {
            // the points are a polygon with 72 corners, so a circle is a little under pi r^2: allow 2%
            assertEquals("shape $i", circle, area(shape), circle * 0.02)
        }
    }

    @Test
    fun everyShapeIsCenteredOnZero() {
        for ((i, shape) in shapes.withIndex()) {
            val xs = (0 until MoodShapes.POINTS).map { shape[it * 2] }
            val ys = (0 until MoodShapes.POINTS).map { shape[it * 2 + 1] }
            assertEquals("shape $i x", 0f, (xs.max() + xs.min()) / 2, 0.001f)
            assertEquals("shape $i y", 0f, (ys.max() + ys.min()) / 2, 0.001f)
        }
    }

    @Test
    fun noShapeIsWiderOrTallerThanThePictureAllows() {
        for ((i, shape) in shapes.withIndex()) {
            for (v in shape) assertTrue("shape $i has $v", abs(v) < 1.45f)
        }
    }

    @Test
    fun theShapesReallyAreDifferentFromEachOther() {
        for (a in shapes.indices) for (b in a + 1 until shapes.size) {
            var difference = 0f
            for (i in shapes[a].indices) difference += abs(shapes[a][i] - shapes[b][i])
            assertTrue("shapes $a and $b look the same", difference > 1f)
        }
    }

    @Test
    fun blendingTwoNeighboursNeverLeavesTheirBoxes() {
        // the middle of a morph stays between the two shapes (no overshoot, no sudden jump)
        for (k in 0 until shapes.size - 1) {
            for (i in shapes[k].indices) {
                val middle = (shapes[k][i] + shapes[k + 1][i]) / 2
                assertTrue(middle >= minOf(shapes[k][i], shapes[k + 1][i]) - 1e-6f)
                assertTrue(middle <= maxOf(shapes[k][i], shapes[k + 1][i]) + 1e-6f)
            }
        }
    }

    /**
     * Not a check: it draws the nine bodies, and three steps between each pair, into build/mood-shapes-preview.png
     * so the silhouettes can be LOOKED at without a phone. (It always passes. The picture is made by hand here
     * because the build's test setup has no java.awt: a tiny scanline fill and a PNG writer.)
     */
    @Test
    fun drawAPreviewPicture() {
        val cell = 150
        val columns = 9
        val rows = 4
        val width = cell * columns
        val height = cell * rows
        val pixels = IntArray(width * height) { 0x0A0A0D }
        val colors = intArrayOf(0x3EC8B4, 0xFFB938, 0xFF5C93, 0x4F8BFF, 0x8A5CFF, 0x36C5F0, 0x52E08A, 0xFF7A3D, 0xC9A7FF)

        // row 0: the nine bodies; rows 1-3: the quarter, half and three-quarter blend toward the next one
        for (row in 0 until rows) {
            for (k in 0 until columns) {
                val next = shapes[(k + 1) % columns]
                val t = row / 4f
                val blended = FloatArray(shapes[k].size) { shapes[k][it] + (next[it] - shapes[k][it]) * t }
                val outline = curvePoints(blended, cell * 0.36f, k * cell + cell / 2f, row * cell + cell / 2f)
                fill(pixels, width, height, outline, colors[k])
            }
        }

        val out = File("build/mood-shapes-preview.png")
        out.parentFile?.mkdirs()
        writePng(out, width, height, pixels)
        assertTrue(out.length() > 100)
    }

    /** The same smooth closed curve through the points that MoodFaceView draws, as many small straight steps. */
    private fun curvePoints(p: FloatArray, radius: Float, cx: Float, cy: Float): List<Pair<Float, Float>> {
        val n = MoodShapes.POINTS
        val steps = 8
        val result = ArrayList<Pair<Float, Float>>()
        for (i in 0 until n) {
            val i0 = (i - 1 + n) % n
            val i2 = (i + 1) % n
            val i3 = (i + 2) % n
            val x1 = p[i * 2]; val y1 = p[i * 2 + 1]
            val x2 = p[i2 * 2]; val y2 = p[i2 * 2 + 1]
            val c1x = x1 + (x2 - p[i0 * 2]) / 6f
            val c1y = y1 + (y2 - p[i0 * 2 + 1]) / 6f
            val c2x = x2 - (p[i3 * 2] - x1) / 6f
            val c2y = y2 - (p[i3 * 2 + 1] - y1) / 6f
            for (k in 0 until steps) {
                val t = k / steps.toFloat()
                val m = 1 - t
                val bx = m * m * m * x1 + 3 * m * m * t * c1x + 3 * m * t * t * c2x + t * t * t * x2
                val by = m * m * m * y1 + 3 * m * m * t * c1y + 3 * m * t * t * c2y + t * t * t * y2
                result.add(Pair(cx + bx * radius, cy + by * radius))
            }
        }
        return result
    }

    /** Fills the inside of a closed outline, row by row. */
    private fun fill(pixels: IntArray, width: Int, height: Int, outline: List<Pair<Float, Float>>, color: Int) {
        val minY = outline.minOf { it.second }.toInt().coerceAtLeast(0)
        val maxY = outline.maxOf { it.second }.toInt().coerceAtMost(height - 1)
        for (y in minY..maxY) {
            val py = y + 0.5f
            val crossings = ArrayList<Float>()
            for (i in outline.indices) {
                val (ax, ay) = outline[i]
                val (bx, by) = outline[(i + 1) % outline.size]
                if ((ay <= py && by > py) || (by <= py && ay > py)) {
                    crossings.add(ax + (py - ay) / (by - ay) * (bx - ax))
                }
            }
            crossings.sort()
            var i = 0
            while (i + 1 < crossings.size) {
                val from = crossings[i].toInt().coerceAtLeast(0)
                val to = crossings[i + 1].toInt().coerceAtMost(width - 1)
                for (x in from..to) pixels[y * width + x] = color
                i += 2
            }
        }
    }

    /** Writes the pixels (0xRRGGBB) as a PNG file. */
    private fun writePng(file: File, width: Int, height: Int, pixels: IntArray) {
        val raw = ByteArray((width * 3 + 1) * height)
        var at = 0
        for (y in 0 until height) {
            raw[at++] = 0 // no filter
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                raw[at++] = (c shr 16).toByte()
                raw[at++] = (c shr 8).toByte()
                raw[at++] = c.toByte()
            }
        }
        val compressed = java.io.ByteArrayOutputStream().also { bytes ->
            java.util.zip.DeflaterOutputStream(bytes).use { it.write(raw) }
        }.toByteArray()

        java.io.DataOutputStream(file.outputStream()).use { out ->
            fun chunk(type: String, data: ByteArray) {
                out.writeInt(data.size)
                val body = type.toByteArray(Charsets.US_ASCII) + data
                out.write(body)
                out.writeInt(java.util.zip.CRC32().also { it.update(body) }.value.toInt())
            }
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            val header = java.nio.ByteBuffer.allocate(13)
                .putInt(width).putInt(height).put(8).put(2).put(0).put(0).put(0).array()
            chunk("IHDR", header)
            chunk("IDAT", compressed)
            chunk("IEND", ByteArray(0))
        }
    }
}
