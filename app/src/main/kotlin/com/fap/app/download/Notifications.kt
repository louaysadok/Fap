package com.fap.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

public object Notifications {

    public const val CHANNEL_DOWNLOADS: String = "downloads"
    public const val DOWNLOAD_NOTIFICATION_ID: Int = 4242

    public fun createChannels(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progress of media downloads"
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    public fun build(context: Context, title: String, text: String, progress: Int?, indeterminate: Boolean = false): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress ?: 0, indeterminate || progress == null)
            .build()

    /** Never let a missing notification permission break a download. */
    public fun notify(context: Context, id: Int, notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    public fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
