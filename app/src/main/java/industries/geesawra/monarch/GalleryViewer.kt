package industries.geesawra.monarch

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            var altExpanded by remember { mutableStateOf(false) }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { altExpanded = false }
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
                    model = imageUrls[page].fullSize,
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
            val currentAlt = imageUrls.getOrNull(pagerState.currentPage)?.alt.orEmpty()
            if (currentAlt.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable { altExpanded = !altExpanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .then(if (altExpanded) Modifier.fillMaxWidth() else Modifier),
                ) {
                    if (altExpanded) {
                        Text(
                            text = currentAlt,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    } else {
                        Text(
                            text = "ALT",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

suspend fun downloadImage(context: Context, imageUrl: HttpUrl) {
    Toast.makeText(context, "Download not yet implemented", Toast.LENGTH_SHORT).show()
}