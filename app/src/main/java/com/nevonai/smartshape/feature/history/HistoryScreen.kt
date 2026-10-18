package com.nevonai.smartshape.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nevonai.smartshape.data.AppSettings
import com.nevonai.smartshape.data.Scan
import com.nevonai.smartshape.data.ScanFilter
import com.nevonai.smartshape.data.ScanRepository
import com.nevonai.smartshape.data.ScanSort
import com.nevonai.smartshape.data.SettingsRepository
import com.nevonai.smartshape.ui.components.EmptyState
import com.nevonai.smartshape.ui.components.ScanCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val scans: ScanRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val _filter = MutableStateFlow(ScanFilter())
    val filter: StateFlow<ScanFilter> = _filter

    val results: StateFlow<List<Scan>?> = _filter.debounce(150).flatMapLatest { scans.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setQuery(q: String) = _filter.update { it.copy(query = q) }
    fun setFavoritesOnly(on: Boolean) = _filter.update { it.copy(favoritesOnly = on) }
    fun setSort(sort: ScanSort) = _filter.update { it.copy(sort = sort) }
    fun toggleFavorite(scan: Scan) = viewModelScope.launch { scans.setFavorite(scan.id, !scan.favorite) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenScan: (Long) -> Unit, onBack: () -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                            ScanSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(if (s == filter.sort) "✓  ${s.label}" else "     ${s.label}") },
                                    onClick = { vm.setSort(s); sortMenu = false },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("Search titles, tags, shapes or scanned text") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (filter.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear") } },
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !filter.favoritesOnly, onClick = { vm.setFavoritesOnly(false) }, label = { Text("All") })
                FilterChip(selected = filter.favoritesOnly, onClick = { vm.setFavoritesOnly(true) }, label = { Text("Favourites") })
            }
            val list = results
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> EmptyState(
                    if (filter.query.isBlank()) "Nothing here yet" else "No matches",
                    if (filter.query.isBlank()) "Saved scans will appear here." else "Try another word, tag or shape name.",
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { scan ->
                        ScanCard(scan, settings.unit, onClick = { onOpenScan(scan.id) }, onFavorite = { vm.toggleFavorite(scan) })
                    }
                }
            }
        }
    }
}
