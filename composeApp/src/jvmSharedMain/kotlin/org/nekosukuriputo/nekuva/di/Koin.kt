package org.nekosukuriputo.nekuva.di

import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.core.KoinApplication
import org.nekosukuriputo.nekuva.core.parser.AppMangaLoaderContext

import org.nekosukuriputo.nekuva.favourites.ui.container.FavouritesViewModel
import org.nekosukuriputo.nekuva.favourites.ui.categories.CategoryListViewModel
import org.nekosukuriputo.nekuva.local.domain.MangaLock
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.local.data.index.LocalMangaIndex
import org.nekosukuriputo.nekuva.local.ui.LocalListViewModel

val localModule = module {
    single { LocalMangaIndex() }
    single { MangaLock() }
    // Shared "local storage changed" bus: emitted on download finish / delete, observed by the Local list.
    single<kotlinx.coroutines.flow.MutableSharedFlow<org.nekosukuriputo.nekuva.local.domain.model.LocalManga?>> {
        kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 16)
    }
    single { LocalMangaRepository(get(), get(), get(), get(), get()) }
    factory { LocalListViewModel(get(), get(), get()) }
}

val exploreModule = module {
    single { org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository(get(), get()) }
    factory { org.nekosukuriputo.nekuva.explore.ui.ExploreViewModel(get(), get()) }
}

val remoteListModule = module {
    single { org.nekosukuriputo.nekuva.filter.data.SavedFiltersRepository(get()) }
    factory { params -> org.nekosukuriputo.nekuva.remotelist.ui.RemoteListViewModel(params.get(), get(), get(), get()) }
}

val searchModule = module {
    single { org.nekosukuriputo.nekuva.search.domain.MangaSearchRepository(get(), get(), get(), get()) }
    factory { org.nekosukuriputo.nekuva.search.ui.suggestion.SearchSuggestionViewModel(get(), get(), get()) }
    factory { params ->
        org.nekosukuriputo.nekuva.search.ui.GlobalSearchViewModel(
            params.get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
}

val detailsModule = module {
	factory { parameters ->
		org.nekosukuriputo.nekuva.details.ui.DetailsViewModel(
			parameters.get(),
			get(),
			get(),
			get(),
			get(),
			get(),
			get()
		)
	}
}

val appModule = module {
    single { AppMangaLoaderContext(get(), get()) }
    single<org.nekosukuriputo.nekuva.parsers.MangaLoaderContext> { get<AppMangaLoaderContext>() }
    single { org.nekosukuriputo.nekuva.core.cache.MemoryContentCache() }
    single { org.nekosukuriputo.nekuva.core.parser.MirrorSwitcher(get(), get()) }
    single { org.nekosukuriputo.nekuva.core.parser.MangaRepository.Factory(get(), get(), get()) }
    single { org.nekosukuriputo.nekuva.core.parser.MangaDataRepository(get()) }
    single { 
        org.nekosukuriputo.nekuva.core.db.getRoomDatabase(
            org.nekosukuriputo.nekuva.core.db.getDatabaseBuilder()
        )
    }
}

val prefsModule = module {
    single { org.nekosukuriputo.nekuva.core.prefs.AppSettings(get()) }
}

val readerModule = module {
    single { org.nekosukuriputo.nekuva.reader.data.TapGridSettings(get()) }
    factory { org.nekosukuriputo.nekuva.settings.ui.reader.TapGridConfigViewModel(get()) }
    single { org.nekosukuriputo.nekuva.reader.domain.PageSaveHelper(get(), get(), get()) }
    single { org.nekosukuriputo.nekuva.reader.domain.DetectReaderModeUseCase(get(), get(), get(), get()) }
    factory { params -> org.nekosukuriputo.nekuva.reader.ui.ReaderViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

val bookmarksModule = module {
    single { org.nekosukuriputo.nekuva.bookmarks.domain.BookmarksRepository(get()) }
    factory { org.nekosukuriputo.nekuva.bookmarks.ui.BookmarksViewModel(get()) }
}

val favouritesModule = module {
    single { org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository(get()) }
    factory { params -> org.nekosukuriputo.nekuva.favourites.ui.list.FavouritesListViewModel(params.get(), get()) }
    factory { FavouritesViewModel(get(), get()) }
    factory { CategoryListViewModel(get(), get()) }
}

val historyModule = module {
    single { org.nekosukuriputo.nekuva.history.data.HistoryRepository(get(), get(), get()) }
    factory { org.nekosukuriputo.nekuva.history.domain.HistoryUpdateUseCase(get()) }
    factory { org.nekosukuriputo.nekuva.history.ui.HistoryViewModel(get()) }
}

val downloadModule = module {
    // Canonical binding for the page-image proxy (also used by the reader/Coil path when wired).
    single<org.nekosukuriputo.nekuva.core.network.imageproxy.ImageProxyInterceptor> {
        org.nekosukuriputo.nekuva.core.network.imageproxy.RealImageProxyInterceptor(get())
    }
    single {
        org.nekosukuriputo.nekuva.download.domain.DownloadManager(
            get(), // OkHttpClient
            get(), // ImageProxyInterceptor
            get(), // LocalMangaRepository
            get(), // MangaLock
            get(), // MangaDataRepository
            get(), // MangaRepository.Factory
            get(), // AppSettings
            get(), // MutableSharedFlow<LocalManga?> (local storage changes)
        )
    }
    factory { params ->
        org.nekosukuriputo.nekuva.download.ui.dialog.DownloadDialogViewModel(
            params.get(), get(), get(), get(), get(), get(), get(),
        )
    }
    factory { org.nekosukuriputo.nekuva.download.ui.list.DownloadsViewModel(get()) }
}

val settingsModule = module {
    factory { org.nekosukuriputo.nekuva.settings.ui.appearance.AppearanceViewModel(get()) }
    factory { org.nekosukuriputo.nekuva.settings.ui.downloads.DownloadsSettingsViewModel(get(), get()) }
    factory { org.nekosukuriputo.nekuva.settings.ui.network.StorageNetworkViewModel(get(), get()) }
    factory { org.nekosukuriputo.nekuva.settings.ui.sources.SourcesCatalogViewModel(get(), get()) }
    factory { org.nekosukuriputo.nekuva.settings.ui.sources.SourcesManageViewModel(get(), get()) }
    factory { params -> org.nekosukuriputo.nekuva.settings.ui.sources.SourceSettingsViewModel(params.get(), get(), get()) }
}

val trackerModule = module {
    single { org.nekosukuriputo.nekuva.tracker.domain.TrackingRepository(get(), get()) }
    single { org.nekosukuriputo.nekuva.tracker.domain.CheckNewChaptersUseCase(get(), get(), get(), get()) }
    factory { org.nekosukuriputo.nekuva.tracker.ui.feed.FeedViewModel(get(), get()) }
}

val scrobblingModule = module {
    // Shikimori reference service: dedicated OkHttp (token interceptor) + per-service token storage.
    single {
        val storage = org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblerStorage(
            get(), org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService.SHIKIMORI,
        )
        val client = get<okhttp3.OkHttpClient>().newBuilder()
            .addInterceptor(org.nekosukuriputo.nekuva.scrobbling.shikimori.data.ShikimoriInterceptor(storage))
            .build()
        org.nekosukuriputo.nekuva.scrobbling.shikimori.data.ShikimoriRepository(client, storage, get())
    }
    single { org.nekosukuriputo.nekuva.scrobbling.shikimori.domain.ShikimoriScrobbler(get(), get(), get()) }
    single {
        org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerManager(
            listOf(get<org.nekosukuriputo.nekuva.scrobbling.shikimori.domain.ShikimoriScrobbler>()),
        )
    }
    // Shared (single) so the Services screen + the OAuth screen see the same login state.
    single { org.nekosukuriputo.nekuva.settings.ui.services.ScrobblerConfigViewModel(get()) }
}

val backupsModule = module {
    single { org.nekosukuriputo.nekuva.backups.data.BackupRepository(get()) }
    factory { org.nekosukuriputo.nekuva.backups.ui.BackupViewModel(get()) }
}

val syncModule = module {
    single { org.nekosukuriputo.nekuva.sync.data.SyncSettings(get()) }
    // Base client (no auth) used for login + the 401-refresh inside the authenticator.
    single { org.nekosukuriputo.nekuva.sync.data.SyncAuthApi(get<okhttp3.OkHttpClient>()) }
    single {
        val settings = get<org.nekosukuriputo.nekuva.sync.data.SyncSettings>()
        val client = get<okhttp3.OkHttpClient>().newBuilder()
            .addInterceptor(org.nekosukuriputo.nekuva.sync.data.SyncInterceptor(settings))
            .authenticator(org.nekosukuriputo.nekuva.sync.data.SyncAuthenticator(settings, get()))
            .build()
        org.nekosukuriputo.nekuva.sync.domain.SyncHelper(client, get(), settings)
    }
    single { org.nekosukuriputo.nekuva.sync.domain.SyncManager(get(), get(), get()) }
    factory { org.nekosukuriputo.nekuva.sync.ui.SyncViewModel(get(), get()) }
}

fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) =
    startKoin {
        appDeclaration()
        modules(appModule, platformModule, localModule, networkModule, prefsModule, exploreModule, remoteListModule, searchModule, detailsModule, readerModule, bookmarksModule, favouritesModule, historyModule, downloadModule, settingsModule, backupsModule, trackerModule, scrobblingModule, syncModule)
    }




