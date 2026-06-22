package org.nekosukuriputo.nekuva.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.nekosukuriputo.nekuva.local.data.DesktopLocalStorageManager
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager

actual val platformModule: Module = module {
    single<com.russhwolf.settings.ObservableSettings> {
        // CrashSafe: a restored/foreign backup can leave a key with a mismatched type; the wrapper
        // returns the default instead of throwing ClassCastException on a mismatched getter.
        org.nekosukuriputo.nekuva.core.prefs.CrashSafeSettings(
            com.russhwolf.settings.PreferencesSettings(java.util.prefs.Preferences.userRoot().node("Nekuva")),
        )
    }
    single { org.nekosukuriputo.nekuva.core.os.NetworkState(get()) }
    single<LocalStorageManager> { DesktopLocalStorageManager(get()) }
    single<org.nekosukuriputo.nekuva.reader.domain.PagePersister> {
        org.nekosukuriputo.nekuva.reader.data.DesktopPagePersister(get())
    }
}
