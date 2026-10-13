package com.fap.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fap.app.di.Graph
import com.fap.app.download.Storage
import com.fap.app.repo.Repository
import com.fap.core.Diagnostic
import com.fap.core.FeedKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public data class SettingsUiState(
    val runningTest: Boolean = false,
    val diagnostics: List<Diagnostic> = emptyList(),
    val testError: String? = null,
    val storageBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val toast: String? = null
)

public class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    public val state: StateFlow<SettingsUiState> get() = _state

    public val settings: StateFlow<com.fap.app.data.Settings> get() = Graph.settings.state

    init {
        refreshStorage()
    }

    public fun refreshStorage() {
        viewModelScope.launch {
            val used = withContext(Dispatchers.IO) { Storage.sizeBytes(Graph.context()) }
            val cache = withContext(Dispatchers.IO) { directorySize(Graph.context().cacheDir) }
            _state.update { it.copy(storageBytes = used, cacheBytes = cache) }
        }
    }

    /** Hits every endpoint once and reports what each selector found. */
    public fun runSelfTest() {
        viewModelScope.launch {
            _state.update { it.copy(runningTest = true, testError = null, diagnostics = emptyList()) }
            try {
                val results = Repository.diagnostics()
                _state.update { it.copy(runningTest = false, diagnostics = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(runningTest = false, testError = e.message) }
            }
        }
    }

    public fun setBaseUrl(value: String) {
        Graph.settings.update { it.copy(baseUrl = value.trim().ifBlank { com.fap.core.FapelloUrls.DEFAULT_BASE }) }
    }

    public fun setConcurrency(value: Int) {
        Graph.settings.update { it.copy(concurrency = value.coerceIn(1, 8)) }
    }

    public fun setRequestsPerSecond(value: Float) {
        Graph.settings.update { it.copy(requestsPerSecond = value.toDouble().coerceIn(0.2, 8.0)) }
    }

    public fun setJitter(value: Float) {
        Graph.settings.update { it.copy(jitterMs = value.toInt().coerceIn(0, 2000)) }
    }

    public fun setWifiOnly(value: Boolean) {
        Graph.settings.update { it.copy(wifiOnly = value) }
    }

    public fun setSkipDownloaded(value: Boolean) {
        Graph.settings.update { it.copy(skipDownloaded = value) }
    }

    public fun setGroupFolders(value: Boolean) {
        Graph.settings.update { it.copy(groupFolders = value) }
    }

    public fun setHideThumbs(value: Boolean) {
        Graph.settings.update { it.copy(hideThumbs = value) }
    }

    public fun setTheme(value: Int) {
        Graph.settings.update { it.copy(theme = value.coerceIn(0, 2)) }
    }

    public fun setSweepPages(value: Int) {
        Graph.settings.update { it.copy(sweepPages = value.coerceIn(1, 100)) }
    }

    public fun setPin(value: String) {
        val pin = value.filter { it.isDigit() }.take(6)
        Graph.settings.update { it.copy(pin = pin) }
    }

    public fun startSiteSweep(kind: FeedKind) {
        val pages = Graph.settings.state.value.sweepPages
        Graph.downloads.enqueueSiteSweep(kind, pages)
        _state.update { it.copy(toast = "Site sweep started over $pages feed pages") }
    }

    public fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    public fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { Graph.context().cacheDir.deleteRecursively() }
            }
            refreshStorage()
            _state.update { it.copy(toast = "Image cache cleared") }
        }
    }

    public fun deleteAllDownloads() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Storage.deleteAll(Graph.context())
                Graph.db.clearLibrary()
            }
            refreshStorage()
            _state.update { it.copy(toast = "All downloads deleted") }
        }
    }

    private fun directorySize(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }
}
