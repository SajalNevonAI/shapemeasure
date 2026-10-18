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
}
