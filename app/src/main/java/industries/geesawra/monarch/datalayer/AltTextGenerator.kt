package industries.geesawra.monarch.datalayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AltTextAvailability {
    Unavailable,
    Downloadable,
    Downloading,
    Available,
}

@Singleton
class AltTextGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lock = Mutex()
    private var client: ImageDescriber? = null

    private fun getOrCreateClient(): ImageDescriber {
        client?.let { return it }
        val options = ImageDescriberOptions.builder(context).build()
        return ImageDescription.getClient(options).also { client = it }
    }

    suspend fun availability(): AltTextAvailability = withContext(Dispatchers.Default) {
        runCatching {
            val status = getOrCreateClient().checkFeatureStatus().await()
            when (status) {
                FeatureStatus.AVAILABLE -> AltTextAvailability.Available
                FeatureStatus.DOWNLOADABLE -> AltTextAvailability.Downloadable
                FeatureStatus.DOWNLOADING -> AltTextAvailability.Downloading
                else -> AltTextAvailability.Unavailable
            }
        }.getOrElse { AltTextAvailability.Unavailable }
    }

    suspend fun ensureDownloaded(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val describer = getOrCreateClient()
            val status = describer.checkFeatureStatus().await()
            if (status == FeatureStatus.AVAILABLE) return@runCatching
            if (status == FeatureStatus.UNAVAILABLE) {
                throw IllegalStateException("Image description feature is not supported on this device")
            }
            suspendCancellableCoroutine<Unit> { cont ->
                describer.downloadFeature(object : DownloadCallback {
                    override fun onDownloadStarted(bytesToDownload: Long) {}
                    override fun onDownloadProgress(bytesDownloaded: Long) {}
                    override fun onDownloadFailed(e: GenAiException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                    override fun onDownloadCompleted() {
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
            }
        }
    }

    suspend fun describe(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        lock.withLock {
            runCatching {
                val describer = getOrCreateClient()
                val request = ImageDescriptionRequest.builder(bitmap).build()
                val result = describer.runInference(request).await()
                result.description.trim()
            }
        }
    }

    suspend fun describeUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(context, uri)
            ?: return@withContext Result.failure(IllegalArgumentException("Could not decode image"))
        try {
            describe(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun close() {
        client?.close()
        client = null
    }
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val orientation = runCatching {
        ExifInterface(bytes.inputStream()).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    if (orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED
    ) return raw
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return raw
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        .also { if (it !== raw) raw.recycle() }
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener({
        try {
            cont.resume(get())
        } catch (ce: CancellationException) {
            cont.cancel(ce)
        } catch (t: Throwable) {
            cont.resumeWithException(t.cause ?: t)
        }
    }, Runnable::run)
    cont.invokeOnCancellation { cancel(true) }
}
