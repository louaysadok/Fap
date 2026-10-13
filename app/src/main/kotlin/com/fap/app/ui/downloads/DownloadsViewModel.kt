package com.fap.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fap.app.data.AppDatabase
import com.fap.app.di.Graph
import com.fap.app.di.ViewerItem
import com.fap.app.download.DownloadManager
import com.fap.app.download.Storage
import com.fap.core.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

public class DownloadsViewModel : ViewModel() {

    public val tasks: StateFlow<List<com.fap.app.download.DownloadTask>> get() = Graph.downloads.tasks
    public val sweeps: StateFlow<List<com.fap.app.download.SweepStatus>> get() = Graph.downloads.sweeps
    public val paused: StateFlow<Boolean> get() = Graph.downloads.paused

    private val _library = MutableStateFlow<List<AppDatabase.LibraryEntry>>(emptyList())
    public val library: StateFlow<List<AppDatabase.LibraryEntry>> get() = _library

    private val _storageBytes = MutableStateFlow(0L)
    public val storageBytes: StateFlow<Long> get() = _storageBytes

    private val _openViewer = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    public val openViewer = _openViewer.asSharedFlow()

    init {
        refreshLibrary()
    }

    public fun refreshLibrary() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { Graph.db.library() }
            val bytes = withContext(Dispatchers.IO) { Storage.sizeBytes(Graph.context()) }
            _library.value = entries
            _storageBytes.value = bytes
        }
    }

    public fun pauseAll(): Unit = Graph.downloads.pauseAll()
    public fun resumeAll(): Unit = Graph.downloads.resumeAll()
    public fun retry(id: Long): Unit = Graph.downloads.retry(id)
    public fun cancel(id: Long): Unit = Graph.downloads.cancel(id)
    public fun clearFinished() {
        Graph.downloads.clearFinished()
    }

    public fun cancelSweep(key: String): Unit = Graph.downloads.cancelSweep(key)

    public fun setWifiOnly(enabled: Boolean) {
        Graph.settings.update { it.copy(wifiOnly = enabled) }
    }

    public fun openLibraryAt(index: Int) {
        val entries = _library.value
        if (entries.isEmpty()) return
        val items = entries.map { entry ->
            ViewerItem(
                source = entry.file,
                thumb = entry.file,
                kind = entry.kind,
                label = File(entry.file).name,
                pageUrl = entry.url
            )
        }
        Graph.viewer.set(items, index, "Library")
        _openViewer.tryEmit(index)
    }

    public fun delete(entry: AppDatabase.LibraryEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(entry.file).delete() }
                Graph.db.deleteLibraryEntry(entry.url)
            }
            refreshLibrary()
        }
    }

    public fun deleteAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Storage.deleteAll(Graph.context())
                Graph.db.clearLibrary()
            }
            refreshLibrary()
        }
    }

    public fun describe(bytes: Long): String = DownloadManager.describe(bytes)

    public fun kindOf(entry: AppDatabase.LibraryEntry): MediaKind = entry.kind
}
