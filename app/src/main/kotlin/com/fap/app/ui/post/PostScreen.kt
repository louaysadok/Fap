package com.fap.app.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fap.app.di.Graph
import com.fap.app.ui.components.LoadingRow
import com.fap.app.ui.components.MessageCard
import com.fap.app.ui.components.StatLabel
import com.fap.app.ui.components.Thumb
import com.fap.core.FapelloUrls
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PostScreen(
    viewModel: PostViewModel,
    onBack: () -> Unit,
    onOpenViewer: () -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val settings by Graph.settings.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.openViewer.collect { onOpenViewer() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatLabel("${state.media.size} media · post ${state.id}")
                }
                IconButton(onClick = {
                    onOpenBrowser(FapelloUrls.post(Graph.client().base, state.id, state.slug))
                }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = viewModel::downloadAll, enabled = state.media.isNotEmpty()) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Download all", maxLines = 1)
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingRow("Loading post…")
                    state.error != null -> MessageCard(state.error ?: "") {
                        Button(onClick = viewModel::load) { Text("Retry") }
                    }
                    state.media.isEmpty() -> MessageCard("This post has no media we could read.")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(state.media, key = { _, item -> item.id }) { index, item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.8f)
                                    .clickable { viewModel.openAt(index) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Thumb(
                                    url = item.thumbUrl ?: item.directUrl,
                                    isVideo = item.isVideo,
                                    hidden = settings.hideThumbs,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        state.toast?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = { TextButton(onClick = viewModel::consumeToast) { Text("OK") } }
            ) { Text(message) }
            LaunchedEffect(message) {
                delay(2600)
                viewModel.consumeToast()
            }
        }
    }
}
