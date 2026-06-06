package org.nekosukuriputo.nekuva.core.prefs

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences

actual fun getSourceObservableSettings(sourceName: String): ObservableSettings {
    return PreferencesSettings(Preferences.userRoot().node("Nekuva_source_$sourceName"))
}
