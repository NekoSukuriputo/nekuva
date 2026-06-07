package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (uiState is ReaderUiState.Success) {
                        Text(
                            text = (uiState as ReaderUiState.Success).chapter.name,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else {
                        Text("Reader")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ReaderUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is ReaderUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.retry() }, modifier = Modifier.padding(paddingValues))
            is ReaderUiState.Success -> {
                ReaderContent(
                    pages = state.pages,
                    paddingValues = paddingValues
                )
            }
        }
    }
}

@Composable
fun ReaderContent(
    pages: List<MangaPage>,
    paddingValues: PaddingValues
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
        ) {
            itemsIndexed(pages) { index, page ->
                ReaderPageItem(page = page, index = index)
            }
        }
        org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
            state = listState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
        )
    }
}

@Composable
fun ReaderPageItem(page: MangaPage, index: Int) {
    var retryHash by remember { mutableIntStateOf(0) }

    key(retryHash) {
        SubcomposeAsyncImage(
            model = page.url,
            contentDescription = "Page $index",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            },
            error = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(Res.string.error), color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { retryHash++ }) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }
        )
    }
}
