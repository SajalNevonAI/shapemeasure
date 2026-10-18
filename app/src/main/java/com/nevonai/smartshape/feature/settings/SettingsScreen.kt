package com.nevonai.smartshape.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nevonai.smartshape.cv.MarkerDetector
import com.nevonai.smartshape.cv.ShapeDetector
import com.nevonai.smartshape.data.AppSettings
import com.nevonai.smartshape.data.Scan
import com.nevonai.smartshape.data.ScanRepository
import com.nevonai.smartshape.data.SettingsRepository
import com.nevonai.smartshape.data.ThemeMode
import com.nevonai.smartshape.model.Formats
import com.nevonai.smartshape.ui.components.UnitSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val scans: ScanRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val deleted: StateFlow<List<Scan>> = scans.observeDeleted().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setUnit(u: com.nevonai.smartshape.model.LengthUnit) = viewModelScope.launch { repo.setUnit(u) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { repo.setTheme(t) }
    fun setHaptics(on: Boolean) = viewModelScope.launch { repo.setHaptics(on) }
    fun setGrid(on: Boolean) = viewModelScope.launch { repo.setGrid(on) }
    fun restore(id: Long) = viewModelScope.launch { scans.restore(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Header("Measurement") }
            item { Text("Default unit", style = MaterialTheme.typography.bodyMedium) }
            item { UnitSelector(s.unit, vm::setUnit) }
            item {
                ListItem(
                    headlineContent = { Text("Calibration marker") },
                    supportingContent = {
                        Text("Print an ArUco 4×4 marker (id 0–49) with a ${Formats.number(MarkerDetector.MARKER_SIDE_MM, 0)} mm black square at 100% scale. Check it with a ruler before use.")
                    },
                )
            }
            item { Header("Camera") }
            item {
                ListItem(
                    headlineContent = { Text("Alignment grid") },
                    trailingContent = { Switch(s.showGrid, vm::setGrid) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Haptic feedback") },
                    trailingContent = { Switch(s.haptics, vm::setHaptics) },
                )
            }
            item { Header("Appearance") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = s.themeMode == t,
                            onClick = { vm.setTheme(t) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
            item { Header("Recently deleted") }
            if (deleted.isEmpty()) item { Text("Nothing deleted. Deleted scans are kept for 30 days.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(deleted, key = { it.id }) { scan ->
                ListItem(
                    headlineContent = { Text(scan.title) },
                    trailingContent = { TextButton(onClick = { vm.restore(scan.id) }) { Text("Restore") } },
                )
            }
            item { Header("About") }
            item {
                ListItem(
                    headlineContent = { Text("SmartShape AI 0.1.0") },
                    supportingContent = { Text("On-device processing · OpenCV ${runCatching { ShapeDetector.openCvVersion }.getOrDefault("?")} · ML Kit text recognition") },
                )
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}
