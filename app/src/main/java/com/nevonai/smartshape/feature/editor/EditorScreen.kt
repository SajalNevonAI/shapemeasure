package com.nevonai.smartshape.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import com.nevonai.smartshape.model.BoxF
import com.nevonai.smartshape.model.ShapeEditing
import com.nevonai.smartshape.ui.components.ZoomableContent
import com.nevonai.smartshape.ui.components.rememberZoomState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nevonai.smartshape.cv.MarkerDetector
import com.nevonai.smartshape.model.Calibration
import com.nevonai.smartshape.model.Formats
import com.nevonai.smartshape.model.MeasurementEngine
import com.nevonai.smartshape.model.ReferencePreset
import com.nevonai.smartshape.ui.components.CalibrationStatusCard
import com.nevonai.smartshape.ui.components.EmptyState
import com.nevonai.smartshape.ui.components.OcrPanel
import com.nevonai.smartshape.ui.components.ScanCanvas
import com.nevonai.smartshape.ui.components.ShapeMeasurementCard
import com.nevonai.smartshape.ui.components.UnitSelector
import com.nevonai.smartshape.util.Exporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(onSaved: (Long) -> Unit, onBack: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var showCalibration by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is EditorEvent.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSaved(e.id)
                }
                is EditorEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.scanId != null) "Edit scan" else "Review scan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    Button(onClick = { showSave = true }, enabled = state.image != null && !state.saving, modifier = Modifier.padding(end = 8.dp)) {
                        Text(if (state.saving) "Saving…" else "Save")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val image = state.image
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Detecting shapes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            image == null -> EmptyState("Couldn't open image", state.error ?: "Unknown error", Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                val imageBitmap = remember(image) { image.asImageBitmap() }
                val zoom = rememberZoomState()
                val textTab = state.tab == EditorTab.TEXT
                // Boxes to highlight in the Text tab: search matches, plus the tapped line (drawn last, as active).
                val textBoxes = if (textTab) state.matches.flatMap { it.boxes } + listOfNotNull(state.focusBox) else emptyList()
                val activeBox = if (state.focusBox != null) textBoxes.lastIndex else activeBoxIndex(state)
                val zoomTarget = state.focusBox ?: state.matches.getOrNull(state.activeMatch)?.boxes?.let(::union)
                LaunchedEffect(textTab, zoomTarget) { if (textTab && zoomTarget != null) zoom.focusOn(zoomTarget, image.width) }
                Box(Modifier.fillMaxWidth().weight(0.52f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                  ZoomableContent(zoom, enabled = textTab, modifier = Modifier.fillMaxSize()) {
                    ScanCanvas(
                        image = imageBitmap,
                        shapes = state.shapes,
                        measurements = state.measurements,
                        unit = state.unit,
                        selectedId = state.selectedId,
                        referenceId = state.referenceShapeId,
                        markerOutline = state.markerCorners,
                        highlights = textBoxes,
                        activeHighlight = activeBox,
                        showShapes = state.tab == EditorTab.MEASURE,
                        onSelect = vm::select,
                        onEditShape = vm::editShape,
                        markMode = state.marking,
                        markRound = state.markRound,
                        onMarked = vm::markSticker,
                    )
                  }
                    if (textTab) {
                        Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallFloatingActionButton(onClick = { zoom.zoomBy(1.6f) }) { Icon(Icons.Rounded.ZoomIn, "Zoom in") }
                            SmallFloatingActionButton(onClick = { zoom.zoomBy(1 / 1.6f) }) { Icon(Icons.Rounded.ZoomOut, "Zoom out") }
                            if (zoom.isZoomed) SmallFloatingActionButton(onClick = zoom::reset) { Icon(Icons.Rounded.FitScreen, "Fit photo") }
                        }
                    }
                    if (state.marking && !textTab) MarkingBar(round = state.markRound, onRound = vm::setMarkRound, onCancel = vm::cancelMarking, modifier = Modifier.align(Alignment.TopCenter))
                }
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(state.tab == EditorTab.MEASURE, onClick = { vm.selectTab(EditorTab.MEASURE) }, text = { Text("Measure (${state.shapes.size})") })
                    Tab(state.tab == EditorTab.TEXT, onClick = { vm.selectTab(EditorTab.TEXT) }, text = { Text("Text") })
                }
                when (state.tab) {
                    EditorTab.MEASURE -> MeasurePanel(state, vm, onCalibrate = { showCalibration = true }, modifier = Modifier.weight(0.48f))
                    EditorTab.TEXT -> LazyColumn(Modifier.weight(0.48f).padding(horizontal = 16.dp)) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            OcrPanel(
                                ocr = state.ocr, running = state.ocrRunning, query = state.query, matches = state.matches,
                                activeMatch = state.activeMatch, onQuery = vm::search, onStep = vm::nextMatch,
                                onCopy = { Exporter.copyToClipboard(context, "Scanned text", state.ocr?.text.orEmpty()) },
                                onRerun = vm::runOcr,
                                focusedLine = state.focusBox,
                                onLineClick = { line -> vm.focusText(if (line.box == state.focusBox) null else line.box) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCalibration) CalibrationSheet(state, vm, onDismiss = { showCalibration = false })
    if (showSave) SaveDialog(
        initialTitle = state.title,
        initialTags = state.tags.joinToString(", "),
        onDismiss = { showSave = false },
        onSave = { title, tags ->
            showSave = false
            vm.setTitle(title)
            vm.setTags(tags.split(',').map { it.trim() }.filter { it.isNotEmpty() })
            vm.save()
        },
    )
}

/** Compact two-line bar shown while marking: instruction, then shape choice and Cancel. Fits narrow screens. */
@Composable
private fun MarkingBar(round: Boolean, onRound: (Boolean) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = 0.72f),
        modifier = modifier.padding(6.dp).widthIn(max = 420.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            Text(
                if (round) "Drag over the circle" else "Drag a box over the sticker",
                color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveContainerColor = Color.Transparent, inactiveContentColor = Color.White,
                    activeBorderColor = MaterialTheme.colorScheme.primary, inactiveBorderColor = Color.White.copy(alpha = 0.5f),
                )
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f).height(36.dp)) {
                    SegmentedButton(!round, { onRound(false) }, SegmentedButtonDefaults.itemShape(0, 2), colors = colors, icon = {}) { Text("Box", maxLines = 1) }
                    SegmentedButton(round, { onRound(true) }, SegmentedButtonDefaults.itemShape(1, 2), colors = colors, icon = {}) { Text("Circle", maxLines = 1) }
                }
                TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.inversePrimary, maxLines = 1) }
            }
        }
    }
}

private fun union(boxes: List<BoxF>): BoxF? = if (boxes.isEmpty()) null else BoxF(
    boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom },
)

private fun activeBoxIndex(state: EditorState): Int {
    if (state.matches.isEmpty()) return -1
    return state.matches.take(state.activeMatch).sumOf { it.boxes.size }
}

@Composable
private fun MeasurePanel(state: EditorState, vm: EditorViewModel, onCalibrate: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            CalibrationStatusCard(state.calibration, onCalibrate = onCalibrate, onClear = vm::clearCalibration)
        }
        item { UnitSelector(state.unit, vm::setUnit, enabled = state.calibration.isCalibrated) }
        item {
            OutlinedButton(onClick = vm::startMarking, enabled = !state.marking, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CropFree, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.shapes.isEmpty()) "Mark the sticker yourself" else "Wrong sticker? Mark it yourself")
            }
        }
        if (state.shapes.isEmpty()) {
            item { EmptyState("No sticker found", "Tap \"Mark the sticker yourself\" and drag a box over it, or retake the photo in better light, centred on the sticker.") }
        }
        itemsIndexed(state.shapes, key = { _, s -> s.id }) { i, shape ->
            val m = state.measurements[shape.id] ?: return@itemsIndexed
            ShapeMeasurementCard(
                index = i + 1,
                measurement = m,
                unit = state.unit,
                expanded = shape.id == state.selectedId,
                confidence = shape.confidence,
                isReference = shape.id == state.referenceShapeId,
                onClick = { vm.select(if (shape.id == state.selectedId) null else shape.id) },
            ) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onCalibrate, label = { Text("Use as reference") }, leadingIcon = { Icon(Icons.Rounded.Straighten, null) })
                    AssistChip(onClick = { vm.deleteShape(shape.id) }, label = { Text("Remove") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                }
                run {
                    Text(
                        when {
                            ShapeEditing.isRound(shape.type) -> "Drag the side dots to resize, the centre to move."
                            shape.hasEditableVertices -> "Drag corners or side dots to fix the edges, the centre to move."
                            else -> "Drag the centre to move."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationSheet(state: EditorState, vm: EditorViewModel, onDismiss: () -> Unit) {
    val selected = state.selected
    var knownValue by remember { mutableStateOf("") }
    var customW by remember { mutableStateOf("") }
    var customH by remember { mutableStateOf("") }
    var ppiValue by remember { mutableStateOf("") }
    var distanceValue by remember { mutableStateOf("") }
    val quad = selected?.points?.size == 4

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calibrate measurements", style = MaterialTheme.typography.titleLarge)
            Text(
                "A phone camera can't know real size on its own. Place a known reference flat on the same surface as the object.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Fixed camera distance (no reference)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Distance from the phone's camera lens to the object's top surface, phone held flat and parallel to it. A stand or fixed mount gives repeatable results.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    distanceValue, { distanceValue = it }, Modifier.weight(1f),
                    label = { Text("Distance (cm)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val cm = distanceValue.toDoubleOrNull()
                TextButton(enabled = cm != null && cm > 0, onClick = { vm.useCameraDistance(cm!!); onDismiss() }) { Text("Apply") }
            }

            ListItem(
                headlineContent = { Text("SmartShape calibration marker") },
                supportingContent = {
                    Text(
                        if (state.markerCalibration != null) "Marker found in this photo · corrects perspective"
                        else "Not found. Print the ${Formats.number(MarkerDetector.MARKER_SIDE_MM, 0)} mm marker and include it in the photo.",
                    )
                },
                leadingContent = { Icon(Icons.Rounded.QrCode2, null) },
                trailingContent = {
                    TextButton(onClick = { vm.useMarker(); onDismiss() }, enabled = state.markerCalibration != null) { Text("Use") }
                },
            )

            Text("Reference object", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                if (quad) "Selected ${selected!!.type.label.lowercase()} will be used as the reference."
                else "Select a four-cornered shape (e.g. a bank card) on the image first.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReferencePreset.entries.forEach { p ->
                ListItem(
                    headlineContent = { Text(p.label) },
                    supportingContent = { Text("${Formats.number(p.widthMm, 2)} × ${Formats.number(p.heightMm, 2)} mm") },
                    leadingContent = { Icon(Icons.Rounded.CropFree, null) },
                    trailingContent = {
                        TextButton(enabled = quad, onClick = { vm.useReference(selected!!.id, p.widthMm, p.heightMm, p.label); onDismiss() }) { Text("Use") }
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(customW, { customW = it }, Modifier.weight(1f), label = { Text("Width mm") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(customH, { customH = it }, Modifier.weight(1f), label = { Text("Height mm") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                val w = customW.toDoubleOrNull()
                val h = customH.toDoubleOrNull()
                TextButton(enabled = quad && w != null && h != null && w > 0 && h > 0, onClick = { vm.useReference(selected!!.id, w!!, h!!, "Custom reference"); onDismiss() }) { Text("Use") }
            }

            Text("Known dimension", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            val firstLabel = selected?.let { MeasurementEngine.measure(it, Calibration.None).dimensions.firstOrNull()?.label }
            Text(
                if (selected != null) "Enter the real ${firstLabel?.lowercase()} of the selected ${selected.type.label.lowercase()}. Scale only – shoot straight on."
                else "Select a shape whose size you know.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    knownValue, { knownValue = it }, Modifier.weight(1f),
                    label = { Text("${firstLabel ?: "Length"} (${state.unit.symbol})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = selected != null,
                )
                val v = knownValue.toDoubleOrNull()
                TextButton(enabled = selected != null && v != null && v > 0, onClick = { vm.useKnownDimension(selected!!.id, v!!); onDismiss() }) { Text("Apply") }
            }

            Text("Image resolution (PPI)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Enter the image resolution to size by cm = px × 2.54 / PPI. Best for scans or straight-on shots at a known resolution.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    ppiValue, { ppiValue = it }, Modifier.weight(1f),
                    label = { Text("PPI") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val ppi = ppiValue.toDoubleOrNull()
                TextButton(enabled = ppi != null && ppi > 0, onClick = { vm.usePpi(ppi!!); onDismiss() }) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun SaveDialog(initialTitle: String, initialTags: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    var tags by remember { mutableStateOf(initialTags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save scan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(title, tags) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
