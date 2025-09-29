package industries.geesawra.jerryno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
//fun PostImageGallery(
//    modifier: Modifier = Modifier,
//    images: List<Image>,
//) {
//    val columns = when (images.size) {
//        1 -> GridCells.Fixed(1) // One image gets one full-width column
//        else -> GridCells.Fixed(2) // Two or more images get two columns
//    }
//
//    LazyVerticalGrid(
//        horizontalArrangement = Arrangement.spacedBy(8.dp),
//        verticalArrangement = Arrangement.spacedBy(8.dp),
//        modifier = modifier,
//        columns = columns,
//        userScrollEnabled = false,
//        content = {
//            items(images.size) { index ->
//                val img = images[index]
//
//                AsyncImage(
//                    model = ImageRequest.Builder(LocalContext.current)
//                        .data(img.url)
//                        .crossfade(true)
//                        .build(),
//                    contentScale = ContentScale.Crop,
//                    contentDescription = img.alt,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .heightIn(max = 75.dp)
//                        .clip(RoundedCornerShape(12.dp))
//                )
//            }
//        }
//    )
//}
fun PostImageGallery(
    modifier: Modifier = Modifier,
    images: List<Image>,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        // This automatically adds 4.dp of space between each image
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // We take the first 4 images and give them each a weight
        images.take(4).forEach { image ->
            AsyncImage(
                model = image.url,
                contentDescription = image.alt,
                contentScale = ContentScale.Crop, // Fills the space
                modifier = Modifier
                    // 1. Give each image an equal share of the width
                    .weight(1f)
                    // 3. Apply rounded corners
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}