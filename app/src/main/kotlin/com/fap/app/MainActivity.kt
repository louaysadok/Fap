package com.fap.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.fap.app.di.Graph
import com.fap.app.ui.AppNav
import com.fap.app.ui.lock.LegalDialog
import com.fap.app.ui.lock.LockScreen
import com.fap.app.ui.theme.FapAppTheme

public class MainActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Progress notifications are a nice to have, never a blocker. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForNotificationPermission()

        setContent {
            val settings by Graph.settings.state.collectAsState()
            var unlocked by remember { mutableStateOf(false) }
            var legalAccepted by remember { mutableStateOf(settings.consentAccepted) }
            var showLegal by remember { mutableStateOf(false) }

            FapAppTheme(themeMode = settings.theme) {
                when {
                    !legalAccepted || showLegal -> LegalDialog {
                        Graph.settings.update { it.copy(consentAccepted = true) }
                        legalAccepted = true
                        showLegal = false
                    }
                    settings.pin.isNotBlank() && !unlocked -> LockScreen(
                        pin = settings.pin,
                        onUnlock = { unlocked = true }
                    )
                    else -> AppNav(
                        onOpenBrowser = { url -> openInBrowser(url) },
                        onPlayVideo = { url -> openInBrowser(url) },
                        onShowLegal = { showLegal = true }
                    )
                }
            }
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    private fun openInBrowser(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        }
    }
}
