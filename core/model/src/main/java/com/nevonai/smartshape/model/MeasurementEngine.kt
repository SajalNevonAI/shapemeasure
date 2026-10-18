package com.nevonai.smartshape.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Converts detected outlines into dimensions. Geometry is computed after mapping the outline onto
 * the measurement plane, so perspective is corrected when the calibration is a full homography.
 */
object MeasurementEngine {

    fun measure(shape: DetectedShape, calibration: Calibration): ShapeMeasurement {
        val pts = calibration.homography()?.map(shape.points) ?: shape.points
        val dims = when (shape.type) {
            ShapeType.RECTANGLE -> rectangle(pts)
            ShapeType.SQUARE -> square(pts)
            ShapeType.TRIANGLE -> triangle(pts)
            ShapeType.CIRCLE -> circle(pts)
            ShapeType.ELLIPSE -> ellipse(pts)
            ShapeType.POLYGON -> polygon(pts)
            ShapeType.IRREGULAR -> irregular(pts)
        }
        return ShapeMeasurement(shape.id, shape.type, calibration.isCalibrated, dims)
    }

    /** Distance between two image points on the calibrated plane (manual measurement). */
    fun distance(a: Point2, b: Point2, calibration: Calibration): Double {
        val h = calibration.homography() ?: return a.distanceTo(b)
        return h.map(a).distanceTo(h.map(b))
    }

    /** Scale-only calibration from a segment of known physical length. */
    fun knownLength(a: Point2, b: Point2, lengthMm: Double): Calibration? {
        val px = a.distanceTo(b)
        if (px < 1.0 || lengthMm <= 0) return null
        return Calibration.of(CalibrationSource.KNOWN_DIMENSION, Homography.scale(lengthMm / px), "${Formats.number(lengthMm, 1)} mm reference")
    }

    /**
     * Scale-only calibration from the image resolution in pixels-per-inch.
     * One inch spans [ppi] pixels, so each pixel is 25.4 / ppi millimetres — the metric form of
     * cm = px * 2.54 / ppi. Only valid when the image pixels map 1:1 to the physical plane
     * (e.g. a flatbed scan, or a straight-on shot at a known resolution), not an arbitrary photo.
     */
    fun fromPpi(ppi: Double): Calibration? {
        if (ppi <= 0) return null
        return Calibration.of(CalibrationSource.IMAGE_PPI, Homography.scale(25.4 / ppi), "${Formats.number(ppi, 0)} PPI")
    }

    private fun rectangle(p: List<Point2>): List<Dimension> {
        if (p.size != 4) return polygon(p)
        val e = Polygons.edges(p)
        val s1 = (e[0] + e[2]) / 2
        val s2 = (e[1] + e[3]) / 2
        return listOf(
            Dimension("Length", max(s1, s2)),
            Dimension("Width", min(s1, s2)),
            Dimension("Diagonal", diagonal(p)),
            Dimension("Perimeter", e.sum()),
            Dimension("Area", Polygons.area(p), DimensionKind.AREA),
        )
    }

    private fun square(p: List<Point2>): List<Dimension> {
        if (p.size != 4) return polygon(p)
        val e = Polygons.edges(p)
        return listOf(
            Dimension("Side", e.average()),
            Dimension("Diagonal", diagonal(p)),
            Dimension("Perimeter", e.sum()),
            Dimension("Area", Polygons.area(p), DimensionKind.AREA),
        )
    }

    private fun diagonal(p: List<Point2>) = (p[0].distanceTo(p[2]) + p[1].distanceTo(p[3])) / 2

    private fun triangle(p: List<Point2>): List<Dimension> {
        if (p.size != 3) return polygon(p)
        val e = Polygons.edges(p)
        return listOf(
            Dimension("Side A", e[0]),
            Dimension("Side B", e[1]),
            Dimension("Side C", e[2]),
            Dimension("Perimeter", e.sum()),
            Dimension("Area", Polygons.area(p), DimensionKind.AREA),
        )
    }

    private fun circle(p: List<Point2>): List<Dimension> {
        val m = Polygons.principalMoments(p)
        // Mean of both principal diameters (each is 4·σ for a filled disc).
        val d = 2 * (sqrt(m.major) + sqrt(m.minor))
        val r = d / 2
        return listOf(
            Dimension("Diameter", d),
            Dimension("Radius", r),
            Dimension("Circumference", PI * d),
            Dimension("Area", PI * r * r, DimensionKind.AREA),
        )
    }

    private fun ellipse(p: List<Point2>): List<Dimension> {
        val m = Polygons.principalMoments(p)
        val a = 2 * sqrt(m.major) // semi-major
        val b = 2 * sqrt(m.minor) // semi-minor
        // Ramanujan's second approximation.
        val h = ((a - b) * (a - b)) / ((a + b) * (a + b))
        val perimeter = PI * (a + b) * (1 + 3 * h / (10 + sqrt(4 - 3 * h)))
        return listOf(
            Dimension("Major axis", 2 * a),
            Dimension("Minor axis", 2 * b),
            Dimension("Perimeter (approx.)", perimeter),
            Dimension("Area", PI * a * b, DimensionKind.AREA),
        )
    }

    private fun polygon(p: List<Point2>): List<Dimension> {
        val edges = Polygons.edges(p)
        return edges.mapIndexed { i, e -> Dimension("Edge ${i + 1}", e) } + listOf(
            Dimension("Perimeter", edges.sum()),
            Dimension("Area", Polygons.area(p), DimensionKind.AREA),
        )
    }

    private fun irregular(p: List<Point2>): List<Dimension> {
        val m = Polygons.principalMoments(p)
        val c = cos(m.angle)
        val s = sin(m.angle)
        val u = p.map { it.x * c + it.y * s }
        val v = p.map { -it.x * s + it.y * c }
        val w = u.max() - u.min()
        val h = v.max() - v.min()
        return listOf(
            Dimension("Width", max(w, h)),
            Dimension("Height", min(w, h)),
            Dimension("Perimeter", Polygons.perimeter(p)),
            Dimension("Area", Polygons.area(p), DimensionKind.AREA),
        )
    }
}
