package com.buildingbox.app.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buildingbox.app.core.designsystem.AppButton
import com.buildingbox.app.core.designsystem.LocalAppColors
import com.buildingbox.app.core.platform.PlatformBackHandler
import com.buildingbox.app.feature.auth.domain.AuthRepository
import com.buildingbox.app.feature.auth.domain.Session
import com.buildingbox.app.feature.calendar.presentation.CalendarScreen
import com.buildingbox.app.feature.dashboard.presentation.DashboardScreen
import com.buildingbox.app.feature.payments.presentation.PaymentsScreen
import com.buildingbox.app.feature.reports.presentation.ReportsScreen
import com.buildingbox.app.feature.units.presentation.UnitsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun MainShell(session: Session, isDark: Boolean, onToggleTheme: () -> Unit, onExit: () -> Unit = {}) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var unitId by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun openUnit(id: String) { unitId = id; tab = Tab.UNITS }
    fun navigate(t: Tab) { unitId = null; tab = t }

    // Central "go back one step" decision, shared by Android back and desktop Esc:
    //   1. a unit detail is open  → close it
    //   2. not on Home            → go to Home
    //   3. on Home                → ask to exit
    fun goBack() {
        when {
            unitId != null -> unitId = null
            tab != Tab.HOME -> navigate(Tab.HOME)
            else -> confirmExit = true
        }
    }

    PlatformBackHandler(enabled = true, onBack = ::goBack)

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                // Desktop: Esc mirrors the system-back behavior.
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { goBack(); true } else false
            },
    ) {
        val expanded = maxWidth >= 840.dp
        val content: @Composable (Modifier) -> Unit = { mod ->
            Box(mod) {
                Content(
                    tab = tab,
                    expanded = expanded,
                    session = session,
                    unitId = unitId,
                    isDark = isDark,
                    onToggleTheme = onToggleTheme,
                    onOpenUnit = ::openUnit,
                    onOpenChange = { unitId = it },
                    onOpenReports = { navigate(Tab.REPORTS) },
                )
            }
        }

        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { navigate(t) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
                content(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { navigate(t) },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = LocalAppColors.current.accent,
                                    selectedTextColor = LocalAppColors.current.accent,
                                    indicatorColor = LocalAppColors.current.accentSoft,
                                ),
                            )
                        }
                    }
                },
            ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Exit BuildingBox?") },
            text = { Text("Do you want to close the app?") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onExit() }) { Text("Exit") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun Content(
    tab: Tab,
    expanded: Boolean,
    session: Session,
    unitId: String?,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenUnit: (String) -> Unit,
    onOpenChange: (String?) -> Unit,
    onOpenReports: () -> Unit,
) {
    when (tab) {
        Tab.HOME -> DashboardScreen(
            displayName = session.displayName,
            isDark = isDark,
            onToggleTheme = onToggleTheme,
            onOpenReports = onOpenReports,
            onOpenUnit = onOpenUnit,
        )
        Tab.UNITS -> UnitsScreen(isAdmin = session.isAdmin, expanded = expanded, openId = unitId, onOpenChange = onOpenChange)
        Tab.PAYMENTS -> PaymentsScreen(isAdmin = session.isAdmin, expanded = expanded, onOpenUnit = onOpenUnit)
        Tab.CALENDAR -> CalendarScreen(isAdmin = session.isAdmin, onOpenUnit = onOpenUnit)
        Tab.REPORTS -> ReportsScreen()
    }
}
