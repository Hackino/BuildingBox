package com.buildingbox.app.feature.auth.domain

import kotlinx.serialization.Serializable

enum class UserRole { ADMIN, VIEWER;
    companion object {
        fun from(value: String?): UserRole = if (value == "admin") ADMIN else VIEWER
    }
}

/** Shape of /users/$uid in Realtime Database. */
@Serializable
data class UserRecord(
    val role: String = "viewer",
    val displayName: String = "",
    val createdAt: Long = 0L,
)

data class Session(
    val uid: String,
    val role: UserRole,
    val displayName: String,
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}
