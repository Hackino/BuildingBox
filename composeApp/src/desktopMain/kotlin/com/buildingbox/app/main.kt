package com.buildingbox.app

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.buildingbox.app.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "BuildingBox",
            icon = painterResource("icon.png"),
            state = rememberWindowState(width = 1100.dp, height = 800.dp),
        ) {
            App()
        }
    }
}
