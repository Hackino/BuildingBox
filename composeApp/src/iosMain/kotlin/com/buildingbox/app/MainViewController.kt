package com.buildingbox.app

import androidx.compose.ui.window.ComposeUIViewController
import com.buildingbox.app.di.initKoin
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database

private val koinStarted by lazy {
    // Requires FirebaseApp.configure() in the iOS app delegate first.
    runCatching { Firebase.database.setPersistenceEnabled(true) }
    initKoin()
}

@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController {
    koinStarted
    App()
}
