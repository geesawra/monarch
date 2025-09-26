package industries.geesawra.jerryno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp


@Composable
private fun IconWithNumber(imageVector: ImageVector, contentDescription: String, number: Long?) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var fontSize by remember {
            mutableStateOf(10.dp)
        }
        Icon(
            imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(15.dp),
        )
        Text(
            modifier = Modifier.padding(start = 2.dp),
            text = (number ?: 0).toString(),
            maxLines = 1,
            onTextLayout = { textLayout ->
                if (textLayout.multiParagraph.didExceedMaxLines) {
                    fontSize -= 1.dp
                }
            }
        )
    }
}

@Composable
fun TimelinePostActionsView(
    modifier: Modifier = Modifier,
    replies: Long?,
    likes: Long?,
    reposts: Long?,
) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = modifier,
    ) {
        IconButton(
            onClick = {}
        ) {
            IconWithNumber(
                Chat_bubble,
                contentDescription = "Reply",
                number = replies,
            )
        }
        IconButton(
            onClick = {}
        ) {
            IconWithNumber(
                Icons.Default.ThumbUp,
                contentDescription = "Like",
                number = likes
            )
        }
        IconButton(
            onClick = {}
        ) {
            IconWithNumber(
                Reload,
                contentDescription = "Repost",
                number = reposts,
            )
        }
    }
}

val Chat_bubble: ImageVector
    get() {
        if (_Chat_bubble != null) return _Chat_bubble!!

        _Chat_bubble = ImageVector.Builder(
            name = "Chat_bubble",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(80f, 880f)
                verticalLineToRelative(-720f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(160f, 80f)
                horizontalLineToRelative(640f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(880f, 160f)
                verticalLineToRelative(480f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(800f, 720f)
                horizontalLineTo(240f)
                close()
                moveToRelative(126f, -240f)
                horizontalLineToRelative(594f)
                verticalLineToRelative(-480f)
                horizontalLineTo(160f)
                verticalLineToRelative(525f)
                close()
                moveToRelative(-46f, 0f)
                verticalLineToRelative(-480f)
                close()
            }
        }.build()

        return _Chat_bubble!!
    }

private var _Chat_bubble: ImageVector? = null

val Reload: ImageVector
    get() {
        if (_Reload != null) return _Reload!!

        _Reload = ImageVector.Builder(
            name = "Reload",
            defaultWidth = 15.dp,
            defaultHeight = 15.dp,
            viewportWidth = 15f,
            viewportHeight = 15f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(1.84998f, 7.49998f)
                curveTo(1.84998f, 4.66458f, 4.05979f, 1.84998f, 7.49998f, 1.84998f)
                curveTo(10.2783f, 1.84998f, 11.6515f, 3.9064f, 12.2367f, 5f)
                horizontalLineTo(10.5f)
                curveTo(10.2239f, 5f, 10f, 5.22386f, 10f, 5.5f)
                curveTo(10f, 5.77614f, 10.2239f, 6f, 10.5f, 6f)
                horizontalLineTo(13.5f)
                curveTo(13.7761f, 6f, 14f, 5.77614f, 14f, 5.5f)
                verticalLineTo(2.5f)
                curveTo(14f, 2.22386f, 13.7761f, 2f, 13.5f, 2f)
                curveTo(13.2239f, 2f, 13f, 2.22386f, 13f, 2.5f)
                verticalLineTo(4.31318f)
                curveTo(12.2955f, 3.07126f, 10.6659f, 0.849976f, 7.49998f, 0.849976f)
                curveTo(3.43716f, 0.849976f, 0.849976f, 4.18537f, 0.849976f, 7.49998f)
                curveTo(0.849976f, 10.8146f, 3.43716f, 14.15f, 7.49998f, 14.15f)
                curveTo(9.44382f, 14.15f, 11.0622f, 13.3808f, 12.2145f, 12.2084f)
                curveTo(12.8315f, 11.5806f, 13.3133f, 10.839f, 13.6418f, 10.0407f)
                curveTo(13.7469f, 9.78536f, 13.6251f, 9.49315f, 13.3698f, 9.38806f)
                curveTo(13.1144f, 9.28296f, 12.8222f, 9.40478f, 12.7171f, 9.66014f)
                curveTo(12.4363f, 10.3425f, 12.0251f, 10.9745f, 11.5013f, 11.5074f)
                curveTo(10.5295f, 12.4963f, 9.16504f, 13.15f, 7.49998f, 13.15f)
                curveTo(4.05979f, 13.15f, 1.84998f, 10.3354f, 1.84998f, 7.49998f)
                close()
            }
        }.build()

        return _Reload!!
    }

private var _Reload: ImageVector? = null

