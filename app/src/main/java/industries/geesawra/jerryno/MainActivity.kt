package industries.geesawra.jerryno

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
import industries.geesawra.jerryno.datalayer.BlueskyConn
import industries.geesawra.jerryno.datalayer.TimelineViewModel
import industries.geesawra.jerryno.ui.theme.JerryNoTheme


@HiltAndroidApp
class Application : Application()

enum class TimelineScreen() {
    Login,
    Timeline,
    Compose
}

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JerryNoTheme {
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
                        if (timelineViewModel.uiState.authenticated) TimelineScreen.Timeline.name else TimelineScreen.Login.name

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
                        composable(route = TimelineScreen.Timeline.name) {
                            TimelineView(
                                timelineViewModel = timelineViewModel,
                                coroutineScope = rememberCoroutineScope()
                            )
                        }
                        composable(route = TimelineScreen.Compose.name) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background // Set compose screen background
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Compose",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                    )
                                }
                            }
                        }
                        composable(route = TimelineScreen.Login.name) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background // Set login screen background
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoginView {
                                        navController.navigate(TimelineScreen.Timeline.name)
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

@Composable
fun ExoPlayerView(uri: String, modifier: Modifier) {
    // Get the current context
    val context = LocalContext.current

    // Initialize ExoPlayer
    val exoPlayer = ExoPlayer.Builder(context).build()

    // Create a MediaSource
    val mediaSource = remember(uri) {
        MediaItem.fromUri(uri)
    }

    // Set MediaSource to ExoPlayer
    LaunchedEffect(mediaSource) {
        exoPlayer.setMediaItem(mediaSource)
        exoPlayer.prepare()
    }

    // Manage lifecycle events
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Use AndroidView to embed an Android View (PlayerView) into Compose
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
            }
        },
        modifier = modifier
    )
}

