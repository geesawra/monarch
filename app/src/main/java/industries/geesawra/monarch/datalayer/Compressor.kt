package industries.geesawra.monarch.datalayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.math.roundToInt

// Adapted from:
// http://github.com/philipplackner/ImageCompression/blob/master/app/src/main/java/com/plcoding/imagecompression/ImageCompressor.kt

data class CompressedImage(
    val data: ByteArray,
    val width: Long,
    val height: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompressedImage

        if (width != other.width) return false
        if (height != other.height) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class Compressor(
    private val context: Context
) {
    suspend fun compressImage(
        contentUri: Uri,
        compressionThreshold: Long
    ): CompressedImage {
        return withContext(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(contentUri)
            val inputBytes = context
                .contentResolver
                .openInputStream(contentUri)
                ?.use { inputStream ->
                    inputStream.readBytes()
                }!!

            ensureActive()

            withContext(Dispatchers.Default) {
                val bitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)

                ensureActive()

                val compressFormat = when (mimeType) {
                    "image/png" -> Bitmap.CompressFormat.PNG
                    "image/jpeg" -> Bitmap.CompressFormat.JPEG
                    "image/webp" ->
                        Bitmap.CompressFormat.WEBP_LOSSLESS

                    else -> Bitmap.CompressFormat.JPEG
                }

                var outputBytes: ByteArray
                var quality = 90

                do {
                    ByteArrayOutputStream().use { outputStream ->
                        bitmap.compress(compressFormat, quality, outputStream)
                        outputBytes = outputStream.toByteArray()
                        quality -= (quality * 0.1).roundToInt()
                    }
                } while (isActive &&
                    outputBytes.size > compressionThreshold &&
                    quality > 5 &&
                    compressFormat != Bitmap.CompressFormat.PNG
                )

                CompressedImage(
                    data = outputBytes,
                    width = bitmap.width.toLong(),
                    height = bitmap.height.toLong()
                )
            }
        }
    }

    fun getVideoSizeFromUri(context: Context, uri: Uri): Long? {
        // Use the ContentResolver to query the Uri's metadata
        val cursor = context.contentResolver.query(uri, null, null, null, null)

        // The 'use' block ensures the cursor is automatically closed
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                // The size column may not exist for all Uris, so check for -1
                if (sizeIndex != -1) {
                    return it.getLong(sizeIndex)
                }
            }
        }
        // If we couldn't get the size, return null
        return null
    }

    @OptIn(UnstableApi::class)
    suspend fun compressVideo(
        contentUri: Uri,
    ): Uri? {
        return withContext(Dispatchers.IO) {
            val sizeLimitBytes = 100 * 1024 * 1024L

            val videoSizeBytes = withContext(Dispatchers.IO) {
                getVideoSizeFromUri(context, contentUri)
            }

            if (videoSizeBytes == null || videoSizeBytes <= sizeLimitBytes) {
                return@withContext null
            }

            val tempFile =
                createTempFile(
                    directory = Path(context.cacheDir.toString()),
                    prefix = "kotlinTemp",
                    suffix = ".tmp",
                ).toFile()

            File.createTempFile("compressor_output", ".mp4", context.cacheDir)

            val scaleAndRotateTransformation = ScaleAndRotateTransformation.Builder()
                .setScale(0.3f, 0.3f) // Scale x and y by 50%
                .build()

            ensureActive()

            suspendCancellableCoroutine { continuation ->
                val inputMediaItem = MediaItem.fromUri(contentUri)
                val editedMediaItem =
                    EditedMediaItem.Builder(inputMediaItem).setEffects(
                        Effects(
                            listOf(),
                            listOf(scaleAndRotateTransformation)
                        )
                    ).build()

                val transformer =
                    Transformer.Builder(context).addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            continuation.resume(value = tempFile.toUri())
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            continuation.resumeWithException(exportException)
                        }
                    }).build()

                transformer.start(editedMediaItem, tempFile.toString())

                continuation.invokeOnCancellation {
                    transformer.cancel()
                }
            }
        }
    }
}