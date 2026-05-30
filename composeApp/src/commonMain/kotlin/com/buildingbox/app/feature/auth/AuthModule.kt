package com.buildingbox.app.feature.auth

import com.buildingbox.app.feature.auth.data.AuthRepositoryImpl
import com.buildingbox.app.feature.auth.domain.AuthRepository
import com.buildingbox.app.feature.auth.presentation.LoginViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    viewModel { LoginViewModel(get()) }
}
