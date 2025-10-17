package industries.geesawra.monarch

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
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
import industries.geesawra.monarch.datalayer.BlueskyConn
import industries.geesawra.monarch.datalayer.TimelineViewModel
import industries.geesawra.monarch.ui.theme.MonarchTheme


@HiltAndroidApp
class Application : Application()

enum class ViewList() {
    Login,
    Main,
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
            MonarchTheme {
                val context = LocalContext.current
                SingletonImageLoader.setSafe {
                    ImageLoader.Builder(context)
                        .components { add(OkHttpNetworkFetcherFactory()) }
                        .memoryCache {
                            MemoryCache.Builder()
                                .maxSizePercent(context, 0.25)
                                .build()
                        }
                        .diskCache {
                            DiskCache.Builder()
                                .directory(context.cacheDir.resolve("image_cache"))
                                .maxSizePercent(0.02)
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
                        popExitTransition = {
                            scaleOut(
                                targetScale = 0.9f,
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            )
                        },
                        popEnterTransition = {
                            EnterTransition.None
                        },
                    ) {
                        composable(route = ViewList.Main.name) {
                            MainView(
                                timelineViewModel = timelineViewModel,
                                coroutineScope = rememberCoroutineScope(),
                                onLoginError = {
                                    navController.navigate(ViewList.Login.name)
                                }
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
                                    LoginView {
                                        navController.navigate(ViewList.Main.name)
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

