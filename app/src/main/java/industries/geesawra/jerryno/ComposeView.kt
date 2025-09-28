package industries.geesawra.jerryno

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import industries.geesawra.jerryno.datalayer.TimelineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeView(
    modalSheetState: SheetState,
    focusRequester: FocusRequester,
    timelineViewModel: TimelineViewModel,
    onDismissRequest: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = {
            onDismissRequest()
            text = ""
        },
        sheetState = modalSheetState,
        modifier = Modifier.imePadding(),
        contentWindowInsets = {
            WindowInsets.ime
        }
    ) {
        // TODO: how do we hide the keyboard when modalbottomsheet is gone?
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        Column(
            Modifier
                .fillMaxWidth() // Changed from fillMaxSize()
                .imePadding()
        ) {

            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .focusRequester(
                        focusRequester
                    )
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(), // Changed from fillMaxSize()
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val maxChars = 300

                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = text.isNotBlank() && text.length <= maxChars,
                        onClick = {
                            timelineViewModel.post(text) {
                                modalSheetState.hide()
                            }
                        },
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.Send,
                            "Send"
                        )
                    }

                    val charColor = if (text.length > maxChars) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        style = MaterialTheme.typography.bodyMedium,
                        text = "${text.length}",
                        modifier = Modifier.padding(4.dp),
                        fontWeight = FontWeight.Bold,
                        color = charColor
                    )
                }

                val pickMultipleMedia =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(
                            5
                        )
                    ) { uris ->
                        // Callback is invoked after the user selects media items or closes the
                        // photo picker.
                        if (uris.isNotEmpty()) {
                            Log.d(
                                "PhotoPicker",
                                "Number of items selected: ${uris.size}"
                            )
                        } else {
                            Log.d(
                                "PhotoPicker",
                                "No media selected"
                            )
                        }
                    }

                Button(
                    onClick = {
                        pickMultipleMedia.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(Icons.Default.Add, "Add")
                    Text("Add media")
                }
            }
        }
    }

}