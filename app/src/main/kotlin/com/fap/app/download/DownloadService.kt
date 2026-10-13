package com.fap.app.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.fap.app.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps downloads alive when the app is backgrounded and owns the progress
 * notification. It stops itself as soon as the queue is idle.
 */
public class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifications.build(this, "Preparing downloads", "", 0, true)
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.DOWNLOAD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }.onFailure {
            // Older platforms / missing type support: plain call is fine.
            startForeground(Notifications.DOWNLOAD_NOTIFICATION_ID, notification)
        }

        scope.launch {
            Graph.downloads.tasks.collectLatest { tasks ->
                val active = tasks.count { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED }
                val done = tasks.count { it.state == TaskState.DONE }
                val failed = tasks.count { it.state == TaskState.FAILED }
                val running = tasks.firstOrNull { it.state == TaskState.RUNNING }
                val totalBytes = tasks.sumOf { it.bytes }

                val title = when {
                    active > 0 -> "Downloading $active file${if (active == 1) "" else "s"}"
                    done + failed > 0 -> "$done downloaded${if (failed > 0) ", $failed failed" else ""}"
                    else -> "Downloads"
                }
                val text = running?.let {
                    "${it.file} · ${it.progress}% · ${DownloadManager.describe(totalBytes)}"
                } ?: "${DownloadManager.describe(totalBytes)} total"

                val sweeps = Graph.downloads.sweeps.value.filter { !it.done }
                val detail = if (sweeps.isNotEmpty()) "${sweeps.first().label}: ${sweeps.first().detail}" else text

                Notifications.notify(
                    this@DownloadService,
                    Notifications.DOWNLOAD_NOTIFICATION_ID,
                    Notifications.build(this@DownloadService, title, detail, running?.progress, running == null)
                )

                if (active == 0 && !Graph.downloads.hasPendingWork()) {
                    stopWhenIdle()
                }
            }
        }
        return START_STICKY
    }

    private fun stopWhenIdle() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }.onFailure {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
