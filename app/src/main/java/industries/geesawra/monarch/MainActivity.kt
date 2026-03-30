package industries.geesawra.monarch

import android.app.Application
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import androidx.compose.foundation.isSystemInDarkTheme
import industries.geesawra.monarch.datalayer.BlueskyConn
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SettingsViewModel
import industries.geesawra.monarch.datalayer.ThemeMode
import industries.geesawra.monarch.datalayer.TimelineViewModel
import industries.geesawra.monarch.ui.theme.MonarchTheme
import sh.christian.ozone.api.Did


@HiltAndroidApp
class Application : Application()

enum class ViewList() {
    Login,
    Main,
    ShowThread,
    Profile,
    Settings,
}

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val firstLoadDone = remember { mutableStateOf(false) }
            val settingsViewModel = hiltViewModel<SettingsViewModel>()
            val settings = settingsViewModel.settingsState

            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            MonarchTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                val context = LocalContext.current
                SingletonImageLoader.setSafe {
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
                                .maxSizePercent(0.05)
                                .build()
                        }
                        .build()
                }

                val conn = BlueskyConn(LocalContext.current)
                val timelineViewModel = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
                    creationCallback = { factory ->
                        factory.create(conn)
                    }
                )
                val navController = rememberNavController()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    timelineViewModel.loadSession()
                    if (!timelineViewModel.uiState.sessionChecked) {
                        Box(
                            modifier = Modifier.fillMaxSize(), // Make the Box take the full available space
                            contentAlignment = Alignment.Center // Align content (LoginView) to the center
                        ) {
                        }

                        return@Surface
                    }

                    val initialRoute =
                        if (timelineViewModel.uiState.authenticated) ViewList.Main.name else ViewList.Login.name

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
                                    timelineViewModel.openProfile(did)
                                    navController.navigate(ViewList.Profile.name)
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
                                    val df = settings.defaultFeed
                                    timelineViewModel.applyFeedState(df.uri, df.displayName, df.avatar)
                                    timelineViewModel.fetchAllNewData {
                                        firstLoadDone.value = true
                                    }
                                }
                            )
                        }
                        composable(route = ViewList.ShowThread.name) {
                            ThreadView(
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.statusBars),
                                timelineViewModel = timelineViewModel,
                                settingsState = settings,
                                coroutineScope = rememberCoroutineScope(),
                                backButton = {
                                    navController.popBackStack()
                                },
                                onProfileTap = { did ->
                                    timelineViewModel.openProfile(did)
                                    navController.navigate(ViewList.Profile.name)
                                },
                            )
                        }
                        composable(route = ViewList.Profile.name) {
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
                                onProfileTap = { did ->
                                    timelineViewModel.openProfile(did)
                                    navController.navigate(ViewList.Profile.name)
                                },
                                onSettingsTap = {
                                    navController.navigate(ViewList.Settings.name)
                                },
                            )
                        }
                        composable(route = ViewList.Settings.name) {
                            SettingsView(
                                settingsViewModel = settingsViewModel,
                                timelineViewModel = timelineViewModel,
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
                }
            }
        }
    }
}

