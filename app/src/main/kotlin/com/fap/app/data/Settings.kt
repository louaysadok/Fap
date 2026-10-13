package com.fap.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fap.core.BrowseMode
import com.fap.core.FapelloUrls
import com.fap.core.FeedKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "fap_settings")

public data class Settings(
    /** What the dropdown is set to; drives the whole browse screen. */
    val mode: BrowseMode = BrowseMode.CREATORS,
    val feedKind: FeedKind = FeedKind.NEW,
    val baseUrl: String = FapelloUrls.DEFAULT_BASE,
    val concurrency: Int = 3,
    val requestsPerSecond: Double = 2.0,
    val jitterMs: Int = 250,
    val wifiOnly: Boolean = false,
    val skipDownloaded: Boolean = true,
    val hideThumbs: Boolean = false,
    val groupFolders: Boolean = true,
    val pin: String = "",
    val theme: Int = 0, // 0 = system, 1 = dark, 2 = light
    val sweepPages: Int = 5,
    val consentAccepted: Boolean = false
)

private object Keys {
    val MODE = stringPreferencesKey("mode")
    val FEED = stringPreferencesKey("feed")
    val BASE = stringPreferencesKey("base_url")
    val CONCURRENCY = intPreferencesKey("concurrency")
    val RPS = doublePreferencesKey("rps")
    val JITTER = intPreferencesKey("jitter")
    val WIFI = booleanPreferencesKey("wifi_only")
    val SKIP = booleanPreferencesKey("skip_downloaded")
    val HIDE = booleanPreferencesKey("hide_thumbs")
    val GROUP = booleanPreferencesKey("group_folders")
    val PIN = stringPreferencesKey("pin")
    val THEME = intPreferencesKey("theme")
    val SWEEP = intPreferencesKey("sweep_pages")
    val CONSENT = booleanPreferencesKey("consent")
}

public class SettingsStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(Settings())
    public val state: StateFlow<Settings> get() = _state

    /** Fire and forget load; safe to call from Application.onCreate. */
    public fun init() {
        scope.launch {
            val first = runCatching { context.dataStore.data.first() }.getOrDefault(emptyPreferences())
            _state.value = fromPrefs(first)
            context.dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { fromPrefs(it) }
                .collect { _state.value = it }
        }
    }

    public fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        _state.value = next
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[Keys.MODE] = next.mode.name
                prefs[Keys.FEED] = next.feedKind.name
                prefs[Keys.BASE] = next.baseUrl
                prefs[Keys.CONCURRENCY] = next.concurrency
                prefs[Keys.RPS] = next.requestsPerSecond
                prefs[Keys.JITTER] = next.jitterMs
                prefs[Keys.WIFI] = next.wifiOnly
                prefs[Keys.SKIP] = next.skipDownloaded
                prefs[Keys.HIDE] = next.hideThumbs
                prefs[Keys.GROUP] = next.groupFolders
                prefs[Keys.PIN] = next.pin
                prefs[Keys.THEME] = next.theme
                prefs[Keys.SWEEP] = next.sweepPages
                prefs[Keys.CONSENT] = next.consentAccepted
            }
        }
    }

    private companion object {
        fun fromPrefs(prefs: Preferences): Settings = Settings(
            mode = runCatching { BrowseMode.valueOf(prefs[Keys.MODE] ?: BrowseMode.CREATORS.name) }
                .getOrDefault(BrowseMode.CREATORS),
            feedKind = runCatching { FeedKind.valueOf(prefs[Keys.FEED] ?: FeedKind.NEW.name) }
                .getOrDefault(FeedKind.NEW),
            baseUrl = prefs[Keys.BASE] ?: FapelloUrls.DEFAULT_BASE,
            concurrency = prefs[Keys.CONCURRENCY] ?: 3,
            requestsPerSecond = prefs[Keys.RPS] ?: 2.0,
            jitterMs = prefs[Keys.JITTER] ?: 250,
            wifiOnly = prefs[Keys.WIFI] ?: false,
            skipDownloaded = prefs[Keys.SKIP] ?: true,
            hideThumbs = prefs[Keys.HIDE] ?: false,
            groupFolders = prefs[Keys.GROUP] ?: true,
            pin = prefs[Keys.PIN] ?: "",
            theme = prefs[Keys.THEME] ?: 0,
            sweepPages = prefs[Keys.SWEEP] ?: 5,
            consentAccepted = prefs[Keys.CONSENT] ?: false
        )
    }
}
