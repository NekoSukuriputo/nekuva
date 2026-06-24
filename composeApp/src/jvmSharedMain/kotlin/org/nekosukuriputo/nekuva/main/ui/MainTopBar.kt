package org.nekosukuriputo.nekuva.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.app_update_available
import nekuva.composeapp.generated.resources.search_manga
import nekuva.composeapp.generated.resources.update_extensions
import nekuva.composeapp.generated.resources.voice_search
import org.jetbrains.compose.resources.stringResource

/** An overflow-menu entry. [checked] != null renders a trailing checkbox (Doki checkable item, e.g. incognito). */
data class OverflowItem(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val checked: Boolean? = null,
)

/** Shared top bar for the main tabs: a search field + a three-dot overflow menu (Doki-style). */
@Composable
fun MainTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    overflowItems: List<OverflowItem>,
    searchActive: Boolean = false,
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onCloseSearch: () -> Unit = {},
    // App-update notification icon (Doki: update badge near the search box) — shown only when a newer
    // release is available; tapping it opens the update dialog.
    appUpdateAvailable: Boolean = false,
    onAppUpdateClick: () -> Unit = {},
    // Extension-update icon (separate from the app-update one) — shown when a newer ext bundle exists;
    // tapping it opens Settings → About where the "Update extensions" row is dot-marked.
    extUpdateAvailable: Boolean = false,
    onExtUpdateClick: () -> Unit = {},
) {
    // Voice search (Doki VoiceInputContract): null on Desktop / when no recognizer → no mic button.
    val voiceSearch = org.nekosukuriputo.nekuva.core.os.rememberVoiceSearchLauncher { onQueryChange(it) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onSearchFocusChanged(it.isFocused) },
            placeholder = { Text(stringResource(Res.string.search_manga)) },
            leadingIcon = {
                if (searchActive) {
                    // Search session open: the icon becomes a back arrow that closes the panel.
                    IconButton(onClick = onCloseSearch) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            // Mic (Doki voice search) — only when a recognizer is available.
            trailingIcon = voiceSearch?.let { launch ->
                {
                    IconButton(onClick = launch) {
                        Icon(Icons.Default.Mic, contentDescription = stringResource(Res.string.voice_search))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        // App-update icon — appears only when a newer release exists (a dot badge marks it), like Doki.
        if (appUpdateAvailable) {
            IconButton(onClick = onAppUpdateClick) {
                BadgedBox(badge = { Badge() }) {
                    Icon(
                        Icons.Outlined.SystemUpdate,
                        contentDescription = stringResource(Res.string.app_update_available),
                    )
                }
            }
        }
        // Extension-update icon — appears only when a newer ext bundle exists; goes to About (dot on the row).
        if (extUpdateAvailable) {
            IconButton(onClick = onExtUpdateClick) {
                BadgedBox(badge = { Badge() }) {
                    Icon(
                        Icons.Outlined.Extension,
                        contentDescription = stringResource(Res.string.update_extensions),
                    )
                }
            }
        }
        Box {
            var expanded by remember { mutableStateOf(false) }
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (item in overflowItems) {
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        enabled = item.enabled,
                        // Checkable item (Doki incognito): show a checkbox; keep the menu open on toggle.
                        trailingIcon = item.checked?.let { checked ->
                            { androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null) }
                        },
                        onClick = {
                            if (item.checked == null) expanded = false
                            item.onClick()
                        },
                    )
                }
            }
        }
    }
}
