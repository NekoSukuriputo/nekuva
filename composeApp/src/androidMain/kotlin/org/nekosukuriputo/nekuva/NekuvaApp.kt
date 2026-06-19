package org.nekosukuriputo.nekuva

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.nekosukuriputo.nekuva.di.initKoin
import org.nekosukuriputo.nekuva.widget.WidgetUpdater

class NekuvaApp : Application(), KoinComponent {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@NekuvaApp)
        }
        // Keep home-screen widgets in sync with history/favourites (Doki WidgetUpdater).
        WidgetUpdater(this, get(), get()).start(appScope)
    }
}
