package com.buildingbox.app.feature.reports

import com.buildingbox.app.feature.reports.presentation.ReportsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reportsModule = module {
    viewModel { ReportsViewModel(get(), get(), get(), get()) }
}
