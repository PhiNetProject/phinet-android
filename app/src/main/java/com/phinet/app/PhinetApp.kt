package com.phinet.app

import android.app.Application
import com.phinet.app.data.ComRepository
import com.phinet.app.data.Settings

/** App singleton wiring the settings + repository (lightweight DI). */
class PhinetApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var repo: ComRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repo = ComRepository(settings)
    }
}
