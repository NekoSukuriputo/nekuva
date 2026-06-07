package org.nekosukuriputo.nekuva

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import org.nekosukuriputo.nekuva.network.createHttpClient
import org.nekosukuriputo.nekuva.core.parser.AppMangaLoaderContext
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect

@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).components {
            add(KtorNetworkFetcherFactory(createHttpClient()))
        }.crossfade(true).build()
    }
    
    val parserContext = koinInject<AppMangaLoaderContext>()
    var dummyData by remember { mutableStateOf("Loading...") }
    
    LaunchedEffect(Unit) {
        dummyData = parserContext.fetchDummyData()
    }
    
    org.nekosukuriputo.nekuva.core.ui.theme.NekuvaTheme {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            org.nekosukuriputo.nekuva.core.nav.AppNavigation()
        }
    }
}

