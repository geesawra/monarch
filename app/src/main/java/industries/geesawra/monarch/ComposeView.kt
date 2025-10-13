package industries.geesawra.monarch

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.atproto.repo.StrongRef
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComposeView(
    context: Context,
    coroutineScope: CoroutineScope,
    timelineViewModel: TimelineViewModel,
    inReplyTo: MutableState<SkeetData?>,
    isQuotePost: MutableState<Boolean>,
    scaffoldState: BottomSheetScaffoldState,
    scrollState: ScrollState
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val charCount = remember { mutableIntStateOf(0) }
    val wasEdited = remember { mutableStateOf(false) }
    val maxChars = 300
    val composeFieldState = rememberTextFieldState(
        ""
    )
    val mediaSelected = remember { mutableStateOf(listOf<Uri>()) }
    val mediaSelectedIsVideo = remember { mutableStateOf(false) }

    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        when (scaffoldState.bottomSheetState.currentValue) {
            SheetValue.Hidden -> {
                composeFieldState.clearText()
                keyboardController?.hide()
                focusManager.clearFocus()
                charCount.intValue = 0
                inReplyTo.value = null
                isQuotePost.value = false
                mediaSelected.value = listOf()
                mediaSelectedIsVideo.value = false
            }

            SheetValue.PartiallyExpanded, SheetValue.Expanded -> {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }
    }

    // Remember the ReceiveContentListener object as it is created inside a Composable scope
    val receiveContentListener = remember {
        ReceiveContentListener { transferableContent ->
            when (transferableContent.hasMediaType(MediaType.Image)) {
                true -> transferableContent.consume {
                    val uri = it.uri
                    val mimeType: String? = context.contentResolver.getType(uri)
                    mediaSelected.value = listOf(uri)
                    true
                }


                false -> transferableContent
            }
        }
    }


    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)) { uris ->
            if (uris.isEmpty()) {
                return@rememberLauncherForActivityResult
            }

            val urisMap = uris.associateWith { uri ->
                val mimeType: String? = context.contentResolver.getType(uri)
                mimeType?.let {
                    if (it.startsWith("image/")) {
                        return@associateWith "image"
                    } else if (it.startsWith("video/")) {
                        return@associateWith "video"
                    }
                }
                null
            }

            val containsVideo = urisMap.values.any { it == "video" }

            if (urisMap.size > 1 && containsVideo) {
                Toast.makeText(
                    context,
                    "Can only post up to 1 video or 4 pictures. Video cannot be mixed with other media.",
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }
            if (containsVideo && urisMap.any { it.value == "image" }) {
                Toast.makeText(
                    context,
                    "Video cannot be mixed with other media.",
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }

            mediaSelectedIsVideo.value = containsVideo && urisMap.size == 1
            mediaSelected.value = urisMap.filterValues { it != null }.keys.toList()
        }

    val uploadingPost = remember { mutableStateOf(false) }

    // Outer Box: Handles IME padding and general content padding for the whole sheet content
    Box(
        modifier = Modifier
            .fillMaxWidth() // Takes full width of the bottom sheet
            .windowInsetsPadding(WindowInsets.ime.add(WindowInsets.navigationBars))
            .verticalScroll(scrollState)
            .padding(16.dp) // General padding around all content inside the sheet
    ) {
        // Inner Box: Fills the space provided by Outer Box, used for layering scrollable content and fixed buttons
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrollable Content Column
            Column(
                modifier = Modifier
                    .fillMaxWidth(), // Takes full width of the Inner Box
                horizontalAlignment = Alignment.End
            ) {
                Row {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                scaffoldState.bottomSheetState.hide()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close compose view")
                    }
                }

                inReplyTo.value?.let {
                    OutlinedCard(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        SkeetView(
                            modifier = Modifier
                                .padding(8.dp)
                                .background(Color.Transparent),
                            skeet = it,
                            nested = true,
                            disableEmbeds = false
                        )
                    }
                }

                LaunchedEffect(composeFieldState.text) {
                    if (composeFieldState.text.isEmpty()) {
                        wasEdited.value = false
                    } else {
                        wasEdited.value = true
                        charCount.intValue = composeFieldState.text.length
                    }
                }

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 250.dp)
                        .focusRequester(focusRequester)
                        .contentReceiver(receiveContentListener),
                    label = {
                        if (wasEdited.value) {
                            Text(
                                text = "${maxChars - charCount.intValue}",
                                color = if (composeFieldState.text.length > maxChars) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "Less cringe this time, okay?",
                            )
                        }
                    },
                    isError = composeFieldState.text.length > maxChars,
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 10),
                    state = composeFieldState
                )

                ActionRow(
                    context,
                    uploadingPost,
                    pickMedia,
                    composeFieldState.text.toString(),
                    mediaSelected,
                    mediaSelectedIsVideo,
                    coroutineScope,
                    maxChars,
                    timelineViewModel,
                    scaffoldState,
                    inReplyTo.value,
                    isQuotePost.value
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (mediaSelected.value.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        when (mediaSelectedIsVideo.value) {
                            false -> PostImageGallery(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                images = mediaSelected.value.map { uri ->
                                    Image(url = uri.toString(), alt = "Selected media")
                                },
                                onCrossClick = {
                                    val toDelUri = mediaSelected.value[it]
                                    mediaSelected.value =
                                        mediaSelected.value.filter { uri ->
                                            uri != toDelUri
                                        }
                                }
                            )

                            true -> DeletableMediaView(
                                originalIndex = 0,
                                onCrossClick = {
                                    mediaSelected.value = listOf()
                                },
                                onMediaClick = { }
                            ) {
                                VideoView(uri = mediaSelected.value.first())
                            }
                        }
                    }
                }

                // Spacer at the end of scrollable content to prevent overlap with fixed buttons
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionRow(
    context: Context,
    uploadingPost: MutableState<Boolean>,
    pickMedia: ManagedActivityResultLauncher<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>,
    postText: String,
    mediaSelected: MutableState<List<Uri>>,
    mediaSelectedIsVideo: MutableState<Boolean>,
    coroutineScope: CoroutineScope,
    maxChars: Int,
    timelineViewModel: TimelineViewModel,
    scaffoldState: BottomSheetScaffoldState,
    inReplyToData: SkeetData? = null,
    isQuotePost: Boolean = false,
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 8.dp,
            ), // Internal padding for the button row
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
        ) {
            Icon(Icons.Default.CameraRoll, contentDescription = "Attach media")
        }

        if (uploadingPost.value) {
            CircularProgressIndicator()
        }

        val postButtonEnabled = remember(postText, mediaSelected.value) {
            (postText.isNotBlank() || mediaSelected.value.isNotEmpty()) && postText.length <= maxChars
        }

        Button(
            onClick = {
                if (postText.isNotBlank() && postText.length <= maxChars) {
                    coroutineScope.launch {
                        uploadingPost.value = true // Show progress immediately
                        timelineViewModel.post(
                            content = postText,
                            images = if (!mediaSelectedIsVideo.value) mediaSelected.value
                                .ifEmpty { null } else null,
                            video = if (mediaSelectedIsVideo.value) mediaSelected.value.firstOrNull() else null,
                            replyRef = if (!isQuotePost) {
                                inReplyToData?.replyRef()
                            } else {
                                null
                            },
                            quotePostRef = if (isQuotePost) {
                                val cid = inReplyToData?.cid
                                val uri = inReplyToData?.uri

                                if (cid == null || uri == null) {
                                    null
                                } else {
                                    StrongRef(uri, cid)
                                }
                            } else {
                                null
                            }
                        ).onSuccess {
                            coroutineScope.launch {
                                scaffoldState.bottomSheetState.hide()
                                // State reset is now in LaunchedEffect for isVisible
                            }
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "Could not post: ${it.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }.also {
                            uploadingPost.value = false // Hide progress after completion
                        }
                    }
                }
            },
            modifier = Modifier.padding(end = 8.dp),
            enabled = postButtonEnabled && !uploadingPost.value // Disable while uploading
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post")
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Skeet")
        }
    }
}
