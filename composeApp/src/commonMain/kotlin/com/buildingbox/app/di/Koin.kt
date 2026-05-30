package com.buildingbox.app.di

import com.buildingbox.app.feature.auth.authModule
import com.buildingbox.app.feature.calendar.calendarModule
import com.buildingbox.app.feature.dashboard.dashboardModule
import com.buildingbox.app.feature.payments.paymentsModule
import com.buildingbox.app.feature.reports.reportsModule
import com.buildingbox.app.feature.units.unitsModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/** Shared Koin modules. Each feature contributes its module here as it lands. */
val appModules: List<Module>
    get() = listOf(platformModule(), authModule, unitsModule, paymentsModule, calendarModule, dashboardModule, reportsModule)

/** Start Koin with optional platform tweaks (e.g. androidContext). Safe to call once. */
fun initKoin(appDeclaration: (KoinApplication.() -> Unit) = {}) {
    startKoin {
        appDeclaration()
        modules(appModules)
    }
}
