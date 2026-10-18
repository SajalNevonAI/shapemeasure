package com.nevonai.smartshape.feature.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nevonai.smartshape.data.AppSettings
import com.nevonai.smartshape.data.Scan
import com.nevonai.smartshape.data.ScanFilter
import com.nevonai.smartshape.data.ScanRepository
import com.nevonai.smartshape.data.SettingsRepository
import com.nevonai.smartshape.ui.components.EmptyState
import com.nevonai.smartshape.ui.components.ScanCard
import com.nevonai.smartshape.ui.theme.BrandGradient
import com.nevonai.smartshape.util.BitmapIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scans: ScanRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val all: StateFlow<List<Scan>?> = scans.observe(ScanFilter()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun toggleFavorite(scan: Scan) = viewModelScope.launch { scans.setFavorite(scan.id, !scan.favorite) }

    suspend fun import(context: Context, uri: Uri): String? = BitmapIO.importToCache(context, uri)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onImported: (String) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onOpenScan: (Long) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val all by vm.all.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            val path = vm.import(context, uri)
            importing = false
            path?.let(onImported)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("SmartShape") },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, "Scan history") }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                HeroCard(
                    importing = importing,
                    onScan = onScan,
                    onImport = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            val scans = all
            if (scans == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (scans.isEmpty()) {
                item { EmptyState("No scans yet", "Your measured objects and scanned text will appear here.") }
            } else {
                item { StatsRow(scans, Modifier.padding(horizontal = 16.dp)) }
                item { Section("Recent", onSeeAll = onHistory) { ScanRow(scans.take(10), settings, onOpenScan, vm::toggleFavorite) } }
                val favorites = scans.filter { it.favorite }
                if (favorites.isNotEmpty()) {
                    item { Section("Favourites", onSeeAll = onHistory) { ScanRow(favorites.take(10), settings, onOpenScan, vm::toggleFavorite) } }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(importing: Boolean, onScan: () -> Unit, onImport: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(BrandGradient))
            .padding(24.dp),
    ) {
        Column {
            Text("Measure anything flat", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "Detect shapes, get calibrated dimensions and search printed text – all on device.",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onScan,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF312E81)),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Rounded.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
                OutlinedButton(
                    onClick = onImport,
                    enabled = !importing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    if (importing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }
    }
}

@Composable
private fun StatsRow(scans: List<Scan>, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Scans", scans.size.toString(), Modifier.weight(1f))
        Stat("Shapes", scans.sumOf { it.shapes.size }.toString(), Modifier.weight(1f))
        Stat("Calibrated", scans.count { it.calibration.isCalibrated }.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String, onSeeAll: () -> Unit, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) {
                Text("See all")
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
            }
        }
        content()
    }
}

@Composable
private fun ScanRow(scans: List<Scan>, settings: AppSettings, onOpen: (Long) -> Unit, onFavorite: (Scan) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(scans, key = { it.id }) { scan ->
            ScanCard(scan, settings.unit, onClick = { onOpen(scan.id) }, modifier = Modifier.width(200.dp), onFavorite = { onFavorite(scan) })
        }
    }
}
