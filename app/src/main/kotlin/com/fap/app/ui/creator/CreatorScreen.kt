package com.fap.app.ui.creator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fap.app.di.Graph
import com.fap.app.ui.components.LoadingRow
import com.fap.app.ui.components.MessageCard
import com.fap.app.ui.components.StatLabel
import com.fap.app.ui.components.Thumb
import com.fap.core.MediaItem
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
public fun CreatorScreen(
    viewModel: CreatorViewModel,
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
                Thumb(
                    url = state.profile?.creator?.avatarUrl,
                    hidden = settings.hideThumbs,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.profile?.creator?.name ?: state.slug,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val count = state.profile?.reportedTotal ?: state.media.size
                    StatLabel(
                        listOfNotNull(
                            "$count media",
                            state.profile?.creator?.likes?.let { "$it likes" },
                            state.profile?.altNames
                        ).joinToString(" · ")
                    )
                }
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Follow",
                        tint = if (state.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { onOpenBrowser(state.profile?.creator?.url ?: "${Graph.client().base}/${state.slug}/") }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = viewModel::downloadAll) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Download all", maxLines = 1)
                }
                TextButton(onClick = viewModel::toggleSelectionMode) {
                    Text(if (state.selectionMode) "Done" else "Select")
                }
                if (state.selectionMode) {
                    TextButton(onClick = viewModel::selectAll) { Text("All") }
                    TextButton(onClick = viewModel::clearSelection) { Text("None") }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingRow("Loading creator…")
                    state.error != null -> MessageCard(state.error ?: "") {
                        Button(onClick = viewModel::load) { Text("Retry") }
                    }
                    state.media.isEmpty() -> MessageCard("No media found for this creator.")
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(110.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(state.media, key = { _, item -> item.id }) { index, item ->
                                MediaTile(
                                    item = item,
                                    selected = item.id in state.selected,
                                    selectionMode = state.selectionMode,
                                    hidden = settings.hideThumbs,
                                    onClick = {
                                        if (state.selectionMode) viewModel.toggleSelected(item.id)
                                        else viewModel.openAt(index)
                                    },
                                    onLongClick = {
                                        if (!state.selectionMode) viewModel.toggleSelectionMode()
                                        viewModel.toggleSelected(item.id)
                                    }
                                )
                                if (index >= state.media.size - 9) viewModel.loadMore()
                            }
                            item { LoadingRow(if (state.endReached) "End of gallery" else "Loading more…") }
                        }
                    }
                }
            }
        }

        if (state.selectionMode && state.selected.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.selected.size} selected", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::downloadSelected) { Text("Download") }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Thumb(url = item.thumbUrl ?: item.directUrl, isVideo = item.isVideo, hidden = hidden, modifier = Modifier.fillMaxSize())
            if (selectionMode) {
                androidx.compose.material3.Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(28.dp)
                )
            }
        }
    }
}
