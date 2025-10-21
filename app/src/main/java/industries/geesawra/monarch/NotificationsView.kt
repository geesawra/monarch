package industries.geesawra.monarch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Composable
fun NotificationsView(
    viewModel: TimelineViewModel,
    state: LazyListState,
    modifier: Modifier = Modifier,
    isScrollEnabled: Boolean,
    onReplyTap: (SkeetData, Boolean) -> Unit = { _, _ -> },
    scaffoldPadding: PaddingValues
) {
    LaunchedEffect(Unit) {
        if (viewModel.uiState.unreadNotificationsAmt != 0) {
            delay(500)
            viewModel.updateSeenNotifications()
        }
    }

    LazyColumn(
        state = state,
        modifier = modifier
            .padding(scaffoldPadding),
        userScrollEnabled = isScrollEnabled,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = viewModel.uiState.notifications,
            key = { it.createdAt() }
        ) { notif ->
            Card(
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (notif.new() && viewModel.uiState.unreadNotificationsAmt != 0) 8.dp else 0.dp,
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                RenderNotification(
                    viewModel = viewModel,
                    notification = notif,
                    onReplyTap = onReplyTap
                )
            }
        }

        if (viewModel.uiState.isFetchingMoreNotifications && viewModel.uiState.notifications.isNotEmpty()) {
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
        if (endOfListReached && viewModel.uiState.notifications.isNotEmpty()) {
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

        is Notification.Like -> LikeRepostRowView(
            data = notification.data,
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

        is Notification.Repost -> LikeRepostRowView(
            data = notification.data,
        )


        else -> {}
    }
}