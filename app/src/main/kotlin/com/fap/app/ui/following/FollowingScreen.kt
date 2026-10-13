package com.fap.app.ui.following

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fap.app.data.AppDatabase
import com.fap.app.di.Graph
import com.fap.app.ui.components.LoadingRow
import com.fap.app.ui.components.MessageCard
import com.fap.app.ui.components.StatLabel
import com.fap.app.ui.components.Thumb

/**
 * Everyone you follow (star on a Creator screen), from the `favorites`
 * table. Each row opens the creator, can queue a full download of them,
 * or unfollow them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun FollowingScreen(
    viewModel: FollowingViewModel,
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val settings by Graph.settings.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Re-read the table every time this screen (re-)appears: follow or
    // unfollow someone on their Creator screen, come back, and the list
    // already matches.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(state.toast) {
        val message = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeToast()
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
                        "Following ★",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatLabel(
                        if (state.favorites.size == 1) {
                            "1 creator"
                        } else {
                            "${state.favorites.size} creators"
                        }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading && state.favorites.isEmpty() -> LoadingRow("Loading following…")
                    state.favorites.isEmpty() -> MessageCard(
                        "You are not following anyone yet.\n" +
                            "Open a creator and tap the star to follow them, " +
                            "and they will show up here."
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.favorites, key = { favorite -> favorite.slug }) { favorite ->
                            FollowingRow(
                                favorite = favorite,
                                hidden = settings.hideThumbs,
                                onClick = { onOpenCreator(favorite.slug) },
                                onDownload = { viewModel.downloadAll(favorite) },
                                onUnfollow = { viewModel.unfollow(favorite) }
                            )
                        }
                    }
                }
            }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun FollowingRow(
    favorite: AppDatabase.Favorite,
    hidden: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onUnfollow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumb(
                url = favorite.avatar,
                hidden = hidden,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    favorite.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatLabel(
                    listOfNotNull(
                        favorite.mediaCount?.let { count -> "$count media" },
                        "@${favorite.slug}"
                    ).joinToString(" · ")
                )
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download everything by ${favorite.name}")
            }
            TextButton(onClick = onUnfollow) {
                Text("Unfollow")
            }
        }
    }
}
