package com.buildingbox.app.feature.calendar

import com.buildingbox.app.feature.calendar.data.ExpensesRepositoryImpl
import com.buildingbox.app.feature.calendar.domain.ExpensesRepository
import com.buildingbox.app.feature.calendar.presentation.CalendarViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val calendarModule = module {
    single<ExpensesRepository> { ExpensesRepositoryImpl(get()) }
    viewModel { CalendarViewModel(get(), get(), get()) }
}
