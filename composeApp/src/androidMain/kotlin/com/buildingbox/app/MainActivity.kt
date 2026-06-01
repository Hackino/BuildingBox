package com.buildingbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system splash (Theme.BuildingBox.Starting)
        // hands off to the post-splash app theme.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // finish() on confirmed exit from Home — matches "back closes app only after confirm".
        setContent { App(onExit = { finish() }) }
    }
}
