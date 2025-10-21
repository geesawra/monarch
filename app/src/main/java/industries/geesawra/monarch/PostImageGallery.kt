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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

data class Image(
    val url: String,
    val alt: String,
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
            Row(
                modifier = modifier
                    .fillMaxWidth()
            ) {
                DeletableImageView(
                    modifier = Modifier.weight(1f),
                    image = imagesToDisplay[0],
                    originalIndex = 0,
                    onCrossClick = onCrossClick,
                    onMediaClick = { galleryVisible.value = 0 })
            }
        }

        2 -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min), // Apply IntrinsicSize.Min to the Row
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeletableImageView(
                        modifier = Modifier.weight(1f),
                        image = imagesToDisplay[2],
                        originalIndex = 2,
                        onCrossClick = onCrossClick,
                        onMediaClick = { galleryVisible.value = it })
                    Spacer(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight() // Spacer fills the height of the intrinsically sized Row
                    )
                }
            }
        }

        else -> { // Handles 4 images
            Column(
                modifier = modifier
                    .fillMaxWidth()
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
    originalIndex: Int,
    onCrossClick: ((Int) -> Unit)? = null,
    onMediaClick: (Int) -> Unit,
) {
    DeletableMediaView(
        modifier = modifier,
        originalIndex = originalIndex,
        onCrossClick = onCrossClick,
        onMediaClick = onMediaClick,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image.url)
                .crossfade(true)
                .build(),
            contentDescription = image.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .aspectRatio(1f) // Changed from fillMaxSize() to make it square
                .clip(RoundedCornerShape(12.dp))
                .clickable { onMediaClick(originalIndex) }
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
            .aspectRatio(1f) // Changed from fillMaxSize() to make it square
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
