package com.fap.app.download

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.fap.app.data.AppDatabase
import com.fap.app.di.Graph
import com.fap.core.FapelloClient
import com.fap.core.FapelloParser
import com.fap.core.FeedKind
import com.fap.core.MediaItem
import com.fap.core.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

public enum class TaskState { QUEUED, RUNNING, WAITING_WIFI, DONE, FAILED, CANCELLED }

public data class DownloadTask(
    val id: Long,
    val url: String,
    val pageUrl: String,
    val owner: String,
    val file: String,
    val kind: MediaKind,
    val state: TaskState,
    val progress: Int,
    val bytes: Long = 0L,
    val total: Long = 0L,
    val error: String? = null
)

/** A bulk crawl that is still feeding the queue (one creator, or a site sweep). */
public data class SweepStatus(
    val key: String,
    val label: String,
    val detail: String,
    val found: Int,
    val done: Boolean
)

/**
 * Download queue: rate limited like everything else, resumable across restarts
 * (the queue lives in SQLite), and driven by a foreground service so Android
 * does not kill it mid-file.
 */
public class DownloadManager(
    private val context: Context,
    private val db: AppDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _tasks = kotlinx.coroutines.flow.MutableStateFlow<List<DownloadTask>>(emptyList())
    public val tasks: kotlinx.coroutines.flow.StateFlow<List<DownloadTask>> get() = _tasks

    private val _paused = kotlinx.coroutines.flow.MutableStateFlow(false)
    public val paused: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _paused

    private val _sweeps = kotlinx.coroutines.flow.MutableStateFlow<List<SweepStatus>>(emptyList())
    public val sweeps: kotlinx.coroutines.flow.StateFlow<List<SweepStatus>> get() = _sweeps

    private val liveJobs = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private val progressBytes = ConcurrentHashMap<Long, Long>()
    private val progressTotal = ConcurrentHashMap<Long, Long>()
    private val sweepJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    public fun start() {
        scope.launch {
            // SQLite on a background thread, always: start() runs from Application.onCreate.
            db.resetRunningToQueued()
            refresh()
        }
        scope.launch {
            while (isActive) {
                runCatching { pump() }
                delay(500)
            }
        }
    }

    // ------------------------------------------------------------------ queue --

    public fun enqueue(items: List<MediaItem>, owner: String? = null) {
        if (items.isEmpty()) return
        db.enqueue(items, owner)
        refresh()
        startService()
    }

    /** Crawl every page of a creator, then queue all of it. */
    public fun enqueueCreator(creatorSlug: String, creatorName: String) {
        val key = "creator:$creatorSlug"
        if (sweepJobs.containsKey(key)) return
        setSweep(SweepStatus(key, creatorName, "crawling…", 0, false))
        sweepJobs[key] = scope.launch {
            try {
                val client = Graph.client()
                val media = client.creatorMediaAll(creatorSlug) { page, fresh, total ->
                    setSweep(SweepStatus(key, creatorName, "page $page · +$fresh", total, false))
                }
                db.enqueue(media, creatorSlug)
                refresh()
                startService()
                setSweep(SweepStatus(key, creatorName, "queued ${media.size}", media.size, true))
            } catch (e: Exception) {
                setSweep(SweepStatus(key, creatorName, "failed: ${e.message}", 0, true))
            } finally {
                sweepJobs.remove(key)
            }
        }
    }

    /** The heavy one: walk the creator feeds and queue every creator found. */
    public fun enqueueSiteSweep(kind: FeedKind, feedPages: Int) {
        val key = "sweep:${kind.name}"
        if (sweepJobs.containsKey(key)) return
        setSweep(SweepStatus(key, "Site sweep (${kind.label})", "listing creators…", 0, false))
        sweepJobs[key] = scope.launch {
            try {
                val client = Graph.client()
                val creators = client.discoverCreators(kind, 1, feedPages) { page, fresh, total ->
                    setSweep(SweepStatus(key, "Site sweep (${kind.label})", "feed page $page · +$fresh", total, false))
                }
                var queued = 0
                for ((index, creator) in creators.withIndex()) {
                    if (!isActive) return@launch
                    setSweep(
                        SweepStatus(
                            key, "Site sweep (${kind.label})",
                            "${index + 1}/${creators.size} · ${creator.name}", queued, false
                        )
                    )
                    val media = runCatching { client.creatorMediaAll(creator.slug) }.getOrDefault(emptyList())
                    db.enqueue(media, creator.slug)
                    queued += media.size
                    refresh()
                    startService()
                }
                setSweep(SweepStatus(key, "Site sweep (${kind.label})", "queued $queued from ${creators.size} creators", queued, true))
            } catch (e: Exception) {
                setSweep(SweepStatus(key, "Site sweep (${kind.label})", "failed: ${e.message}", 0, true))
            } finally {
                sweepJobs.remove(key)
            }
        }
    }

    public fun cancelSweep(key: String) {
        sweepJobs[key]?.cancel()
        sweepJobs.remove(key)
        _sweeps.value = _sweeps.value.filter { it.key != key }
    }

    public fun pauseAll() {
        _paused.value = true
        scope.launch {
            mutex.withLock {
                db.tasks().filter { it.state == TaskState.RUNNING.name }.forEach {
                    db.updateTask(it.id, TaskState.QUEUED.name, 0)
                }
            }
            refresh()
        }
    }

    public fun resumeAll() {
        _paused.value = false
        startService()
        refresh()
    }

    public fun retry(id: Long) {
        db.updateTask(id, TaskState.QUEUED.name, 0, null)
        refresh()
        startService()
    }

    public fun cancel(id: Long) {
        liveJobs[id]?.cancel()
        liveJobs.remove(id)
        db.deleteTask(id)
        refresh()
    }

    public fun clearFinished() {
        db.clearFinishedTasks()
        refresh()
    }

    public fun refresh() {
        val rows = db.tasks()
        _tasks.value = rows.map { row ->
            DownloadTask(
                id = row.id,
                url = row.url,
                pageUrl = row.pageUrl,
                owner = row.owner,
                file = row.file,
                kind = row.kind,
                state = runCatching { TaskState.valueOf(row.state) }.getOrDefault(TaskState.QUEUED),
                progress = row.progress,
                bytes = progressBytes[row.id] ?: 0L,
                total = progressTotal[row.id] ?: 0L,
                error = row.error
            )
        }
    }

    public fun hasPendingWork(): Boolean =
        _tasks.value.any { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING || it.state == TaskState.WAITING_WIFI } ||
            sweepJobs.isNotEmpty()

    // ------------------------------------------------------------------ engine --

    private suspend fun pump() {
        if (_paused.value) return
        val settings = Graph.settings.state.value
        if (settings.wifiOnly && !isWifiConnected()) {
            markWaitingForWifi()
            return
        }
        val running = _tasks.value.count { it.state == TaskState.RUNNING }
        val slots = settings.concurrency - running
        if (slots <= 0) return
        val next = _tasks.value
            .filter { it.state == TaskState.QUEUED || it.state == TaskState.WAITING_WIFI }
            .take(slots)
        for (task in next) launchTask(task)
    }

    private fun markWaitingForWifi() {
        val changed = _tasks.value.any { it.state == TaskState.QUEUED }
        if (!changed) return
        db.tasks().filter { it.state == TaskState.QUEUED.name }.forEach {
            db.updateTask(it.id, TaskState.WAITING_WIFI.name, 0)
        }
        refresh()
    }

    private fun launchTask(task: DownloadTask) {
        if (liveJobs.containsKey(task.id)) return
        db.updateTask(task.id, TaskState.RUNNING.name, 0, null)
        refresh()
        liveJobs[task.id] = scope.launch {
            runTask(task)
            liveJobs.remove(task.id)
            refresh()
        }
    }

    private suspend fun runTask(task: DownloadTask) {
        val client = Graph.client()
        val http = Graph.http
        try {
            if (Graph.settings.state.value.skipDownloaded && db.isDownloaded(task.url)) {
                db.updateTask(task.id, TaskState.DONE.name, 100, null)
                refresh()
                return
            }
            val direct = if (FapelloParser.isMediaUrl(task.url)) {
                task.url
            } else {
                // Videos only expose their file on the item page.
                val resolved = client.resolve(
                    MediaItem(
                        id = task.pageUrl.trimEnd('/').substringAfterLast('/'),
                        pageUrl = task.pageUrl,
                        thumbUrl = null,
                        kind = task.kind,
                        owner = task.owner
                    )
                )
                resolved.directUrl ?: throw IllegalStateException("could not resolve media url")
            }

            val candidates = if (task.kind == MediaKind.IMAGE) {
                FapelloParser.sizeCandidates(direct)
            } else {
                listOf(direct)
            }

            var lastError: String? = null
            for (candidate in candidates) {
                val result = downloadTo(task, candidate, http, client)
                if (result.isSuccess) return
                lastError = result.exceptionOrNull()?.message
            }
            throw IllegalStateException(lastError ?: "download failed")
        } catch (e: Exception) {
            db.updateTask(task.id, TaskState.FAILED.name, 0, e.message)
            refresh()
        }
    }

    private fun downloadTo(
        task: DownloadTask,
        url: String,
        http: OkHttpClient,
        client: FapelloClient
    ): Result<Unit> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", FapelloClient.DEFAULT_USER_AGENT)
            .header("Referer", "${client.base}/")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("empty response")
            val total = body.contentLength()
            progressTotal[task.id] = total

            val target = Storage.file(context, task.owner, task.file)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.part")
            var copied = 0L
            var lastUiUpdate = 0L

            FileOutputStream(tmp).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (_paused.value) {
                            out.flush()
                            tmp.delete()
                            db.updateTask(task.id, TaskState.QUEUED.name, 0, null)
                            return@use
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        copied += read
                        progressBytes[task.id] = copied
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate > 400) {
                            lastUiUpdate = now
                            val percent = if (total > 0) (copied * 100 / total).toInt() else 0
                            db.updateTask(task.id, TaskState.RUNNING.name, percent, null)
                            refresh()
                        }
                    }
                }
            }

            if (_paused.value) {
                tmp.delete()
                db.updateTask(task.id, TaskState.QUEUED.name, 0, null)
                return@runCatching
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            db.recordDownload(task.url, task.owner, target.absolutePath, task.kind, target.length())
            db.updateTask(task.id, TaskState.DONE.name, 100, null)
            refresh()
        }
    }

    private fun setSweep(status: SweepStatus) {
        _sweeps.value = _sweeps.value.filter { it.key != status.key } + status
    }

    private fun isWifiConnected(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun startService() {
        runCatching {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    public companion object {
        public fun describe(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var index = 0
            while (value >= 1024 && index < units.lastIndex) {
                value /= 1024
                index++
            }
            return if (index == 0) "$bytes B" else "%.1f %s".format(value, units[index])
        }
    }
}
