package org.nekosukuriputo.nekuva.explore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.model.MangaSourceInfo

@Composable
fun ExploreScreen(
	viewModel: ExploreViewModel = koinViewModel(),
	onSourceClick: (String) -> Unit,
) {
	val uiState by viewModel.uiState.collectAsState()

	when (val state = uiState) {
		is ExploreUiState.Loading -> {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				CircularProgressIndicator()
			}
		}
		is ExploreUiState.Empty -> {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				Text("No sources found")
			}
		}
        is ExploreUiState.Success -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.sources) { source ->
                    SourceItem(
                        source = source,
                        onClick = {
                            val id = (source.mangaSource as? org.nekosukuriputo.nekuva.parsers.model.MangaParserSource)?.name ?: ""
                            onSourceClick(id)
                        }
                    )
                }
            }
        }
	}
}

@Composable
private fun SourceItem(
	source: MangaSourceInfo,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column {
			Text(text = source.mangaSource.name)
		}
	}
}
