package industries.geesawra.monarch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.Notification
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Composable
fun NotificationsView(
    viewModel: TimelineViewModel,
    state: LazyListState,
    modifier: Modifier = Modifier,
    isScrollEnabled: Boolean,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
) {
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = isScrollEnabled,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        viewModel.uiState.notifications.list.forEach { notif ->
            item() {
                RenderNotification(
                    viewModel = viewModel,
                    notification = notif,
                    onReplyTap = onReplyTap
                )
            }
        }

        if (viewModel.uiState.isFetchingMoreNotifications && viewModel.uiState.notifications.list.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(64.dp),
                        )
                    }
                }
            }
        }
    }

    val endOfListReached by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
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
        if (endOfListReached && viewModel.uiState.notifications.list.isNotEmpty()) {
            viewModel.fetchNotifications()
        }
    }
}

@ExperimentalTime
@Composable
private fun RenderNotification(
    viewModel: TimelineViewModel,
    notification: Notification,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
) {
    when (notification) {
        is Notification.Follow -> SkeetView(
            skeet = SkeetData(
                authorName = (notification.follow.displayName
                    ?: notification.follow.handle).toString() + " followed you!",
                authorAvatarURL = notification.follow.avatar.toString(),
            ),
            nested = true
        )

        is Notification.Like -> LikeRowView(
            likeData = notification.data,
            modifier = Modifier.height(120.dp),
        )

        is Notification.Mention -> SkeetView(
            viewModel = viewModel,
            skeet = SkeetData.fromPost(
                notification.parent,
                notification.mention,
                notification.author
            ),
            onReplyTap = onReplyTap,
        )

        is Notification.Quote -> SkeetView(
            viewModel = viewModel,
            skeet = SkeetData.fromPost(
                notification.parent,
                notification.quote,
                notification.author
            ),
            onReplyTap = onReplyTap,
        )

        is Notification.Reply -> SkeetView(
            viewModel = viewModel,
            skeet = SkeetData.fromPost(
                notification.parent,
                notification.reply,
                notification.author
            ),
            onReplyTap = onReplyTap,
        )

        is Notification.Repost -> SkeetView(
            skeet = SkeetData(
                authorName = (notification.author.displayName
                    ?: notification.author.handle).toString() + " reposted your post",
                authorAvatarURL = notification.author.avatar.toString()
            ),
            nested = true
        )

        else -> {}
    }
}