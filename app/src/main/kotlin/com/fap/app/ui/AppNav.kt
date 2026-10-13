package com.fap.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fap.app.di.Graph
import com.fap.app.ui.browse.BrowseScreen
import com.fap.app.ui.browse.BrowseViewModel
import com.fap.app.ui.creator.CreatorScreen
import com.fap.app.ui.creator.CreatorViewModel
import com.fap.app.ui.downloads.DownloadsScreen
import com.fap.app.ui.downloads.DownloadsViewModel
import com.fap.app.ui.post.PostScreen
import com.fap.app.ui.post.PostViewModel
import com.fap.app.ui.settings.SettingsScreen
import com.fap.app.ui.settings.SettingsViewModel
import com.fap.app.ui.viewer.ViewerScreen
import com.fap.core.MediaItem
import com.fap.core.MediaKind

private object Routes {
    const val BROWSE = "browse"
    const val CREATOR = "creator/{slug}"
    const val POST = "post/{id}/{slug}"
    const val VIEWER = "viewer"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
}

@Composable
public fun AppNav(
    onOpenBrowser: (String) -> Unit,
    onPlayVideo: (String) -> Unit,
    onShowLegal: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBottomBar = route == Routes.BROWSE || route == Routes.DOWNLOADS || route == Routes.SETTINGS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.BROWSE,
                        onClick = {
                            navController.navigate(Routes.BROWSE) {
                                launchSingleTop = true
                                popUpTo(Routes.BROWSE) { inclusive = false }
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Browse") },
                        label = { Text("Browse") }
                    )
                    NavigationBarItem(
                        selected = route == Routes.DOWNLOADS,
                        onClick = {
                            navController.navigate(Routes.DOWNLOADS) {
                                launchSingleTop = true
                                popUpTo(Routes.BROWSE)
                            }
                        },
                        icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                        label = { Text("Downloads") }
                    )
                    NavigationBarItem(
                        selected = route == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                                popUpTo(Routes.BROWSE)
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BROWSE,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.BROWSE) {
                val vm: BrowseViewModel = viewModel()
                BrowseScreen(
                    viewModel = vm,
                    onOpenCreator = { slug -> navController.navigate("creator/$slug") },
                    onOpenPost = { id, slug -> navController.navigate("post/$id/$slug") },
                    onOpenViewer = { navController.navigate(Routes.VIEWER) }
                )
            }

            composable(
                Routes.CREATOR,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) { entry ->
                val slug = entry.arguments?.getString("slug").orEmpty()
                val vm: CreatorViewModel = viewModel(factory = CreatorViewModel.factory(slug))
                CreatorScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenViewer = { navController.navigate(Routes.VIEWER) },
                    onOpenBrowser = onOpenBrowser
                )
            }

            composable(
                Routes.POST,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("slug") { type = NavType.StringType }
                )
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val slug = entry.arguments?.getString("slug").orEmpty()
                val vm: PostViewModel = viewModel(factory = PostViewModel.factory(id, slug))
                PostScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenViewer = { navController.navigate(Routes.VIEWER) },
                    onOpenBrowser = onOpenBrowser
                )
            }

            composable(Routes.VIEWER) {
                ViewerScreen(
                    onBack = { navController.popBackStack() },
                    onPlayVideo = onPlayVideo,
                    onDownload = { item -> enqueueViewerItem(item) }
                )
            }

            composable(Routes.DOWNLOADS) {
                val vm: DownloadsViewModel = viewModel()
                DownloadsScreen(viewModel = vm, onOpenViewer = { navController.navigate(Routes.VIEWER) })
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel()
                SettingsScreen(viewModel = vm, onShowLegal = onShowLegal)
            }
        }
    }
}

private fun enqueueViewerItem(item: com.fap.app.di.ViewerItem) {
    val owner = ownerFor(item)
    val media = MediaItem(
        id = item.label,
        pageUrl = item.pageUrl ?: item.source,
        thumbUrl = item.thumb,
        directUrl = if (item.source.startsWith("http")) item.source else null,
        kind = if (item.source.endsWith(".mp4", true)) MediaKind.VIDEO else item.kind,
        owner = owner
    )
    Graph.downloads.enqueue(listOf(media), owner)
}

private fun ownerFor(item: com.fap.app.di.ViewerItem): String? {
    val title = Graph.viewer.title
    if (title.isBlank() || title == "Library") return null
    return title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}
