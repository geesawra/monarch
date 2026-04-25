package industries.geesawra.monarch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import io.github.fornewid.placeholder.foundation.PlaceholderHighlight
import io.github.fornewid.placeholder.foundation.fade
import io.github.fornewid.placeholder.material3.placeholder
import app.bsky.actor.VerificationStateVerifiedStatus
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.SettingsState

val LocalBaselineProfileMode = compositionLocalOf { false }

val SettingsState.avatarClipShape: Shape
    get() = if (avatarShape == AvatarShape.RoundedSquare) RoundedCornerShape(8.dp) else CircleShape

@Composable
fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard post?") },
        text = { Text("You have unsaved changes that will be lost.") },
        confirmButton = {
            Button(onClick = onDiscard) {
                Text("Discard")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onKeepEditing) {
                Text("Keep editing")
            }
        }
    )
}

@Composable
fun SaveDraftDialog(
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Save draft?") },
        text = { Text("Save your post as a draft, or discard it.") },
        confirmButton = {
            Button(onClick = onSaveDraft) {
                Text("Save draft")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onKeepEditing) {
                Text("Keep editing")
            }
            TextButton(onClick = onDiscard) {
                Text("Discard")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator()
    }
}

@Composable
fun Modifier.themedPlaceholder(
    visible: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape? = null,
): Modifier = this.then(
    placeholder(
        visible = visible,
        color = contentColorFor(backgroundColor).copy(alpha = 0.1f).compositeOver(backgroundColor),
        shape = shape,
        highlight = PlaceholderHighlight.fade(
            highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    )
)

@Composable
fun OnEndOfListReached(listState: LazyListState, items: List<Any>, onEndReached: () -> Unit) {
    val endOfListReached by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
            }
        }
    }

    LaunchedEffect(endOfListReached) {
        if (endOfListReached && items.isNotEmpty()) {
            onEndReached()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun monarchTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
    )

fun isVerificationStateVerifiedStatus(status: VerificationStateVerifiedStatus?): Boolean =
    status is VerificationStateVerifiedStatus.Valid

@Composable
fun SquigglyDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
    amplitude: androidx.compose.ui.unit.Dp = 3.dp,
    wavelength: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val path = Path()
        val amp = with(density) { amplitude.toPx() }
        val wave = with(density) { wavelength.toPx() }
        val stroke = with(density) { strokeWidth.toPx() }
        val y = size.height / 2f
        val halfWave = wave / 2f

        path.moveTo(-stroke, y)
        val segments = ((size.width + stroke * 2) / halfWave).toInt() + 2
        for (i in 0 until segments) {
            val startX = -stroke + i * halfWave
            val endX = startX + halfWave
            val controlX = (startX + endX) / 2f
            val controlY = if (i % 2 == 0) y - amp else y + amp
            path.quadraticTo(controlX, controlY, endX, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
