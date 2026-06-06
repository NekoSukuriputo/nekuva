package org.nekosukuriputo.nekuva.core.prefs

import android.content.Context
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object ContextProvider : KoinComponent {
    val context: Context by inject()
}

actual fun getSourceObservableSettings(sourceName: String): ObservableSettings {
    val prefs = ContextProvider.context.getSharedPreferences("source_$sourceName", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}
