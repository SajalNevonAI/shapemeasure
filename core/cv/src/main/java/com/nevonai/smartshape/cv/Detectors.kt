package com.nevonai.smartshape.cv

import android.graphics.Bitmap
import com.nevonai.smartshape.model.Calibration
import com.nevonai.smartshape.model.CalibrationSource
import com.nevonai.smartshape.model.DetectedShape
import com.nevonai.smartshape.model.Homography
import com.nevonai.smartshape.model.Point2
import com.nevonai.smartshape.model.Polygons
import com.nevonai.smartshape.model.ShapeType
import java.nio.ByteBuffer

/** Detects and classifies object outlines. Calls are blocking; run them off the main thread. */
object ShapeDetector {

    val openCvVersion: String by lazy { NativeCv.openCvVersion() }

    /** Full-quality detection on a captured or imported photo. */
    fun detect(bitmap: Bitmap, maxDim: Int = 1600): List<DetectedShape> {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return decodeShapes(NativeCv.detectShapesBitmap(argb, maxDim, true))
    }

    /** Fast detection on a camera luma plane for the live overlay (no sub-pixel refinement). */
    fun detectLuma(y: ByteBuffer, width: Int, height: Int, rowStride: Int, maxDim: Int = 480): List<DetectedShape> =
        decodeShapes(NativeCv.detectShapesLuma(y, width, height, rowStride, maxDim))

    fun quality(y: ByteBuffer, width: Int, height: Int, rowStride: Int): FrameQuality {
        val q = NativeCv.qualityLuma(y, width, height, rowStride)
        return FrameQuality(q[0].toDouble(), q[1].toDouble())
    }

    internal fun decodeShapes(raw: FloatArray): List<DetectedShape> {
        val types = ShapeType.entries
        val count = raw.firstOrNull()?.toInt() ?: 0
        val out = ArrayList<DetectedShape>(count)
        var i = 1
        repeat(count) { index ->
            val type = types[raw[i].toInt()]
            val confidence = raw[i + 1]
            val n = raw[i + 2].toInt()
            i += 3
            val pts = List(n) { k -> Point2(raw[i + 2 * k].toDouble(), raw[i + 2 * k + 1].toDouble()) }
            i += 2 * n
            out += DetectedShape(index + 1, type, confidence, pts)
        }
        return out
    }
}

data class FrameQuality(val sharpness: Double, val brightness: Double) {
    val isBlurry get() = sharpness < 60.0
    val isDark get() = brightness < 50.0
    val isOverexposed get() = brightness > 235.0
}

data class CalibrationMarker(val id: Int, val corners: List<Point2>)

/**
 * Finds printed SmartShape calibration markers (ArUco 4x4, id 0–49). The black square of the
 * printed marker is [MARKER_SIDE_MM] on each side; print at 100% scale and confirm with a ruler.
 */
object MarkerDetector {
    const val MARKER_SIDE_MM = 40.0

    fun detect(bitmap: Bitmap): List<CalibrationMarker> {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val raw = NativeCv.detectMarkersBitmap(argb)
        val count = raw.firstOrNull()?.toInt() ?: 0
        return List(count) { m ->
            val base = 1 + m * 9
            CalibrationMarker(raw[base].toInt(), List(4) { k -> Point2(raw[base + 1 + 2 * k].toDouble(), raw[base + 2 + 2 * k].toDouble()) })
        }
    }

    /** Builds a perspective-correcting calibration from the largest detected marker. */
    fun calibrationFrom(markers: List<CalibrationMarker>): Calibration? {
        val marker = markers.maxByOrNull { Polygons.area(it.corners) } ?: return null
        val s = MARKER_SIDE_MM
        val h = Homography.fromCorrespondences(
            marker.corners,
            listOf(Point2(0.0, 0.0), Point2(s, 0.0), Point2(s, s), Point2(0.0, s)),
        ) ?: return null
        return Calibration.of(CalibrationSource.ARUCO_MARKER, h, "Marker #${marker.id}")
    }

    /** Removes detected shapes that are the marker itself so it is not reported as an object. */
    fun withoutMarkers(shapes: List<DetectedShape>, markers: List<CalibrationMarker>): List<DetectedShape> {
        if (markers.isEmpty()) return shapes
        return shapes.filterNot { shape ->
            val c = Polygons.centroid(shape.points)
            markers.any { m -> contains(m.corners, c) && Polygons.area(shape.points) < 2.0 * Polygons.area(m.corners) }
        }
    }

    private fun contains(poly: List<Point2>, p: Point2): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}
