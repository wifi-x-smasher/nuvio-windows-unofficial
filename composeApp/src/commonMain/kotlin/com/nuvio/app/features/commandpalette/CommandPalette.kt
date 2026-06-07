package com.nuvio.app.features.commandpalette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.nuvio.app.AccountSettingsRoute
import com.nuvio.app.AddonsSettingsRoute
import com.nuvio.app.AppScreenTab
import com.nuvio.app.CollectionsRoute
import com.nuvio.app.DetailRoute
import com.nuvio.app.DownloadsSettingsRoute
import com.nuvio.app.HomescreenSettingsRoute
import com.nuvio.app.PluginsSettingsRoute
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.search.SearchRepository
import kotlinx.coroutines.delay

private data class PaletteEntry(
    val label: String,
    val hint: String,
    val keywords: String = "",
    val action: () -> Unit,
)

/**
 * Spotlight/Raycast-style command palette (Ctrl+K). Renders inside `App()` so it can drive the real
 * navigation. Jump to tabs/settings or live-search titles, fully keyboard-driven (↑/↓/Enter/Esc).
 */
@Composable
fun CommandPalette(
    navController: NavHostController,
    onSelectTab: (AppScreenTab) -> Unit,
) {
    val visible by CommandPaletteController.visible.collectAsState()
    if (!visible) return

    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val addonsUiState by AddonRepository.uiState.collectAsState()
    val searchUiState by SearchRepository.uiState.collectAsState()

    val normalizedQuery = query.trim()

    val staticEntries = remember(navController, onSelectTab) {
        listOf(
            PaletteEntry("Home", "Go to", "home tab") { onSelectTab(AppScreenTab.Home) },
            PaletteEntry("Search", "Go to", "search find") { onSelectTab(AppScreenTab.Search) },
            PaletteEntry("Library", "Go to", "library saved bookmarks") { onSelectTab(AppScreenTab.Library) },
            PaletteEntry("Settings", "Go to", "settings preferences options") { onSelectTab(AppScreenTab.Settings) },
            PaletteEntry("Downloads", "Open", "downloads offline files") { navController.navigate(DownloadsSettingsRoute) },
            PaletteEntry("Add-ons", "Open", "addons sources catalogs") { navController.navigate(AddonsSettingsRoute) },
            PaletteEntry("Plugins", "Open", "plugins scrapers providers") { navController.navigate(PluginsSettingsRoute) },
            PaletteEntry("Collections", "Open", "collections lists folders") { navController.navigate(CollectionsRoute) },
            PaletteEntry("Account", "Open", "account profile sync login") { navController.navigate(AccountSettingsRoute) },
            PaletteEntry("Home screen settings", "Settings", "home catalogs layout rows") {
                navController.navigate(HomescreenSettingsRoute)
            },
        )
    }

    LaunchedEffect(normalizedQuery, addonsUiState.addons) {
        if (normalizedQuery.length >= 2) {
            delay(250)
            SearchRepository.search(normalizedQuery, addonsUiState.addons)
        }
    }

    val filteredStatic = if (normalizedQuery.isBlank()) {
        staticEntries
    } else {
        staticEntries.filter {
            it.label.contains(normalizedQuery, ignoreCase = true) ||
                it.keywords.contains(normalizedQuery, ignoreCase = true)
        }
    }
    val contentEntries = if (normalizedQuery.length < 2) {
        emptyList()
    } else {
        searchUiState.sections
            .flatMap { it.items }
            .distinctBy { "${it.type}:${it.id}" }
            .take(8)
            .map { item ->
                PaletteEntry(
                    label = item.name,
                    hint = item.type.replaceFirstChar { c -> c.uppercase() },
                ) {
                    navController.navigate(DetailRoute(type = item.type, id = item.id))
                }
            }
    }
    val entries = filteredStatic + contentEntries
    val safeIndex = if (entries.isEmpty()) 0 else selectedIndex.coerceIn(0, entries.size - 1)

    fun execute(index: Int) {
        val entry = entries.getOrNull(index) ?: return
        CommandPaletteController.hide()
        entry.action()
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { CommandPaletteController.hide() } }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        CommandPaletteController.hide()
                        true
                    }
                    Key.DirectionDown -> {
                        if (entries.isNotEmpty()) selectedIndex = (safeIndex + 1) % entries.size
                        true
                    }
                    Key.DirectionUp -> {
                        if (entries.isNotEmpty()) selectedIndex = (safeIndex - 1 + entries.size) % entries.size
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        execute(safeIndex)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 96.dp)
                .width(560.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp,
        ) {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedIndex = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search titles, jump to a screen…") },
                    singleLine = true,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    itemsIndexed(entries) { index, entry ->
                        val isSelected = index == safeIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable { execute(index) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.label,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = entry.hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
