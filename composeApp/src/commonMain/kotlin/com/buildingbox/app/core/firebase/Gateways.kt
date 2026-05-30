package com.buildingbox.app.core.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement

/** Authentication surface (mirrors Firebase Auth; GitLive on mobile, REST on desktop). */
interface AuthGateway {
    /** Current signed-in user id, or null. */
    val currentUserId: String?

    /** Emits the uid on sign-in / null on sign-out. */
    val authState: Flow<String?>

    /** Returns the uid on success. */
    suspend fun signIn(email: String, password: String): Result<String>

    suspend fun signOut()
}

/**
 * Thin Realtime Database surface. Reads/writes are typed via kotlinx.serialization so the same
 * domain models work over GitLive (mobile) and REST (desktop). [update] does atomic multi-path
 * fan-out for keeping aggregates exact.
 */
interface RealtimeDb {
    suspend fun <T> getValue(path: String, strategy: DeserializationStrategy<T>): T?
    fun <T> observeValue(path: String, strategy: DeserializationStrategy<T>): Flow<T?>
    suspend fun <T> setValue(path: String, value: T, strategy: SerializationStrategy<T>)
    /** Create a child under [path] with a generated key; returns the new key. */
    suspend fun <T> push(path: String, value: T, strategy: SerializationStrategy<T>): String
    suspend fun update(updates: Map<String, JsonElement>)
}

/** Crash/diagnostics sink. Native Crashlytics on mobile; file/console on desktop. */
interface CrashReporter {
    fun log(message: String)
    fun recordException(throwable: Throwable)
    fun setUserId(userId: String?)
}
