package com.buildingbox.app

import android.app.Application
import com.buildingbox.app.di.initKoin
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class BuildingBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase (and Crashlytics) auto-initialize from google-services.json.
        // Disk persistence → instant reads + full offline support. Must run before any DB use.
        runCatching { Firebase.database.setPersistenceEnabled(true) }
        initKoin {
            androidLogger()
            androidContext(this@BuildingBoxApp)
        }
    }
}
