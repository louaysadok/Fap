package com.fap.app.repo

import com.fap.core.CreatorProfile
import com.fap.core.CreatorRef
import com.fap.core.Diagnostic
import com.fap.core.FapelloClient
import com.fap.core.FeedKind
import com.fap.core.MediaItem
import com.fap.core.PageResult
import com.fap.core.UserPost
import com.fap.app.data.AppDatabase
import com.fap.app.di.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every blocking call in the core goes through here, onto Dispatchers.IO. */
public object Repository {

    private fun client(): FapelloClient = Graph.client()

    public suspend fun feed(kind: FeedKind, page: Int): PageResult<CreatorRef> =
        withContext(Dispatchers.IO) { client().feed(kind, page) }

    public suspend fun creator(slug: String): CreatorProfile =
        withContext(Dispatchers.IO) { client().creator(slug) }

    public suspend fun creatorMedia(slug: String, page: Int): PageResult<MediaItem> =
        withContext(Dispatchers.IO) { client().creatorMedia(slug, page) }

    public suspend fun userPosts(page: Int): PageResult<UserPost> =
        withContext(Dispatchers.IO) { client().userPosts(page) }

    public suspend fun userPostMedia(post: UserPost): List<MediaItem> =
        withContext(Dispatchers.IO) { client().userPostMedia(post) }

    public suspend fun open(raw: String): FapelloClient.Opened =
        withContext(Dispatchers.IO) { client().open(raw) }

    public suspend fun diagnostics(): List<Diagnostic> =
        withContext(Dispatchers.IO) { client().diagnostics() }

    public suspend fun isFavorite(slug: String): Boolean =
        withContext(Dispatchers.IO) { Graph.db.isFavorite(slug) }

    public suspend fun favorites(): List<AppDatabase.Favorite> =
        withContext(Dispatchers.IO) { Graph.db.favorites() }

    public suspend fun removeFavorite(slug: String) {
        withContext(Dispatchers.IO) { Graph.db.removeFavorite(slug) }
    }

    /** Videos only expose their real file on their own page: resolve it there. */
    public suspend fun resolve(item: MediaItem): MediaItem =
        withContext(Dispatchers.IO) { client().resolve(item) }
}
