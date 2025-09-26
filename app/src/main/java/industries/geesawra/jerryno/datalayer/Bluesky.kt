package industries.geesawra.jerryno.datalayer

import android.util.Log
import androidx.lifecycle.viewModelScope
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.GetTimelineResponse
import app.bsky.feed.Post
import com.atproto.repo.CreateRecordRequest
import com.atproto.server.CreateSessionRequest
import com.atproto.server.CreateSessionResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi
import sh.christian.ozone.api.BlueskyAuthPlugin
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import sh.christian.ozone.api.response.AtpResponse
import javax.inject.Inject

data class Client(
    var client: AuthenticatedXrpcBlueskyApi,
    var session: CreateSessionResponse
)

private class BlueskyConn() {
    var client: AuthenticatedXrpcBlueskyApi? = null
    var session: CreateSessionResponse? = null

    suspend fun create(pdsURL: String, handle: String, password: String): Result<Client> {
        return runCatching {
            if (client != null && session != null) {
                return Result.success(Client(client!!, session!!))
            }

            val httpClient = HttpClient(OkHttp) {
                defaultRequest {
                    url(pdsURL)
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 15000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 15000
                }
            }

            val client = XrpcBlueskyApi(httpClient)

            val s = client.createSession(CreateSessionRequest(handle, password))
            when (s) {
                is AtpResponse.Failure<*> -> Result.failure<Exception>(Exception("Failed to create session: ${s.error}"))
                is AtpResponse.Success<CreateSessionResponse> -> {
                    val tokens = BlueskyAuthPlugin.Tokens(s.response.accessJwt, s.response.refreshJwt)
                    val authClient = AuthenticatedXrpcBlueskyApi(httpClient, tokens)
                    this.client = authClient
                    session = s.response
                }
            }

            return Result.success(Client(this.client!!, session!!))
        }
    }
}

class Bluesky(val pdsURL: String, val handle: String, val password: String) {
    private val conn = BlueskyConn()

    suspend fun fetchTimeline(): Result<GetTimelineResponse>  {
        return runCatching {
            val conn = conn.create(pdsURL, handle, password).getOrThrow()

            val timeline = conn.client.getTimeline(GetTimelineQueryParams());
            val feed = when (timeline) {
                is AtpResponse.Failure<*> -> {
                    return Result.failure(Exception("Failed to fetch timeline: ${timeline.error}"))
                }

                is AtpResponse.Success<GetTimelineResponse> -> timeline.response
            };

            return Result.success(feed)
        }
    }

    suspend fun post(content: String){
        val conn = conn.create(pdsURL, handle, password).getOrThrow()

        val r = BlueskyJson.encodeAsJsonContent(Post(
                text = content,
                createdAt = Clock.System.now()
            ))
            val resp = conn.client.createRecord(
                CreateRecordRequest(
                    repo = conn.session.handle,
                    collection = Nsid("app.bsky.feed.post"),
                    record = r,
                )
            ) // TODO: finish

    }
}