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
                Box(Modifier.fillMaxWidth().weight(0.52f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    ScanCanvas(
                        image = imageBitmap,
                        shapes = state.shapes,
                        measurements = state.measurements,
                        unit = state.unit,
                        selectedId = state.selectedId,
                        referenceId = state.referenceShapeId,
                        markerOutline = state.markerCorners,
                        highlights = if (state.tab == EditorTab.TEXT) state.matches.flatMap { it.boxes } else emptyList(),
                        activeHighlight = activeBoxIndex(state),
                        showShapes = state.tab == EditorTab.MEASURE,
                        onSelect = vm::select,
                        onMoveVertex = vm::moveVertex,
                    )
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
        if (state.shapes.isEmpty()) {
            item { EmptyState("No shapes found", "Try better lighting, a plain contrasting background, or retake the photo square to the object.") }
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
                if (shape.hasEditableVertices) {
                    Text("Drag the corner handles on the image to adjust.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val quad = selected?.points?.size == 4

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calibrate measurements", style = MaterialTheme.typography.titleLarge)
            Text(
                "A phone camera can't know real size on its own. Place a known reference flat on the same surface as the object.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
