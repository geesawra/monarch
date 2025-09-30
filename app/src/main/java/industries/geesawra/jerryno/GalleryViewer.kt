package industries.geesawra.jerryno

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun GalleryViewer(
    imageUrls: List<Image>, // Now takes a list of URLs
    initialPage: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            val pagerState = rememberPagerState(initialPage = initialPage) {
                imageUrls.size
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableAsyncImage(
                    state = rememberZoomableImageState(
                        zoomableState = rememberZoomableState(
                            zoomSpec = ZoomSpec(
                                maxZoomFactor = 8f,
                                overzoomEffect = OverzoomEffect.RubberBanding,
                            )
                        )
                    ),
                    model = imageUrls[page].url,
                    contentDescription = imageUrls[page].alt,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onClick = { onDismiss() },
                    onLongClick = {
                        coroutineScope.launch {
                            downloadImage(context, imageUrls[page].url.toHttpUrl())
                        }
                    }
                )
            }
        }
    }
}

suspend fun downloadImage(context: Context, imageUrl: HttpUrl) {
    Toast.makeText(context, "Don't remember to implement download!!", Toast.LENGTH_LONG).show()
//
//    val result = context.imageLoader.execute(
//        ImageRequest.Builder(context)
//            .data(imageUrl)
//            .build()
//    )
//    if (result is SuccessResult) {
//        val cacheKey = result.diskCacheKey ?: error("image wasn't saved to disk")
//        val diskCache = context.imageLoader.diskCache!!
//        diskCache.openSnapshot(cacheKey)!!.use {
//        }
//    }
}