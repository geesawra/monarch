package industries.geesawra.monarch.datalayer

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import sh.christian.ozone.api.Cid
import javax.inject.Inject
import javax.inject.Singleton

enum class TranslationPhase {
    Idle,
    DetectingLanguage,
    DownloadingModel,
    Translating,
}

data class PostTranslation(
    val originalText: String,
    val translatedText: String? = null,
    val detectedLanguage: String? = null,
    val phase: TranslationPhase = TranslationPhase.Idle,
    val error: String? = null,
    val showOriginal: Boolean = false,
) {
    val isTranslating: Boolean get() = phase != TranslationPhase.Idle && translatedText == null && error == null
}

@Singleton
class PostTranslationStore @Inject constructor() {
    val states: SnapshotStateMap<Cid, PostTranslation> = mutableStateMapOf()

    fun getState(cid: Cid): PostTranslation? = states[cid]

    fun setPhase(cid: Cid, originalText: String, phase: TranslationPhase) {
        val current = states[cid] ?: PostTranslation(originalText = originalText)
        states[cid] = current.copy(phase = phase, error = null)
    }

    fun setTranslated(cid: Cid, result: TranslationResult) {
        states[cid]?.let { current ->
            states[cid] = current.copy(
                translatedText = result.translatedText,
                detectedLanguage = result.detectedLanguage,
                phase = TranslationPhase.Idle,
                error = null,
            )
        }
    }

    fun setError(cid: Cid, error: String) {
        states[cid]?.let { current ->
            states[cid] = current.copy(phase = TranslationPhase.Idle, error = error)
        }
    }

    fun toggleShowOriginal(cid: Cid) {
        states[cid]?.let { current ->
            states[cid] = current.copy(showOriginal = !current.showOriginal)
        }
    }

    fun retranslate(cid: Cid) {
        states[cid]?.let { current ->
            states[cid] = current.copy(
                translatedText = null,
                detectedLanguage = null,
                phase = TranslationPhase.DetectingLanguage,
                error = null,
                showOriginal = false,
            )
        }
    }

    fun clear() {
        states.clear()
    }
}
