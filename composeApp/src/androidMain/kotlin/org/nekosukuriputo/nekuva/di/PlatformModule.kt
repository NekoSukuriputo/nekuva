package org.nekosukuriputo.nekuva.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.nekosukuriputo.nekuva.local.data.AndroidLocalStorageManager
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager

actual val platformModule: Module = module {
    single<LocalStorageManager> { AndroidLocalStorageManager(get(), get()) }
}
