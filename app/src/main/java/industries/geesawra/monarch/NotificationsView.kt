package industries.geesawra.monarch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope

import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.Notification
import industries.geesawra.monarch.datalayer.NotificationTab
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Did
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Composable
fun NotificationsView(
    viewModel: TimelineViewModel,
    scrollToTopSignal: Int,
    modifier: Modifier = Modifier,
    isScrollEnabled: Boolean,
    settingsState: SettingsState = SettingsState(),
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onSeeMoreTap: ((SkeetData) -> Unit)? = null,
    onProfileTap: ((Did) -> Unit)? = null,
    scaffoldPadding: PaddingValues
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.unreadNotificationsAmt != 0) {
                viewModel.updateSeenNotifications()
            }
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm.cancelAll()
        }
    }

    val tabs = NotificationTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val listStates = remember {
        tabs.associateWith { LazyListState() }
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        val tab = tabs[pagerState.currentPage]
        viewModel.activeNotificationTab = tab
        if (viewModel.notificationsForTab(tab).isEmpty() && !viewModel.isFetchingNotificationsForTab(tab)) {
            viewModel.fetchNotifications(tab)
        }
    }

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            val activeTab = tabs[pagerState.settledPage]
            listStates[activeTab]?.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
    ) {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex),
            divider = {},
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(tab.name) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
        ) { page ->
            val tab = tabs[page]
            val data = viewModel.notificationsForTab(tab)
            val listState = listStates.getValue(tab)
            val isLoading = viewModel.isFetchingNotificationsForTab(tab)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("notifications_list_${tab.name}"),
                userScrollEnabled = isScrollEnabled,
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = 0.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isLoading && data.isEmpty()) {
                    items(6, key = { "skeleton_${tab.name}_$it" }) {
                        SkeletonNotification()
                    }
                } else {
                    items(
                        items = data,
                        key = { it.uniqueKey() }
                    ) { notif ->
                        val isUnread = viewModel.isNotificationNew(notif)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier
                        ) {
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(4.dp)
                                        .background(
                                            if (isUnread) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    RenderNotification(
                                        viewModel = viewModel,
                                        notification = notif,
                                        settingsState = settingsState,
                                        onReplyTap = onReplyTap,
                                        onProfileTap = onProfileTap,
                                        onShowThread = { skeet ->
                                            if (onSeeMoreTap != null) {
                                                viewModel.startThread(skeet)
                                                onSeeMoreTap(skeet)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OnEndOfListReached(
                listState = listState,
                items = data,
                onEndReached = {
                    if (!viewModel.isFetchingNotificationsForTab(tab)) {
                        viewModel.fetchNotifications(tab)
                    }
                },
            )
        }
    }
}

@Composable
private fun SkeletonNotification() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .themedPlaceholder()
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .themedPlaceholder()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .themedPlaceholder()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .themedPlaceholder()
                )
            }
        }
    }
}

@ExperimentalTime
@Composable
private fun RenderNotification(
    viewModel: TimelineViewModel,
    notification: Notification,
    settingsState: SettingsState = SettingsState(),
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onProfileTap: ((Did) -> Unit)? = null,
    onShowThread: (SkeetData) -> Unit = {},
) {
    val avatarClipShape = settingsState.avatarClipShape

    when (notification) {
        is Notification.Follow -> {
            Column {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .padding(
                            top = 16.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 4.dp
                        )
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Image(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New follower icon",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.inverseSurface),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = (notification.follow.displayName
                            ?: notification.follow.handle).toString() + " followed you!",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.padding(
                        top = 8.dp,
                        start = 40.dp,
                        end = 16.dp,
                        bottom = 8.dp
                    ),
                ) {
                    SkeetView(
                        viewModel = viewModel,
                        skeet = SkeetData(
                            authorName = (notification.follow.displayName
                                ?: notification.follow.handle).toString(),
                            authorAvatarURL = notification.follow.avatar?.uri,
                            authorHandle = notification.follow.handle,
                            content = notification.follow.description ?: ""
                        ),
                        nested = true,
                        postTextSize = settingsState.postTextSize,
                        avatarShape = avatarClipShape,
                        showLabels = settingsState.showLabels,
                        showPronouns = settingsState.showPronounsInPosts,
                        onShowThread = {
                            onProfileTap?.invoke(notification.follow.did)
                        },
                        translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
                        targetTranslationLanguage = settingsState.targetTranslationLanguage,
                        carouselImageGallery = settingsState.carouselImageGallery,
                    )
                }
            }
        }

        is Notification.Like -> LikeRepostRowView(
            data = notification.data,
            settingsState = settingsState,
            onShowThread = onShowThread,
            onProfileTap = onProfileTap,
        )

        is Notification.Mention -> SkeetView(
            viewModel = viewModel,
            skeet = notification.hydratedPost ?: SkeetData.fromPost(
                notification.parent,
                notification.mention,
                notification.author,
            ),
            onReplyTap = onReplyTap,
            postTextSize = settingsState.postTextSize,
            avatarShape = avatarClipShape,
            showLabels = settingsState.showLabels,
            showPronouns = settingsState.showPronounsInPosts,
            renderingMention = true,
            onShowThread = onShowThread,
            onAvatarTap = onProfileTap,
            translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
            targetTranslationLanguage = settingsState.targetTranslationLanguage,
            carouselImageGallery = settingsState.carouselImageGallery,
        )

        is Notification.Quote -> SkeetView(
            viewModel = viewModel,
            skeet = notification.hydratedPost ?: SkeetData.fromPost(
                notification.parent,
                notification.quote,
                notification.author,
                notification.quotedPost
            ),
            onReplyTap = onReplyTap,
            postTextSize = settingsState.postTextSize,
            avatarShape = avatarClipShape,
            showLabels = settingsState.showLabels,
            showPronouns = settingsState.showPronounsInPosts,
            onShowThread = onShowThread,
            onAvatarTap = onProfileTap,
            translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
            targetTranslationLanguage = settingsState.targetTranslationLanguage,
            carouselImageGallery = settingsState.carouselImageGallery,
        )

        is Notification.Reply -> SkeetView(
            viewModel = viewModel,
            skeet = notification.hydratedPost ?: SkeetData.fromPost(
                notification.parent,
                notification.reply,
                notification.author
            ),
            onReplyTap = onReplyTap,
            postTextSize = settingsState.postTextSize,
            avatarShape = avatarClipShape,
            showLabels = settingsState.showLabels,
            showPronouns = settingsState.showPronounsInPosts,
            renderingReplyNotif = false,
            onShowThread = onShowThread,
            onAvatarTap = onProfileTap,
            translationEnabled = settingsState.aiEnabled && settingsState.translationEnabled,
            targetTranslationLanguage = settingsState.targetTranslationLanguage,
            carouselImageGallery = settingsState.carouselImageGallery,
        )

        is Notification.Repost -> LikeRepostRowView(
            data = notification.data,
            settingsState = settingsState,
            onShowThread = onShowThread,
            onProfileTap = onProfileTap,
        )


        else -> {}
    }
}
