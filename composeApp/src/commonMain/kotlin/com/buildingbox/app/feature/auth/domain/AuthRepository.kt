package com.buildingbox.app.feature.auth.domain

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Reactive session: Loading → SignedOut | SignedIn(role). */
    val sessionState: Flow<SessionState>

    /** Sign in with email/password. On first sign-in, provisions /users/$uid as viewer. */
    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun signOut()
}
