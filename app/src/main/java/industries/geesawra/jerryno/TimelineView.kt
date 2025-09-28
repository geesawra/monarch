package industries.geesawra.jerryno

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import industries.geesawra.jerryno.datalayer.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class TabBarDestinations(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    @param:StringRes val contentDescription: Int
) {
    HOME(R.string.timeline, Icons.Filled.Home, R.string.timeline),
    NOTIFICATIONS(R.string.notifications, Icons.Filled.Notifications, R.string.notifications)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineView(
    timelineViewModel: TimelineViewModel,
    coroutineScope: CoroutineScope
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentDestination by rememberSaveable { mutableStateOf(TabBarDestinations.HOME) }
    val modalSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { sv ->
            sv != SheetValue.PartiallyExpanded
        }
    )
    val focusRequester = remember { FocusRequester() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val listState = rememberLazyListState()


    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Text("Drawer title", modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(text = "Drawer Item") },
                    selected = false,
                    onClick = { /*TODO*/ }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumTopAppBar(
                    colors = TopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground, // Ensuring correct contrast
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Text(text = "Jerry No")
                    },
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        showBottomSheet = true
                    },
                ) {
                    Icon(Icons.Filled.Create, "Post")
                }
            },
            bottomBar = {
                NavigationBar {
                    TabBarDestinations.entries.forEach {
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    it.icon,
                                    contentDescription = stringResource(it.contentDescription)
                                )
                            },
                            label = { Text(stringResource(it.label)) },
                            selected = it == currentDestination,
                            onClick = { currentDestination = it }
                        )
                    }
                }
            }
        ) { values ->
            timelineViewModel.create()

            ShowSkeets(
                viewModel = timelineViewModel,
                state = listState,
                modifier = Modifier.padding(values)
            )

            if (showBottomSheet) {
                ComposeView(
                    modalSheetState = modalSheetState,
                    focusRequester = focusRequester,
                    timelineViewModel = timelineViewModel,
                    onDismissRequest = {
                        showBottomSheet = false
                    }
                )
            }
        }
    }
}