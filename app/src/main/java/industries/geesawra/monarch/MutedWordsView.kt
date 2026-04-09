package industries.geesawra.monarch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bsky.actor.MutedWordActorTarget
import app.bsky.actor.MutedWordTarget
import industries.geesawra.monarch.datalayer.TimelineViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MutedWordsView(
    timelineViewModel: TimelineViewModel,
    backButton: () -> Unit,
) {
    var newWord by remember { mutableStateOf("") }
    var targetContent by remember { mutableStateOf(true) }
    var targetTag by remember { mutableStateOf(false) }
    var actorTargetAll by remember { mutableStateOf(true) }

    val mutedWords = timelineViewModel.uiState.mutedWords

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        topBar = {
            TopAppBar(
                title = { Text("Muted Words") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { newWord = it },
                        label = { Text("Add a word or phrase") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Text(
                        text = "Apply to",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = targetContent,
                            onClick = { if (targetTag || !targetContent) targetContent = !targetContent },
                            label = { Text("Text content") },
                        )
                        FilterChip(
                            selected = targetTag,
                            onClick = { if (targetContent || !targetTag) targetTag = !targetTag },
                            label = { Text("Tags") },
                        )
                    }

                    Text(
                        text = "Mute from",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SegmentedButton(
                            selected = actorTargetAll,
                            onClick = { actorTargetAll = true },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) {
                            Text("Everyone")
                        }
                        SegmentedButton(
                            selected = !actorTargetAll,
                            onClick = { actorTargetAll = false },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) {
                            Text("Non-followed")
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            if (newWord.isBlank()) return@FilledTonalButton
                            val targets = mutableListOf<MutedWordTarget>()
                            if (targetContent) targets.add(MutedWordTarget.Content)
                            if (targetTag) targets.add(MutedWordTarget.Tag)
                            timelineViewModel.addMutedWord(
                                value = newWord.trim(),
                                targets = targets,
                                actorTarget = if (actorTargetAll) MutedWordActorTarget.All else MutedWordActorTarget.ExcludeFollowing,
                            )
                            newWord = ""
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = newWord.isNotBlank(),
                    ) {
                        Text("Add")
                    }
                }

                HorizontalDivider()
            }

            if (mutedWords.isEmpty()) {
                item {
                    Text(
                        text = "No muted words yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            items(mutedWords, key = { "${it.value}_${it.targets}" }) { word ->
                ListItem(
                    headlineContent = { Text(word.value) },
                    supportingContent = {
                        val parts = mutableListOf<String>()
                        if (word.targets.contains(MutedWordTarget.Content)) parts.add("text")
                        if (word.targets.contains(MutedWordTarget.Tag)) parts.add("tags")
                        val target = if (word.actorTarget is MutedWordActorTarget.ExcludeFollowing) "non-followed" else "everyone"
                        Text("${parts.joinToString(" & ")} from $target")
                    },
                    trailingContent = {
                        IconButton(onClick = { timelineViewModel.removeMutedWord(word) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove muted word",
                            )
                        }
                    },
                )
            }
        }
    }
}
