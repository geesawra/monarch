package industries.geesawra.monarch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize // Added import
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight // Added import
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height // Added import
import androidx.compose.foundation.shape.RoundedCornerShape
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
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imagesToDisplay[0].url)
                        .crossfade(true)
                        .build(),
                    contentDescription = imagesToDisplay[0].alt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // Added aspect ratio for defined height
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { galleryVisible.value = 0 } // Index in original list
                )
            }
        }

        2 -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GalleryImageCell(
                    image = imagesToDisplay[0],
                    originalIndex = 0,
                    onImageClick = { galleryVisible.value = it })
                GalleryImageCell(
                    image = imagesToDisplay[1],
                    originalIndex = 1,
                    onImageClick = { galleryVisible.value = it })
            }
        }

        3 -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GalleryImageCell(
                        image = imagesToDisplay[0],
                        originalIndex = 0,
                        onImageClick = { galleryVisible.value = it })
                    GalleryImageCell(
                        image = imagesToDisplay[1],
                        originalIndex = 1,
                        onImageClick = { galleryVisible.value = it })
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min), // Apply IntrinsicSize.Min to the Row
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GalleryImageCell(
                        image = imagesToDisplay[2],
                        originalIndex = 2,
                        onImageClick = { galleryVisible.value = it })
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
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GalleryImageCell(
                        image = imagesToDisplay[0],
                        originalIndex = 0,
                        onImageClick = { galleryVisible.value = it })
                    GalleryImageCell(
                        image = imagesToDisplay[1],
                        originalIndex = 1,
                        onImageClick = { galleryVisible.value = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GalleryImageCell(
                        image = imagesToDisplay[2],
                        originalIndex = 2,
                        onImageClick = { galleryVisible.value = it })
                    GalleryImageCell(
                        image = imagesToDisplay[3],
                        originalIndex = 3,
                        onImageClick = { galleryVisible.value = it })
                }
            }
        }
    }
}

@Composable
private fun RowScope.GalleryImageCell(
    image: Image,
    originalIndex: Int, // Index in the original `images` list
    onImageClick: (Int) -> Unit
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(image.url)
            .crossfade(true)
            .build(),
        contentDescription = image.alt,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f) // Changed from fillMaxSize() to make it square
            .clip(RoundedCornerShape(12.dp))
            .clickable { onImageClick(originalIndex) }
    )
}

// Placeholder for GalleryViewer - ensure it's defined elsewhere
/*
@Composable
fun GalleryViewer(imageUrls: List<Image>, initialPage: Int, onDismiss: () -> Unit) {
    // ... your GalleryViewer implementation ...
}
*/
