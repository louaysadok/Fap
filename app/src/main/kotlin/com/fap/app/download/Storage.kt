package com.fap.app.download

import android.content.Context
import com.fap.app.di.Graph
import java.io.File

/**
 * Everything lands in the app's private external files directory
 * (Android/data/<app>/files/Fapello/...). That is invisible to the Gallery and
 * to other apps, which is exactly what "app-private" means here, and it needs
 * no storage permission at all.
 */
public object Storage {

    public fun root(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "Fapello").apply { mkdirs() }
    }

    public fun dir(context: Context, owner: String?): File =
        if (Graph.settings.state.value.groupFolders && !owner.isNullOrBlank()) {
            File(root(context), sanitise(owner)).apply { mkdirs() }
        } else {
            root(context)
        }

    public fun file(context: Context, owner: String?, name: String): File =
        File(dir(context, owner), sanitise(name))

    public fun allFiles(context: Context, owner: String? = null): List<File> {
        val dir = if (owner == null) root(context) else File(root(context), sanitise(owner))
        if (!dir.exists()) return emptyList()
        val out = ArrayList<File>()
        dir.walkTopDown().forEach { if (it.isFile && !it.name.endsWith(".part")) out.add(it) }
        return out.sortedByDescending { it.lastModified() }
    }

    public fun owners(context: Context): List<String> {
        val root = root(context)
        if (!root.exists()) return emptyList()
        return root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .map { it.name }
            .sorted()
    }

    public fun sizeBytes(context: Context): Long = allFiles(context).sumOf { it.length() }

    public fun deleteAll(context: Context) {
        root(context).deleteRecursively()
        root(context).mkdirs()
    }

    private fun sanitise(value: String): String =
        value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "unnamed" }
}
