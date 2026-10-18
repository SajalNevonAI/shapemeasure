package com.nevonai.smartshape.model

import kotlin.math.abs
import kotlin.math.atan2

/**
 * 3x3 projective transform stored row-major. Maps image pixels onto the physical
 * measurement plane (millimetres).
 */
class Homography(val m: DoubleArray) {
    init {
        require(m.size == 9)
    }

    fun map(p: Point2): Point2 {
        val w = m[6] * p.x + m[7] * p.y + m[8]
        return Point2((m[0] * p.x + m[1] * p.y + m[2]) / w, (m[3] * p.x + m[4] * p.y + m[5]) / w)
    }

    fun map(pts: List<Point2>) = pts.map(::map)

    companion object {
        fun scale(s: Double) = Homography(doubleArrayOf(s, 0.0, 0.0, 0.0, s, 0.0, 0.0, 0.0, 1.0))

        /** Solves the 4-point DLT (h33 = 1). Returns null when the points are degenerate. */
        fun fromCorrespondences(src: List<Point2>, dst: List<Point2>): Homography? {
            require(src.size == 4 && dst.size == 4)
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val (x, y) = src[i]
                val (u, v) = dst[i]
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            val h = solve(a) ?: return null
            return Homography(doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0))
        }

        /**
         * Maps the image corners of a known-size rectangular reference (any order) onto a metric
         * plane of [widthMm] x [heightMm]. The longer image side is matched to the longer physical side.
         */
        fun fromReferenceRect(cornersPx: List<Point2>, widthMm: Double, heightMm: Double): Homography? {
            val q = orderQuad(cornersPx)
            val top = q[0].distanceTo(q[1]) + q[2].distanceTo(q[3])
            val side = q[1].distanceTo(q[2]) + q[3].distanceTo(q[0])
            val long = maxOf(widthMm, heightMm)
            val short = minOf(widthMm, heightMm)
            val (w, h) = if (top >= side) long to short else short to long
            val dst = listOf(Point2(0.0, 0.0), Point2(w, 0.0), Point2(w, h), Point2(0.0, h))
            return fromCorrespondences(q, dst)
        }

        /** Gaussian elimination with partial pivoting on an n x (n+1) augmented matrix. */
        private fun solve(a: Array<DoubleArray>): DoubleArray? {
            val n = a.size
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
                if (abs(a[pivot][col]) < 1e-12) return null
                val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = a[r][col] / a[col][col]
                    for (c in col..n) a[r][c] -= f * a[col][c]
                }
            }
            return DoubleArray(n) { a[it][n] / a[it][it] }
        }
    }
}

/** Orders 4 points clockwise (image coordinates, y down) starting from the top-left. */
fun orderQuad(pts: List<Point2>): List<Point2> {
    require(pts.size == 4)
    val cx = pts.sumOf { it.x } / 4
    val cy = pts.sumOf { it.y } / 4
    val sorted = pts.sortedBy { atan2(it.y - cy, it.x - cx) }
    val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
    return List(4) { sorted[(start + it) % 4] }
}
