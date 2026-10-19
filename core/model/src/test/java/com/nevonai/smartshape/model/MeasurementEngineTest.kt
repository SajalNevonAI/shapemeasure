package com.nevonai.smartshape.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MeasurementEngineTest {

    private fun dims(m: ShapeMeasurement) = m.dimensions.associate { it.label to it.value }

    private fun ellipsePoints(cx: Double, cy: Double, a: Double, b: Double, rot: Double = 0.0, n: Int = 720) =
        List(n) {
            val t = 2 * PI * it / n
            val x = a * cos(t)
            val y = b * sin(t)
            Point2(cx + x * cos(rot) - y * sin(rot), cy + x * sin(rot) + y * cos(rot))
        }

    @Test
    fun rectangleWithScaleCalibration() {
        val rect = DetectedShape(1, ShapeType.RECTANGLE, 1f, listOf(Point2(0.0, 0.0), Point2(200.0, 0.0), Point2(200.0, 100.0), Point2(0.0, 100.0)))
        val cal = Calibration.of(CalibrationSource.KNOWN_DIMENSION, Homography.scale(0.5))
        val d = dims(MeasurementEngine.measure(rect, cal))
        assertEquals(100.0, d.getValue("Length"), 1e-9)
        assertEquals(50.0, d.getValue("Width"), 1e-9)
        assertEquals(300.0, d.getValue("Perimeter"), 1e-9)
        assertEquals(5000.0, d.getValue("Area"), 1e-9)
    }

    @Test
    fun uncalibratedIsFlaggedAsPixels() {
        val tri = DetectedShape(1, ShapeType.TRIANGLE, 1f, listOf(Point2(0.0, 0.0), Point2(30.0, 0.0), Point2(0.0, 40.0)))
        val m = MeasurementEngine.measure(tri, Calibration.None)
        assertFalse(m.calibrated)
        assertEquals(120.0, dims(m).getValue("Perimeter"), 1e-9)
        assertEquals("120 px", Formats.dimension(m.dimensions.first { it.label == "Perimeter" }, LengthUnit.MM, m.calibrated))
    }

    @Test
    fun circleDiameterFromSampledContour() {
        val c = DetectedShape(1, ShapeType.CIRCLE, 1f, ellipsePoints(500.0, 400.0, 150.0, 150.0))
        val d = dims(MeasurementEngine.measure(c, Calibration.of(CalibrationSource.KNOWN_DIMENSION, Homography.scale(0.2))))
        assertEquals(60.0, d.getValue("Diameter"), 0.01)
        assertEquals(30.0, d.getValue("Radius"), 0.01)
    }

    @Test
    fun rotatedEllipseAxes() {
        val e = DetectedShape(1, ShapeType.ELLIPSE, 1f, ellipsePoints(0.0, 0.0, 80.0, 30.0, rot = 0.7))
        val d = dims(MeasurementEngine.measure(e, Calibration.of(CalibrationSource.KNOWN_DIMENSION, Homography.scale(1.0))))
        assertEquals(160.0, d.getValue("Major axis"), 0.05)
        assertEquals(60.0, d.getValue("Minor axis"), 0.05)
    }

    @Test
    fun referenceCardCorrectsPerspective() {
        // A card photographed at an angle: a trapezoid in the image.
        val cardPx = listOf(Point2(110.0, 100.0), Point2(490.0, 120.0), Point2(520.0, 360.0), Point2(80.0, 340.0))
        val h = Homography.fromReferenceRect(cardPx, 85.60, 53.98)
        assertNotNull(h)
        val cal = Calibration.of(CalibrationSource.REFERENCE_OBJECT, h!!)
        assertTrue(cal.correctsPerspective)
        val d = dims(MeasurementEngine.measure(DetectedShape(1, ShapeType.RECTANGLE, 1f, cardPx), cal))
        assertEquals(85.60, d.getValue("Length"), 1e-6)
        assertEquals(53.98, d.getValue("Width"), 1e-6)
    }

    @Test
    fun knownLengthCalibration() {
        val cal = MeasurementEngine.knownLength(Point2(0.0, 0.0), Point2(300.0, 400.0), 100.0)!!
        assertEquals(50.0, MeasurementEngine.distance(Point2(0.0, 0.0), Point2(150.0, 200.0), cal), 1e-9)
    }

    @Test
    fun unitConversion() {
        val d = Dimension("Length", 25.4)
        assertEquals("1.00 in", Formats.dimension(d, LengthUnit.INCH, true))
        assertEquals("2.54 cm", Formats.dimension(d, LengthUnit.CM, true))
        assertEquals("6.45 in²", Formats.dimension(Dimension("Area", 4161.28, DimensionKind.AREA), LengthUnit.INCH, true))
    }

    @Test
    fun orderQuadStartsTopLeftClockwise() {
        val q = orderQuad(listOf(Point2(10.0, 90.0), Point2(90.0, 10.0), Point2(10.0, 10.0), Point2(90.0, 90.0)))
        assertEquals(listOf(Point2(10.0, 10.0), Point2(90.0, 10.0), Point2(90.0, 90.0), Point2(10.0, 90.0)), q)
    }

    @Test
    fun cameraDistanceUsesPinholeScale() {
        // 4 mm lens on a 6.4 mm-wide sensor, 4000 px image: focal = 2500 px. At 300 mm, 1 px = 0.12 mm.
        val cal = MeasurementEngine.fromCameraDistance(300.0, 4.0 / 6.4, 4000)!!
        val rect = DetectedShape(1, ShapeType.RECTANGLE, 1f, listOf(Point2(0.0, 0.0), Point2(500.0, 0.0), Point2(500.0, 250.0), Point2(0.0, 250.0)))
        val d = dims(MeasurementEngine.measure(rect, cal))
        assertEquals(60.0, d.getValue("Length"), 1e-9)
        assertEquals(30.0, d.getValue("Width"), 1e-9)
    }

    @Test
    fun ppiMatchesCentimetreFormula() {
        // cm = px * 2.54 / ppi: 300 px at 300 PPI = 2.54 cm.
        val cal = MeasurementEngine.fromPpi(300.0)!!
        assertEquals(25.4, MeasurementEngine.distance(Point2(0.0, 0.0), Point2(300.0, 0.0), cal), 1e-9)
    }

    @Test
    fun primaryPicksShapeNearestCentre() {
        fun sq(x: Double, y: Double, s: Double, id: Int) =
            DetectedShape(id, ShapeType.SQUARE, 1f, listOf(Point2(x, y), Point2(x + s, y), Point2(x + s, y + s), Point2(x, y + s)))
        val big = sq(0.0, 800.0, 400.0, 1) // large, near the bottom edge
        val centred = sq(450.0, 450.0, 100.0, 2)
        assertEquals(2, listOf(big, centred).primary(1000, 1000)?.id)
    }

    @Test
    fun s23UltraFocusCorrectionMatchesBoxPhotos() {
        // Reported autofocus distance -> distance implied by the 75 mm box in the same photo.
        val c = FocusCorrection.forDevice("SM-S918B")
        assertEquals(164.0, c.trueDistanceMm(148.0)!!, 6.0)
        assertEquals(314.0, c.trueDistanceMm(242.0)!!, 15.0)
        assertEquals(232.0, c.trueDistanceMm(205.0)!!, 20.0)
        assertEquals(250.0, FocusCorrection.forDevice("Pixel 9").trueDistanceMm(250.0)!!, 1e-9)
    }

    @Test
    fun edgeHandleMovesWholeSideAlongItsNormal() {
        val box = DetectedShape(1, ShapeType.RECTANGLE, 1f, listOf(Point2(0.0, 0.0), Point2(100.0, 0.0), Point2(100.0, 50.0), Point2(0.0, 50.0)))
        val right = ShapeEditing.handles(box).filterIsInstance<EdgeHandle>().first { it.index == 1 }
        // Diagonal drag: only the horizontal part moves the right side; the box stays a rectangle.
        val p = ShapeEditing.drag(box, right, Point2(20.0, 7.0))
        assertEquals(listOf(Point2(0.0, 0.0), Point2(120.0, 0.0), Point2(120.0, 50.0), Point2(0.0, 50.0)), p)
    }

    @Test
    fun circleSideHandleResizesAndRetypes() {
        val c = DetectedShape(1, ShapeType.CIRCLE, 1f, ShapeEditing.ellipseIn(0.0, 0.0, 100.0, 100.0))
        val right = ShapeEditing.handles(c).filterIsInstance<SideHandle>().first { it.side == Side.RIGHT }
        val p = ShapeEditing.drag(c, right, Point2(50.0, 0.0))
        assertEquals(150.0, p.maxOf { it.x } - p.minOf { it.x }, 1e-6)
        assertEquals(ShapeType.ELLIPSE, ShapeEditing.retype(ShapeType.CIRCLE, p))
        val moved = ShapeEditing.drag(c, ShapeEditing.handles(c).filterIsInstance<MoveHandle>().single(), Point2(10.0, 20.0))
        assertEquals(60.0, moved.map { it.x }.average(), 1e-6)
        assertEquals(70.0, moved.map { it.y }.average(), 1e-6)
    }
}
