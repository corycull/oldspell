package com.talewind.app

import android.app.Application
import com.talewind.app.data.LibraryStore
import com.talewind.app.data.Settings

class TalewindApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var store: LibraryStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        store = LibraryStore(this)
    }

    fun rescan() = store.scan(settings.folders.value, settings.hidden.value)

    companion object {
        lateinit var instance: TalewindApp
            private set
    }
}
