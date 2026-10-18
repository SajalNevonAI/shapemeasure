package com.nevonai.smartshape.model

import java.util.Locale

enum class LengthUnit(val symbol: String, val mmPerUnit: Double) {
    MM("mm", 1.0),
    CM("cm", 10.0),
    INCH("in", 25.4),
    FOOT("ft", 304.8);

    fun fromMm(mm: Double) = mm / mmPerUnit
    fun fromMm2(mm2: Double) = mm2 / (mmPerUnit * mmPerUnit)
    fun toMm(value: Double) = value * mmPerUnit

    val decimals
        get() = when (this) {
            MM -> 1
            CM, INCH -> 2
            FOOT -> 3
        }
}

object Formats {
    fun dimension(d: Dimension, unit: LengthUnit, calibrated: Boolean): String {
        if (!calibrated) {
            return when (d.kind) {
                DimensionKind.LINEAR -> "${number(d.value, 0)} px"
                DimensionKind.AREA -> "${number(d.value, 0)} px²"
            }
        }
        return when (d.kind) {
            DimensionKind.LINEAR -> "${number(unit.fromMm(d.value), unit.decimals)} ${unit.symbol}"
            DimensionKind.AREA -> "${number(unit.fromMm2(d.value), unit.decimals)} ${unit.symbol}²"
        }
    }

    fun number(v: Double, decimals: Int): String = String.format(Locale.US, "%,.${decimals}f", v)

    /** Plain-text summary used for sharing and export. */
    fun summary(title: String, measurements: List<ShapeMeasurement>, calibration: Calibration, unit: LengthUnit): String =
        buildString {
            appendLine(title)
            appendLine("Calibration: ${calibration.source.label}")
            if (!calibration.isCalibrated) appendLine("Note: values are image pixels, not physical dimensions.")
            measurements.forEachIndexed { i, m ->
                appendLine()
                appendLine("${i + 1}. ${m.type.label}")
                m.dimensions.forEach { appendLine("   ${it.label}: ${dimension(it, unit, m.calibrated)}") }
            }
        }.trimEnd()
}
