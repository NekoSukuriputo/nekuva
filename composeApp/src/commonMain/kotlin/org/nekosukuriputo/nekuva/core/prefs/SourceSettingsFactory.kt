package org.nekosukuriputo.nekuva.core.prefs

import com.russhwolf.settings.ObservableSettings

expect fun getSourceObservableSettings(sourceName: String): ObservableSettings
