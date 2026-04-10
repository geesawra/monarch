package industries.geesawra.monarch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import industries.geesawra.monarch.datalayer.DocumentRecord
import industries.geesawra.monarch.datalayer.SettingsState
import industries.geesawra.monarch.datalayer.TimelineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListView(
    timelineViewModel: TimelineViewModel,
    settingsState: SettingsState,
    backButton: () -> Unit,
    onDocumentTap: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val pubState = timelineViewModel.publicationsState
    val publication = pubState.selectedPublication
    val context = LocalContext.current

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
                        publication?.publication?.name ?: "Documents",
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pubState.isFetchingDocuments) {
                    item(key = "loading") { LoadingBox() }
                } else if (pubState.documents.isEmpty()) {
                    item(key = "empty") {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No articles yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(pubState.documents.size, key = { "doc_$it" }) { idx ->
                        val doc = pubState.documents[idx]
                        val canonicalUrl = if (publication?.publication?.url != null && doc.document.path != null) {
                            publication.publication.url.trimEnd('/') + doc.document.path
                        } else null
                        OutlinedCard(
                            onClick = {
                                if (canonicalUrl != null) {
                                    if (settingsState.openLinksInBrowser) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(canonicalUrl)))
                                    } else {
                                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(canonicalUrl))
                                    }
                                } else {
                                    timelineViewModel.selectDocument(doc)
                                    onDocumentTap()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(doc.document.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                doc.document.description?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                doc.document.publishedAt?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(it.substringBefore("T"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
