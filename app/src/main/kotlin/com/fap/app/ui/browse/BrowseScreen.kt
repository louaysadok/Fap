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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
    onOpenViewer: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenTags: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val settings by Graph.settings.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Every (re-)entry to Browse — including coming back from a creator
    // you just followed/unfollowed — refreshes the Following count.
    LaunchedEffect(Unit) {
        viewModel.refreshFavorites()
    }

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

    // In search mode these are the site's own results (its matching is
    // fuzzy, so they must NOT be re-filtered by the query text). Otherwise
    // the box just narrows whatever feed pages are already loaded.
    val creators = if (state.inSearch) {
        state.searchCreators
    } else {
        state.creators.filter {
            state.query.isBlank() ||
                it.name.contains(state.query, ignoreCase = true) ||
                it.slug.contains(state.query, ignoreCase = true)
        }
    }
    val posts = if (state.inSearch) {
        state.searchPosts
    } else {
        state.posts.filter {
            state.query.isBlank() ||
                it.title.contains(state.query, ignoreCase = true) ||
                (it.poster ?: "").contains(state.query, ignoreCase = true) ||
                it.slug.contains(state.query, ignoreCase = true)
        }
    }
    val hasContent = if (state.mode == BrowseMode.CREATORS) creators.isNotEmpty() else posts.isNotEmpty()

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
                keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() }),
                trailingIcon = {
                    if (state.inSearch) {
                        IconButton(onClick = viewModel::exitSearch) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    } else {
                        IconButton(onClick = viewModel::submitSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        }

        if (state.mode == BrowseMode.CREATORS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabRow(
                    selectedTabIndex = state.feedKind.ordinal,
                    modifier = Modifier.weight(1f)
                ) {
                    FeedKind.entries.forEach { kind ->
                        Tab(
                            selected = state.feedKind == kind,
                            onClick = { viewModel.setFeed(kind) },
                            text = { Text(kind.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = false,
                    onClick = onOpenFollowing,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Following ★ ${state.favoritesCount}", maxLines = 1) }
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = false,
                    onClick = onOpenTags,
                    label = { Text("Tags #", maxLines = 1) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.inSearch && state.searchLoading && !hasContent ->
                    LoadingRow("Searching Fapello for \"${state.searchedQuery}\"…")
                state.inSearch && state.searchError != null && !hasContent ->
                    MessageCard(state.searchError ?: "Search failed") {
                        androidx.compose.material3.Button(onClick = viewModel::submitSearch) { Text("Retry") }
                    }
                state.inSearch && !state.searchLoading && !hasContent ->
                    MessageCard("No ${if (state.mode == BrowseMode.CREATORS) "creators" else "posts"} found for \"${state.searchedQuery}\" on Fapello.") {
                        androidx.compose.material3.TextButton(onClick = viewModel::exitSearch) { Text("Back to browse") }
                    }
                !state.inSearch && state.loading -> LoadingRow("Loading ${state.mode.name.lowercase()}…")
                !state.inSearch && state.error != null && state.creators.isEmpty() && state.posts.isEmpty() ->
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
                        if (state.inSearch || state.query.isNotBlank()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SearchBanner(state = state, onSubmitSearch = viewModel::submitSearch)
                            }
                        }
                        itemsIndexed(creators, key = { _, item -> item.slug }) { index, creator ->
                            CreatorCard(
                                creator = creator,
                                hidden = settings.hideThumbs,
                                onClick = { onOpenCreator(creator.slug) },
                                onDownload = { viewModel.downloadCreator(creator) }
                            )
                            if (index >= creators.size - 6) {
                                if (state.inSearch) viewModel.loadMoreSearch() else viewModel.loadMore()
                            }
                        }
                        if (if (state.inSearch) state.searchLoading && creators.isNotEmpty()
                            else state.loadingMore || (!state.endReached && creators.isNotEmpty())
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow("Loading more…") }
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                        if (state.inSearch || state.query.isNotBlank()) {
                            item {
                                SearchBanner(state = state, onSubmitSearch = viewModel::submitSearch)
                            }
                        }
                        itemsIndexed(posts, key = { _, item -> item.id }) { index, post ->
                            PostCard(
                                post = post,
                                hidden = settings.hideThumbs,
                                onClick = { onOpenPost(post.id, post.slug) },
                                onDownload = { viewModel.downloadPost(post) }
                            )
                            if (index >= posts.size - 4) {
                                if (state.inSearch) viewModel.loadMoreSearch() else viewModel.loadMore()
                            }
                        }
                        if (if (state.inSearch) state.searchLoading && posts.isNotEmpty()
                            else state.loadingMore || (!state.endReached && posts.isNotEmpty())
                        ) {
                            item { LoadingRow("Loading more…") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBanner(state: BrowseUiState, onSubmitSearch: () -> Unit) {
    if (state.inSearch) {
        Text(
            "${state.searchTotal} result${if (state.searchTotal == 1) "" else "s"} for \"${state.searchedQuery}\"",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )
    } else {
        androidx.compose.material3.Button(
            onClick = onSubmitSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" Search Fapello for \"${state.query.trim()}\"", maxLines = 1)
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
                    post.category?.let { category -> StatLabel(category) }
                }
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download post")
            }
        }
    }
}
