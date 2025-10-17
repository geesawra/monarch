package industries.geesawra.monarch

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.RepeatableNotification
import industries.geesawra.monarch.datalayer.RepeatedNotification
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun LikeRepostRowView(
    modifier: Modifier = Modifier,
    data: RepeatedNotification

) {
    val minSize = 24.dp

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
    ) {

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box {
                data.authors.take(8).forEachIndexed { idx, it ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(it.author.avatar?.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(minSize)
                            .offset(
                                x = when (idx) {
                                    0 -> 0.dp
                                    else -> (idx * 16).dp
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Image(
                    imageVector = when (data.kind) {
                        RepeatableNotification.Like -> {
                            HeartFilled
                        }

                        RepeatableNotification.Repost -> {
                            Icons.Default.Repeat
                        }
                    },
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    contentDescription = "${
                        when (data.kind) {
                            RepeatableNotification.Like -> "Like"
                            RepeatableNotification.Repost -> "Repost"
                        }
                    } icon",
                    modifier = Modifier
                        .size(minSize)
                        .clip(CircleShape)
                )

                val authors = data.authors
                val firstAuthorName =
                    authors.first().author.displayName ?: authors.first().author.handle
                val remainingCount = authors.size - 1
                val text = when {
                    remainingCount > 1 -> "$firstAuthorName and $remainingCount others ${
                        when (data.kind) {
                            RepeatableNotification.Like -> "liked"
                            RepeatableNotification.Repost -> "reposted"
                        }
                    } this"

                    remainingCount == 1 -> "$firstAuthorName and 1 other ${
                        when (data.kind) {
                            RepeatableNotification.Like -> "liked"
                            RepeatableNotification.Repost -> "reposted"
                        }
                    } this"

                    else -> "$firstAuthorName ${
                        when (data.kind) {
                            RepeatableNotification.Like -> "liked"
                            RepeatableNotification.Repost -> "reposted"
                        }
                    } this"
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
                        text = HumanReadable.timeAgo(data.timestamp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = data.post.text,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private val HeartFilled: ImageVector
    get() {
        if (_HeartFilled != null) return _HeartFilled!!

        _HeartFilled = ImageVector.Builder(
            name = "HeartFilled",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(14.88f, 4.78079f)
                curveTo(14.7993f, 4.46498f, 14.6748f, 4.16202f, 14.51f, 3.88077f)
                curveTo(14.3518f, 3.58819f, 14.1493f, 3.3217f, 13.91f, 3.09073f)
                curveTo(13.563f, 2.74486f, 13.152f, 2.46982f, 12.7f, 2.28079f)
                curveTo(11.7902f, 1.90738f, 10.7698f, 1.90738f, 9.85999f, 2.28079f)
                curveTo(9.43276f, 2.46163f, 9.04027f, 2.71541f, 8.70002f, 3.03079f)
                lineTo(8.65003f, 3.09073f)
                lineTo(8.00001f, 3.74075f)
                lineTo(7.34999f, 3.09073f)
                lineTo(7.3f, 3.03079f)
                curveTo(6.95975f, 2.71541f, 6.56726f, 2.46163f, 6.14002f, 2.28079f)
                curveTo(5.23018f, 1.90738f, 4.20984f, 1.90738f, 3.3f, 2.28079f)
                curveTo(2.84798f, 2.46982f, 2.43706f, 2.74486f, 2.09004f, 3.09073f)
                curveTo(1.85051f, 3.32402f, 1.64514f, 3.59002f, 1.48002f, 3.88077f)
                curveTo(1.32258f, 4.1644f, 1.20161f, 4.46682f, 1.12f, 4.78079f)
                curveTo(1.03522f, 5.10721f, 0.994861f, 5.44358f, 1.00001f, 5.78079f)
                curveTo(1.00053f, 6.09791f, 1.04084f, 6.41365f, 1.12f, 6.72073f)
                curveTo(1.20384f, 7.03078f, 1.32472f, 7.32961f, 1.48002f, 7.61075f)
                curveTo(1.64774f, 7.89975f, 1.85285f, 8.16542f, 2.09004f, 8.40079f)
                lineTo(8.00001f, 14.3108f)
                lineTo(13.91f, 8.40079f)
                curveTo(14.1471f, 8.16782f, 14.3492f, 7.90169f, 14.51f, 7.61075f)
                curveTo(14.6729f, 7.33211f, 14.7974f, 7.03272f, 14.88f, 6.72073f)
                curveTo(14.9592f, 6.41365f, 14.9995f, 6.09791f, 15f, 5.78079f)
                curveTo(15.0052f, 5.44358f, 14.9648f, 5.10721f, 14.88f, 4.78079f)
                close()
            }
        }.build()

        return _HeartFilled!!
    }

private var _HeartFilled: ImageVector? = null

