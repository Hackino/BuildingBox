package com.buildingbox.app.feature.payments

import com.buildingbox.app.feature.payments.data.DuesRepositoryImpl
import com.buildingbox.app.feature.payments.domain.DuesRepository
import com.buildingbox.app.feature.payments.presentation.PaymentsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val paymentsModule = module {
    single<DuesRepository> { DuesRepositoryImpl(get()) }
    viewModel { PaymentsViewModel(get(), get()) }
}
