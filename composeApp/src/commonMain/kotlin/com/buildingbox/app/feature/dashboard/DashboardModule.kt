package com.buildingbox.app.feature.dashboard

import com.buildingbox.app.feature.dashboard.presentation.DashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dashboardModule = module {
    viewModel { DashboardViewModel(get(), get(), get()) }
}
