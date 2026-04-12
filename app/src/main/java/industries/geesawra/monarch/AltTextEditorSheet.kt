package industries.geesawra.monarch

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import industries.geesawra.monarch.datalayer.AltTextAvailability
import industries.geesawra.monarch.datalayer.AltTextGenerator
import kotlinx.coroutines.launch

private const val ALT_TEXT_MAX_CHARS = 2000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AltTextEditorSheet(
    uri: Uri,
    initialText: String,
    generator: AltTextGenerator,
    aiEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(initialText) }
    var availability by remember { mutableStateOf<AltTextAvailability?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(aiEnabled) {
        availability = if (aiEnabled) generator.availability() else AltTextAvailability.Unavailable
    }

    val canGenerate = aiEnabled && availability != null &&
        availability != AltTextAvailability.Unavailable

    ModalBottomSheet(
        onDismissRequest = {
            onSave(text)
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.add(WindowInsets.navigationBars))
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Image description",
                style = MaterialTheme.typography.titleLarge,
            )

            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(12.dp)),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            )

            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= ALT_TEXT_MAX_CHARS) text = it },
                label = { Text("Describe this image") },
                supportingText = {
                    val remaining = ALT_TEXT_MAX_CHARS - text.length
                    Text("$remaining characters remaining")
                },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGenerate) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                if (availability == AltTextAvailability.Downloadable) {
                                    val downloadResult = generator.ensureDownloaded()
                                    if (downloadResult.isFailure) {
                                        error = "Model download failed"
                                        busy = false
                                        return@launch
                                    }
                                }
                                generator.describeUri(uri)
                                    .onSuccess { description ->
                                        text = description.take(ALT_TEXT_MAX_CHARS)
                                    }
                                    .onFailure {
                                        error = "Generation failed: ${it.message ?: "unknown error"}"
                                    }
                                availability = generator.availability()
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text(
                            text = when {
                                busy && availability == AltTextAvailability.Downloadable ->
                                    "Downloading model…"
                                busy -> "Generating…"
                                availability == AltTextAvailability.Downloadable ->
                                    "Download & generate"
                                else -> "Generate with AI"
                            }
                        )
                    }
                } else {
                    Spacer(modifier = Modifier)
                }
                TextButton(
                    onClick = {
                        onSave(text)
                        onDismiss()
                    },
                ) {
                    Text("Done")
                }
            }
        }
    }
}
