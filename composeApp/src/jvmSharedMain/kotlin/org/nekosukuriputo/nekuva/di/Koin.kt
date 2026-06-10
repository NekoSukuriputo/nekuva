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
    single { LocalMangaRepository(get(), get(), kotlinx.coroutines.flow.MutableSharedFlow(), get(), get()) }
    factory { LocalListViewModel(get()) }
}

val exploreModule = module {
    single { org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository(get(), get()) }
    factory { org.nekosukuriputo.nekuva.explore.ui.ExploreViewModel(get(), get()) }
}

val remoteListModule = module {
    factory { params -> org.nekosukuriputo.nekuva.remotelist.ui.RemoteListViewModel(params.get(), get(), get()) }
}

val searchModule = module {
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
    factory { params -> org.nekosukuriputo.nekuva.reader.ui.ReaderViewModel(params.get(), get(), get(), get(), get(), get()) }
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

fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) =
    startKoin {
        appDeclaration()
        modules(appModule, platformModule, localModule, networkModule, prefsModule, exploreModule, remoteListModule, searchModule, detailsModule, readerModule, bookmarksModule, favouritesModule, historyModule)
    }




