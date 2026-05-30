package com.buildingbox.app.feature.auth.data

import com.buildingbox.app.core.firebase.AuthGateway
import com.buildingbox.app.core.firebase.CrashReporter
import com.buildingbox.app.core.firebase.RealtimeDb
import com.buildingbox.app.feature.auth.domain.AuthRepository
import com.buildingbox.app.feature.auth.domain.Session
import com.buildingbox.app.feature.auth.domain.SessionState
import com.buildingbox.app.feature.auth.domain.UserRecord
import com.buildingbox.app.feature.auth.domain.UserRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class AuthRepositoryImpl(
    private val auth: AuthGateway,
    private val db: RealtimeDb,
    private val crash: CrashReporter,
) : AuthRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val sessionState: Flow<SessionState> =
        auth.authState
            .flatMapLatest { uid ->
                if (uid == null) flow { emit(SessionState.SignedOut) } else sessionForUid(uid)
            }
            .onStart { emit(SessionState.Loading) }
            .catch { emit(SessionState.SignedOut) }

    private fun sessionForUid(uid: String): Flow<SessionState> = flow {
        crash.setUserId(uid)
        // First sign-in: self-provision as viewer (rules forbid self-promotion to admin).
        val existing = db.getValue("users/$uid", UserRecord.serializer())
        if (existing == null) {
            db.setValue("users/$uid", UserRecord(role = "viewer"), UserRecord.serializer())
        }
        emitAll(
            db.observeValue("users/$uid", UserRecord.serializer()).map { record ->
                val r = record ?: UserRecord()
                SessionState.SignedIn(Session(uid, UserRole.from(r.role), r.displayName))
            },
        )
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        auth.signIn(email.trim(), password).map { }.onFailure { crash.recordException(it) }

    override suspend fun signOut() {
        crash.setUserId(null)
        auth.signOut()
    }
}
