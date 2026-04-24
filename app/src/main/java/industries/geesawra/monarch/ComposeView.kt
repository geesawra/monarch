package industries.geesawra.monarch

import android.content.Context
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import industries.geesawra.monarch.datalayer.ThreadPostData
import industries.geesawra.monarch.datalayer.splitTextForThread
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

private const val MAX_POST_CHARS = 300
private const val KEYBOARD_SHOW_DELAY_MS = 100L
private const val MENTION_SEARCH_DEBOUNCE_MS = 300L
private const val LINK_PREVIEW_FETCH_DEBOUNCE_MS = 500L
private const val MAX_THREAD_POSTS = 10

class ThreadPostState(
    val id: Int,
    val textFieldState: TextFieldState,
) {
    val media = mutableStateOf<List<Uri>>(emptyList())
    val mediaIsVideo = mutableStateOf(false)
    val mediaAltTexts = mutableStateOf<Map<Uri, String>>(emptyMap())
    val facets = mutableStateListOf<Facet>()
    val mentionDids = mutableStateMapOf<String, Did>()
    val linkPreview = mutableStateOf<LinkPreviewData?>(null)
    val linkPreviewDismissed = mutableStateOf(false)
}

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
    textfieldState: TextFieldState,
    mediaSelected: MutableState<List<Uri>>,
    mediaSelectedIsVideo: MutableState<Boolean>,
    mediaAltTexts: MutableState<Map<Uri, String>>,
    threadgateRules: MutableState<List<ThreadgateAllowUnion>?>,
    linkPreview: MutableState<LinkPreviewData?>,
    onDraftsClick: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val maxChars = MAX_POST_CHARS
    val facets = remember { mutableListOf<Facet>() }
    val mentionResults = remember { mutableStateOf(listOf<ProfileViewBasic>()) }
    val showMentionDropdown = remember { mutableStateOf(false) }
    val mentionDids = remember { mutableStateMapOf<String, Did>() }

    val linkPreviewLoading = remember { mutableStateOf(false) }
    val linkPreviewDismissed = remember { mutableStateOf(false) }
    val linkPreviewCache = remember { mutableMapOf<String, LinkPreviewData?>() }

    val isThreadMode = remember { mutableStateOf(false) }
    var threadPostIdCounter = remember { mutableStateOf(1) }
    val threadPosts = remember {
        mutableStateListOf(
            ThreadPostState(0, TextFieldState())
        )
    }
    val uploadingThread = remember { mutableStateOf(false) }
    val threadProgress = remember { mutableStateOf(0 to 0) }

    LaunchedEffect(scaffoldState.bottomSheetState.targetValue) {
        when (scaffoldState.bottomSheetState.targetValue) {
            SheetValue.Hidden -> {
                keyboardController?.hide()
                focusManager.clearFocus()
                if (timelineViewModel.uploadingPost) {
                    timelineViewModel.cancelPost()
                }
                if (!wasEdited.value) {
                    textfieldState.clearText()
                    inReplyTo.value = null
                    isQuotePost.value = false
                    mediaSelected.value = listOf()
                    mediaSelectedIsVideo.value = false
                    mediaAltTexts.value = emptyMap()
                    threadgateRules.value = null
                    mentionResults.value = listOf()
                    showMentionDropdown.value = false
                    mentionDids.clear()
                    linkPreview.value = null
                    linkPreviewLoading.value = false
                    linkPreviewDismissed.value = false
                    linkPreviewCache.clear()
                    timelineViewModel.clearActiveDraft()
                    isThreadMode.value = false
                    threadPosts.clear()
                    threadPosts.add(ThreadPostState(0, TextFieldState()))
                    threadPostIdCounter.value = 1
                    uploadingThread.value = false
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
                delay(KEYBOARD_SHOW_DELAY_MS)
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

    val focusedThreadPostIndex = remember { mutableStateOf(0) }

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

            if (isThreadMode.value) {
                val targetPost = threadPosts.getOrNull(focusedThreadPostIndex.value)
                if (targetPost != null) {
                    targetPost.mediaIsVideo.value = containsVideo && urisMap.size == 1
                    targetPost.media.value = urisMap.filterValues { it != null }.keys.toList()
                }
            } else {
                mediaSelectedIsVideo.value = containsVideo && urisMap.size == 1
                mediaSelected.value = urisMap.filterValues { it != null }.keys.toList()
            }
        }

    val cameraImageUri = remember { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraImageUri.value?.let { uri ->
                    if (isThreadMode.value) {
                        val targetPost = threadPosts.getOrNull(focusedThreadPostIndex.value)
                        if (targetPost != null) {
                            targetPost.mediaIsVideo.value = false
                            targetPost.media.value = targetPost.media.value + uri
                        }
                    } else {
                        mediaSelectedIsVideo.value = false
                        mediaSelected.value = mediaSelected.value + uri
                    }
                }
            }
        }

    val altEditorUri = remember { mutableStateOf<Uri?>(null) }

    val sheetScrollConnection = remember(scrollState) {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (scrollState.value > 0) return available
                return Velocity.Zero
            }
        }
    }

                                                                                                                                                                                                                    var showThreadgateSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.add(WindowInsets.navigationBars))
            .padding(start = 16.dp, top = 16.dp, end = 16.dp)
    ) {
        // Scrollable Content Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(sheetScrollConnection)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.End
        ) {
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

                PostButtonRow(
                    context = context,
                    uploadingPost = timelineViewModel.uploadingPost,
                    postText = textfieldState.text.toString(),
                    mediaSelected = mediaSelected,
                    mediaSelectedIsVideo = mediaSelectedIsVideo,
                    mediaAltTexts = mediaAltTexts,
                    coroutineScope = coroutineScope,
                    maxChars = maxChars,
                    timelineViewModel = timelineViewModel,
                    autoLikeOnReply = settingsState.autoLikeOnReply,
                    requireAltText = settingsState.requireAltText,
                    autoThreadOnOverflow = settingsState.autoThreadOnOverflow,
                    scaffoldState = scaffoldState,
                    inReplyToData = inReplyTo.value,
                    isQuotePost = isQuotePost.value,
                    facets = facets,
                    linkPreview = if (!linkPreviewDismissed.value) linkPreview.value else null,
                    threadgateRules = threadgateRules,
                    wasEdited = wasEdited,
                    isThreadMode = isThreadMode,
                    threadPosts = threadPosts,
                    focusedThreadPostIndex = focusedThreadPostIndex.value,
                    onThreadPost = {
                        val job = coroutineScope.launch {
                            timelineViewModel.uploadingPost = true
                            val posts = threadPosts.map { post ->
                                ThreadPostData(
                                    text = post.textFieldState.text.toString(),
                                    facets = post.facets.toList(),
                                    images = if (!post.mediaIsVideo.value) post.media.value.ifEmpty { null } else null,
                                    video = if (post.mediaIsVideo.value) post.media.value.firstOrNull() else null,
                                    mediaAltTexts = post.mediaAltTexts.value,
                                    linkPreview = if (!post.linkPreviewDismissed.value) post.linkPreview.value else null,
                                )
                            }
                            timelineViewModel.postThread(
                                posts = posts,
                                threadgateRules = threadgateRules.value,
                                onProgress = { current, total ->
                                    threadProgress.value = current to total
                                },
                            ).onSuccess {
                                wasEdited.value = false
                                coroutineScope.launch {
                                    scaffoldState.bottomSheetState.hide()
                                }
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "Could not post thread: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.also {
                                timelineViewModel.uploadingPost = false
                                timelineViewModel.setPostJob(null)
                            }
                        }
                    },
                    mentionDids = mentionDids,
                )

                LaunchedEffect(Unit) {
                    snapshotFlow {
                        if (isThreadMode.value) {
                            threadPosts.any { it.textFieldState.text.isNotEmpty() || it.media.value.isNotEmpty() }
                        } else {
                            textfieldState.text.isNotEmpty() || mediaSelected.value.isNotEmpty()
                        }
                    }.collect { hasContent ->
                        wasEdited.value = hasContent
                    }
                }

                if (isThreadMode.value) {
                    ThreadComposeContent(
                        threadPosts = threadPosts,
                        timelineViewModel = timelineViewModel,
                        settingsState = settingsState,
                        maxChars = maxChars,
                        onRemovePost = { index ->
                            if (threadPosts.size > 1) {
                                threadPosts.removeAt(index)
                                if (focusedThreadPostIndex.value >= threadPosts.size) {
                                    focusedThreadPostIndex.value = threadPosts.size - 1
                                }
                            }
                        },
                        onFocusChanged = { index ->
                            focusedThreadPostIndex.value = index
                        },
                        linkPreviewCache = linkPreviewCache,
                    )
                } else {
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

                    delay(MENTION_SEARCH_DEBOUNCE_MS)

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

                    delay(LINK_PREVIEW_FETCH_DEBOUNCE_MS)

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
                            model = timelineViewModel.user?.avatar?.uri,
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

                inReplyTo.value?.let {
                    Text(
                        text = if (isQuotePost.value) "Quoting" else "Replying to",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 8.dp, top = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
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
                            translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                            targetTranslationLanguage = settingsState.targetTranslationLanguage,
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
                    mediaAltTexts = mediaAltTexts.value,
                    onImageRemove = { index ->
                        val toDelUri = mediaSelected.value[index]
                        mediaSelected.value = mediaSelected.value.filter { uri -> uri != toDelUri }
                        mediaAltTexts.value = mediaAltTexts.value - toDelUri
                    },
                    onVideoRemove = {
                        mediaAltTexts.value = mediaAltTexts.value - mediaSelected.value.toSet()
                        mediaSelected.value = listOf()
                    },
                    onEditAlt = { index -> altEditorUri.value = mediaSelected.value[index] },
                )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            ComposeBottomBar(
                context = context,
                pickMedia = pickMedia,
                takePicture = takePicture,
                cameraImageUri = cameraImageUri,
                mediaSelected = mediaSelected,
                mediaSelectedIsVideo = mediaSelectedIsVideo,
                mediaAltTexts = mediaAltTexts,
                onDraftsClick = onDraftsClick,
                onThreadgateClick = { showThreadgateSheet = true },
                threadgateRules = threadgateRules,
                isThreadMode = isThreadMode,
                threadPosts = threadPosts,
                threadPostIdCounter = threadPostIdCounter,
                textfieldState = textfieldState,
                linkPreviewState = linkPreview,
                linkPreviewDismissedState = linkPreviewDismissed,
                mentionDids = mentionDids,
                inReplyToData = inReplyTo.value,
                isQuotePost = isQuotePost.value,
                coroutineScope = coroutineScope,
                facets = facets,
            )
        }

    altEditorUri.value?.let { uri ->
        AltTextEditorSheet(
            uri = uri,
            initialText = mediaAltTexts.value[uri].orEmpty(),
            generator = timelineViewModel.altTextGenerator,
            aiEnabled = settingsState.aiAltTextEnabled,
            onDismiss = { altEditorUri.value = null },
            onSave = { text ->
                mediaAltTexts.value = mediaAltTexts.value + (uri to text)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostButtonRow(
    context: Context,
    uploadingPost: Boolean,
    postText: String,
    mediaSelected: MutableState<List<Uri>>,
    mediaSelectedIsVideo: MutableState<Boolean>,
    mediaAltTexts: MutableState<Map<Uri, String>>,
    coroutineScope: CoroutineScope,
    maxChars: Int,
    timelineViewModel: TimelineViewModel,
    autoLikeOnReply: Boolean = false,
    requireAltText: Boolean = false,
    autoThreadOnOverflow: Boolean = false,
    scaffoldState: BottomSheetScaffoldState,
    inReplyToData: SkeetData? = null,
    isQuotePost: Boolean = false,
    facets: List<Facet> = listOf(),
    linkPreview: LinkPreviewData? = null,
    threadgateRules: MutableState<List<ThreadgateAllowUnion>?>,
    wasEdited: MutableState<Boolean> = mutableStateOf(false),
    isThreadMode: MutableState<Boolean> = mutableStateOf(false),
    threadPosts: SnapshotStateList<ThreadPostState>? = null,
    focusedThreadPostIndex: Int = 0,
    onThreadPost: (() -> Unit)? = null,
    mentionDids: MutableMap<String, Did>? = null,
) {
    val allMediaHasAlt = mediaSelected.value.isEmpty() ||
        mediaSelected.value.all { uri -> mediaAltTexts.value[uri]?.isNotBlank() == true }

    val threadButtonEnabled = if (isThreadMode.value && threadPosts != null) {
        threadPosts.all { post ->
            val hasContent = post.textFieldState.text.isNotBlank() || post.media.value.isNotEmpty()
            val withinLimit = post.textFieldState.text.length <= maxChars
            val altTextOk = !requireAltText || post.media.value.isEmpty() ||
                post.media.value.all { uri -> post.mediaAltTexts.value[uri]?.isNotBlank() == true }
            hasContent && withinLimit && altTextOk
        }
    } else false

    val isImplicitEligible = inReplyToData == null && !isQuotePost && autoThreadOnOverflow
    val overflow = postText.length > maxChars
    val implicitSplitCount = if (isImplicitEligible && overflow) {
        splitTextForThread(postText, maxChars).size
    } else 0
    val canImplicitSplit = implicitSplitCount in 2..MAX_THREAD_POSTS

    val postButtonEnabled = remember(postText, mediaSelected.value, mediaAltTexts.value, requireAltText, isImplicitEligible) {
        val hasContent = postText.isNotBlank() || mediaSelected.value.isNotEmpty()
        val withinLimit = postText.length <= maxChars
        val altTextOk = !requireAltText || allMediaHasAlt
        val implicitOk = isImplicitEligible && postText.length > maxChars &&
            splitTextForThread(postText, maxChars).size in 2..MAX_THREAD_POSTS
        hasContent && (withinLimit || implicitOk) && altTextOk
    }

    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uploadingPost) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                timelineViewModel.videoUploadStatus?.let { status ->
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.defaultMinSize(minWidth = 120.dp),
                    )
                }
                Spacer(Modifier.size(4.dp))
                val progress = timelineViewModel.videoUploadProgress
                if (progress != null && progress > 0) {
                    CircularWavyProgressIndicator(
                        progress = { progress.toFloat() / 100f },
                    )
                } else {
                    CircularWavyProgressIndicator()
                }
            }
        } else if (isThreadMode.value && onThreadPost != null) {
            val focusedPostText = threadPosts?.getOrNull(focusedThreadPostIndex)?.textFieldState?.text?.length ?: 0
            val charsRemaining = maxChars - focusedPostText
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onThreadPost()
                },
                modifier = Modifier.padding(end = 8.dp),
                enabled = threadButtonEnabled && !uploadingPost,
                colors = if (focusedPostText > maxChars) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Post thread")
                if (focusedPostText > 0) {
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("$charsRemaining")
                }
            }
        } else {
            if (canImplicitSplit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.FormatListNumbered,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "Auto-thread",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val job = coroutineScope.launch {
                        timelineViewModel.uploadingPost = true
                        val result = if (canImplicitSplit) {
                            val posts = buildImplicitThreadPosts(
                                text = postText,
                                mentionDids = mentionDids.orEmpty(),
                                maxChars = maxChars,
                                images = if (!mediaSelectedIsVideo.value) mediaSelected.value.ifEmpty { null } else null,
                                video = if (mediaSelectedIsVideo.value) mediaSelected.value.firstOrNull() else null,
                                mediaAltTexts = mediaAltTexts.value,
                                linkPreview = linkPreview,
                            )
                            timelineViewModel.postThread(
                                posts = posts,
                                threadgateRules = threadgateRules.value,
                            ).map { Unit }
                        } else {
                            timelineViewModel.post(
                                content = postText,
                                facets = facets,
                                images = if (!mediaSelectedIsVideo.value) mediaSelected.value.ifEmpty { null } else null,
                                video = if (mediaSelectedIsVideo.value) mediaSelected.value.firstOrNull() else null,
                                mediaAltTexts = mediaAltTexts.value,
                                replyRef = if (!isQuotePost) inReplyToData?.replyRef() else null,
                                quotePostRef = if (isQuotePost) {
                                    val cid = inReplyToData?.cid
                                    val uri = inReplyToData?.uri
                                    if (cid == null || uri == null) null else StrongRef(uri, cid)
                                } else null,
                                linkPreview = linkPreview,
                                threadgateRules = threadgateRules.value,
                            ).map { Unit }
                        }
                        result.onSuccess {
                            wasEdited.value = false
                            timelineViewModel.draftsState.activeDraftId?.let { draftId ->
                                timelineViewModel.deleteDraft(draftId)
                                timelineViewModel.clearActiveDraft()
                            }
                            if (!canImplicitSplit && autoLikeOnReply && !isQuotePost && inReplyToData != null && !inReplyToData.didLike && !timelineViewModel.isOwnPost(inReplyToData)) {
                                timelineViewModel.like(inReplyToData.uri, inReplyToData.cid)
                            }
                            coroutineScope.launch { scaffoldState.bottomSheetState.hide() }
                        }.onFailure {
                            Toast.makeText(context, "Could not post: ${it.message}", Toast.LENGTH_LONG).show()
                        }.also {
                            timelineViewModel.uploadingPost = false
                            timelineViewModel.setPostJob(null)
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
                enabled = postButtonEnabled && !uploadingPost,
                colors = if (overflow && !canImplicitSplit) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Post")
                if (postText.isNotEmpty()) {
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    val label = when {
                        canImplicitSplit -> "$implicitSplitCount"
                        else -> "${maxChars - postText.length}"
                    }
                    Text(label)
                }
            }
        }
    }
}

@Composable
fun ComposeBottomBar(
    context: Context,
    pickMedia: ManagedActivityResultLauncher<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>,
    takePicture: ManagedActivityResultLauncher<Uri, Boolean>,
    cameraImageUri: MutableState<Uri?>,
    mediaSelected: MutableState<List<Uri>>,
    mediaSelectedIsVideo: MutableState<Boolean>,
    mediaAltTexts: MutableState<Map<Uri, String>>,
    onDraftsClick: () -> Unit = {},
    onThreadgateClick: () -> Unit = {},
    threadgateRules: MutableState<List<ThreadgateAllowUnion>?>,
    isThreadMode: MutableState<Boolean> = mutableStateOf(false),
    threadPosts: SnapshotStateList<ThreadPostState>? = null,
    threadPostIdCounter: MutableState<Int>? = null,
    textfieldState: TextFieldState? = null,
    linkPreviewState: MutableState<LinkPreviewData?>? = null,
    linkPreviewDismissedState: MutableState<Boolean>? = null,
    mentionDids: MutableMap<String, Did>? = null,
    inReplyToData: SkeetData? = null,
    isQuotePost: Boolean = false,
    coroutineScope: CoroutineScope,
    facets: List<Facet> = listOf(),
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        TextButton(onClick = onDraftsClick) {
            Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Drafts")
        }
        TextButton(onClick = onThreadgateClick) {
            Icon(
                Icons.Default.Shield,
                contentDescription = "Reply settings",
                tint = if (threadgateRules.value != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
        }
        if (inReplyToData == null && !isQuotePost && (threadPosts == null || threadPosts.size < MAX_THREAD_POSTS)) {
            TextButton(onClick = {
                if (!isThreadMode.value && threadPosts != null && textfieldState != null && threadPostIdCounter != null) {
                    val firstPost = threadPosts[0]
                    firstPost.textFieldState.edit {
                        replace(0, length, textfieldState.text.toString())
                        selection = TextRange(textfieldState.text.length)
                    }
                    firstPost.media.value = mediaSelected.value
                    firstPost.mediaIsVideo.value = mediaSelectedIsVideo.value
                    firstPost.mediaAltTexts.value = mediaAltTexts.value
                    firstPost.facets.clear()
                    firstPost.facets.addAll(facets)
                    mentionDids?.let { dids ->
                        firstPost.mentionDids.clear()
                        firstPost.mentionDids.putAll(dids)
                    }
                    linkPreviewState?.let { firstPost.linkPreview.value = it.value }
                    linkPreviewDismissedState?.let { firstPost.linkPreviewDismissed.value = it.value }
                    textfieldState.clearText()
                    mediaSelected.value = emptyList()
                    mediaSelectedIsVideo.value = false
                    mediaAltTexts.value = emptyMap()
                    isThreadMode.value = true
                    val newId = threadPostIdCounter.value++
                    threadPosts.add(ThreadPostState(newId, TextFieldState()))
                    keyboardController?.show()
                } else if (isThreadMode.value && threadPosts != null && threadPostIdCounter != null) {
                    val newId = threadPostIdCounter.value++
                    threadPosts.add(ThreadPostState(newId, TextFieldState()))
                    keyboardController?.show()
                }
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add thread post",
                    tint = if (isThreadMode.value) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }
    }
}

val tokensRegexp = Regex("(\\S+)")

private val bareUrlRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\\.[a-zA-Z]{2,}(/\\S*)?$")

internal fun isUrl(s: String): Boolean {
    return URLUtil.isHttpUrl(s) || URLUtil.isHttpsUrl(s) || bareUrlRegex.matches(s)
}

internal fun normalizeUrl(s: String): String {
    if (URLUtil.isHttpUrl(s) || URLUtil.isHttpsUrl(s)) return s
    return "https://$s"
}

internal fun readFacets(data: String, mentionDids: Map<String, Did> = emptyMap()): List<Facet> {
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

private fun buildImplicitThreadPosts(
    text: String,
    mentionDids: Map<String, Did>,
    maxChars: Int,
    images: List<Uri>?,
    video: Uri?,
    mediaAltTexts: Map<Uri, String>,
    linkPreview: LinkPreviewData?,
): List<ThreadPostData> {
    val fragments = splitTextForThread(text, maxChars)

    val linkPreviewFragmentIdx: Int? = if (linkPreview != null) {
        val idx = fragments.indexOfFirst { frag: String ->
            tokensRegexp.findAll(frag)
                .map { m -> m.value }
                .firstOrNull { s -> isUrl(s) }
                ?.let { s -> normalizeUrl(s) == linkPreview.url } == true
        }
        if (idx >= 0) idx else null
    } else null

    return fragments.mapIndexed { idx: Int, frag: String ->
        ThreadPostData(
            text = frag,
            facets = readFacets(frag, mentionDids),
            images = if (idx == 0) images else null,
            video = if (idx == 0) video else null,
            mediaAltTexts = if (idx == 0) mediaAltTexts else emptyMap(),
            linkPreview = if (idx == linkPreviewFragmentIdx) linkPreview else null,
        )
    }
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
    mediaAltTexts: Map<Uri, String>,
    onImageRemove: (Int) -> Unit,
    onVideoRemove: () -> Unit,
    onEditAlt: (Int) -> Unit,
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
                        val alt = mediaAltTexts[uri].orEmpty()
                        Image(
                            url = uri.toString(),
                            alt = alt.ifEmpty { "Tap to add alt text" },
                        )
                    },
                    onCrossClick = { onImageRemove(it) },
                    onImageClick = { onEditAlt(it) },
                )

                true -> DeletableMediaView(
                    originalIndex = 0,
                    onCrossClick = { onVideoRemove() },
                    onMediaClick = { }
                ) {
                    VideoPlayer(
                        url = mediaSelected.first().toString(),
                        mimeType = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
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

            ThreadgateRuleItem("Your followers", followers, { followers = it }, !nobody)
            ThreadgateRuleItem("People you follow", following, { following = it }, !nobody)
            ThreadgateRuleItem("People you mention", mentions, { mentions = it }, !nobody)

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

@Composable
private fun ThreadgateRuleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadComposeContent(
    threadPosts: SnapshotStateList<ThreadPostState>,
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState,
    maxChars: Int,
    onRemovePost: (Int) -> Unit,
    onFocusChanged: (Int) -> Unit,
    linkPreviewCache: MutableMap<String, LinkPreviewData?>,
) {
    val urlColor = MaterialTheme.colorScheme.primary

    val keyboardController = LocalSoftwareKeyboardController.current
    Column(modifier = Modifier.fillMaxWidth()) {
        threadPosts.forEachIndexed { index, postState ->
            val focusRequester = remember { FocusRequester() }
            val linkPreviewLoading = remember { mutableStateOf(false) }

            LaunchedEffect(threadPosts.size) {
                if (index == threadPosts.size - 1 && threadPosts.size > 1) {
                    runCatching { focusRequester.requestFocus() }
                    keyboardController?.show()
                }
            }

            LaunchedEffect(postState.textFieldState.text.toString()) {
                val text = postState.textFieldState.text.toString()
                val firstUrl = tokensRegexp.findAll(text)
                    .map { it.value }
                    .firstOrNull { isUrl(it) }

                if (firstUrl == null) {
                    linkPreviewLoading.value = false
                    return@LaunchedEffect
                }

                val normalizedUrl = normalizeUrl(firstUrl)

                if (postState.linkPreviewDismissed.value && postState.linkPreview.value == null) {
                    return@LaunchedEffect
                }

                if (postState.linkPreview.value?.url == normalizedUrl) {
                    return@LaunchedEffect
                }

                if (linkPreviewCache.containsKey(normalizedUrl)) {
                    postState.linkPreview.value = linkPreviewCache[normalizedUrl]
                    linkPreviewLoading.value = false
                    return@LaunchedEffect
                }

                delay(LINK_PREVIEW_FETCH_DEBOUNCE_MS)

                linkPreviewLoading.value = true
                val preview = LinkPreviewFetcher.fetch(normalizedUrl)
                linkPreviewCache[normalizedUrl] = preview
                postState.linkPreview.value = preview
                linkPreviewLoading.value = false
                postState.linkPreviewDismissed.value = false
            }

            LaunchedEffect(postState.textFieldState.text) {
                val data = postState.textFieldState.text.toString()
                val computed = readFacets(data, postState.mentionDids)
                postState.facets.clear()
                postState.facets.addAll(computed)
            }

            val facetHighlighter = remember(postState) {
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
                                if (postState.mentionDids.containsKey(handle)) {
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
                        model = timelineViewModel.user?.avatar?.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(avatarSize())
                            .clip(settingsState.avatarClipShape),
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    if (index < threadPosts.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = avatarTextGap())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (it.isFocused) onFocusChanged(index) },
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                autoCorrectEnabled = true,
                                keyboardType = KeyboardType.Text,
                            ),
                            placeholder = {
                                Text(if (index == 0) "Start your thread..." else "Continue...")
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                errorContainerColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent,
                            ),
                            isError = postState.textFieldState.text.length > maxChars,
                            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                            state = postState.textFieldState,
                            outputTransformation = facetHighlighter,
                        )
                        if (threadPosts.size > 1) {
                            IconButton(
                                onClick = { onRemovePost(index) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove post",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    LinkPreviewCard(
                        isLoading = linkPreviewLoading.value,
                        preview = if (!postState.linkPreviewDismissed.value) postState.linkPreview.value else null,
                        onDismiss = {
                            postState.linkPreview.value = null
                            postState.linkPreviewDismissed.value = true
                        },
                    )

                    if (postState.media.value.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            if (!postState.mediaIsVideo.value) {
                                PostImageGallery(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    images = postState.media.value.map { uri ->
                                        Image(
                                            url = uri.toString(),
                                            alt = postState.mediaAltTexts.value[uri].orEmpty().ifEmpty { "Tap to add alt text" },
                                        )
                                    },
                                    onCrossClick = { i ->
                                        val toDelUri = postState.media.value[i]
                                        postState.media.value = postState.media.value.filter { it != toDelUri }
                                        postState.mediaAltTexts.value = postState.mediaAltTexts.value - toDelUri
                                    },
                                    onImageClick = { },
                                )
                            } else {
                                DeletableMediaView(
                                    originalIndex = 0,
                                    onCrossClick = {
                                        postState.mediaAltTexts.value = postState.mediaAltTexts.value - postState.media.value.toSet()
                                        postState.media.value = emptyList()
                                        postState.mediaIsVideo.value = false
                                    },
                                    onMediaClick = { }
                                ) {
                                    VideoPlayer(
                                        url = postState.media.value.first().toString(),
                                        mimeType = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}