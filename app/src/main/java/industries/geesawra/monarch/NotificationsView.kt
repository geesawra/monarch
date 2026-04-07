package industries.geesawra.monarch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.AvatarShape
import industries.geesawra.monarch.datalayer.Notification
import industries.geesawra.monarch.datalayer.PostTextSize
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import androidx.compose.runtime.DisposableEffect
import sh.christian.ozone.api.Did
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Composable
fun NotificationsView(
    viewModel: TimelineViewModel,
    state: LazyListState,
    modifier: Modifier = Modifier,
    isScrollEnabled: Boolean,
    settingsState: SettingsState = SettingsState(),
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    onSeeMoreTap: ((SkeetData) -> Unit)? = null,
    onProfileTap: ((Did) -> Unit)? = null,
    scaffoldPadding: PaddingValues
) {
    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.uiState.unreadNotificationsAmt != 0) {
                viewModel.updateSeenNotifications()
            }
        }
    }

    LazyColumn(
        state = state,
        modifier = modifier.testTag("notifications_list"),
        userScrollEnabled = isScrollEnabled,
        contentPadding = PaddingValues(
            top = scaffoldPadding.calculateTopPadding(),
            bottom = scaffoldPadding.calculateBottomPadding(),
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = viewModel.uiState.notifications,
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
                            .width(4.dp)
                            .fillMaxHeight()
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
                                    viewModel.setThread(skeet)
                                    onSeeMoreTap(skeet)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    OnEndOfListReached(
        listState = state,
        items = viewModel.uiState.notifications,
        onEndReached = { viewModel.fetchNotifications() },
    )
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
                        onShowThread = {
                            onProfileTap?.invoke(notification.follow.did)
                        },
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
            renderingMention = true,
            onShowThread = onShowThread,
            onAvatarTap = onProfileTap,
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
            onShowThread = onShowThread,
            onAvatarTap = onProfileTap,
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
            renderingReplyNotif = false,
            onShowThread = onShowThread,
            onAvatarTap = onProfileTap,
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