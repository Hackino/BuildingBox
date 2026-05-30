package com.buildingbox.app.feature.units

import com.buildingbox.app.feature.units.data.ApartmentRepositoryImpl
import com.buildingbox.app.feature.units.domain.ApartmentRepository
import com.buildingbox.app.feature.units.presentation.UnitsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val unitsModule = module {
    single<ApartmentRepository> { ApartmentRepositoryImpl(get()) }
    viewModel { UnitsViewModel(get(), get()) }
}
