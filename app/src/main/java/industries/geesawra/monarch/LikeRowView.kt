package industries.geesawra.monarch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.RepeatedNotification
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun LikeRowView(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    likeData: RepeatedNotification
) {
    val minSize = 55.dp

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
            .fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(likeData.authors.first().author.avatar?.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(minSize)
                    .clip(CircleShape)
            )

            val authors = likeData.authors
            val firstAuthorName =
                authors.first().author.displayName ?: authors.first().author.handle
            val remainingCount = authors.size - 1
            val text = when {
                remainingCount > 1 -> "$firstAuthorName and $remainingCount others liked this"
                remainingCount == 1 -> "$firstAuthorName and 1 other liked this"
                else -> "$firstAuthorName liked this"
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = HumanReadable.timeAgo(likeData.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = likeData.post.text,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )

            }
        }
    }
}
