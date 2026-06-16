package org.nekosukuriputo.nekuva.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.nekosukuriputo.nekuva.local.data.AndroidLocalStorageManager
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager

actual val platformModule: Module = module {
    single<com.russhwolf.settings.ObservableSettings> { 
        val context: android.content.Context = get()
        val prefs = context.getSharedPreferences("nekuva_prefs", android.content.Context.MODE_PRIVATE)
        com.russhwolf.settings.SharedPreferencesSettings(prefs)
    }
    single {
        val context: android.content.Context = get()
        org.nekosukuriputo.nekuva.core.os.NetworkState(
            context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager,
            get(),
        )
    }
    single<LocalStorageManager> { AndroidLocalStorageManager(get(), get()) }
    single<org.nekosukuriputo.nekuva.reader.domain.PagePersister> {
        org.nekosukuriputo.nekuva.reader.data.AndroidPagePersister(get(), get())
    }
}
