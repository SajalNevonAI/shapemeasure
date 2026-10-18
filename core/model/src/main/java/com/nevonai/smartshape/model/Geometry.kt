package com.nevonai.smartshape.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

@Serializable
data class Point2(val x: Double, val y: Double) {
    operator fun minus(o: Point2) = Point2(x - o.x, y - o.y)
    operator fun plus(o: Point2) = Point2(x + o.x, y + o.y)
    fun distanceTo(o: Point2) = hypot(x - o.x, y - o.y)
}

@Serializable
data class BoxF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Principal second moments of a region: variances along the major/minor axes and the major-axis angle. */
data class Moments(val major: Double, val minor: Double, val angle: Double)

/** Basic polygon geometry. Polygons are closed implicitly (last vertex connects to first). */
object Polygons {

    fun signedArea(pts: List<Point2>): Double {
        if (pts.size < 3) return 0.0
        var s = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2.0
    }

    fun area(pts: List<Point2>) = abs(signedArea(pts))

    fun perimeter(pts: List<Point2>): Double = edges(pts).sum()

    fun edges(pts: List<Point2>): List<Double> =
        if (pts.size < 2) emptyList() else pts.indices.map { pts[it].distanceTo(pts[(it + 1) % pts.size]) }

    fun centroid(pts: List<Point2>): Point2 {
        val a = signedArea(pts)
        if (abs(a) < 1e-12) return Point2(pts.map { it.x }.average(), pts.map { it.y }.average())
        var cx = 0.0
        var cy = 0.0
        for (i in pts.indices) {
            val p = pts[i]
            val q = pts[(i + 1) % pts.size]
            val cross = p.x * q.y - q.x * p.y
            cx += (p.x + q.x) * cross
            cy += (p.y + q.y) * cross
        }
        return Point2(cx / (6 * a), cy / (6 * a))
    }

    /**
     * Area-normalised second central moments of the filled polygon. For a filled ellipse with
     * semi-axes a ≥ b this gives major = a²/4 and minor = b²/4.
     */
    fun principalMoments(pts: List<Point2>): Moments {
        val c = centroid(pts)
        val q = pts.map { it - c }
        var a2 = 0.0
        var ix2 = 0.0 // ∫x² dA * 12
        var iy2 = 0.0 // ∫y² dA * 12
        var ixy = 0.0 // ∫xy dA * 24
        for (i in q.indices) {
            val p = q[i]
            val n = q[(i + 1) % q.size]
            val cross = p.x * n.y - n.x * p.y
            a2 += cross
            ix2 += (p.x * p.x + p.x * n.x + n.x * n.x) * cross
            iy2 += (p.y * p.y + p.y * n.y + n.y * n.y) * cross
            ixy += (p.x * n.y + 2 * p.x * p.y + 2 * n.x * n.y + n.x * p.y) * cross
        }
        val area = a2 / 2.0
        if (abs(area) < 1e-12) return Moments(0.0, 0.0, 0.0)
        val vx = ix2 / 12.0 / area
        val vy = iy2 / 12.0 / area
        val cxy = ixy / 24.0 / area
        val half = (vx + vy) / 2
        val disc = sqrt(max(0.0, (vx - vy) * (vx - vy) / 4 + cxy * cxy))
        return Moments(half + disc, max(0.0, half - disc), 0.5 * atan2(2 * cxy, vx - vy))
    }
}
