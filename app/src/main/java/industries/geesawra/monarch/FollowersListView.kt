package industries.geesawra.monarch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.bsky.actor.ProfileView
import coil3.compose.AsyncImage
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.Did

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowersListView(
    timelineViewModel: TimelineViewModel,
    backButton: () -> Unit,
    onProfileTap: (Did) -> Unit,
) {
    val listDid = timelineViewModel.followersListDid ?: return
    val listName = timelineViewModel.followersListName ?: ""
    var selectedTab by rememberSaveable { mutableIntStateOf(if (timelineViewModel.showFollowersTab) 0 else 1) }
    var mutualsOnly by rememberSaveable { mutableStateOf(false) }


    val followers = timelineViewModel.profileFollowers
    val follows = timelineViewModel.profileFollows
    val currentList = if (selectedTab == 0) followers else follows

    val knownList = remember(currentList) {
        currentList.filter { it.viewer?.following != null }
    }
    val othersList = remember(currentList) {
        currentList.filter { it.viewer?.following == null }
    }

    val displayKnown = knownList
    val displayOthers = if (mutualsOnly) emptyList() else othersList

    val followersListState = rememberLazyListState()
    val followsListState = rememberLazyListState()
    val listState = if (selectedTab == 0) followersListState else followsListState

    val endReached by remember(selectedTab) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index >= info.totalItemsCount - 3
        }
    }

    LaunchedEffect(endReached, selectedTab) {
        if (!endReached) return@LaunchedEffect
        if (selectedTab == 0 && timelineViewModel.profileFollowersCursor != null) {
            timelineViewModel.fetchFollowers(listDid)
        } else if (selectedTab == 1 && timelineViewModel.profileFollowsCursor != null) {
            timelineViewModel.fetchFollows(listDid)
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        topBar = {
            TopAppBar(
                title = { Text(listName) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = backButton) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Followers") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Following") },
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FilterChip(
                    selected = mutualsOnly,
                    onClick = { mutualsOnly = !mutualsOnly },
                    label = { Text("People you follow") },
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (displayKnown.isNotEmpty()) {
                    item(key = "header_known") {
                        Text(
                            text = "People you follow",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(displayKnown, key = { "known_${it.did.did}" }) { user ->
                        val followsYou = user.viewer?.followedBy != null
                        val youFollow = user.viewer?.following != null
                        val badge = when {
                            youFollow && followsYou -> "Mutuals"
                            youFollow -> "Following"
                            followsYou -> "Follows you"
                            else -> ""
                        }
                        FollowerItem(
                            user = user,
                            badgeText = badge,
                            showBadge = badge.isNotEmpty(),
                            onTap = { onProfileTap(user.did) },
                        )
                    }
                }

                if (displayOthers.isNotEmpty()) {
                    if (displayKnown.isNotEmpty()) {
                        item(key = "header_others") {
                            Text(
                                text = "Others",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(displayOthers, key = { "other_${it.did.did}" }) { user ->
                        val followsYou = user.viewer?.followedBy != null
                        FollowerItem(
                            user = user,
                            badgeText = if (followsYou) "Follows you" else "",
                            showBadge = followsYou,
                            onTap = { onProfileTap(user.did) },
                        )
                    }
                }

                if (currentList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No results",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowerItem(
    user: ProfileView,
    badgeText: String = "",
    showBadge: Boolean = false,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = user.avatar?.uri,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            contentDescription = "Avatar",
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = user.displayName ?: user.handle.handle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "@${user.handle.handle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showBadge && badgeText.isNotEmpty()) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
