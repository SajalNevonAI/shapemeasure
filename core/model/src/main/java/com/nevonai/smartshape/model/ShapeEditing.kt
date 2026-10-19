package com.nevonai.smartshape.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A draggable point on a selected shape, in image pixels. */
sealed interface EditHandle {
    val at: Point2
}

/** A corner of a straight-edged shape. */
data class VertexHandle(val index: Int, override val at: Point2) : EditHandle

/** Midpoint of the edge from vertex [index] to the next one; dragging moves that whole side. */
data class EdgeHandle(val index: Int, override val at: Point2) : EditHandle

/** One side of a round shape's bounding box; dragging resizes the circle/ellipse from that side. */
data class SideHandle(val side: Side, override val at: Point2) : EditHandle

/** Centre of the shape; dragging moves it without changing its size. */
data class MoveHandle(override val at: Point2) : EditHandle

enum class Side { LEFT, TOP, RIGHT, BOTTOM }

/**
 * Hand-editing of outlines. [drag] always starts from the outline as it was when the finger went
 * down plus the total finger movement, so repeated moves never accumulate rounding error.
 */
object ShapeEditing {

    private const val ELLIPSE_SAMPLES = 72
    private const val MIN_SIZE_PX = 8.0

    fun isRound(type: ShapeType) = type == ShapeType.CIRCLE || type == ShapeType.ELLIPSE

    fun handles(shape: DetectedShape): List<EditHandle> {
        val p = shape.points
        if (p.isEmpty()) return emptyList()
        return when {
            isRound(shape.type) -> {
                val b = bounds(p)
                listOf(
                    SideHandle(Side.LEFT, Point2(b.left.toDouble(), b.centreY)),
                    SideHandle(Side.TOP, Point2(b.centreX, b.top.toDouble())),
                    SideHandle(Side.RIGHT, Point2(b.right.toDouble(), b.centreY)),
                    SideHandle(Side.BOTTOM, Point2(b.centreX, b.bottom.toDouble())),
                    MoveHandle(Point2(b.centreX, b.centreY)),
                )
            }
            shape.hasEditableVertices -> {
                val vertices = p.mapIndexed { i, v -> VertexHandle(i, v) }
                // Side handles only where sides are long enough to grab separately from the corners.
                val edges = if (p.size == 4) p.indices.map { i -> EdgeHandle(i, mid(p[i], p[(i + 1) % p.size])) } else emptyList()
                vertices + edges + MoveHandle(Polygons.centroid(p))
            }
            else -> listOf(MoveHandle(Polygons.centroid(p)))
        }
    }

    fun drag(shape: DetectedShape, handle: EditHandle, delta: Point2): List<Point2> {
        val p = shape.points
        return when (handle) {
            is MoveHandle -> p.map { it + delta }
            is VertexHandle -> p.toMutableList().also { it[handle.index] = p[handle.index] + delta }
            is EdgeHandle -> {
                val a = handle.index
                val b = (a + 1) % p.size
                // Move the side along its own normal only, so the shape keeps its corner angles.
                val ex = p[b].x - p[a].x
                val ey = p[b].y - p[a].y
                val len = hypot(ex, ey)
                if (len < 1e-9) return p
                val nx = -ey / len
                val ny = ex / len
                val d = delta.x * nx + delta.y * ny
                val shift = Point2(nx * d, ny * d)
                p.toMutableList().also { it[a] = p[a] + shift; it[b] = p[b] + shift }
            }
            is SideHandle -> {
                val o = bounds(p)
                var l = o.left.toDouble(); var t = o.top.toDouble(); var r = o.right.toDouble(); var btm = o.bottom.toDouble()
                when (handle.side) {
                    Side.LEFT -> l = min(l + delta.x, r - MIN_SIZE_PX)
                    Side.RIGHT -> r = max(r + delta.x, l + MIN_SIZE_PX)
                    Side.TOP -> t = min(t + delta.y, btm - MIN_SIZE_PX)
                    Side.BOTTOM -> btm = max(btm + delta.y, t + MIN_SIZE_PX)
                }
                ellipseIn(l, t, r, btm)
            }
        }
    }

    /** Re-labels a hand-edited outline: a box becomes a square/rectangle, a round one a circle/ellipse. */
    fun retype(type: ShapeType, points: List<Point2>): ShapeType = when {
        isRound(type) -> {
            val b = bounds(points)
            if (min(b.width, b.height) / max(b.width, b.height) > 0.92f) ShapeType.CIRCLE else ShapeType.ELLIPSE
        }
        (type == ShapeType.RECTANGLE || type == ShapeType.SQUARE) && points.size == 4 -> {
            val e = Polygons.edges(points)
            val s1 = (e[0] + e[2]) / 2
            val s2 = (e[1] + e[3]) / 2
            if (min(s1, s2) / max(s1, s2) > 0.95) ShapeType.SQUARE else ShapeType.RECTANGLE
        }
        else -> type
    }

    /** Ellipse inscribed in the box, sampled the same way as detected curves. */
    fun ellipseIn(left: Double, top: Double, right: Double, bottom: Double): List<Point2> {
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        val rx = (right - left) / 2
        val ry = (bottom - top) / 2
        return List(ELLIPSE_SAMPLES) { k ->
            val a = 2 * PI * k / ELLIPSE_SAMPLES
            Point2(cx + rx * cos(a), cy + ry * sin(a))
        }
    }

    private fun mid(a: Point2, b: Point2) = Point2((a.x + b.x) / 2, (a.y + b.y) / 2)

    private fun bounds(p: List<Point2>) = BoxF(
        p.minOf { it.x }.toFloat(), p.minOf { it.y }.toFloat(), p.maxOf { it.x }.toFloat(), p.maxOf { it.y }.toFloat(),
    )

    private val BoxF.centreX get() = (left + right) / 2.0
    private val BoxF.centreY get() = (top + bottom) / 2.0
}
