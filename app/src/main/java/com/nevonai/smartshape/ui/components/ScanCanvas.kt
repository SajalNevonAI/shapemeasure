package com.nevonai.smartshape.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nevonai.smartshape.model.BoxF
import com.nevonai.smartshape.model.DetectedShape
import com.nevonai.smartshape.model.LengthUnit
import com.nevonai.smartshape.model.Point2
import com.nevonai.smartshape.model.Polygons
import com.nevonai.smartshape.model.ShapeMeasurement
import com.nevonai.smartshape.ui.theme.overlayColor
import kotlin.math.roundToInt

/**
 * Scan image with interactive overlays. All inputs are in image pixel coordinates; the canvas
 * keeps the image's aspect ratio so the mapping is a single uniform scale.
 */
@Composable
fun ScanCanvas(
    image: ImageBitmap,
    shapes: List<DetectedShape>,
    measurements: Map<Int, ShapeMeasurement>,
    unit: LengthUnit,
    modifier: Modifier = Modifier,
    selectedId: Int? = null,
    referenceId: Int? = null,
    markerOutline: List<Point2>? = null,
    highlights: List<BoxF> = emptyList(),
    activeHighlight: Int = -1,
    showShapes: Boolean = true,
    onSelect: (Int?) -> Unit = {},
    onMoveVertex: ((shapeId: Int, index: Int, point: Point2) -> Unit)? = null,
    /** While true, dragging draws a box and reports its four corners (image px) through [onMarked]. */
    markMode: Boolean = false,
    onMarked: (List<Point2>) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val handleRadiusPx = with(LocalDensity.current) { 22.dp.toPx() }
    var drag by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val currentShapes by rememberUpdatedState(shapes)
    val currentSelected by rememberUpdatedState(selectedId)
    var markStart by remember { mutableStateOf<Offset?>(null) }
    var markEnd by remember { mutableStateOf(Offset.Zero) }
    val pulse by animateFloatAsState(if (selectedId != null) 1f else 0f, tween(260), label = "select")

    Box(modifier.aspectRatio(image.width.toFloat() / image.height)) {
        Image(image, contentDescription = "Scanned image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(markMode) {
                    if (!markMode) return@pointerInput
                    detectDragGestures(
                        onDragStart = { markStart = it; markEnd = it },
                        onDragCancel = { markStart = null },
                        onDragEnd = {
                            val a = markStart ?: return@detectDragGestures
                            markStart = null
                            val s = size.width / image.width.toFloat()
                            val l = minOf(a.x, markEnd.x) / s; val r = maxOf(a.x, markEnd.x) / s
                            val t = minOf(a.y, markEnd.y) / s; val b = maxOf(a.y, markEnd.y) / s
                            // Ignore accidental taps: the box must be at least a few screen dp across.
                            if ((r - l) * s < handleRadiusPx || (b - t) * s < handleRadiusPx) return@detectDragGestures
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onMarked(listOf(Point2(l.toDouble(), t.toDouble()), Point2(r.toDouble(), t.toDouble()), Point2(r.toDouble(), b.toDouble()), Point2(l.toDouble(), b.toDouble())))
                        },
                    ) { change, amount ->
                        change.consume()
                        markEnd = Offset((markEnd.x + amount.x).coerceIn(0f, size.width.toFloat()), (markEnd.y + amount.y).coerceIn(0f, size.height.toFloat()))
                    }
                }
                .pointerInput(showShapes, markMode) {
                    if (!showShapes || markMode) return@pointerInput
                    detectTapGestures { tap ->
                        val s = size.width / image.width.toFloat()
                        val p = Point2(tap.x / s.toDouble(), tap.y / s.toDouble())
                        val hit = currentShapes
                            .filter { contains(it.points, p) }
                            .minByOrNull { Polygons.area(it.points) }
                        onSelect(if (hit?.id == currentSelected) null else hit?.id)
                    }
                }
                .pointerInput(showShapes, onMoveVertex != null, markMode) {
                    if (!showShapes || onMoveVertex == null || markMode) return@pointerInput
                    detectDragGestures(
                        onDragStart = { start ->
                            val s = size.width / image.width.toFloat()
                            val shape = currentShapes.firstOrNull { it.id == currentSelected && it.hasEditableVertices } ?: return@detectDragGestures
                            val idx = shape.points.indices.minByOrNull { (toOffset(shape.points[it], s) - start).getDistance() } ?: return@detectDragGestures
                            if ((toOffset(shape.points[idx], s) - start).getDistance() <= handleRadiusPx * 1.6f) {
                                drag = shape.id to idx
                                dragPos = start
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = { drag = null },
                        onDragCancel = { drag = null },
                    ) { change, amount ->
                        val d = drag ?: return@detectDragGestures
                        change.consume()
                        val s = size.width / image.width.toFloat()
                        dragPos = Offset(
                            (dragPos.x + amount.x).coerceIn(0f, size.width.toFloat()),
                            (dragPos.y + amount.y).coerceIn(0f, size.height.toFloat()),
                        )
                        onMoveVertex(d.first, d.second, Point2(dragPos.x / s.toDouble(), dragPos.y / s.toDouble()))
                    }
                },
        ) {
            val s = size.width / image.width
            highlights.forEachIndexed { i, b ->
                val tl = Offset(b.left * s, b.top * s)
                val sz = Size(b.width * s, b.height * s)
                val active = i == activeHighlight
                drawRoundRect(Color(0xFFFFD54F).copy(alpha = if (active) 0.45f else 0.28f), tl, sz, CornerRadius(3.dp.toPx()))
                if (active) drawRoundRect(Color(0xFFFFB300), tl, sz, CornerRadius(3.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            markerOutline?.let { drawOutline(it, s, Color(0xFF34D399), 2.dp.toPx(), dashed = true) }
            if (!showShapes) return@Canvas

            shapes.forEach { shape ->
                val color = shape.type.overlayColor()
                val selected = shape.id == selectedId
                val isReference = shape.id == referenceId
                val path = pathOf(shape.points, s)
                if (selected) drawPath(path, color.copy(alpha = 0.18f * pulse))
                drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke((if (selected) 5.dp else 3.5.dp).toPx(), join = StrokeJoin.Round))
                drawPath(
                    path, if (isReference) Color(0xFF34D399) else color,
                    style = Stroke(
                        (if (selected) 3.dp else 2.dp).toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round,
                        pathEffect = if (isReference) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
                    ),
                )
                if (selected && shape.hasEditableVertices) {
                    shape.points.forEach { p ->
                        val o = toOffset(p, s)
                        drawCircle(Color.White, 9.dp.toPx(), o)
                        drawCircle(color, 6.dp.toPx(), o)
                    }
                }
                val label = measurements[shape.id]?.headline(unit) ?: shape.type.label
                drawLabel(textMeasurer, if (isReference) "Reference" else label, toOffset(Polygons.centroid(shape.points), s), color, selected)
            }

            markStart?.let { a ->
                val tl = Offset(minOf(a.x, markEnd.x), minOf(a.y, markEnd.y))
                val sz = Size(kotlin.math.abs(markEnd.x - a.x), kotlin.math.abs(markEnd.y - a.y))
                drawRect(Color(0xFF34D399).copy(alpha = 0.18f), tl, sz)
                drawRect(Color(0xFF34D399), tl, sz, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                drawLoupe(image, markEnd, s)
            }
            drag?.let { drawLoupe(image, dragPos, s) }
        }
    }
}

private fun toOffset(p: Point2, s: Float) = Offset(p.x.toFloat() * s, p.y.toFloat() * s)

private fun pathOf(points: List<Point2>, s: Float) = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x.toFloat() * s, p.y.toFloat() * s) else lineTo(p.x.toFloat() * s, p.y.toFloat() * s) }
    close()
}

private fun DrawScope.drawOutline(points: List<Point2>, s: Float, color: Color, width: Float, dashed: Boolean) {
    drawPath(pathOf(points, s), color, style = Stroke(width, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null))
}

private fun DrawScope.drawLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
    selected: Boolean,
) {
    val layout = measurer.measure(text, TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
    val padH = 8.dp.toPx()
    val padV = 4.dp.toPx()
    val w = layout.size.width + 2 * padH
    val h = layout.size.height + 2 * padV
    // Keep the label on the image; if it's wider/taller than the image itself, pin it to the edge.
    val tl = Offset((center.x - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f)), (center.y - h / 2).coerceIn(0f, (size.height - h).coerceAtLeast(0f)))
    drawRoundRect(if (selected) color else Color(0xCC111320), tl, Size(w, h), CornerRadius(h / 2))
    if (!selected) drawRoundRect(color, tl, Size(w, h), CornerRadius(h / 2), style = Stroke(1.dp.toPx()))
    drawText(layout, topLeft = tl + Offset(padH, padV))
}

/** Magnified view of the image under the finger while a corner is dragged. */
private fun DrawScope.drawLoupe(image: ImageBitmap, at: Offset, s: Float) {
    val r = 56.dp.toPx()
    val zoom = 3f
    val center = Offset(if (at.x < size.width / 2) size.width - r - 12.dp.toPx() else r + 12.dp.toPx(), r + 12.dp.toPx())
    val srcHalf = r / (zoom * s)
    val src = IntOffset(((at.x / s) - srcHalf).roundToInt(), ((at.y / s) - srcHalf).roundToInt())
    val srcSize = IntSize((2 * srcHalf).roundToInt(), (2 * srcHalf).roundToInt())
    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
    drawCircle(Color.Black.copy(alpha = 0.5f), r + 3.dp.toPx(), center)
    clipPath(circle) {
        drawRect(Color.Black, center - Offset(r, r), Size(2 * r, 2 * r))
        val clampedX = src.x.coerceIn(0, (image.width - srcSize.width).coerceAtLeast(0))
        val clampedY = src.y.coerceIn(0, (image.height - srcSize.height).coerceAtLeast(0))
        val shift = Offset((src.x - clampedX) * s * zoom, (src.y - clampedY) * s * zoom)
        drawImage(
            image, IntOffset(clampedX, clampedY), srcSize,
            dstOffset = IntOffset((center.x - r - shift.x).roundToInt(), (center.y - r - shift.y).roundToInt()),
            dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
        )
    }
    drawCircle(Color.White, r, center, style = Stroke(2.dp.toPx()))
    val c = 8.dp.toPx()
    drawLine(Color.White, center - Offset(c, 0f), center + Offset(c, 0f), 1.5.dp.toPx())
    drawLine(Color.White, center - Offset(0f, c), center + Offset(0f, c), 1.5.dp.toPx())
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
