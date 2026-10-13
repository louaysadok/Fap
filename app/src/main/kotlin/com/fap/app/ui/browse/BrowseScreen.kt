package com.fap.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.fap.app.di.Graph
import com.fap.app.ui.components.LoadingRow
import com.fap.app.ui.components.MessageCard
import com.fap.app.ui.components.ModeDropdown
import com.fap.app.ui.components.StatLabel
import com.fap.app.ui.components.Thumb
import com.fap.core.BrowseMode
import com.fap.core.CreatorRef
import com.fap.core.FeedKind
import com.fap.core.UserPost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun BrowseScreen(
    viewModel: BrowseViewModel,
    onOpenCreator: (String) -> Unit,
    onOpenPost: (String, String) -> Unit,
    onOpenViewer: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val settings by Graph.settings.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.nav.collect { event ->
            when (event) {
                is BrowseNav.Creator -> onOpenCreator(event.slug)
                is BrowseNav.Post -> onOpenPost(event.id, event.slug)
                BrowseNav.Viewer -> onOpenViewer()
            }
        }
    }

    LaunchedEffect(state.toast) {
        val message = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeToast()
    }

    val creators = state.creators.filter {
        state.query.isBlank() ||
            it.name.contains(state.query, ignoreCase = true) ||
            it.slug.contains(state.query, ignoreCase = true)
    }
    val posts = state.posts.filter {
        state.query.isBlank() ||
            it.title.contains(state.query, ignoreCase = true) ||
            (it.poster ?: "").contains(state.query, ignoreCase = true) ||
            it.slug.contains(state.query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeDropdown(
                mode = state.mode,
                onModeChange = viewModel::setMode,
                modifier = Modifier.width(150.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search, or paste a link") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.openRaw(state.query) }),
                trailingIcon = {
                    IconButton(onClick = { viewModel.openRaw(state.query) }) {
                        Icon(Icons.Default.Search, contentDescription = "Open")
                    }
                }
            )
        }

        if (state.mode == BrowseMode.CREATORS) {
            TabRow(selectedTabIndex = state.feedKind.ordinal) {
                FeedKind.entries.forEach { kind ->
                    Tab(
                        selected = state.feedKind == kind,
                        onClick = { viewModel.setFeed(kind) },
                        text = { Text(kind.label) }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loading -> LoadingRow("Loading ${state.mode.name.lowercase()}…")
                state.error != null && state.creators.isEmpty() && state.posts.isEmpty() ->
                    MessageCard(state.error ?: "Something went wrong") {
                        androidx.compose.material3.Button(onClick = viewModel::reload) { Text("Retry") }
                    }
                state.mode == BrowseMode.CREATORS -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(creators, key = { _, item -> item.slug }) { index, creator ->
                            CreatorCard(
                                creator = creator,
                                hidden = settings.hideThumbs,
                                onClick = { onOpenCreator(creator.slug) },
                                onDownload = { viewModel.downloadCreator(creator) }
                            )
                            if (index >= creators.size - 6) viewModel.loadMore()
                        }
                        if (state.loadingMore || (!state.endReached && creators.isNotEmpty())) {
                            item { LoadingRow("Loading more…") }
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                        itemsIndexed(posts, key = { _, item -> item.id }) { index, post ->
                            PostCard(
                                post = post,
                                hidden = settings.hideThumbs,
                                onClick = { onOpenPost(post.id, post.slug) },
                                onDownload = { viewModel.downloadPost(post) }
                            )
                            if (index >= posts.size - 4) viewModel.loadMore()
                        }
                        if (state.loadingMore || (!state.endReached && posts.isNotEmpty())) {
                            item { LoadingRow("Loading more…") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorCard(
    creator: CreatorRef,
    hidden: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.78f)) {
            Thumb(
                url = creator.coverUrl ?: creator.thumbnails.firstOrNull() ?: creator.avatarUrl,
                hidden = hidden,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .padding(6.dp)
            ) {
                Text(
                    creator.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
            ) {
                IconButton(onClick = onDownload, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download everything",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (creator.mediaCount != null) {
                StatLabel(
                    "${creator.mediaCount} media",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun PostCard(
    post: UserPost,
    hidden: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.height(104.dp).padding(8.dp)) {
            Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.size(88.dp)) {
                Thumb(
                    url = post.thumbnails.firstOrNull(),
                    hidden = hidden,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    post.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (post.poster != null) StatLabel("by ${post.poster}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (post.likes != null) StatLabel("${post.likes} likes")
                    if (post.thumbnails.isNotEmpty()) StatLabel("${post.thumbnails.size}+ media")
                    if (post.category != null) StatLabel(post.category)
                }
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download post")
            }
        }
    }
}
