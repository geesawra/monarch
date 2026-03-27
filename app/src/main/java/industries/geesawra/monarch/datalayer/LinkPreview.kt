package industries.geesawra.monarch.datalayer

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LinkPreviewData(
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val url: String,
)

object LinkPreviewFetcher {
    private val ogTitleRegex =
        Regex("""<meta\s+[^>]*property\s*=\s*["']og:title["'][^>]*content\s*=\s*["']([^"']*)["'][^>]*/?>""", RegexOption.IGNORE_CASE)
    private val ogTitleRegexAlt =
        Regex("""<meta\s+[^>]*content\s*=\s*["']([^"']*)["'][^>]*property\s*=\s*["']og:title["'][^>]*/?>""", RegexOption.IGNORE_CASE)
    private val ogDescRegex =
        Regex("""<meta\s+[^>]*property\s*=\s*["']og:description["'][^>]*content\s*=\s*["']([^"']*)["'][^>]*/?>""", RegexOption.IGNORE_CASE)
    private val ogDescRegexAlt =
        Regex("""<meta\s+[^>]*content\s*=\s*["']([^"']*)["'][^>]*property\s*=\s*["']og:description["'][^>]*/?>""", RegexOption.IGNORE_CASE)
    private val ogImageRegex =
        Regex("""<meta\s+[^>]*property\s*=\s*["']og:image["'][^>]*content\s*=\s*["']([^"']*)["'][^>]*/?>""", RegexOption.IGNORE_CASE)
    private val ogImageRegexAlt =
        Regex("""<meta\s+[^>]*content\s*=\s*["']([^"']*)["'][^>]*property\s*=\s*["']og:image["'][^>]*/?>""", RegexOption.IGNORE_CASE)
    private val titleTagRegex =
        Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 10000
        }
    }

    suspend fun fetch(url: String): LinkPreviewData? = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(url)
            if (!response.status.isSuccess()) return@withContext null

            val html = response.bodyAsText()
            // Only parse the head section for efficiency
            val headEnd = html.indexOf("</head>", ignoreCase = true)
            val headHtml = if (headEnd > 0) html.substring(0, headEnd) else html.take(16000)

            val title = ogTitleRegex.find(headHtml)?.groupValues?.get(1)
                ?: ogTitleRegexAlt.find(headHtml)?.groupValues?.get(1)
                ?: titleTagRegex.find(headHtml)?.groupValues?.get(1)

            val description = ogDescRegex.find(headHtml)?.groupValues?.get(1)
                ?: ogDescRegexAlt.find(headHtml)?.groupValues?.get(1)

            val imageUrl = ogImageRegex.find(headHtml)?.groupValues?.get(1)
                ?: ogImageRegexAlt.find(headHtml)?.groupValues?.get(1)

            if (title == null && description == null && imageUrl == null) {
                return@withContext null
            }

            LinkPreviewData(
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                url = url,
            )
        } catch (_: Exception) {
            null
        }
    }
}
