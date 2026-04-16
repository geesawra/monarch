package industries.geesawra.monarch

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import androidx.compose.foundation.isSystemInDarkTheme
import industries.geesawra.monarch.datalayer.AltTextGenerator
import industries.geesawra.monarch.datalayer.BlueskyConn
import industries.geesawra.monarch.datalayer.PushNotificationManager
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SettingsViewModel
import jakarta.inject.Inject
import industries.geesawra.monarch.datalayer.ThemeMode
import industries.geesawra.monarch.datalayer.TimelineViewModel
import industries.geesawra.monarch.ui.theme.MonarchTheme
import industries.geesawra.monarch.datalayer.SkeetData
import kotlin.time.ExperimentalTime
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import androidx.navigation.NavType
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder


@HiltAndroidApp
class Application : Application(), SingletonImageLoader.Factory {
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate() {
        super.onCreate()
        industries.geesawra.monarch.datalayer.MessagingService.createNotificationChannel(this)
        // androidx.compose.runtime.tracing.ComposeTracingInitializer().create(this) // composition tracing - enable for profiling
        androidx.compose.ui.contentcapture.ContentCaptureManager.isEnabled = false
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                add(coil3.gif.GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.15)
                    .build()
            }
            .fetcherCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(8))
            .decoderCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(4))
            .build()
}

enum class ViewList() {
    Login,
    Main,
    ShowThread,
    Profile,
    Settings,
    MutedWords,
    FollowersList,
    DocumentList,
    DocumentReader,
}

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
class MainActivity : ComponentActivity() {
    @Inject lateinit var pushNotificationManager: PushNotificationManager
    @Inject lateinit var altTextGenerator: AltTextGenerator
    private val currentIntent = mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent.value = intent
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentIntent.value = intent

        val baselineProfileMode = intent.getBooleanExtra("baseline_profile_mode", false)

        setContent {
            val firstLoadDone = remember { mutableStateOf(false) }
            val settingsViewModel = hiltViewModel<SettingsViewModel>()
            val settings = settingsViewModel.settingsState

            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            CompositionLocalProvider(LocalBaselineProfileMode provides baselineProfileMode) {
            MonarchTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                appTheme = settings.appTheme,
            ) {
                val context = LocalContext.current
                val conn = BlueskyConn(context)
                val timelineViewModel = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
                    creationCallback = { factory ->
                        factory.create(conn)
                    }
                )
                val navController = rememberNavController()

                // OAuth deep-link callback: industries.geesawra.monarch:/oauth/callback?code=...&state=...
                // Note the SINGLE slash — RFC 8252 form has no authority/host, just scheme + path.
                val pendingIntent = currentIntent.value
                LaunchedEffect(pendingIntent) {
                    val uri = pendingIntent?.data ?: return@LaunchedEffect
                    if (uri.scheme != "industries.geesawra.monarch" || uri.path != "/oauth/callback") {
                        return@LaunchedEffect
                    }
                    val code = uri.getQueryParameter("code")
                    val state = uri.getQueryParameter("state")
                    val errorCode = uri.getQueryParameter("error")
                    if (errorCode != null) {
                        val description = uri.getQueryParameter("error_description") ?: errorCode
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "OAuth failed: $description",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        conn.clearInFlightOAuthState()
                        currentIntent.value = null
                        return@LaunchedEffect
                    }
                    if (code.isNullOrBlank() || state.isNullOrBlank()) {
                        currentIntent.value = null
                        return@LaunchedEffect
                    }
                    conn.oauthCompleteLogin(code, state).onSuccess { account ->
                        timelineViewModel.completeOAuthLogin(account) {
                            navController.navigate(ViewList.Main.name) {
                                popUpTo(ViewList.Login.name) { inclusive = true }
                            }
                        }
                    }.onFailure { err ->
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            err.message ?: "OAuth login failed",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    currentIntent.value = null
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    timelineViewModel.loadSession()
                    if (!timelineViewModel.sessionChecked) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularWavyProgressIndicator()
                        }

                        return@Surface
                    }

                    val initialRoute =
                        if (timelineViewModel.authenticated) ViewList.Main.name else ViewList.Login.name

                    NavHost(
                        navController = navController,
                        startDestination = initialRoute,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it })
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { -it })
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it })
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it })
                        },
                    ) {
                        composable(route = ViewList.Main.name) {
                            MainView(
                                timelineViewModel = timelineViewModel,
                                settingsState = settings,
                                coroutineScope = rememberCoroutineScope(),
                                onLoginError = {
                                    navController.navigate(ViewList.Login.name)
                                },
                                onThreadTap = {
                                    navController.navigate(ViewList.ShowThread.name)
                                },
                                onProfileTap = { did ->
                                    navController.navigate("Profile/${did.did}")
                                },
                                onSettingsTap = {
                                    navController.navigate(ViewList.Settings.name)
                                },
                                onAddAccount = {
                                    navController.navigate(ViewList.Login.name)
                                },
                                onFirstLoad = {
                                    if (firstLoadDone.value || !settings.loaded) {
                                        return@MainView
                                    }
                                    timelineViewModel.fetchAllNewData {
                                        firstLoadDone.value = true
                                    }
                                }
                            )
                        }
                        composable(route = ViewList.ShowThread.name) {
                            val threadCoroutineScope = rememberCoroutineScope()
                            val threadScaffoldState = rememberBottomSheetScaffoldState(
                                bottomSheetState = rememberModalBottomSheetState(
                                    skipPartiallyExpanded = true,
                                )
                            )
                            val threadInReplyTo = remember { mutableStateOf<SkeetData?>(null) }
                            val threadIsQuotePost = remember { mutableStateOf(false) }
                            val threadScrollState = rememberScrollState()
                            val threadContext = LocalContext.current
                            val threadTextFieldState = rememberTextFieldState()
                            val threadMediaSelected = remember { mutableStateOf(listOf<android.net.Uri>()) }
                            val threadMediaSelectedIsVideo = remember { mutableStateOf(false) }
                            val threadMediaAltTexts = remember { mutableStateOf(mapOf<android.net.Uri, String>()) }
                            val threadThreadgateRules = remember { mutableStateOf<List<app.bsky.feed.ThreadgateAllowUnion>?>(null) }
                            val threadLinkPreview = remember { mutableStateOf<industries.geesawra.monarch.datalayer.LinkPreviewData?>(null) }

                            BottomSheetScaffold(
                                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                                scaffoldState = threadScaffoldState,
                                sheetPeekHeight = 0.dp,
                                sheetDragHandle = {},
                                sheetContent = {
                                    ComposeView(
                                        context = threadContext,
                                        coroutineScope = threadCoroutineScope,
                                        timelineViewModel = timelineViewModel,
                                        settingsState = settings,
                                        inReplyTo = threadInReplyTo,
                                        isQuotePost = threadIsQuotePost,
                                        scaffoldState = threadScaffoldState,
                                        scrollState = threadScrollState,
                                        textfieldState = threadTextFieldState,
                                        mediaSelected = threadMediaSelected,
                                        mediaSelectedIsVideo = threadMediaSelectedIsVideo,
                                        mediaAltTexts = threadMediaAltTexts,
                                        threadgateRules = threadThreadgateRules,
                                        linkPreview = threadLinkPreview,
                                    )
                                },
                            ) {
                                ThreadView(
                                    timelineViewModel = timelineViewModel,
                                    settingsState = settings,
                                    coroutineScope = threadCoroutineScope,
                                    backButton = {
                                        timelineViewModel.popThread()
                                        navController.popBackStack()
                                    },
                                    onProfileTap = { did ->
                                        navController.navigate("Profile/${did.did}")
                                    },
                                    onReplyTap = { skeetData, quotePost ->
                                        threadInReplyTo.value = skeetData
                                        threadIsQuotePost.value = quotePost
                                        threadCoroutineScope.launch {
                                            threadScaffoldState.bottomSheetState.expand()
                                        }
                                    },
                                    onThreadTap = {
                                        navController.navigate(ViewList.ShowThread.name)
                                    },
                                )
                            }
                        }
                        composable(
                            route = "Profile/{did}",
                            arguments = listOf(navArgument("did") { type = NavType.StringType }),
                        ) { backStackEntry ->
                            val did = Did(backStackEntry.arguments!!.getString("did")!!)
                            LaunchedEffect(did) {
                                timelineViewModel.openProfile(did)
                            }
                            ProfileView(
                                timelineViewModel = timelineViewModel,
                                settingsState = settings,
                                coroutineScope = rememberCoroutineScope(),
                                backButton = {
                                    navController.popBackStack()
                                },
                                onThreadTap = {
                                    navController.navigate(ViewList.ShowThread.name)
                                },
                                onProfileTap = { profileDid ->
                                    navController.navigate("Profile/${profileDid.did}")
                                },
                                onFollowersTap = { showFollowers, name ->
                                    val encodedName = URLEncoder.encode(name, "UTF-8")
                                    navController.navigate("FollowersList/${did.did}/$showFollowers/$encodedName")
                                },
                                onPublicationTap = {
                                    navController.navigate(ViewList.DocumentList.name)
                                },
                            )
                        }
                        composable(
                            route = "FollowersList/{did}/{showFollowers}/{name}",
                            arguments = listOf(
                                navArgument("did") { type = NavType.StringType },
                                navArgument("showFollowers") { type = NavType.BoolType },
                                navArgument("name") { type = NavType.StringType },
                            ),
                        ) { backStackEntry ->
                            val did = Did(backStackEntry.arguments!!.getString("did")!!)
                            val showFollowers = backStackEntry.arguments!!.getBoolean("showFollowers")
                            val name = URLDecoder.decode(backStackEntry.arguments!!.getString("name")!!, "UTF-8")
                            LaunchedEffect(did) {
                                timelineViewModel.openFollowersList(did, showFollowers, name)
                                timelineViewModel.fetchFollowers(did, fresh = true)
                                timelineViewModel.fetchFollows(did, fresh = true)
                            }
                            FollowersListView(
                                timelineViewModel = timelineViewModel,
                                backButton = {
                                    navController.popBackStack()
                                },
                                onProfileTap = { profileDid ->
                                    navController.navigate("Profile/${profileDid.did}")
                                },
                            )
                        }
                        composable(route = ViewList.DocumentList.name) {
                            DocumentListView(
                                timelineViewModel = timelineViewModel,
                                settingsState = settings,
                                backButton = { navController.popBackStack() },
                                onDocumentTap = {
                                    navController.navigate(ViewList.DocumentReader.name)
                                },
                            )
                        }
                        composable(route = ViewList.DocumentReader.name) {
                            DocumentReaderView(
                                timelineViewModel = timelineViewModel,
                                settingsState = settings,
                                backButton = {
                                    navController.popBackStack()
                                },
                            )
                        }
                        composable(route = ViewList.Settings.name) {
                            SettingsView(
                                settingsViewModel = settingsViewModel,
                                timelineViewModel = timelineViewModel,
                                pushNotificationManager = pushNotificationManager,
                                altTextGenerator = altTextGenerator,
                                backButton = {
                                    navController.popBackStack()
                                },
                                onLogout = {
                                    timelineViewModel.logout {
                                        if (timelineViewModel.accounts.isEmpty()) {
                                            navController.navigate(ViewList.Login.name) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate(ViewList.Main.name) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                },
                                onMutedWordsTap = {
                                    navController.navigate(ViewList.MutedWords.name)
                                },
                            )
                        }

                        composable(route = ViewList.MutedWords.name) {
                            MutedWordsView(
                                timelineViewModel = timelineViewModel,
                                backButton = {
                                    navController.popBackStack()
                                },
                            )
                        }

                        composable(route = ViewList.Login.name) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background // Set login screen background
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoginView(
                                        blueskyConn = conn,
                                        onSettingsTap = {
                                            navController.navigate(ViewList.Settings.name)
                                        },
                                    ) {
                                        timelineViewModel.onNewLogin()
                                        firstLoadDone.value = false
                                        navController.navigate(ViewList.Main.name) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val notificationIntent = currentIntent.value
                    val isAuthenticated = timelineViewModel.authenticated
                    @OptIn(ExperimentalTime::class)
                    LaunchedEffect(notificationIntent, isAuthenticated) {
                        if (!isAuthenticated) return@LaunchedEffect
                        val ni = notificationIntent ?: return@LaunchedEffect

                        val deepLinkUri = ni.data
                        if (deepLinkUri != null) {
                            val host = deepLinkUri.host
                            if (host == "bsky.app" || host == "witchsky.app") {
                                val segments = deepLinkUri.pathSegments
                                if (segments.size >= 2 && segments[0] == "profile") {
                                    val actor = segments[1]
                                    if (segments.size >= 4 && segments[2] == "post") {
                                        val rkey = segments[3]
                                        val atUri = "at://$actor/app.bsky.feed.post/$rkey"
                                        timelineViewModel.startThread(SkeetData(uri = AtUri(atUri)))
                                        navController.navigate(ViewList.ShowThread.name)
                                    } else {
                                        navController.navigate("Profile/$actor")
                                    }
                                }
                                ni.data = null
                                return@LaunchedEffect
                            }
                        }

                        val kind = ni.getStringExtra("notification_kind") ?: return@LaunchedEffect
                        val notifUri = ni.getStringExtra("notification_uri")
                        val notifAuthorDid = ni.getStringExtra("notification_author_did")
                        val notifId = ni.getIntExtra("notification_id", -1)

                        when (kind) {
                            "group_summary" -> {
                                timelineViewModel.pendingNotificationsTab = true
                                navController.navigate(ViewList.Main.name) {
                                    popUpTo(ViewList.Main.name) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                            "app.bsky.graph.follow" -> {
                                val did = notifAuthorDid ?: return@LaunchedEffect
                                navController.navigate("Profile/$did")
                            }
                            "app.bsky.feed.like", "app.bsky.feed.repost", "app.bsky.feed.post", "app.bsky.feed.reply", "app.bsky.feed.mention", "app.bsky.feed.quote" -> {
                                val uri = notifUri ?: return@LaunchedEffect
                                timelineViewModel.startThread(SkeetData(uri = AtUri(uri)))
                                navController.navigate(ViewList.ShowThread.name)
                            }
                        }

                        if (notifId != -1) {
                            val nm = context.getSystemService(android.app.NotificationManager::class.java)
                            nm.cancel(notifId)
                        }
                        ni.removeExtra("notification_kind")
                    }
                }
            }
            }
        }
    }
}

