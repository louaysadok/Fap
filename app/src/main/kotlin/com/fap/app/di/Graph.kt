package com.fap.app.di

import android.content.Context
import com.fap.app.data.AppDatabase
import com.fap.app.data.SettingsStore
import com.fap.app.download.DownloadManager
import com.fap.app.download.Notifications
import com.fap.app.net.HttpProvider
import com.fap.app.net.OkHttpTransport
import com.fap.core.FapelloClient
import com.fap.core.FapelloUrls
import com.fap.core.MediaKind
import com.fap.core.RateLimiter
import okhttp3.OkHttpClient

/** Hand rolled service locator: small, obvious, and nothing to configure. */
public object Graph {

    private lateinit var appContext: Context

    public lateinit var settings: SettingsStore
        private set
    public lateinit var db: AppDatabase
        private set
    public lateinit var downloads: DownloadManager
        private set
    public lateinit var http: OkHttpClient
        private set

    private lateinit var transport: OkHttpTransport
    private val rateLimiter = RateLimiter(requestsPerSecond = 2.0, jitterMs = 0L..250L)
    private var cachedClient: FapelloClient? = null
    private var cachedBase: String = ""

    public val viewer: ViewerState = ViewerState()

    public fun init(context: Context) {
        appContext = context.applicationContext
        settings = SettingsStore(appContext)
        settings.init()
        db = AppDatabase(appContext)
        http = HttpProvider.create()
        transport = OkHttpTransport(http)
        downloads = DownloadManager(appContext, db)
        downloads.start()
        Notifications.createChannels(appContext)
    }

    public fun context(): Context = appContext

    /** Rebuilt only when the host changes; the rate limiter is shared. */
    public fun client(): FapelloClient {
        val current = settings.state.value
        rateLimiter.requestsPerSecond = current.requestsPerSecond.coerceIn(0.2, 10.0)
        rateLimiter.jitterMs = 0L..current.jitterMs.toLong().coerceAtLeast(0L)
        val base = FapelloUrls.normaliseBase(current.baseUrl)
        val cached = cachedClient
        if (cached == null || cachedBase != base) {
            cachedClient = FapelloClient(transport, base, rateLimiter, maxRetries = 2)
            cachedBase = base
        }
        return cachedClient!!
    }
}

public data class ViewerItem(
    val source: String,
    val thumb: String? = null,
    val kind: MediaKind = MediaKind.IMAGE,
    val label: String = "",
    val pageUrl: String? = null
)

/** Anything the pager can show: a remote url or an already downloaded file. */
public fun com.fap.core.MediaItem.toViewerItem(): ViewerItem = ViewerItem(
    source = directUrl ?: thumbUrl ?: pageUrl,
    thumb = thumbUrl,
    kind = kind,
    label = fileName(),
    pageUrl = pageUrl
)

/** The pager needs a list, and navigation routes do not carry lists. */
public class ViewerState {
    public var items: List<ViewerItem> = emptyList()
        private set
    public var index: Int = 0
    public var title: String = ""

    public fun set(items: List<ViewerItem>, index: Int, title: String) {
        this.items = items
        this.index = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        this.title = title
    }
}
