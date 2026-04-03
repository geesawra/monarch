package industries.geesawra.monarch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

data class Image(
    val url: String,
    val fullSize: String = "",
    val alt: String,
    val width: Long? = null,
    val height: Long? = null,
)

@Composable
fun PostImageGallery(
    modifier: Modifier = Modifier,
    images: List<Image>,
    onCrossClick: ((Int) -> Unit)? = null
) {
    val galleryVisible = remember { mutableStateOf<Int?>(null) }

    galleryVisible.value?.let {
        // Ensure the index is valid for the original images list
        if (it < images.size) {
            GalleryViewer(
                imageUrls = images, // Pass the full list to the viewer
                initialPage = it    // 'it' is the index in the original images list
            ) {
                galleryVisible.value = null
            }
        }
    }

    val imagesToDisplay = images.take(4)

    if (imagesToDisplay.isEmpty()) {
        return
    }

    when (imagesToDisplay.size) {
        1 -> {
            val img = imagesToDisplay[0]

            val aspectRatio = if (img.width != null && img.height != null) {
                img.width.toFloat() / img.height.toFloat()
            } else {
                null
            }

            DeletableImageView(
                modifier = modifier.fillMaxWidth().heightIn(max = 360.dp),
                image = img,
                originalIndex = 0,
                onCrossClick = onCrossClick,
                onMediaClick = { galleryVisible.value = 0 },
                aspectRatio = aspectRatio
            )
        }

        2 -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeletableImageView(
                    modifier = Modifier.weight(1f),
                    image = imagesToDisplay[0],
                    originalIndex = 0,
                    onCrossClick = onCrossClick,
                    onMediaClick = { galleryVisible.value = it })
                DeletableImageView(
                    modifier = Modifier.weight(1f),

                    image = imagesToDisplay[1],
                    originalIndex = 1,
                    onCrossClick = onCrossClick,
                    onMediaClick = { galleryVisible.value = it })
            }
        }

        3 -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeletableImageView(
                        modifier = Modifier.weight(1f),
                        image = imagesToDisplay[0],
                        originalIndex = 0,
                        onCrossClick = onCrossClick,
                        onMediaClick = { galleryVisible.value = it })
                    DeletableImageView(
                        modifier = Modifier.weight(1f),
                        image = imagesToDisplay[1],
                        originalIndex = 1,
                        onCrossClick = onCrossClick,
                        onMediaClick = { galleryVisible.value = it })
                }
                DeletableImageView(
                    modifier = Modifier.fillMaxWidth(),
                    image = imagesToDisplay[2],
                    originalIndex = 2,
                    aspectRatio = 2f,
                    onCrossClick = onCrossClick,
                    onMediaClick = { galleryVisible.value = it }
                )
            }
        }

        else -> { // Handles 4 images
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeletableImageView(
                        modifier = Modifier.weight(1f),
                        image = imagesToDisplay[0],
                        originalIndex = 0,
                        onCrossClick = onCrossClick,
                        onMediaClick = { galleryVisible.value = it })
                    DeletableImageView(
                        modifier = Modifier.weight(1f),
                        image = imagesToDisplay[1],
                        originalIndex = 1,
                        onCrossClick = onCrossClick,
                        onMediaClick = { galleryVisible.value = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeletableImageView(
                        modifier = Modifier.weight(1f),
                        image = imagesToDisplay[2],
                        originalIndex = 2,
                        onCrossClick = onCrossClick,
                        onMediaClick = { galleryVisible.value = it })
                    DeletableImageView(
                        modifier = Modifier.weight(1f),
                        image = imagesToDisplay[3],
                        originalIndex = 3,
                        onCrossClick = onCrossClick,
                        onMediaClick = { galleryVisible.value = it })
                }
            }
        }
    }
}

@Composable
private fun DeletableImageView(
    modifier: Modifier = Modifier,
    image: Image,
    aspectRatio: Float? = null,
    originalIndex: Int,
    onCrossClick: ((Int) -> Unit)? = null,
    onMediaClick: (Int) -> Unit,
) {
    DeletableMediaView(
        modifier = run {
            if (aspectRatio != null) {
                modifier
            } else {
                modifier.aspectRatio(1f)
            }
        },
        originalIndex = originalIndex,
        onCrossClick = onCrossClick,
        onMediaClick = onMediaClick,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image.url)
                .crossfade(true)
                .build(),
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            contentDescription = image.alt,
            contentScale = ContentScale.Crop,
            modifier = if (aspectRatio != null) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onMediaClick(originalIndex) }
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onMediaClick(originalIndex) }
            }
        )
    }
}

@Composable
fun DeletableMediaView(
    modifier: Modifier = Modifier,
    originalIndex: Int,
    onCrossClick: ((Int) -> Unit)? = null,
    onMediaClick: (Int) -> Unit,
    mediaView: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onMediaClick(originalIndex) }
    ) {

        mediaView()

        if (onCrossClick != null) {
            Button(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                onClick = {
                    onCrossClick(originalIndex)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove embed",
                )
            }
        }
    }
}
