package industries.geesawra.monarch

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.AsyncImage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import industries.geesawra.monarch.datalayer.ContentBlockType
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.TimelineViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentReaderView(
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState,
    backButton: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val doc = timelineViewModel.publicationsState.selectedDocument ?: return
    val pub = timelineViewModel.publicationsState.selectedPublication
    val context = LocalContext.current

    val accentColor = MaterialTheme.colorScheme.primary
    val canonicalUrl = if (pub?.publication?.url != null && doc.document.path != null) {
        pub.publication.url.trimEnd('/') + doc.document.path
    } else null

    val contentModifier = if (!settingsState.forceCompactLayout) {
        Modifier.widthIn(max = 600.dp)
    } else {
        Modifier
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = monarchTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = backButton) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back")
                    }
                },
                title = {
                    Text(
                        doc.document.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item(key = "header") {
                    doc.document.description?.let { desc ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    doc.document.publishedAt?.let { date ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = date.substringBefore("T"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (doc.document.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            doc.document.tags.forEach { tag ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (doc.document.contentBlocks.isNotEmpty()) {
                    items(doc.document.contentBlocks.size, key = { "block_$it" }) { idx ->
                        val block = doc.document.contentBlocks[idx]
                        when (block.type) {
                            ContentBlockType.HEADING -> {
                                Spacer(modifier = Modifier.height(if (idx > 0) 24.dp else 0.dp))
                                Text(
                                    text = block.text,
                                    style = when (block.level) {
                                        1 -> MaterialTheme.typography.headlineSmall
                                        2 -> MaterialTheme.typography.titleLarge
                                        else -> MaterialTheme.typography.titleMedium
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            ContentBlockType.PARAGRAPH -> {
                                Text(
                                    text = block.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            ContentBlockType.BLOCKQUOTE -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .drawBehind {
                                            drawRect(
                                                color = accentColor,
                                                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                                                size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                                            )
                                        }
                                        .padding(start = 16.dp),
                                ) {
                                    Text(
                                        text = block.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            ContentBlockType.CODE -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                ) {
                                    Text(
                                        text = block.text,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            lineHeight = 20.sp,
                                        ),
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            ContentBlockType.LIST_ITEM -> {
                                Row(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = block.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                                    )
                                }
                            }
                            ContentBlockType.HORIZONTAL_RULE -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            ContentBlockType.WEBSITE -> {
                                if (block.linkUrl?.isNotBlank() == true) {
                                    OutlinedCard(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(block.linkUrl))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            if (block.linkTitle?.isNotBlank() == true) {
                                                Text(block.linkTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                            if (block.linkDescription?.isNotBlank() == true) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(block.linkDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(block.linkUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                            ContentBlockType.LINK -> {
                                if (block.embeddedPost != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    ) {
                                        SkeetView(
                                            skeet = block.embeddedPost,
                                            showLabels = false,
                                            onShowThread = {},
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                } else if (block.linkUrl?.isNotBlank() == true) {
                                    OutlinedCard(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(block.linkUrl))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(block.linkTitle ?: block.linkUrl, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(block.linkUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                            ContentBlockType.IMAGE -> {
                                if (block.imageUrl != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AsyncImage(
                                        model = block.imageUrl,
                                        contentDescription = block.text.ifBlank { null },
                                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 200.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.FillWidth,
                                    )
                                    if (block.text.isNotBlank()) {
                                        Text(
                                            text = block.text,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            else -> {
                                if (block.text.isNotBlank()) {
                                    Text(
                                        text = block.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                    item(key = "body_end") { Spacer(modifier = Modifier.height(24.dp)) }
                } else if (doc.document.textContent != null) {
                    item(key = "body") {
                        Text(
                            text = doc.document.textContent,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                if (canonicalUrl != null) {
                    item(key = "readOnWeb") {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(canonicalUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.padding(end = 8.dp))
                            Text("Read on web")
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
