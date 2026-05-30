package org.nekosukuriputo.nekuva

import android.app.Application
import org.nekosukuriputo.nekuva.di.initKoin
import org.koin.android.ext.koin.androidContext

class NekuvaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@NekuvaApp)
        }
    }
}
