package com.oldspell.app

import android.app.Application
import com.oldspell.app.data.LibraryStore
import com.oldspell.app.data.Settings

class OldspellApp : Application() {
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
        lateinit var instance: OldspellApp
            private set
    }
}
