package com.buildingbox.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildingbox.app.app.MainShell
import com.buildingbox.app.core.designsystem.BuildingBoxTheme
import com.buildingbox.app.feature.auth.domain.AuthRepository
import com.buildingbox.app.feature.auth.domain.SessionState
import com.buildingbox.app.feature.auth.presentation.LoginScreen
import org.koin.compose.koinInject

@Composable
fun App(onExit: () -> Unit = {}) {
    var override by remember { mutableStateOf<Boolean?>(null) }
    val dark = override ?: isSystemInDarkTheme()

    BuildingBoxTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val authRepository = koinInject<AuthRepository>()
            val sessionState by authRepository.sessionState
                .collectAsStateWithLifecycle(initialValue = SessionState.Loading)

            when (val s = sessionState) {
                SessionState.Loading -> LoadingScreen()
                SessionState.SignedOut -> LoginScreen()
                is SessionState.SignedIn -> MainShell(
                    session = s.session,
                    isDark = dark,
                    onToggleTheme = { override = !dark },
                    onExit = onExit,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}
