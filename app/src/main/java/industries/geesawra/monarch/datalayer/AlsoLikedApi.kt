package industries.geesawra.monarch.datalayer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class AlsoLikedPost(
    @SerialName("post") val post: String,
)

@Serializable
data class AlsoLikedResponse(
    @SerialName("feed") val feed: List<AlsoLikedPost>,
    @SerialName("cursor") val cursor: String? = null,
)

suspend fun fetchAlsoLiked(postUrl: String, cursor: String? = null): Result<AlsoLikedResponse> = suspendRunCatching {
    val url = buildString {
        append("https://foryou.club/also-liked?post=")
        append(java.net.URLEncoder.encode(postUrl, "UTF-8"))
        append("&format=json")
        if (cursor != null) {
            append("&cursor=")
            append(java.net.URLEncoder.encode(cursor, "UTF-8"))
        }
    }
    Log.d("AlsoLiked", "Fetching: $url")
    val body = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        Log.d("AlsoLiked", "Response code: $code")
        if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            throw Exception("HTTP $code: ${conn.errorStream?.bufferedReader()?.readText()}")
        }
    }
    Log.d("AlsoLiked", "Body: ${body.take(200)}...")
    json.decodeFromString<AlsoLikedResponse>(body)
}
