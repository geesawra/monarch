package industries.geesawra.monarch.datalayer

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.pushDataStore by preferencesDataStore("push_notifications")

@Serializable
data class PushRegistrationRequest(
    val token: String,
    val did: String,
)

@Singleton
class PushNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PushNotificationManager"
        const val REGISTER_URL = "http://10.0.2.2:9999/subscribe"
        private val FCM_TOKEN = stringPreferencesKey("fcm_token")
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun registerToken(token: String, did: String): Result<Unit> {
        return runCatching {
            val response = httpClient.post(REGISTER_URL) {
                contentType(ContentType.Application.Json)
                setBody(PushRegistrationRequest(token = token, did = did))
            }
            if (!response.status.isSuccess()) {
                throw Exception("Server returned ${response.status.value}: ${response.bodyAsText()}")
            }
            Log.d(TAG, "Push token registered for DID: $did")
            Unit
        }.onFailure {
            Log.e(TAG, "Failed to register push token: ${it.message}")
        }
    }

    suspend fun getAndRegisterToken(did: String): Result<Unit> {
        return runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            saveTokenLocally(token)
            registerToken(token, did).getOrThrow()
        }.onFailure {
            Log.e(TAG, "Failed to get/register FCM token: ${it.message}")
        }
    }

    suspend fun saveTokenLocally(token: String) {
        context.pushDataStore.edit { it[FCM_TOKEN] = token }
    }

    fun getLocalToken(): Flow<String?> {
        return context.pushDataStore.data.map { it[FCM_TOKEN] }
    }

    suspend fun getLocalTokenOnce(): String? {
        return context.pushDataStore.data.map { it[FCM_TOKEN] }.first()
    }
}
