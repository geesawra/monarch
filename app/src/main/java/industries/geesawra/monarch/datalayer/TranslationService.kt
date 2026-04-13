package industries.geesawra.monarch.datalayer

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TranslationResult(
    val translatedText: String,
    val detectedLanguage: String,
)

interface TranslationProgressListener {
    fun onDetectingLanguage()
    fun onDownloadingModel()
    fun onTranslating()
}

val TRANSLATION_LANGUAGE_OPTIONS = listOf(
    "English" to "en",
    "Spanish" to "es",
    "French" to "fr",
    "German" to "de",
    "Italian" to "it",
    "Portuguese" to "pt",
    "Japanese" to "ja",
    "Korean" to "ko",
    "Chinese" to "zh",
    "Arabic" to "ar",
    "Hindi" to "hi",
    "Russian" to "ru",
)

fun languageCodeToName(code: String): String {
    return TRANSLATION_LANGUAGE_OPTIONS.firstOrNull { it.second == code }?.first ?: code.uppercase()
}

@Singleton
class TranslationService @Inject constructor() {
    private val lock = Mutex()
    private val translatorCache = mutableMapOf<Pair<String, String>, Translator>()
    private val languageIdentifier = LanguageIdentification.getClient()

    suspend fun identifyLanguage(text: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            suspendCancellableCoroutine { cont ->
                languageIdentifier.identifyLanguage(text)
                    .addOnSuccessListener { languageCode ->
                        if (cont.isActive) cont.resume(languageCode)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            }
        }
    }

    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguageOverride: String? = null,
        progressListener: TranslationProgressListener? = null,
    ): Result<TranslationResult> = withContext(Dispatchers.Default) {
        runCatching {
            val sourceLanguage = if (sourceLanguageOverride != null) {
                sourceLanguageOverride
            } else {
                progressListener?.onDetectingLanguage()
                identifyLanguage(text).getOrThrow().let { detected ->
                    if (detected == "und") throw IllegalStateException("Could not detect language")
                    detected
                }
            }

            if (sourceLanguage == targetLanguage) {
                return@runCatching TranslationResult(text, sourceLanguage)
            }

            val translator = getOrCreateTranslator(sourceLanguage, targetLanguage)
            progressListener?.onDownloadingModel()
            ensureModelDownloaded(translator)

            progressListener?.onTranslating()
            val translatedText = suspendCancellableCoroutine { cont ->
                translator.translate(text)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            }

            TranslationResult(translatedText, sourceLanguage)
        }
    }

    private suspend fun getOrCreateTranslator(source: String, target: String): Translator {
        return lock.withLock {
            val key = source to target
            translatorCache.getOrPut(key) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
                Translation.getClient(options)
            }
        }
    }

    private suspend fun ensureModelDownloaded(translator: Translator) {
        suspendCancellableCoroutine { cont ->
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    fun close() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        languageIdentifier.close()
    }
}
