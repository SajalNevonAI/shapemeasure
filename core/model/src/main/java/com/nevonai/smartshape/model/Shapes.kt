package com.nevonai.smartshape.model

import kotlinx.serialization.Serializable

@Serializable
enum class ShapeType(val label: String) {
    RECTANGLE("Rectangle"),
    SQUARE("Square"),
    CIRCLE("Circle"),
    TRIANGLE("Triangle"),
    ELLIPSE("Ellipse"),
    POLYGON("Polygon"),
    IRREGULAR("Irregular"),
}

/**
 * A shape found in an image. [points] are polygon vertices for straight-edged shapes and
 * evenly sampled contour points for curved / irregular shapes, in image pixel coordinates.
 */
@Serializable
data class DetectedShape(
    val id: Int,
    val type: ShapeType,
    val confidence: Float,
    val points: List<Point2>,
) {
    /** Straight-edged shapes expose editable corners; curved outlines are edited as a whole. */
    val hasEditableVertices get() = type in setOf(ShapeType.RECTANGLE, ShapeType.SQUARE, ShapeType.TRIANGLE, ShapeType.POLYGON)
}

@Serializable
enum class CalibrationSource(val label: String) {
    NONE("Uncalibrated"),
    ARUCO_MARKER("Calibration marker"),
    REFERENCE_OBJECT("Reference object"),
    KNOWN_DIMENSION("Known dimension"),
    IMAGE_PPI("Image resolution (PPI)"),
}

/** Pixel → millimetre mapping for one image. [matrix] is null when uncalibrated. */
@Serializable
data class Calibration(
    val source: CalibrationSource = CalibrationSource.NONE,
    val matrix: List<Double>? = null,
    val note: String? = null,
) {
    val isCalibrated get() = matrix != null && source != CalibrationSource.NONE

    /** Scale-only calibrations do not correct perspective, so they are less reliable off-axis. */
    val correctsPerspective get() = source == CalibrationSource.ARUCO_MARKER || source == CalibrationSource.REFERENCE_OBJECT

    fun homography(): Homography? = matrix?.let { Homography(it.toDoubleArray()) }

    companion object {
        val None = Calibration()
        fun of(source: CalibrationSource, h: Homography, note: String? = null) =
            Calibration(source, h.m.toList(), note)
    }
}

/** Common physical references users can place next to the object. */
enum class ReferencePreset(val label: String, val widthMm: Double, val heightMm: Double) {
    BANK_CARD("Bank / ID card (ISO ID-1)", 85.60, 53.98),
    A4("A4 sheet", 297.0, 210.0),
    LETTER("US Letter sheet", 279.4, 215.9),
}

@Serializable
enum class DimensionKind { LINEAR, AREA }

/** A single measured value in millimetres (mm² for area), or pixels when uncalibrated. */
@Serializable
data class Dimension(val label: String, val value: Double, val kind: DimensionKind = DimensionKind.LINEAR)

@Serializable
data class ShapeMeasurement(
    val shapeId: Int,
    val type: ShapeType,
    val calibrated: Boolean,
    val dimensions: List<Dimension>,
)
