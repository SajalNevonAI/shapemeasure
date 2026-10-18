package com.nevonai.smartshape.ui.components

import com.nevonai.smartshape.model.Dimension
import com.nevonai.smartshape.model.Formats
import com.nevonai.smartshape.model.LengthUnit
import com.nevonai.smartshape.model.ShapeMeasurement
import com.nevonai.smartshape.model.ShapeType

/** One-line headline for a shape, e.g. "85.6 × 54.0 mm" or "⌀ 42.0 mm". */
fun ShapeMeasurement.headline(unit: LengthUnit): String {
    fun v(label: String) = dimensions.firstOrNull { it.label == label }
    fun f(d: Dimension?) = d?.let { Formats.dimension(it, unit, calibrated) } ?: "–"
    fun pair(a: String, b: String): String {
        val x = v(a) ?: return f(v(b))
        val y = v(b) ?: return f(x)
        val sym = if (calibrated) unit.symbol else "px"
        val dec = if (calibrated) unit.decimals else 0
        val conv = { d: Dimension -> if (calibrated) unit.fromMm(d.value) else d.value }
        return "${Formats.number(conv(x), dec)} × ${Formats.number(conv(y), dec)} $sym"
    }
    return when (type) {
        ShapeType.RECTANGLE -> pair("Length", "Width")
        ShapeType.SQUARE -> f(v("Side"))
        ShapeType.CIRCLE -> "⌀ ${f(v("Diameter"))}"
        ShapeType.ELLIPSE -> pair("Major axis", "Minor axis")
        ShapeType.IRREGULAR -> pair("Width", "Height")
        ShapeType.TRIANGLE, ShapeType.POLYGON -> "P ${f(v("Perimeter"))}"
    }
}
