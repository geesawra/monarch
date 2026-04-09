package industries.geesawra.monarch

import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.bsky.richtext.Facet
import app.bsky.richtext.FacetByteSlice
import app.bsky.richtext.FacetFeatureUnion
import app.bsky.richtext.FacetLink
import app.bsky.richtext.FacetTag
import com.atproto.repo.StrongRef
import app.bsky.feed.ThreadgateAllowUnion
import app.bsky.feed.ThreadgateFollowerRule
import app.bsky.feed.ThreadgateFollowingRule
import app.bsky.feed.ThreadgateMentionRule
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import app.bsky.actor.ProfileViewBasic
import app.bsky.richtext.FacetMention
import sh.christian.ozone.api.Did
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import industries.geesawra.monarch.datalayer.LinkPreviewData
import industries.geesawra.monarch.datalayer.LinkPreviewFetcher
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ComposeView(
    context: Context,
    coroutineScope: CoroutineScope,
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState = SettingsState(),
    inReplyTo: MutableState<SkeetData?>,
    isQuotePost: MutableState<Boolean>,
    scaffoldState: BottomSheetScaffoldState,
    scrollState: ScrollState,
    wasEdited: MutableState<Boolean> = mutableStateOf(false),
    initialText: String = "",
    initialMentions: Map<String, Did> = emptyMap(),
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val charCount = remember { mutableIntStateOf(0) }
    val maxChars = 300
    val textfieldState = rememberTextFieldState()
    val facets = remember { mutableListOf<Facet>() }
    val mediaSelected = remember { mutableStateOf(listOf<Uri>()) }
    val mediaSelectedIsVideo = remember { mutableStateOf(false) }
    val threadgateRules = remember { mutableStateOf<List<ThreadgateAllowUnion>?>(null) }
    val mentionResults = remember { mutableStateOf(listOf<ProfileViewBasic>()) }
    val showMentionDropdown = remember { mutableStateOf(false) }
    val mentionDids = remember { mutableStateMapOf<String, Did>() }

    val linkPreview = remember { mutableStateOf<LinkPreviewData?>(null) }
    val linkPreviewLoading = remember { mutableStateOf(false) }
    val linkPreviewDismissed = remember { mutableStateOf(false) }
    val linkPreviewCache = remember { mutableMapOf<String, LinkPreviewData?>() }

    LaunchedEffect(scaffoldState.bottomSheetState.targetValue) {
        when (scaffoldState.bottomSheetState.targetValue) {
            SheetValue.Hidden -> {
                keyboardController?.hide()
                focusManager.clearFocus()
                if (!wasEdited.value) {
                    textfieldState.clearText()
                    charCount.intValue = 0
                    inReplyTo.value = null
                    isQuotePost.value = false
                    mediaSelected.value = listOf()
                    mediaSelectedIsVideo.value = false
                    mentionResults.value = listOf()
                    showMentionDropdown.value = false
                    mentionDids.clear()
                    linkPreview.value = null
                    linkPreviewLoading.value = false
                    linkPreviewDismissed.value = false
                    linkPreviewCache.clear()
                }
            }

            SheetValue.PartiallyExpanded, SheetValue.Expanded -> {
                if (initialText.isNotEmpty() && inReplyTo.value == null && !isQuotePost.value && textfieldState.text.isEmpty()) {
                    textfieldState.edit {
                        replace(0, length, initialText)
                        selection = TextRange(initialText.length)
                    }
                    mentionDids.putAll(initialMentions)
                }
                delay(100)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    // Remember the ReceiveContentListener object as it is created inside a Composable scope
    val receiveContentListener = remember {
        ReceiveContentListener { transferableContent ->
            when (transferableContent.hasMediaType(MediaType.Image)) {
                true -> transferableContent.consume {
                    val uri = it.uri
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

    val cameraImageUri = remember { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraImageUri.value?.let { uri ->
                    mediaSelectedIsVideo.value = false
                    mediaSelected.value = mediaSelected.value + uri
                }
            }
        }

    val uploadingPost = remember { mutableStateOf(false) }

    val sheetScrollConnection = remember(scrollState) {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (scrollState.value > 0) return available
                return Velocity.Zero
            }
        }
    }

    // Outer Box: Handles IME padding and general content padding for the whole sheet content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(sheetScrollConnection)
            .windowInsetsPadding(WindowInsets.ime.add(WindowInsets.navigationBars))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Inner Box: Fills the space provided by Outer Box, used for layering scrollable content and fixed buttons
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrollable Content Column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.End
            ) {
                ActionRow(
                    context,
                    uploadingPost,
                    pickMedia,
                    takePicture,
                    cameraImageUri,
                    textfieldState.text.toString(),
                    mediaSelected,
                    mediaSelectedIsVideo,
                    coroutineScope,
                    maxChars,
                    timelineViewModel,
                    autoLikeOnReply = settingsState.autoLikeOnReply,
                    scaffoldState,
                    inReplyTo.value,
                    isQuotePost.value,
                    facets = facets,
                    linkPreview = if (!linkPreviewDismissed.value) linkPreview.value else null,
                    threadgateRules = threadgateRules,
                    wasEdited = wasEdited,
                )

                LaunchedEffect(Unit) {
                    snapshotFlow { textfieldState.text.toString() to mediaSelected.value }
                        .collect { (text, media) ->
                            charCount.intValue = text.length
                            wasEdited.value = text.isNotEmpty() || media.isNotEmpty()
                        }
                }

                // Mention typeahead: detect @query and search
                LaunchedEffect(textfieldState.text, textfieldState.selection) {
                    val text = textfieldState.text.toString()
                    val cursorPos = textfieldState.selection.min

                    if (cursorPos <= 0 || text.isEmpty()) {
                        showMentionDropdown.value = false
                        return@LaunchedEffect
                    }

                    // Find the last @ before cursor that is preceded by whitespace or is at start
                    val textBeforeCursor = text.substring(0, cursorPos)
                    val atIndex = textBeforeCursor.lastIndexOf('@')

                    if (atIndex < 0) {
                        showMentionDropdown.value = false
                        return@LaunchedEffect
                    }

                    // @ must be at start or preceded by whitespace
                    if (atIndex > 0 && !textBeforeCursor[atIndex - 1].isWhitespace()) {
                        showMentionDropdown.value = false
                        return@LaunchedEffect
                    }

                    val query = textBeforeCursor.substring(atIndex + 1)

                    // No whitespace allowed in the query part
                    if (query.contains(' ') || query.isEmpty()) {
                        showMentionDropdown.value = false
                        return@LaunchedEffect
                    }

                    delay(300)

                    timelineViewModel.searchActorsTypeahead(query)
                        .onSuccess {
                            mentionResults.value = it
                            showMentionDropdown.value = it.isNotEmpty()
                        }
                        .onFailure {
                            showMentionDropdown.value = false
                        }
                }

                // Debounced link preview fetching
                LaunchedEffect(textfieldState.text.toString()) {
                    val text = textfieldState.text.toString()
                    val firstUrl = tokensRegexp.findAll(text)
                        .map { it.value }
                         .firstOrNull { isUrl(it) }

                    if (firstUrl == null) {
                        linkPreviewLoading.value = false
                        return@LaunchedEffect
                    }

                    val normalizedUrl = normalizeUrl(firstUrl)

                    // If user dismissed this URL, do not re-fetch
                    if (linkPreviewDismissed.value && linkPreview.value == null) {
                        return@LaunchedEffect
                    }

                    // If already showing preview for same URL, skip
                    if (linkPreview.value?.url == normalizedUrl) {
                        return@LaunchedEffect
                    }

                    // Check cache
                    if (linkPreviewCache.containsKey(normalizedUrl)) {
                        linkPreview.value = linkPreviewCache[normalizedUrl]
                        linkPreviewLoading.value = false
                        return@LaunchedEffect
                    }

                    // Debounce 500ms
                    delay(500)

                    linkPreviewLoading.value = true
                    val preview = LinkPreviewFetcher.fetch(normalizedUrl)
                    linkPreviewCache[normalizedUrl] = preview
                    linkPreview.value = preview
                    linkPreviewLoading.value = false
                    linkPreviewDismissed.value = false
                }

                val urlColor = MaterialTheme.colorScheme.primary

                LaunchedEffect(textfieldState.text) {
                    val data = textfieldState.text.toString()
                    val computed = readFacets(data, mentionDids)
                    facets.clear()
                    facets.addAll(computed)
                }

                val facetHighlighter = remember {
                    object : OutputTransformation {
                        override fun TextFieldBuffer.transformOutput() {
                            for (token in tokensRegexp.findAll(originalText)) {
                                val s = token.value
                                if (isUrl(s)) {
                                    addStyle(
                                        SpanStyle(color = urlColor),
                                        token.range.first,
                                        token.range.last + 1,
                                    )
                                } else if (s.startsWith("#") && s.length > 1) {
                                    val tag = s.substring(1)
                                    if (tag.isNotEmpty() && !tag.contains(" ") && tag.length <= 64) {
                                        addStyle(
                                            SpanStyle(color = urlColor),
                                            token.range.first,
                                            token.range.last + 1,
                                        )
                                    }
                                } else if (s.startsWith("@") && s.length > 1) {
                                    val handle = s.removePrefix("@")
                                    if (mentionDids.containsKey(handle)) {
                                        addStyle(
                                            SpanStyle(color = urlColor),
                                            token.range.first,
                                            token.range.last + 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = postHorizontalPadding(), end = postHorizontalPadding(), top = 8.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(avatarSize()),
                    ) {
                        AsyncImage(
                            model = timelineViewModel.uiState.user?.avatar?.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(avatarSize())
                                .clip(settingsState.avatarClipShape),
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                    TextField(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = avatarTextGap())
                            .focusRequester(focusRequester)
                            .contentReceiver(receiveContentListener),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            autoCorrectEnabled = true,
                            keyboardType = KeyboardType.Text,
                        ),
                        placeholder = {
                            if (!wasEdited.value) {
                                Text("Less cringe this time, okay?")
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            errorContainerColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                        ),
                        isError = textfieldState.text.length > maxChars,
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 10),
                        state = textfieldState,
                        outputTransformation = facetHighlighter,
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

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
                            disableEmbeds = false,
                            showLabels = false,
                            showInReplyTo = false,
                            avatarShape = settingsState.avatarClipShape,
                            postTextSize = settingsState.postTextSize,
                        )
                    }
                }

                if (showMentionDropdown.value) {
                    MentionDropdown(
                        mentionResults = mentionResults.value,
                        onProfileSelected = { profile ->
                            val text = textfieldState.text.toString()
                            val cursorPos = textfieldState.selection.min
                            val textBeforeCursor = text.substring(0, cursorPos)
                            val atIndex = textBeforeCursor.lastIndexOf('@')

                            if (atIndex >= 0) {
                                val fullHandle = profile.handle.handle
                                val replacement = "@$fullHandle "
                                val afterCursor = text.substring(cursorPos)
                                val newText = text.substring(0, atIndex) + replacement + afterCursor

                                textfieldState.edit {
                                    replace(0, length, newText)
                                    val newCursorPos = atIndex + replacement.length
                                    selection = TextRange(newCursorPos, newCursorPos)
                                }

                                mentionDids[fullHandle] = profile.did
                            }

                            showMentionDropdown.value = false
                        },
                    )
                }

                LinkPreviewCard(
                    isLoading = linkPreviewLoading.value,
                    preview = if (!linkPreviewDismissed.value) linkPreview.value else null,
                    onDismiss = {
                        linkPreview.value = null
                        linkPreviewDismissed.value = true
                    },
                )

                MediaSelectionSection(
                    mediaSelected = mediaSelected.value,
                    mediaSelectedIsVideo = mediaSelectedIsVideo.value,
                    onImageRemove = { index ->
                        val toDelUri = mediaSelected.value[index]
                        mediaSelected.value = mediaSelected.value.filter { uri -> uri != toDelUri }
                    },
                    onVideoRemove = { mediaSelected.value = listOf() },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionRow(
    context: Context,
    uploadingPost: MutableState<Boolean>,
    pickMedia: ManagedActivityResultLauncher<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>,
    takePicture: ManagedActivityResultLauncher<Uri, Boolean>,
    cameraImageUri: MutableState<Uri?>,
    postText: String,
    mediaSelected: MutableState<List<Uri>>,
    mediaSelectedIsVideo: MutableState<Boolean>,
    coroutineScope: CoroutineScope,
    maxChars: Int,
    timelineViewModel: TimelineViewModel,
    autoLikeOnReply: Boolean = false,
    scaffoldState: BottomSheetScaffoldState,
    inReplyToData: SkeetData? = null,
    isQuotePost: Boolean = false,
    facets: List<Facet> = listOf(),
    linkPreview: LinkPreviewData? = null,
    threadgateRules: MutableState<List<ThreadgateAllowUnion>?>,
    wasEdited: MutableState<Boolean> = mutableStateOf(false),
) {
    var showThreadgateSheet by remember { mutableStateOf(false) }

    if (showThreadgateSheet) {
        ThreadgateSettings(
            currentRules = threadgateRules.value,
            onDismiss = { showThreadgateSheet = false },
            onApply = { rules ->
                threadgateRules.value = rules
                showThreadgateSheet = false
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 8.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            TextButton(
                onClick = {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
            ) {
                Icon(Icons.Default.CameraRoll, contentDescription = "Attach media")
            }
            TextButton(
                onClick = {
                    val cacheDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
                    val file = File.createTempFile("capture_", ".jpg", cacheDir)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraImageUri.value = uri
                    takePicture.launch(uri)
                }
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Take photo")
            }
            TextButton(onClick = { showThreadgateSheet = true }) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Reply settings",
                    tint = if (threadgateRules.value != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }

        val postButtonEnabled = remember(postText, mediaSelected.value) {
            (postText.isNotBlank() || mediaSelected.value.isNotEmpty()) && postText.length <= maxChars
        }

        val haptic = LocalHapticFeedback.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        if (postText.isNotEmpty()) {
            Text(
                text = "${maxChars - postText.length}",
                style = MaterialTheme.typography.labelMedium,
                color = if (postText.length > maxChars) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uploadingPost.value) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularWavyProgressIndicator()
                timelineViewModel.uiState.videoUploadStatus?.let { status ->
                    val progress = timelineViewModel.uiState.videoUploadProgress
                    val text = if (progress != null && progress > 0) {
                        "${status.label} ${progress}%"
                    } else {
                        status.label
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    coroutineScope.launch {
                        uploadingPost.value = true // Show progress immediately
                        timelineViewModel.post(
                            content = postText,
                            facets = facets,
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
                            },
                            linkPreview = linkPreview,
                            threadgateRules = threadgateRules.value,
                        ).onSuccess {
                            wasEdited.value = false
                            if (autoLikeOnReply && !isQuotePost && inReplyToData != null && !inReplyToData.didLike) {
                                timelineViewModel.like(inReplyToData.uri, inReplyToData.cid)
                            }
                            coroutineScope.launch {
                                scaffoldState.bottomSheetState.hide()
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
    }
}

val tokensRegexp = Regex("(\\S+)")

private val bareUrlRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\\.[a-zA-Z]{2,}(/\\S*)?$")

private fun isUrl(s: String): Boolean {
    return URLUtil.isHttpUrl(s) || URLUtil.isHttpsUrl(s) || bareUrlRegex.matches(s)
}

private fun normalizeUrl(s: String): String {
    if (URLUtil.isHttpUrl(s) || URLUtil.isHttpsUrl(s)) return s
    return "https://$s"
}

private fun readFacets(data: String, mentionDids: Map<String, Did> = emptyMap()): List<Facet> {
    val facets = mutableListOf<Facet>()

    for (token in tokensRegexp.findAll(data)) {
        val s = token.value
        val startByte =
            data.substring(0, token.range.first).encodeToByteArray().size
        val endByte =
            data.substring(0, token.range.last + 1).encodeToByteArray().size

        if (isUrl(s)) {
            facets.add(
                Facet(
                    index = FacetByteSlice(startByte.toLong(), endByte.toLong()),
                    features = listOf(
                        FacetFeatureUnion.Link(
                            value = FacetLink(
                                uri = sh.christian.ozone.api.Uri(normalizeUrl(s))
                            )
                        )
                    )
                )
            )
        } else if (s.startsWith("#") && s.length > 1) {
            val tag = s.substring(1)
            if (tag.isNotEmpty() && !tag.contains(" ") && tag.length <= 64) {
                facets.add(
                    Facet(
                        index = FacetByteSlice(
                            startByte.toLong(),
                            endByte.toLong()
                        ),
                        features = listOf(
                            FacetFeatureUnion.Tag(
                                value = FacetTag(
                                    tag = s.removePrefix("#"),
                                )
                            )
                        )
                    )
                )
            }
        } else if (s.startsWith("@") && s.length > 1) {
            val handle = s.substring(1)
            val did = mentionDids[handle]
            if (did != null) {
                facets.add(
                    Facet(
                        index = FacetByteSlice(
                            startByte.toLong(),
                            endByte.toLong()
                        ),
                        features = listOf(
                            FacetFeatureUnion.Mention(
                                value = FacetMention(
                                    did = did,
                                )
                            )
                        )
                    )
                )
            }
        }
    }

    return facets
}

@Composable
private fun MentionDropdown(
    mentionResults: List<ProfileViewBasic>,
    onProfileSelected: (ProfileViewBasic) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(max = 200.dp)
    ) {
        LazyColumn {
            items(mentionResults) { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProfileSelected(profile) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(profile.avatar?.uri)
                            .build(),
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        contentDescription = "${profile.displayName ?: profile.handle.handle}'s avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                    )
                    Column {
                        profile.displayName?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = "@${profile.handle.handle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinkPreviewCard(
    isLoading: Boolean,
    preview: LinkPreviewData?,
    onDismiss: () -> Unit,
) {
    if (isLoading) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularWavyProgressIndicator()
            }
        }
    } else if (preview != null) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Box {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    preview.imageUrl?.let { imgUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imgUrl)
                                .build(),
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                            contentDescription = "Link preview thumbnail",
                            modifier = Modifier
                                .height(150.dp)
                                .fillMaxWidth()
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    preview.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                top = 8.dp, start = 8.dp, end = 8.dp, bottom = 4.dp
                            )
                        )
                    }
                    preview.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                start = 8.dp, end = 8.dp, bottom = 4.dp
                            )
                        )
                    }
                    Text(
                        text = try {
                            URI(preview.url).host ?: preview.url
                        } catch (_: Exception) {
                            preview.url
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = 8.dp, end = 8.dp, bottom = 8.dp
                        )
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss link preview"
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaSelectionSection(
    mediaSelected: List<Uri>,
    mediaSelectedIsVideo: Boolean,
    onImageRemove: (Int) -> Unit,
    onVideoRemove: () -> Unit,
) {
    if (mediaSelected.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            when (mediaSelectedIsVideo) {
                false -> PostImageGallery(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    images = mediaSelected.map { uri ->
                        Image(url = uri.toString(), alt = "Selected media")
                    },
                    onCrossClick = { onImageRemove(it) }
                )

                true -> DeletableMediaView(
                    originalIndex = 0,
                    onCrossClick = { onVideoRemove() },
                    onMediaClick = { }
                ) {
                    AsyncImage(
                        model = mediaSelected.first(),
                        contentDescription = "Selected video",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadgateSettings(
    currentRules: List<ThreadgateAllowUnion>?,
    onDismiss: () -> Unit,
    onApply: (List<ThreadgateAllowUnion>?) -> Unit,
) {
    var nobody by remember { mutableStateOf(currentRules != null && currentRules.isEmpty()) }
    var followers by remember {
        mutableStateOf(currentRules?.any { it is ThreadgateAllowUnion.FollowerRule } == true)
    }
    var following by remember {
        mutableStateOf(currentRules?.any { it is ThreadgateAllowUnion.FollowingRule } == true)
    }
    var mentions by remember {
        mutableStateOf(currentRules?.any { it is ThreadgateAllowUnion.MentionRule } == true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(
                text = "Who can reply",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                SegmentedButton(
                    selected = !nobody,
                    onClick = { nobody = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Anyone") }
                SegmentedButton(
                    selected = nobody,
                    onClick = {
                        nobody = true
                        followers = false
                        following = false
                        mentions = false
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Nobody") }
            }

            ListItem(
                headlineContent = { Text("Your followers") },
                leadingContent = {
                    Checkbox(
                        checked = followers,
                        onCheckedChange = { followers = it },
                        enabled = !nobody,
                    )
                },
                modifier = Modifier.clickable(enabled = !nobody) { followers = !followers }
            )
            ListItem(
                headlineContent = { Text("People you follow") },
                leadingContent = {
                    Checkbox(
                        checked = following,
                        onCheckedChange = { following = it },
                        enabled = !nobody,
                    )
                },
                modifier = Modifier.clickable(enabled = !nobody) { following = !following }
            )
            ListItem(
                headlineContent = { Text("People you mention") },
                leadingContent = {
                    Checkbox(
                        checked = mentions,
                        onCheckedChange = { mentions = it },
                        enabled = !nobody,
                    )
                },
                modifier = Modifier.clickable(enabled = !nobody) { mentions = !mentions }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (nobody) {
                        onApply(emptyList())
                    } else if (!followers && !following && !mentions) {
                        onApply(null)
                    } else {
                        val rules = mutableListOf<ThreadgateAllowUnion>()
                        if (followers) rules += ThreadgateAllowUnion.FollowerRule(ThreadgateFollowerRule)
                        if (following) rules += ThreadgateAllowUnion.FollowingRule(ThreadgateFollowingRule)
                        if (mentions) rules += ThreadgateAllowUnion.MentionRule(ThreadgateMentionRule)
                        onApply(rules)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Done") }
        }
    }
}