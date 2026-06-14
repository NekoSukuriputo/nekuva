package org.nekosukuriputo.nekuva.settings.ui.appearance

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.NavItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader

/** Localized label for a [NavItem] (Doki nav_main section names). */
@Composable
private fun navItemLabel(item: NavItem): String = when (item) {
    NavItem.HISTORY -> stringResource(Res.string.history)
    NavItem.FAVORITES -> stringResource(Res.string.favourites)
    NavItem.EXPLORE -> stringResource(Res.string.explore)
    NavItem.FEED -> stringResource(Res.string.feed)
    NavItem.LOCAL -> stringResource(Res.string.local_storage)
}

/**
 * Configure the main-screen bottom-nav sections (Doki `NavConfigFragment`): reorder the active items
 * and add/remove sections. At least one section must stay active. Writes [AppSettings.mainNavItems] live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavConfigScreen(onBackClick: () -> Unit) {
    val settings = koinInject<AppSettings>()
    var active by remember { mutableStateOf(settings.mainNavItems) }
    val available = remember(active) { NavItem.entries.filter { it !in active } }

    fun persist(new: List<NavItem>) {
        if (new.isEmpty()) return // keep at least one section (Doki)
        active = new
        settings.mainNavItems = new
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.main_screen_sections)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            itemsIndexed(active, key = { _, it -> it.name }) { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(navItemLabel(item), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (index > 0) persist(active.toMutableList().also { it.add(index - 1, it.removeAt(index)) }) }, enabled = index > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                    }
                    IconButton(onClick = { if (index < active.lastIndex) persist(active.toMutableList().also { it.add(index + 1, it.removeAt(index)) }) }, enabled = index < active.lastIndex) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null)
                    }
                    IconButton(onClick = { persist(active - item) }, enabled = active.size > 1) {
                        Icon(Icons.Filled.RemoveCircleOutline, contentDescription = null)
                    }
                }
            }
            if (available.isNotEmpty()) {
                item { SettingsCategoryHeader(stringResource(Res.string.add)) }
                itemsIndexed(available, key = { _, it -> "add_${it.name}" }) { _, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(navItemLabel(item), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { persist(active + item) }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
