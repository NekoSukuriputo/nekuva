package org.nekosukuriputo.nekuva.di

import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.core.KoinApplication
import org.nekosukuriputo.nekuva.core.parser.AppMangaLoaderContext

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
    factory { params -> org.nekosukuriputo.nekuva.remotelist.ui.RemoteListViewModel(params.get(), get()) }
}

val appModule = module {
    single { AppMangaLoaderContext(get(), get()) }
    single<org.nekosukuriputo.nekuva.parsers.MangaLoaderContext> { get<AppMangaLoaderContext>() }
    single { org.nekosukuriputo.nekuva.core.cache.MemoryContentCache() }
    single { org.nekosukuriputo.nekuva.core.parser.MirrorSwitcher(get(), get()) }
    single { org.nekosukuriputo.nekuva.core.parser.MangaRepository.Factory(get(), get(), get()) }
    single { 
        org.nekosukuriputo.nekuva.core.db.getRoomDatabase(
            org.nekosukuriputo.nekuva.core.db.getDatabaseBuilder()
        )
    }
}

val prefsModule = module {
    single { org.nekosukuriputo.nekuva.core.prefs.AppSettings(get()) }
}

fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) =
    startKoin {
        appDeclaration()
        modules(appModule, platformModule, localModule, networkModule, prefsModule, exploreModule, remoteListModule)
    }




