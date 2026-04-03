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
import industries.geesawra.monarch.BuildConfig
import jakarta.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal val Context.settingsDataStore by preferencesDataStore("settings")

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
    val dynamicColor: Boolean = false,
    val postTextSize: PostTextSize = PostTextSize.Medium,
    val avatarShape: AvatarShape = AvatarShape.Circle,
    val replyFilterMode: ReplyFilterMode = ReplyFilterMode.OnlyFilterDeepThreads,
    val showLabels: Boolean = true,
    val defaultFeed: DefaultFeed = DefaultFeed(),
    val forceCompactLayout: Boolean = false,
    val swipeableFeeds: Boolean = true,
    val autoLikeOnReply: Boolean = false,
    val autoLikeOnScroll: Boolean = false,
    val pushNotificationsEnabled: Boolean = false,
    val notificationServerUrl: String = BuildConfig.PUSH_SERVER_URL,
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
        private val SWIPEABLE_FEEDS = stringPreferencesKey("swipeable_feeds")
        private val AUTO_LIKE_ON_REPLY = stringPreferencesKey("auto_like_on_reply")
        private val AUTO_LIKE_ON_SCROLL = stringPreferencesKey("auto_like_on_scroll")
        private val PUSH_NOTIFICATIONS_ENABLED = stringPreferencesKey("push_notifications_enabled")
        internal val NOTIFICATION_SERVER_URL = stringPreferencesKey("notification_server_url")
    }

    var settingsState by mutableStateOf(SettingsState())
        private set

    init {
        viewModelScope.launch {
            context.settingsDataStore.data.map { prefs ->
                val narrowScreen = context.resources.configuration.screenWidthDp <= 360
                SettingsState(
                    themeMode = prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
                    dynamicColor = prefs[DYNAMIC_COLOR]?.toBooleanStrictOrNull() ?: false,
                    postTextSize = prefs[POST_TEXT_SIZE]?.let { runCatching { PostTextSize.valueOf(it) }.getOrNull() } ?: if (narrowScreen) PostTextSize.Small else PostTextSize.Medium,
                    avatarShape = prefs[AVATAR_SHAPE]?.let { runCatching { AvatarShape.valueOf(it) }.getOrNull() } ?: AvatarShape.Circle,
                    replyFilterMode = prefs[REPLY_FILTER_MODE]?.let { runCatching { ReplyFilterMode.valueOf(it) }.getOrNull() } ?: ReplyFilterMode.OnlyFilterDeepThreads,
                    showLabels = prefs[SHOW_LABELS]?.toBooleanStrictOrNull() ?: !narrowScreen,
                    defaultFeed = DefaultFeed(
                        uri = prefs[DEFAULT_FEED_URI] ?: "following",
                        displayName = prefs[DEFAULT_FEED_NAME] ?: "Following",
                        avatar = prefs[DEFAULT_FEED_AVATAR],
                    ),
                    forceCompactLayout = prefs[FORCE_COMPACT_LAYOUT]?.toBooleanStrictOrNull() ?: false,
                    swipeableFeeds = prefs[SWIPEABLE_FEEDS]?.toBooleanStrictOrNull() ?: !narrowScreen,
                    autoLikeOnReply = prefs[AUTO_LIKE_ON_REPLY]?.toBooleanStrictOrNull() ?: false,
                    autoLikeOnScroll = prefs[AUTO_LIKE_ON_SCROLL]?.toBooleanStrictOrNull() ?: false,
                    pushNotificationsEnabled = prefs[PUSH_NOTIFICATIONS_ENABLED]?.toBooleanStrictOrNull() ?: false,
                    notificationServerUrl = prefs[NOTIFICATION_SERVER_URL] ?: BuildConfig.PUSH_SERVER_URL,
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

    fun setSwipeableFeeds(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[SWIPEABLE_FEEDS] = enabled.toString() }
        }
    }

    fun setAutoLikeOnScroll(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[AUTO_LIKE_ON_SCROLL] = enabled.toString() }
        }
    }

    fun setAutoLikeOnReply(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[AUTO_LIKE_ON_REPLY] = enabled.toString() }
        }
    }

    fun setPushNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[PUSH_NOTIFICATIONS_ENABLED] = enabled.toString() }
        }
    }

    fun setNotificationServerUrl(url: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[NOTIFICATION_SERVER_URL] = url }
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
