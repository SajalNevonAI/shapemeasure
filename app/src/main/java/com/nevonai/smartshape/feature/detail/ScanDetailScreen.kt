package com.nevonai.smartshape.feature.detail

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nevonai.smartshape.data.AppSettings
import com.nevonai.smartshape.data.Scan
import com.nevonai.smartshape.data.ScanDraft
import com.nevonai.smartshape.data.ScanRepository
import com.nevonai.smartshape.data.SettingsRepository
import com.nevonai.smartshape.model.LengthUnit
import com.nevonai.smartshape.ui.components.CalibrationStatusCard
import com.nevonai.smartshape.ui.components.EmptyState
import com.nevonai.smartshape.ui.components.ScanCanvas
import com.nevonai.smartshape.ui.components.ShapeMeasurementCard
import com.nevonai.smartshape.ui.components.UnitSelector
import com.nevonai.smartshape.ui.navigation.ScanDetail
import com.nevonai.smartshape.util.BitmapIO
import com.nevonai.smartshape.util.Exporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ScanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scans: ScanRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val id = savedStateHandle.toRoute<ScanDetail>().scanId
    val scan: StateFlow<Scan?> = scans.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Unit for this view; starts from the app default and can be switched without rescanning. */
    val unit = MutableStateFlow<LengthUnit?>(null)

    fun toggleFavorite() = viewModelScope.launch { scan.value?.let { scans.setFavorite(it.id, !it.favorite) } }
    fun rename(title: String) = viewModelScope.launch { if (title.isNotBlank()) scans.rename(id, title) }
    fun delete() = viewModelScope.launch { scans.delete(id) }
    fun restore() = viewModelScope.launch { scans.restore(id) }
    suspend fun duplicate(): Long? = scans.duplicate(id)

    fun saveNotes(notes: String, tags: String) = viewModelScope.launch {
        val s = scan.value ?: return@launch
        scans.update(s.id, ScanDraft(s.title, s.calibration, s.shapes, s.ocr, notes, tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, s.collection))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDetailScreen(onEdit: (Long, Boolean) -> Unit, onBack: () -> Unit, vm: ScanDetailViewModel = hiltViewModel()) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unitOverride by vm.unit.collectAsStateWithLifecycle()
    val unit = unitOverride ?: settings.unit
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var deleted by remember { mutableStateOf(false) }

    val s = scan
    val image by produceState<Bitmap?>(null, s?.imagePath) { value = s?.imagePath?.let { BitmapIO.load(it) } }

    LaunchedEffect(deleted) {
        if (!deleted) return@LaunchedEffect
        val r = snackbar.showSnackbar("Scan moved to Recently deleted", actionLabel = "Undo", withDismissAction = true)
        if (r == SnackbarResult.ActionPerformed) {
            vm.restore()
            deleted = false
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (s != null) {
                        IconButton(onClick = vm::toggleFavorite) {
                            Icon(if (s.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite")
                        }
                        IconButton(onClick = { Exporter.shareImage(context, s, unit) }) { Icon(Icons.Rounded.Share, "Share") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                                DropdownMenuItem(text = { Text("Notes & tags") }, onClick = { menu = false; editingNotes = true })
                                DropdownMenuItem(text = { Text("Export PDF") }, onClick = { menu = false; scope.launch { Exporter.sharePdf(context, s, unit) } })
                                DropdownMenuItem(text = { Text("Export text (TXT)") }, enabled = !s.ocr?.text.isNullOrBlank(), onClick = { menu = false; scope.launch { Exporter.shareTxt(context, s) } })
                                DropdownMenuItem(text = { Text("Share measurement summary") }, onClick = { menu = false; Exporter.shareText(context, s.title, Exporter.summary(s, unit)) })
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                    menu = false
                                    scope.launch { if (vm.duplicate() != null) snackbar.showSnackbar("Duplicated") }
                                })
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = {
                                    menu = false
                                    vm.delete()
                                    deleted = true
                                })
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val bmp = image
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (bmp != null) {
                    val ib = remember(bmp) { bmp.asImageBitmap() }
                    ScanCanvas(
                        image = ib, shapes = s.shapes, measurements = s.measurements.associateBy { it.shapeId }, unit = unit,
                        selectedId = selected, modifier = Modifier.fillMaxWidth(), onSelect = { selected = it },
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            item {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.createdAt)) +
                        if (s.tags.isNotEmpty()) "  ·  " + s.tags.joinToString(" ") { "#$it" } else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onEdit(s.id, false) }, label = { Text("Edit measurements") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    AssistChip(onClick = { onEdit(s.id, true) }, enabled = !s.ocr?.text.isNullOrBlank(), label = { Text("Search text") })
                }
            }
            item { CalibrationStatusCard(s.calibration) }
            if (s.calibration.isCalibrated) item { UnitSelector(unit, { vm.unit.value = it }) }
            if (s.shapes.isEmpty()) item { EmptyState("No shapes", "This scan has no measured shapes.") }
            itemsIndexed(s.measurements, key = { _, m -> m.shapeId }) { i, m ->
                ShapeMeasurementCard(
                    index = i + 1, measurement = m, unit = unit, expanded = selected == m.shapeId,
                    confidence = null, isReference = false, onClick = { selected = if (selected == m.shapeId) null else m.shapeId },
                )
            }
            if (s.notes.isNotBlank()) item {
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                Text(s.notes, style = MaterialTheme.typography.bodyMedium)
            }
            val text = s.ocr?.text.orEmpty()
            if (text.isNotBlank()) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recognised text", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { Exporter.copyToClipboard(context, "Scanned text", text) }) { Icon(Icons.Rounded.ContentCopy, "Copy text") }
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (renaming && s != null) {
        var title by remember { mutableStateOf(s.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename scan") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { vm.rename(title); renaming = false }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (editingNotes && s != null) {
        var notes by remember { mutableStateOf(s.notes) }
        var tags by remember { mutableStateOf(s.tags.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { editingNotes = false },
            title = { Text("Notes & tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { vm.saveNotes(notes, tags); editingNotes = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingNotes = false }) { Text("Cancel") } },
        )
    }
}
