package com.fap.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fap.core.MediaKind
import com.fap.core.MediaItem

/**
 * Tiny hand written SQLite layer: favourites, the download journal (so re-runs
 * skip what we already have) and the task queue (so a crash does not lose it).
 * No codegen, no annotation processor - less that can break in the build.
 */
public class AppDatabase(context: Context) : SQLiteOpenHelper(context, "fap.db", null, 2) {

    public data class Favorite(
        val slug: String,
        val name: String,
        val avatar: String?,
        val mediaCount: Int?,
        val addedAt: Long
    )

    public data class LibraryEntry(
        val file: String,
        val owner: String,
        val url: String,
        val kind: MediaKind,
        val size: Long,
        val savedAt: Long
    )

    public data class TaskRow(
        val id: Long,
        val url: String,
        val pageUrl: String,
        val owner: String,
        val file: String,
        val kind: MediaKind,
        val state: String,
        val progress: Int,
        val error: String?
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS favorites (
                slug TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                avatar TEXT,
                media_count INTEGER,
                added_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS library (
                url TEXT PRIMARY KEY,
                owner TEXT NOT NULL,
                file TEXT NOT NULL,
                kind TEXT NOT NULL,
                size INTEGER NOT NULL,
                saved_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT UNIQUE,
                page_url TEXT NOT NULL,
                owner TEXT NOT NULL,
                file TEXT NOT NULL,
                kind TEXT NOT NULL,
                state TEXT NOT NULL,
                progress INTEGER NOT NULL DEFAULT 0,
                error TEXT)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS favorites")
        db.execSQL("DROP TABLE IF EXISTS library")
        db.execSQL("DROP TABLE IF EXISTS tasks")
        onCreate(db)
    }

    // ------------------------------------------------------------ favourites --

    public fun favorites(): List<Favorite> {
        val out = ArrayList<Favorite>()
        readableDatabase.query("favorites", null, null, null, null, null, "added_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    Favorite(
                        slug = cursor.getString(cursor.getColumnIndexOrThrow("slug")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        avatar = cursor.getString(cursor.getColumnIndexOrThrow("avatar")),
                        mediaCount = cursor.getInt(cursor.getColumnIndexOrThrow("media_count")).takeIf { it >= 0 },
                        addedAt = cursor.getLong(cursor.getColumnIndexOrThrow("added_at"))
                    )
                )
            }
        }
        return out
    }

    public fun isFavorite(slug: String): Boolean {
        readableDatabase.query("favorites", arrayOf("slug"), "slug=?", arrayOf(slug), null, null, null).use {
            return it.moveToFirst()
        }
    }

    public fun setFavorite(slug: String, name: String, avatar: String?, mediaCount: Int?, favorite: Boolean) {
        if (favorite) {
            val values = ContentValues().apply {
                put("slug", slug)
                put("name", name)
                put("avatar", avatar)
                put("media_count", mediaCount ?: -1)
                put("added_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            writableDatabase.delete("favorites", "slug=?", arrayOf(slug))
        }
    }

    // --------------------------------------------------------------- library --

    public fun isDownloaded(url: String): Boolean {
        readableDatabase.query("library", arrayOf("url"), "url=?", arrayOf(url), null, null, null).use {
            return it.moveToFirst()
        }
    }

    public fun downloadedUrls(owner: String? = null, limit: Int = 5000): Set<String> {
        val out = LinkedHashSet<String>()
        val selection = if (owner == null) null else "owner=?"
        val args = if (owner == null) null else arrayOf(owner)
        readableDatabase.query("library", arrayOf("url"), selection, args, null, null, "saved_at DESC", limit.toString())
            .use { cursor ->
                while (cursor.moveToNext()) out.add(cursor.getString(0))
            }
        return out
    }

    public fun recordDownload(url: String, owner: String, file: String, kind: MediaKind, size: Long) {
        val values = ContentValues().apply {
            put("url", url)
            put("owner", owner)
            put("file", file)
            put("kind", kind.name)
            put("size", size)
            put("saved_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("library", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    public fun library(owner: String? = null, limit: Int = 2000): List<LibraryEntry> {
        val out = ArrayList<LibraryEntry>()
        val selection = if (owner == null) null else "owner=?"
        val args = if (owner == null) null else arrayOf(owner)
        readableDatabase.query("library", null, selection, args, null, null, "saved_at DESC", limit.toString())
            .use { cursor ->
                while (cursor.moveToNext()) {
                    out.add(
                        LibraryEntry(
                            file = cursor.getString(cursor.getColumnIndexOrThrow("file")),
                            owner = cursor.getString(cursor.getColumnIndexOrThrow("owner")),
                            url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
                            kind = runCatching { MediaKind.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("kind"))) }
                                .getOrDefault(MediaKind.IMAGE),
                            size = cursor.getLong(cursor.getColumnIndexOrThrow("size")),
                            savedAt = cursor.getLong(cursor.getColumnIndexOrThrow("saved_at"))
                        )
                    )
                }
            }
        return out
    }

    public fun deleteLibraryEntry(url: String) {
        writableDatabase.delete("library", "url=?", arrayOf(url))
    }

    public fun clearLibrary() {
        writableDatabase.delete("library", null, null)
    }

    // ----------------------------------------------------------------- tasks --

    public fun enqueue(items: List<MediaItem>, ownerOverride: String? = null): List<Long> {
        val db = writableDatabase
        val ids = ArrayList<Long>()
        db.beginTransaction()
        try {
            for (item in items) {
                val key = item.directUrl ?: item.pageUrl
                val values = ContentValues().apply {
                    put("url", key)
                    put("page_url", item.pageUrl)
                    put("owner", ownerOverride ?: item.owner ?: "fapello")
                    put("file", item.fileName())
                    put("kind", item.kind.name)
                    put("state", "QUEUED")
                    put("progress", 0)
                    putNull("error")
                }
                val id = db.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (id >= 0) ids.add(id)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ids
    }

    public fun tasks(): List<TaskRow> {
        val out = ArrayList<TaskRow>()
        readableDatabase.query("tasks", null, null, null, null, null, "id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    TaskRow(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
                        pageUrl = cursor.getString(cursor.getColumnIndexOrThrow("page_url")),
                        owner = cursor.getString(cursor.getColumnIndexOrThrow("owner")),
                        file = cursor.getString(cursor.getColumnIndexOrThrow("file")),
                        kind = runCatching { MediaKind.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("kind"))) }
                            .getOrDefault(MediaKind.IMAGE),
                        state = cursor.getString(cursor.getColumnIndexOrThrow("state")),
                        progress = cursor.getInt(cursor.getColumnIndexOrThrow("progress")),
                        error = cursor.getString(cursor.getColumnIndexOrThrow("error"))
                    )
                )
            }
        }
        return out
    }

    public fun updateTask(id: Long, state: String, progress: Int? = null, error: String? = null) {
        val values = ContentValues().apply {
            put("state", state)
            if (progress != null) put("progress", progress)
            if (error != null) put("error", error) else putNull("error")
        }
        writableDatabase.update("tasks", values, "id=?", arrayOf(id.toString()))
    }

    public fun deleteTask(id: Long) {
        writableDatabase.delete("tasks", "id=?", arrayOf(id.toString()))
    }

    public fun clearFinishedTasks() {
        writableDatabase.delete("tasks", "state IN ('DONE','CANCELLED')", null)
    }

    public fun resetRunningToQueued() {
        val values = ContentValues().apply { put("state", "QUEUED") }
        writableDatabase.update("tasks", values, "state='RUNNING'", null)
    }
}
