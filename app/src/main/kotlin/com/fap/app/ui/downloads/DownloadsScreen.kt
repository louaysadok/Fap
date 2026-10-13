package com.fap.app.ui.downloads

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fap.app.data.AppDatabase
import com.fap.app.di.Graph
import com.fap.app.download.DownloadTask
import com.fap.app.download.TaskState
import com.fap.app.ui.components.MessageCard
import com.fap.app.ui.components.StatLabel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onOpenViewer: () -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    val sweeps by viewModel.sweeps.collectAsState()
    val paused by viewModel.paused.collectAsState()
    val library by viewModel.library.collectAsState()
    val storageBytes by viewModel.storageBytes.collectAsState()
    val settings by Graph.settings.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.openViewer.collect { onOpenViewer() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Queue") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Library (${library.size})") })
        }

        if (tab == 0) {
            QueueTab(
                tasks = tasks,
                sweeps = sweeps,
                paused = paused,
                wifiOnly = settings.wifiOnly,
                storage = viewModel.describe(storageBytes),
                onPause = viewModel::pauseAll,
                onResume = viewModel::resumeAll,
                onClear = viewModel::clearFinished,
                onRetry = viewModel::retry,
                onCancel = viewModel::cancel,
                onCancelSweep = viewModel::cancelSweep,
                onWifiOnlyChange = viewModel::setWifiOnly
            )
        } else {
            LibraryTab(
                entries = library,
                storage = viewModel.describe(storageBytes),
                onOpen = viewModel::openLibraryAt,
                onDelete = viewModel::delete,
                onDeleteAll = viewModel::deleteAll,
                onRefresh = viewModel::refreshLibrary
            )
        }
    }
}

@Composable
private fun QueueTab(
    tasks: List<DownloadTask>,
    sweeps: List<com.fap.app.download.SweepStatus>,
    paused: Boolean,
    wifiOnly: Boolean,
    storage: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onClear: () -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onCancelSweep: (String) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (paused) {
                Button(onClick = onResume) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Resume")
                }
            } else {
                Button(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Pause")
                }
            }
            TextButton(onClick = onClear) { Text("Clear done") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
                StatLabel("Wi-Fi only")
            }
        }
        StatLabel(
            "$storage in private storage · ${tasks.count { it.state == TaskState.DONE }} done · " +
                "${tasks.count { it.state == TaskState.QUEUED }} queued",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        if (sweeps.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                sweeps.forEach { sweep ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sweep.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                StatLabel(sweep.detail)
                            }
                            if (!sweep.done) {
                                TextButton(onClick = { onCancelSweep(sweep.key) }) { Text("Stop") }
                            }
                        }
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            MessageCard("Nothing queued. Hit the download icon on a creator or post to queue everything.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onRetry = { onRetry(task.id) },
                        onCancel = { onCancel(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: DownloadTask, onRetry: () -> Unit, onCancel: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.file, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    StatLabel(
                        buildString {
                            append(task.owner)
                            append(" · ")
                            append(task.state.name.lowercase().replaceFirstChar { it.uppercaseChar() })
                            if (task.total > 0) append(" · ").append(com.fap.app.download.DownloadManager.describe(task.total))
                            if (task.error != null) append(" · ").append(task.error)
                        }
                    )
                }
                if (task.state == TaskState.FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }
                }
                if (task.state != TaskState.RUNNING) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
            if (task.state == TaskState.RUNNING) {
                LinearProgressIndicator(
                    progress = (task.progress.coerceIn(0, 100) / 100f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTab(
    entries: List<AppDatabase.LibraryEntry>,
    storage: String,
    onOpen: (Int) -> Unit,
    onDelete: (AppDatabase.LibraryEntry) -> Unit,
    onDeleteAll: () -> Unit,
    onRefresh: () -> Unit
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val owners = remember(entries) { entries.map { it.owner }.distinct().sorted() }
    var filter by remember { mutableStateOf<String?>(null) }
    val visible = remember(entries, filter) {
        if (filter == null) entries else entries.filter { it.owner == filter }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatLabel("$storage saved privately", modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("Refresh") }
            TextButton(onClick = { confirmDeleteAll = true }) { Text("Delete all") }
        }
        if (owners.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                owners.take(6).forEach { owner ->
                    FilterChip(
                        selected = filter == owner,
                        onClick = { filter = if (filter == owner) null else owner },
                        label = { Text(owner, maxLines = 1) }
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            MessageCard("No downloads yet.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(visible, key = { _, entry -> entry.url }) { index, entry ->
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.8f)) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onOpen(index) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            com.fap.app.ui.components.Thumb(
                                url = entry.file,
                                isVideo = entry.kind == com.fap.core.MediaKind.VIDEO,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        IconButton(
                            onClick = { onDelete(entry) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        }
                        StatLabel(
                            File(entry.file).name,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete everything?") },
            text = { Text("Removes all downloaded files from private storage. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteAll(); confirmDeleteAll = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
    }
}
