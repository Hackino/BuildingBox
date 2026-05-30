package com.buildingbox.app.di

import com.buildingbox.app.core.firebase.AuthGateway
import com.buildingbox.app.core.firebase.CrashReporter
import com.buildingbox.app.core.firebase.RealtimeDb
import com.buildingbox.app.core.firebase.toAny
import com.buildingbox.app.feature.reports.IosReportExporter
import com.buildingbox.app.feature.reports.domain.ReportExporter
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import org.koin.core.module.Module
import org.koin.dsl.module

private class GitLiveAuthGateway : AuthGateway {
    override val currentUserId: String? get() = Firebase.auth.currentUser?.uid
    override val authState: Flow<String?> = Firebase.auth.authStateChanged.map { it?.uid }
    override suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        Firebase.auth.signInWithEmailAndPassword(email, password).user?.uid ?: error("No user returned")
    }
    override suspend fun signOut() = Firebase.auth.signOut()
}

private class GitLiveRealtimeDb : RealtimeDb {
    private val db = Firebase.database
    override suspend fun <T> getValue(path: String, strategy: DeserializationStrategy<T>): T? {
        val snap = db.reference(path).valueEvents.first()
        return if (snap.exists) snap.value(strategy) else null
    }
    override fun <T> observeValue(path: String, strategy: DeserializationStrategy<T>): Flow<T?> =
        db.reference(path).valueEvents
            .map { if (it.exists) it.value(strategy) else null }
            .catch { e ->
                // PERMISSION_DENIED fires on the open listeners during sign-out; let the
                // stream end quietly instead of crashing. Never swallow cancellation.
                if (e is CancellationException) throw e
            }
    override suspend fun <T> setValue(path: String, value: T, strategy: SerializationStrategy<T>) {
        db.reference(path).setValue(strategy, value)
    }
    override suspend fun <T> push(path: String, value: T, strategy: SerializationStrategy<T>): String {
        val ref = db.reference(path).push()
        ref.setValue(strategy, value)
        return ref.key ?: error("Failed to generate key")
    }
    override suspend fun update(updates: Map<String, JsonElement>) {
        db.reference().updateChildren(updates.mapValues { it.value.toAny() })
    }
}

private class IosCrashReporter : CrashReporter {
    override fun log(message: String) { println("[crash] $message") }
    override fun recordException(throwable: Throwable) { println("[crash] $throwable") }
    override fun setUserId(userId: String?) {}
}

actual fun platformModule(): Module = module {
    single<AuthGateway> { GitLiveAuthGateway() }
    single<RealtimeDb> { GitLiveRealtimeDb() }
    single<CrashReporter> { IosCrashReporter() }
    single<ReportExporter> { IosReportExporter() }
}
