package industries.geesawra.monarch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileView
import app.bsky.actor.VerifiedStatus
import app.bsky.feed.SearchPostsSort
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import industries.geesawra.monarch.datalayer.SkeetData
import industries.geesawra.monarch.datalayer.TimelineViewModel
import sh.christian.ozone.api.Did


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchView(
    viewModel: TimelineViewModel,
    postsListState: LazyListState,
    peopleListState: LazyListState,
    modifier: Modifier = Modifier,
    isScrollEnabled: Boolean,
    scaffoldPadding: PaddingValues,
    onThreadTap: (SkeetData) -> Unit,
    onProfileTap: (Did) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showPeople by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
    ) {
        // M3 SearchBar
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {
                        viewModel.search(query)
                    },
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text("Search Bluesky") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                viewModel.search("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {}

        // Filter chips row
        SearchFilters(viewModel, showPeople, onShowPeopleChange = { showPeople = it })

        // Content
        if (showPeople) {
            SearchPeopleResults(
                viewModel = viewModel,
                listState = peopleListState,
                isScrollEnabled = isScrollEnabled,
                onProfileTap = onProfileTap,
            )
        } else {
            SearchPostsResults(
                viewModel = viewModel,
                listState = postsListState,
                isScrollEnabled = isScrollEnabled,
                onThreadTap = onThreadTap,
                onProfileTap = onProfileTap,
            )
        }
    }
}

@Composable
private fun SearchFilters(
    viewModel: TimelineViewModel,
    showPeople: Boolean,
    onShowPeopleChange: (Boolean) -> Unit,
) {
    val sort = viewModel.uiState.searchPostsSort
    val authorFilter = viewModel.uiState.searchAuthorFilter

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !showPeople && sort == SearchPostsSort.Latest,
            onClick = {
                onShowPeopleChange(false)
                viewModel.setSearchSort(SearchPostsSort.Latest)
            },
            label = { Text("Latest") },
        )
        FilterChip(
            selected = !showPeople && sort == SearchPostsSort.Top,
            onClick = {
                onShowPeopleChange(false)
                viewModel.setSearchSort(SearchPostsSort.Top)
            },
            label = { Text("Top") },
        )
        FilterChip(
            selected = showPeople,
            onClick = { onShowPeopleChange(!showPeople) },
            label = { Text("People") },
        )
        if (authorFilter != null) {
            FilterChip(
                selected = true,
                onClick = { viewModel.setSearchAuthorFilter(null) },
                label = { Text("from:$authorFilter") },
                trailingIcon = {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Remove author filter",
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun SearchPostsResults(
    viewModel: TimelineViewModel,
    listState: LazyListState,
    isScrollEnabled: Boolean,
    onThreadTap: (SkeetData) -> Unit,
    onProfileTap: (Did) -> Unit,
) {
    val posts = viewModel.uiState.searchPostResults
    val isSearching = viewModel.uiState.isSearching

    if (posts.isEmpty() && !isSearching && viewModel.uiState.searchQuery.isNotBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No posts found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (posts.isEmpty() && !isSearching) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Search for posts on Bluesky",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = isScrollEnabled,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(
            items = posts,
            key = { _, skeet -> "search_post_${skeet.key()}" }
        ) { _, skeet ->
            Card {
                SkeetView(
                    viewModel = viewModel,
                    skeet = skeet,
                    onAvatarTap = onProfileTap,
                    onShowThread = { s ->
                        viewModel.setThread(s)
                        onThreadTap(s)
                    },
                )
            }
        }

        if (isSearching) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Pagination
    val endReached by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val last = layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index == layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(endReached) {
        if (endReached && posts.isNotEmpty()) {
            viewModel.fetchMoreSearchPosts()
        }
    }
}

@Composable
private fun SearchPeopleResults(
    viewModel: TimelineViewModel,
    listState: LazyListState,
    isScrollEnabled: Boolean,
    onProfileTap: (Did) -> Unit,
) {
    val actors = viewModel.uiState.searchActorResults
    val isSearching = viewModel.uiState.isSearching

    if (actors.isEmpty() && !isSearching && viewModel.uiState.searchQuery.isNotBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No people found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (actors.isEmpty() && !isSearching) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Search for people on Bluesky",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = isScrollEnabled,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(
            items = actors,
            key = { "search_actor_${it.did.did}" }
        ) { actor ->
            ActorCard(actor = actor, onProfileTap = onProfileTap)
        }

        if (isSearching) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    val endReached by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val last = layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index == layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(endReached) {
        if (endReached && actors.isNotEmpty()) {
            viewModel.fetchMoreSearchActors()
        }
    }
}

@Composable
private fun ActorCard(
    actor: ProfileView,
    onProfileTap: (Did) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onProfileTap(actor.did) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(actor.avatar?.uri)
                    .crossfade(true)
                    .build(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                contentDescription = "${actor.displayName ?: actor.handle.handle}'s avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = actor.displayName ?: actor.handle.handle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    val isVerified = actor.verification?.verifiedStatus is VerifiedStatus.Valid
                    if (isVerified) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Text(
                    text = "@${actor.handle.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!actor.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = actor.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
