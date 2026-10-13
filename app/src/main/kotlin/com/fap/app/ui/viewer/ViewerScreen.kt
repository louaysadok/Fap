package com.fap.app.ui.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fap.app.di.Graph
import com.fap.app.di.ViewerItem
import com.fap.app.repo.Repository
import com.fap.core.FapelloClient
import com.fap.core.MediaItem
import com.fap.core.MediaKind
import java.io.File
import kotlinx.coroutines.delay

private val VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".mov", ".m4v", ".mkv")
private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif", ".bmp")

private fun String.pathPart(): String = substringBefore('?').substringBefore('#').lowercase()

private fun String.hasVideoExtension(): Boolean = VIDEO_EXTENSIONS.any { pathPart().endsWith(it) }

private fun String.hasImageExtension(): Boolean = IMAGE_EXTENSIONS.any { pathPart().endsWith(it) }

private fun String.isGif(): Boolean = pathPart().endsWith(".gif")

/** True when [source] already points at a playable file (remote or on disk). */
private fun isPlayableVideoSource(source: String): Boolean =
    source.hasVideoExtension() &&
        (source.startsWith("http", ignoreCase = true) ||
            source.startsWith("/") ||
            source.startsWith("file://"))

/**
 * Full screen media viewer. Swipe left/right to move through the set, pinch to
 * zoom (double tap to reset), and download whatever is on screen.
 *
 * Videos play inline here with ExoPlayer — playback never leaves the app.
 * A video whose real file is not known yet (creator videos only expose it on
 * their own item page) is resolved first, exactly like the downloader does.
 * GIFs animate in place via the coil-gif decoders registered in [com.fap.app.FapApp].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ViewerScreen(
    onBack: () -> Unit,
    onDownload: (ViewerItem) -> Unit
) {
    val items = Graph.viewer.items
    var index by remember { mutableIntStateOf(Graph.viewer.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val title = Graph.viewer.title

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing to show", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    fun go(delta: Int) {
        val next = (index + delta).coerceIn(0, items.size - 1)
        if (next != index) {
            index = next
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val item = items[index]

        if (item.kind == MediaKind.VIDEO) {
            // The player owns its gestures (tap for controls); page through
            // the set with the Prev/Next row below, like everywhere else.
            ViewerVideo(
                item = item,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Zoomed in: pinch / pan. Zoomed out: swipe between items.
                    .pointerInput(scale > 1.01f, index) {
                        if (scale > 1.01f) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 8f)
                                offset += pan
                            }
                        } else {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount < -60f) go(1) else if (dragAmount > 60f) go(-1)
                            }
                        }
                    }
                    .pointerInput(index) {
                        detectTapGestures(onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        })
                    }
            ) {
                ViewerImage(
                    item = item,
                    scale = scale,
                    offset = offset,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    Text("${index + 1} / ${items.size}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { onDownload(item) }) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { go(-1) }, enabled = index > 0) { Text("Prev") }
            Text(item.label, color = Color.White, style = MaterialTheme.typography.labelSmall)
            Button(onClick = { go(1) }, enabled = index < items.size - 1) { Text("Next") }
        }
    }
}

// ------------------------------------------------------------------- video --

/**
 * Resolves the playable file for a video item, then hands it to the player.
 * While resolving (or if resolving fails) the poster thumbnail stays visible.
 */
@Composable
private fun ViewerVideo(
    item: ViewerItem,
    modifier: Modifier = Modifier
) {
    var playableUrl by remember(item) { mutableStateOf<String?>(null) }
    var resolving by remember(item) { mutableStateOf(false) }
    var resolveError by remember(item) { mutableStateOf<String?>(null) }
    var retryTick by remember(item) { mutableIntStateOf(0) }

    LaunchedEffect(item, retryTick) {
        if (isPlayableVideoSource(item.source)) {
            playableUrl = item.source
            resolving = false
            resolveError = null
            return@LaunchedEffect
        }
        val pageUrl = item.pageUrl
        if (pageUrl.isNullOrBlank()) {
            playableUrl = null
            resolveError = "This video has no page to resolve its file from."
            return@LaunchedEffect
        }
        resolving = true
        resolveError = null
        runCatching {
            Repository.resolve(
                MediaItem(
                    id = pageUrl.trimEnd('/').substringAfterLast('/').ifBlank { item.label },
                    pageUrl = pageUrl,
                    thumbUrl = item.thumb,
                    kind = MediaKind.VIDEO
                )
            )
        }.onSuccess { resolved ->
            playableUrl = resolved.directUrl
            if (resolved.directUrl == null) {
                resolveError = "Could not find a video file on that page."
            }
        }.onFailure { error ->
            playableUrl = null
            resolveError = error.message ?: "Could not load this video."
        }
        resolving = false
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val url = playableUrl
        when {
            url != null -> VideoPlayerSurface(url = url, modifier = Modifier.fillMaxSize())

            else -> {
                // Poster while we resolve, and behind the error message.
                val poster = item.thumb ?: item.source.takeIf { it.hasImageExtension() }
                if (poster != null) {
                    ViewerImage(
                        item = item.copy(source = poster, kind = MediaKind.IMAGE),
                        scale = 1f,
                        offset = Offset.Zero,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                when {
                    resolving -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            "Loading video…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    resolveError != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            resolveError ?: "Could not load this video.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { retryTick++ },
                            modifier = Modifier.padding(top = 12.dp)
                        ) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerSurface(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(url) {
        // Same politeness/identity headers the image loader and downloader
        // send: the CDN refuses bare requests.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(FapelloClient.DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to "${Graph.client().base}/"))
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                val uri = when {
                    url.startsWith("file://") -> Uri.parse(url)
                    url.startsWith("/") -> Uri.fromFile(File(url))
                    else -> Uri.parse(url)
                }
                setMediaItem(Media3MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember(exoPlayer) { mutableStateOf(exoPlayer.isPlaying) }
    var isBuffering by remember(exoPlayer) { mutableStateOf(false) }
    var hasEnded by remember(exoPlayer) { mutableStateOf(false) }
    var playbackError by remember(exoPlayer) { mutableStateOf<String?>(null) }
    var muted by remember(exoPlayer) { mutableStateOf(false) }
    var controlsVisible by remember(exoPlayer) { mutableStateOf(true) }
    var positionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var durationMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var scrubbing by remember(exoPlayer) { mutableStateOf(false) }
    var scrubFraction by remember(exoPlayer) { mutableFloatStateOf(0f) }

    // Listener + lifecycle + release, all tied to this exoPlayer instance:
    // leaving the screen (or paging to another item) disposes this
    // composable, which releases the exoPlayer.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                hasEnded = playbackState == Player.STATE_ENDED
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: "Could not play this video."
                isBuffering = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) exoPlayer.pause()
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Keep the scrub bar and clock honest.
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!scrubbing) positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: durationMs
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
            delay(400)
        }
    }

    fun togglePlay() {
        when {
            hasEnded -> {
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            exoPlayer.isPlaying -> exoPlayer.pause()
            else -> exoPlayer.play()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = exoPlayer
                }
            },
            update = { view -> view.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // Tap toggles the controls; the controls themselves sit above this.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(exoPlayer) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                }
        )

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        val error = playbackError
        if (error != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(error, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        playbackError = null
                        exoPlayer.prepare()
                        exoPlayer.play()
                    },
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text("Retry") }
            }
        } else if (!isPlaying && !isBuffering && controlsVisible) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center)
            ) {
                IconButton(onClick = ::togglePlay, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (hasEnded) Icons.Default.Replay else Icons.Default.PlayArrow,
                        contentDescription = if (hasEnded) "Replay" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        if (controlsVisible) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Leave room for the viewer's Prev/Next row underneath.
                    .padding(bottom = 72.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = ::togglePlay) {
                        Icon(
                            when {
                                hasEnded -> Icons.Default.Replay
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                    Text(
                        formatTime(if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = when {
                            scrubbing -> scrubFraction
                            durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            else -> 0f
                        },
                        onValueChange = { fraction ->
                            scrubbing = true
                            scrubFraction = fraction
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0) {
                                exoPlayer.seekTo((scrubFraction * durationMs).toLong())
                            }
                            scrubbing = false
                        },
                        enabled = durationMs > 0,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        formatTime(durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    IconButton(
                        onClick = {
                            muted = !muted
                            exoPlayer.volume = if (muted) 0f else 1f
                        }
                    ) {
                        Icon(
                            if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (muted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

// ------------------------------------------------------------------- image --

@Composable
private fun ViewerImage(
    item: ViewerItem,
    scale: Float,
    offset: Offset,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Animated images must not be crossfaded frame-by-frame; the GIF itself
    // is the animation (decoded by the coil-gif decoders, see FapApp).
    val animated = remember(item.source, item.label) { item.source.isGif() || item.label.isGif() }
    val model = remember(item.source, animated) {
        if (item.source.startsWith("/") || item.source.startsWith("file://")) {
            ImageRequest.Builder(context)
                .data(File(item.source.removePrefix("file://")))
                .crossfade(!animated)
                .build()
        } else {
            ImageRequest.Builder(context)
                .data(item.source)
                .crossfade(!animated)
                .addHeader("User-Agent", FapelloClient.DEFAULT_USER_AGENT)
                .addHeader("Referer", "${Graph.client().base}/")
                .build()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = model,
            contentDescription = item.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
