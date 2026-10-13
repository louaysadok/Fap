package com.fap.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fap.app.di.Graph
import com.fap.core.BrowseMode
import com.fap.core.FapelloClient

/**
 * The one dropdown the whole app is organised around: Creators or Posts.
 * Everything downstream (paging, search, what "download all" means) follows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ModeDropdown(
    mode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (mode == BrowseMode.CREATORS) "Creators" else "Posts"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Browse") },
            leadingIcon = {
                Icon(
                    imageVector = if (mode == BrowseMode.CREATORS) Icons.Default.Person else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BrowseMode.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(if (entry == BrowseMode.CREATORS) "Creators" else "Posts") },
                    onClick = {
                        onModeChange(entry)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
public fun rememberImageModel(url: String?): Any? {
    val context = LocalContext.current
    return remember(url) {
        when {
            url.isNullOrBlank() -> null
            // Already downloaded: load straight off disk.
            url.startsWith("/") || url.startsWith("file://") ->
                ImageRequest.Builder(context)
                    .data(java.io.File(url.removePrefix("file://")))
                    .crossfade(true)
                    .build()
            else -> ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .addHeader("User-Agent", FapelloClient.DEFAULT_USER_AGENT)
                .addHeader("Referer", "${Graph.client().base}/")
                .build()
        }
    }
}

@Composable
public fun Thumb(
    url: String?,
    modifier: Modifier = Modifier,
    isVideo: Boolean = false,
    hidden: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop
) {
    val model = rememberImageModel(url)
    Box(modifier = modifier) {
        if (model == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hidden) Modifier.alpha(0.06f) else Modifier)
            )
        }
        if (isVideo) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "video",
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
public fun LoadingRow(text: String = "Loading…", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
public fun MessageCard(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        action?.invoke()
    }
}

@Composable
public fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
public fun StatLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
