package industries.geesawra.monarch.datalayer

import android.content.Context
import androidx.compose.runtime.Stable
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

enum class AppTheme {
    Monarch,
    Bluesky,
    Witchsky,
    Blacksky,
    Deer,
    Zeppelin,
    Kitty,
    Reddwarf,
    Catppuccin,
    Evergarden,
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
    val appTheme: AppTheme = AppTheme.Monarch,
    val dynamicColor: Boolean = false,
    val postTextSize: PostTextSize = PostTextSize.Medium,
    val avatarShape: AvatarShape = AvatarShape.Circle,
    val replyFilterMode: ReplyFilterMode = ReplyFilterMode.OnlyFilterDeepThreads,
    val showLabels: Boolean = true,
    val showPronounsInPosts: Boolean = false,
    val defaultFeed: DefaultFeed = DefaultFeed(),
    val defaultAppviewProxy: String = BLUESKY_APPVIEW_DID,
    val forceCompactLayout: Boolean = false,
    val swipeableFeeds: Boolean = true,
    val autoLikeOnReply: Boolean = false,
    val autoLikeOnScroll: Boolean = false,
    val aiEnabled: Boolean = true,
    val aiAltTextEnabled: Boolean = true,
    val requireAltText: Boolean = false,
    val translationEnabled: Boolean = true,
    val targetTranslationLanguage: String = "en",
    val openLinksInBrowser: Boolean = false,
    val pushNotificationsEnabled: Boolean = false,
    val notificationServerUrl: String = BuildConfig.PUSH_SERVER_URL,
    val loaded: Boolean = false,
)

const val BLUESKY_APPVIEW_DID = "did:web:api.bsky.app#bsky_appview"
const val BLACKSKY_APPVIEW_DID = "did:web:api.blacksky.community#bsky_appview"

val APPVIEW_PROXY_OPTIONS = listOf(
    "Bluesky" to BLUESKY_APPVIEW_DID,
    "Blacksky" to BLACKSKY_APPVIEW_DID,
)

@Stable
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val DYNAMIC_COLOR = stringPreferencesKey("dynamic_color")
        private val POST_TEXT_SIZE = stringPreferencesKey("post_text_size")
        private val AVATAR_SHAPE = stringPreferencesKey("avatar_shape")
        private val REPLY_FILTER_MODE = stringPreferencesKey("reply_filter_mode")
        private val SHOW_LABELS = stringPreferencesKey("show_labels")
        private val SHOW_PRONOUNS_IN_POSTS = stringPreferencesKey("show_pronouns_in_posts")
        private val DEFAULT_APPVIEW_PROXY = stringPreferencesKey("default_appview_proxy")
        private val DEFAULT_FEED_URI = stringPreferencesKey("default_feed_uri")
        private val DEFAULT_FEED_NAME = stringPreferencesKey("default_feed_name")
        private val DEFAULT_FEED_AVATAR = stringPreferencesKey("default_feed_avatar")
        private val FORCE_COMPACT_LAYOUT = stringPreferencesKey("force_compact_layout")
        private val SWIPEABLE_FEEDS = stringPreferencesKey("swipeable_feeds")
        private val AUTO_LIKE_ON_REPLY = stringPreferencesKey("auto_like_on_reply")
        private val AUTO_LIKE_ON_SCROLL = stringPreferencesKey("auto_like_on_scroll")
        private val AI_ENABLED = stringPreferencesKey("ai_enabled")
        private val AI_ALT_TEXT_ENABLED = stringPreferencesKey("ai_alt_text_enabled")
        private val REQUIRE_ALT_TEXT = stringPreferencesKey("require_alt_text")
        private val TRANSLATION_ENABLED = stringPreferencesKey("translation_enabled")
        private val TARGET_TRANSLATION_LANGUAGE = stringPreferencesKey("target_translation_language")
        private val PUSH_NOTIFICATIONS_ENABLED = stringPreferencesKey("push_notifications_enabled")
        private val OPEN_LINKS_IN_BROWSER = stringPreferencesKey("open_links_in_browser")
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
                    appTheme = prefs[APP_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.Monarch,
                    dynamicColor = prefs[DYNAMIC_COLOR]?.toBooleanStrictOrNull() ?: false,
                    postTextSize = prefs[POST_TEXT_SIZE]?.let { runCatching { PostTextSize.valueOf(it) }.getOrNull() } ?: if (narrowScreen) PostTextSize.Small else PostTextSize.Medium,
                    avatarShape = prefs[AVATAR_SHAPE]?.let { runCatching { AvatarShape.valueOf(it) }.getOrNull() } ?: AvatarShape.Circle,
                    replyFilterMode = prefs[REPLY_FILTER_MODE]?.let { runCatching { ReplyFilterMode.valueOf(it) }.getOrNull() } ?: ReplyFilterMode.OnlyFilterDeepThreads,
                    showLabels = prefs[SHOW_LABELS]?.toBooleanStrictOrNull() ?: !narrowScreen,
                    showPronounsInPosts = prefs[SHOW_PRONOUNS_IN_POSTS]?.toBooleanStrictOrNull() ?: false,
                    defaultAppviewProxy = prefs[DEFAULT_APPVIEW_PROXY] ?: BLUESKY_APPVIEW_DID,
                    defaultFeed = DefaultFeed(
                        uri = prefs[DEFAULT_FEED_URI] ?: "following",
                        displayName = prefs[DEFAULT_FEED_NAME] ?: "Following",
                        avatar = prefs[DEFAULT_FEED_AVATAR],
                    ),
                    forceCompactLayout = prefs[FORCE_COMPACT_LAYOUT]?.toBooleanStrictOrNull() ?: false,
                    swipeableFeeds = prefs[SWIPEABLE_FEEDS]?.toBooleanStrictOrNull() ?: !narrowScreen,
                    autoLikeOnReply = prefs[AUTO_LIKE_ON_REPLY]?.toBooleanStrictOrNull() ?: false,
                    autoLikeOnScroll = prefs[AUTO_LIKE_ON_SCROLL]?.toBooleanStrictOrNull() ?: false,
                    aiEnabled = prefs[AI_ENABLED]?.toBooleanStrictOrNull() ?: true,
                    aiAltTextEnabled = prefs[AI_ALT_TEXT_ENABLED]?.toBooleanStrictOrNull() ?: true,
                    requireAltText = prefs[REQUIRE_ALT_TEXT]?.toBooleanStrictOrNull() ?: false,
                    translationEnabled = prefs[TRANSLATION_ENABLED]?.toBooleanStrictOrNull() ?: true,
                    targetTranslationLanguage = prefs[TARGET_TRANSLATION_LANGUAGE] ?: "en",
                    openLinksInBrowser = prefs[OPEN_LINKS_IN_BROWSER]?.toBooleanStrictOrNull() ?: false,
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

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[APP_THEME] = theme.name }
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

    fun setShowPronounsInPosts(show: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[SHOW_PRONOUNS_IN_POSTS] = show.toString() }
        }
    }

    fun setDefaultAppviewProxy(did: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[DEFAULT_APPVIEW_PROXY] = did }
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

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[AI_ENABLED] = enabled.toString() }
        }
    }

    fun setAiAltTextEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[AI_ALT_TEXT_ENABLED] = enabled.toString() }
        }
    }

    fun setRequireAltText(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[REQUIRE_ALT_TEXT] = enabled.toString() }
        }
    }

    fun setTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[TRANSLATION_ENABLED] = enabled.toString() }
        }
    }

    fun setTargetTranslationLanguage(code: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[TARGET_TRANSLATION_LANGUAGE] = code }
        }
    }

    fun setOpenLinksInBrowser(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[OPEN_LINKS_IN_BROWSER] = enabled.toString() }
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
