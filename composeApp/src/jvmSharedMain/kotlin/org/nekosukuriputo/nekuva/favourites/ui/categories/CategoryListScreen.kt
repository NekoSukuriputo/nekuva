package org.nekosukuriputo.nekuva.favourites.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    onBackClick: () -> Unit
) {
    val viewModel = koinViewModel<CategoryListViewModel>()
    val categories by viewModel.categories.collectAsState()
    val isAllFavouritesVisible by viewModel.isAllFavouritesVisible.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<FavouriteCategory?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.favourites_categories)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingCategory = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.all_favourites)) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.toggleAllFavouritesVisibility() }) {
                            Icon(
                                if (isAllFavouritesVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility"
                            )
                        }
                    }
                )
                Divider()
            }
            
            itemsIndexed(categories, key = { _, it -> it.id }) { index, category ->
                ListItem(
                    headlineContent = { Text(category.title) },
                    trailingContent = {
                        Row {
                            if (index > 0) {
                                IconButton(onClick = {
                                    val newList = categories.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index - 1]
                                    newList[index - 1] = temp
                                    viewModel.reorderCategories(newList.map { it.id })
                                }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                }
                            }
                            if (index < categories.size - 1) {
                                IconButton(onClick = {
                                    val newList = categories.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index + 1]
                                    newList[index + 1] = temp
                                    viewModel.reorderCategories(newList.map { it.id })
                                }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                }
                            }
                            IconButton(onClick = {
                                editingCategory = category
                                showDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = {
                                viewModel.deleteCategory(category.id)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                )
                Divider()
            }
        }

        if (showDialog) {
            CategoryDialog(
                initialName = editingCategory?.title ?: "",
                onDismiss = { showDialog = false },
                onConfirm = { name ->
                    showDialog = false
                    if (editingCategory == null) {
                        viewModel.createCategory(name)
                    } else {
                        viewModel.updateCategory(editingCategory!!, name)
                    }
                }
            )
        }
    }
}

@Composable
fun CategoryDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialName.isEmpty()) Res.string.create_category else Res.string.rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.enter_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
