package industries.geesawra.monarch.datalayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore("settings")

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class PostTextSize {
    Small,
    Medium,
    Large,
}

enum class AvatarShape {
    Circle,
    RoundedSquare,
}

data class DefaultFeed(
    val uri: String = "following",
    val displayName: String = "Following",
    val avatar: String? = null,
)

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val postTextSize: PostTextSize = PostTextSize.Medium,
    val avatarShape: AvatarShape = AvatarShape.Circle,
    val replyFilterMode: ReplyFilterMode = ReplyFilterMode.OnlyFilterDeepThreads,
    val showLabels: Boolean = true,
    val defaultFeed: DefaultFeed = DefaultFeed(),
    val forceCompactLayout: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = stringPreferencesKey("dynamic_color")
        private val POST_TEXT_SIZE = stringPreferencesKey("post_text_size")
        private val AVATAR_SHAPE = stringPreferencesKey("avatar_shape")
        private val REPLY_FILTER_MODE = stringPreferencesKey("reply_filter_mode")
        private val SHOW_LABELS = stringPreferencesKey("show_labels")
        private val DEFAULT_FEED_URI = stringPreferencesKey("default_feed_uri")
        private val DEFAULT_FEED_NAME = stringPreferencesKey("default_feed_name")
        private val DEFAULT_FEED_AVATAR = stringPreferencesKey("default_feed_avatar")
        private val FORCE_COMPACT_LAYOUT = stringPreferencesKey("force_compact_layout")
    }

    var settingsState by mutableStateOf(SettingsState())
        private set

    init {
        viewModelScope.launch {
            context.settingsDataStore.data.map { prefs ->
                SettingsState(
                    themeMode = prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
                    dynamicColor = prefs[DYNAMIC_COLOR]?.toBooleanStrictOrNull() ?: true,
                    postTextSize = prefs[POST_TEXT_SIZE]?.let { runCatching { PostTextSize.valueOf(it) }.getOrNull() } ?: PostTextSize.Medium,
                    avatarShape = prefs[AVATAR_SHAPE]?.let { runCatching { AvatarShape.valueOf(it) }.getOrNull() } ?: AvatarShape.Circle,
                    replyFilterMode = prefs[REPLY_FILTER_MODE]?.let { runCatching { ReplyFilterMode.valueOf(it) }.getOrNull() } ?: ReplyFilterMode.OnlyFilterDeepThreads,
                    showLabels = prefs[SHOW_LABELS]?.toBooleanStrictOrNull() ?: true,
                    defaultFeed = DefaultFeed(
                        uri = prefs[DEFAULT_FEED_URI] ?: "following",
                        displayName = prefs[DEFAULT_FEED_NAME] ?: "Following",
                        avatar = prefs[DEFAULT_FEED_AVATAR],
                    ),
                    forceCompactLayout = prefs[FORCE_COMPACT_LAYOUT]?.toBooleanStrictOrNull() ?: false,
                    loaded = true,
                )
            }.collect {
                settingsState = it
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[THEME_MODE] = mode.name }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[DYNAMIC_COLOR] = enabled.toString() }
        }
    }

    fun setPostTextSize(size: PostTextSize) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[POST_TEXT_SIZE] = size.name }
        }
    }

    fun setAvatarShape(shape: AvatarShape) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[AVATAR_SHAPE] = shape.name }
        }
    }

    fun setReplyFilterMode(mode: ReplyFilterMode) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[REPLY_FILTER_MODE] = mode.name }
        }
    }

    fun setShowLabels(show: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[SHOW_LABELS] = show.toString() }
        }
    }

    fun setForceCompactLayout(force: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[FORCE_COMPACT_LAYOUT] = force.toString() }
        }
    }

    fun setDefaultFeed(uri: String, displayName: String, avatar: String?) {
        viewModelScope.launch {
            context.settingsDataStore.edit {
                it[DEFAULT_FEED_URI] = uri
                it[DEFAULT_FEED_NAME] = displayName
                if (avatar != null) it[DEFAULT_FEED_AVATAR] = avatar
                else it.remove(DEFAULT_FEED_AVATAR)
            }
        }
    }
}
