package com.buildingbox.app.feature.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildingbox.app.feature.auth.domain.AuthRepository
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val repo: AuthRepository) : ViewModel() {
    var state by mutableStateOf(LoginUiState())
        private set

    fun onEmail(value: String) { state = state.copy(email = value, error = null) }
    fun onPassword(value: String) { state = state.copy(password = value, error = null) }

    fun submit() {
        if (state.loading || state.email.isBlank() || state.password.isBlank()) return
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            val result = repo.signIn(state.email, state.password)
            state = if (result.isSuccess) {
                // Keep the loader up: sign-in succeeded but navigation happens reactively
                // when SessionState.SignedIn propagates. Staying `loading` covers that gap
                // so the user sees a spinner the whole way, not a dead frame.
                state.copy(loading = true)
            } else {
                state.copy(loading = false, error = result.exceptionOrNull()?.message ?: "Sign-in failed")
            }
        }
    }
}
