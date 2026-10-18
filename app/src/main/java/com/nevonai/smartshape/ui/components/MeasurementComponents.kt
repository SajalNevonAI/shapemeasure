package com.nevonai.smartshape.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nevonai.smartshape.model.Calibration
import com.nevonai.smartshape.model.Formats
import com.nevonai.smartshape.model.LengthUnit
import com.nevonai.smartshape.model.ShapeMeasurement
import com.nevonai.smartshape.ui.theme.MeasurementValueStyle
import com.nevonai.smartshape.ui.theme.overlayColor

private val CalibratedGreen = Color(0xFF10B981)
private val UncalibratedAmber = Color(0xFFF59E0B)

/** Always-visible calibration status: the app never presents pixel values as physical sizes. */
@Composable
fun CalibrationStatusCard(calibration: Calibration, modifier: Modifier = Modifier, onCalibrate: (() -> Unit)? = null, onClear: (() -> Unit)? = null) {
    val ok = calibration.isCalibrated
    val accent = if (ok) CalibratedGreen else UncalibratedAmber
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null, tint = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (ok) "Calibrated · ${calibration.source.label}" else "Not calibrated", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        !ok -> "Values are in image pixels. Add a reference to get real-world size."
                        calibration.correctsPerspective -> calibration.note?.let { "$it · perspective corrected" } ?: "Perspective corrected"
                        else -> (calibration.note?.let { "$it · " } ?: "") + "keep the camera square to the surface"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ok && onClear != null) TextButton(onClick = onClear) { Text("Clear") }
            if (onCalibrate != null) TextButton(onClick = onCalibrate) { Text(if (ok) "Change" else "Calibrate") }
        }
    }
}

@Composable
fun UnitSelector(unit: LengthUnit, onUnit: (LengthUnit) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        LengthUnit.entries.forEachIndexed { i, u ->
            SegmentedButton(
                selected = u == unit,
                onClick = { onUnit(u) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, LengthUnit.entries.size),
                label = { Text(u.symbol) },
            )
        }
    }
}

@Composable
fun ShapeMeasurementCard(
    index: Int,
    measurement: ShapeMeasurement,
    unit: LengthUnit,
    expanded: Boolean,
    confidence: Float?,
    isReference: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    val color = measurement.type.overlayColor()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(color.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("$index", style = MaterialTheme.typography.labelMedium, color = color)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isReference) "${measurement.type.label} · Reference" else measurement.type.label,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    confidence?.let {
                        Text("Detection confidence ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(measurement.headline(unit), style = MeasurementValueStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (expanded) {
                Spacer(Modifier.padding(top = 10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                measurement.dimensions.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(d.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formats.dimension(d, unit, measurement.calibrated), style = MeasurementValueStyle)
                    }
                }
                actions()
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Straighten, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.padding(top = 16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(top = 4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { Spacer(Modifier.padding(top = 16.dp)); it() }
    }
}
