package com.buildingbox.app.di

import com.buildingbox.app.core.firebase.AuthGateway
import com.buildingbox.app.core.firebase.CrashReporter
import com.buildingbox.app.core.firebase.RealtimeDb
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.util.Properties

private data class DesktopFirebaseConfig(val apiKey: String, val databaseUrl: String)

private fun loadConfig(): DesktopFirebaseConfig {
    val candidates = listOf(
        File("desktop-firebase.properties"),
        File("composeApp/desktop-firebase.properties"),
        File("mobile/composeApp/desktop-firebase.properties"),
    )
    val file = candidates.firstOrNull { it.exists() }
        ?: error("desktop-firebase.properties not found (copy config/desktop-firebase.example.properties to composeApp/desktop-firebase.properties)")
    val props = Properties().apply { file.inputStream().use { load(it) } }
    return DesktopFirebaseConfig(
        apiKey = props.getProperty("firebase.apiKey").orEmpty(),
        databaseUrl = props.getProperty("firebase.databaseUrl").orEmpty().trimEnd('/'),
    )
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable private data class SignInRequest(val email: String, val password: String, val returnSecureToken: Boolean = true)
@Serializable private data class SignInResponse(val idToken: String = "", val localId: String = "")
@Serializable private data class AuthError(val error: ErrorBody = ErrorBody())
@Serializable private data class ErrorBody(val message: String = "Sign-in failed")
@Serializable private data class PushResponse(val name: String = "")

private class RestAuthGateway(private val config: DesktopFirebaseConfig, private val client: HttpClient) : AuthGateway {
    @Volatile var idToken: String? = null
        private set
    private val state = MutableStateFlow<String?>(null)

    override val currentUserId: String? get() = state.value
    override val authState: Flow<String?> = state

    override suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = client.post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword") {
                parameter("key", config.apiKey)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SignInRequest.serializer(), SignInRequest(email, password)))
            }
            val text = resp.bodyAsText()
            if (!resp.status.isSuccess()) {
                error(json.decodeFromString(AuthError.serializer(), text).error.message)
            }
            val parsed = json.decodeFromString(SignInResponse.serializer(), text)
            idToken = parsed.idToken
            state.value = parsed.localId
            parsed.localId
        }
    }

    override suspend fun signOut() {
        idToken = null
        state.value = null
    }
}

private class RestRealtimeDb(
    private val config: DesktopFirebaseConfig,
    private val auth: RestAuthGateway,
    private val client: HttpClient,
) : RealtimeDb {
    private fun url(path: String) = "${config.databaseUrl}/$path.json"

    override suspend fun <T> getValue(path: String, strategy: DeserializationStrategy<T>): T? =
        withContext(Dispatchers.IO) {
            val text = client.get(url(path)) { parameter("auth", auth.idToken) }.bodyAsText()
            if (text.isBlank() || text == "null") null else json.decodeFromString(strategy, text)
        }

    override fun <T> observeValue(path: String, strategy: DeserializationStrategy<T>): Flow<T?> = flow {
        // Desktop has no socket SDK; poll the node. Cheap because nodes are month-sharded.
        while (true) {
            try {
                emit(getValue(path, strategy))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Denied after sign-out, or a transient network error — keep the last value
                // and keep polling; the coroutine is cancelled on teardown.
            }
            delay(3000)
        }
    }.distinctUntilChanged()

    override suspend fun <T> setValue(path: String, value: T, strategy: SerializationStrategy<T>) {
        withContext(Dispatchers.IO) {
            client.put(url(path)) {
                parameter("auth", auth.idToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(strategy, value))
            }
        }
    }

    override suspend fun <T> push(path: String, value: T, strategy: SerializationStrategy<T>): String =
        withContext(Dispatchers.IO) {
            val text = client.post(url(path)) {
                parameter("auth", auth.idToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(strategy, value))
            }.bodyAsText()
            json.decodeFromString(PushResponse.serializer(), text).name
        }

    override suspend fun update(updates: Map<String, JsonElement>) {
        withContext(Dispatchers.IO) {
            client.patch("${config.databaseUrl}/.json") {
                parameter("auth", auth.idToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), JsonObject(updates)))
            }
        }
    }
}

private class DesktopCrashReporter : CrashReporter {
    override fun log(message: String) { println("[log] $message") }
    override fun recordException(throwable: Throwable) { System.err.println("[crash] ${throwable.stackTraceToString()}") }
    override fun setUserId(userId: String?) {}
}

actual fun platformModule(): Module = module {
    single { loadConfig() }
    single { HttpClient(CIO) }
    single<AuthGateway> { RestAuthGateway(get(), get()) }
    single<RealtimeDb> { RestRealtimeDb(get(), get<AuthGateway>() as RestAuthGateway, get()) }
    single<CrashReporter> { DesktopCrashReporter() }
    single<com.buildingbox.app.feature.reports.domain.ReportExporter> { com.buildingbox.app.feature.reports.DesktopReportExporter() }
}
