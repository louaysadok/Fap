package com.fap.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fap.app.download.DownloadManager
import com.fap.core.FeedKind
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsScreen(viewModel: SettingsViewModel, onShowLegal: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var pin by remember { mutableStateOf("") }
    var showSweepWarning by remember { mutableStateOf(false) }

    LaunchedEffect(state.toast) {
        val message = state.toast ?: return@LaunchedEffect
        delay(2200)
        viewModel.consumeToast()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("Source") {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Site host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.setBaseUrl(baseUrl) }) { Text("Apply") }
                    TextButton(onClick = {
                        baseUrl = com.fap.core.FapelloUrls.DEFAULT_BASE
                        viewModel.setBaseUrl(baseUrl)
                    }) { Text("Reset") }
                }
                Text(
                    "Changing the host switches every request the scraper makes. Leave it alone unless a mirror is needed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section("Politeness & speed") {
                LabelledSlider(
                    label = "Concurrent downloads",
                    value = settings.concurrency.toFloat(),
                    range = 1f..8f,
                    steps = 6,
                    display = "${settings.concurrency}",
                    onValueChange = { viewModel.setConcurrency(it.toInt()) }
                )
                LabelledSlider(
                    label = "Requests per second",
                    value = settings.requestsPerSecond.toFloat(),
                    range = 0.2f..8f,
                    steps = 14,
                    display = "%.1f/s".format(settings.requestsPerSecond),
                    onValueChange = { viewModel.setRequestsPerSecond(it) }
                )
                LabelledSlider(
                    label = "Random delay between requests",
                    value = settings.jitterMs.toFloat(),
                    range = 0f..1500f,
                    steps = 14,
                    display = "${settings.jitterMs} ms",
                    onValueChange = { viewModel.setJitter(it) }
                )
                Text(
                    "Lower is faster and ruder. If pages start coming back empty, back off.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section("Downloads") {
                ToggleRow("Wi-Fi only", settings.wifiOnly, viewModel::setWifiOnly)
                ToggleRow("Skip files already downloaded", settings.skipDownloaded, viewModel::setSkipDownloaded)
                ToggleRow("Group into per-creator folders", settings.groupFolders, viewModel::setGroupFolders)
                Text(
                    "Saved to private app storage (${DownloadManager.describe(state.storageBytes)} used) - " +
                        "invisible to Gallery and other apps.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::clearCache) { Text("Clear image cache") }
                    OutlinedButton(onClick = viewModel::deleteAllDownloads) { Text("Delete downloads") }
                }
            }

            Section("Privacy") {
                ToggleRow("Hide thumbnails (private mode)", settings.hideThumbs, viewModel::setHideThumbs)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text(if (settings.pin.isBlank()) "Set an app PIN" else "Change PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.setPin(pin); pin = "" }, enabled = pin.length >= 4) { Text("Save PIN") }
                    if (settings.pin.isNotBlank()) {
                        TextButton(onClick = { viewModel.setPin("") }) { Text("Remove PIN") }
                    }
                }
            }

            Section("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = settings.theme == 0, onClick = { viewModel.setTheme(0) }, label = { Text("System") })
                    FilterChip(selected = settings.theme == 1, onClick = { viewModel.setTheme(1) }, label = { Text("Dark") })
                    FilterChip(selected = settings.theme == 2, onClick = { viewModel.setTheme(2) }, label = { Text("Light") })
                }
            }

            Section("Site-wide sweep") {
                LabelledSlider(
                    label = "Feed pages to walk",
                    value = settings.sweepPages.toFloat(),
                    range = 1f..100f,
                    steps = 20,
                    display = "${settings.sweepPages}",
                    onValueChange = { viewModel.setSweepPages(it.toInt()) }
                )
                Text(
                    "Walks the creator feed and downloads every creator in full. Thousands of requests - " +
                        "expect hours and a lot of storage.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showSweepWarning = true }) { Text("Start sweep…") }
                }
            }

            Section("Self-test") {
                Text(
                    "Fetches one real page per endpoint and reports exactly what each parser found. " +
                        "Run this first if grids ever look empty.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = viewModel::runSelfTest, enabled = !state.runningTest) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(" Run self-test")
                }
                if (state.runningTest) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.testError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.diagnostics.forEach { diagnostic ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(diagnostic.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (diagnostic.status == "OK") MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        diagnostic.status,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Text(
                                diagnostic.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(diagnostic.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Section("About") {
                Text(
                    "Personal-use downloader for public pages. Nothing here bypasses logins, paywalls or " +
                        "age gates. You are responsible for what you download and for respecting creators' rights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onShowLegal) { Text("Read the full notice") }
            }
        }

        state.toast?.let { message ->
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(message) }
        }

        if (showSweepWarning) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSweepWarning = false },
                title = { Text("Start a site-wide sweep?") },
                text = {
                    Text(
                        "This walks ${settings.sweepPages} feed pages of ${settings.feedKind.label}, then crawls " +
                            "every creator it finds in full. It is deliberately slow and can take hours. " +
                            "Only run it for content you have the right to keep."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.startSiteSweep(settings.feedKind); showSweepWarning = false }) {
                        Text("Start")
                    }
                },
                dismissButton = { TextButton(onClick = { showSweepWarning = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
