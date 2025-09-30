package industries.geesawra.jerryno

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

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
        GalleryViewer(
            imageUrls = images,
            initialPage = it
        ) {
            galleryVisible.value = null
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        // This automatically adds 4.dp of space between each image
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // We take the first 4 images and give them each a weight
        images.take(4).forEachIndexed { idx, image ->
            AsyncImage(
                model = image.url,
                contentDescription = image.alt,
                contentScale = ContentScale.Crop, // Fills the space
                modifier = Modifier
                    .clickable {
                        galleryVisible.value = idx
                    }
                    // 1. Give each image an equal share of the width
                    .weight(1f)
                    // 3. Apply rounded corners
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}