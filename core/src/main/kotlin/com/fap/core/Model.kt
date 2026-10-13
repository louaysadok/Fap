package com.fap.core

public enum class MediaKind { IMAGE, VIDEO }

public enum class FeedKind(public val path: String, public val label: String) {
    NEW("index", "New"),
    HOT("hot", "Hot"),
    POPULAR("popular", "Popular");
}

/** Which of the two browsing modes the user picked in the app's dropdown. */
public enum class BrowseMode { CREATORS, POSTS }

public data class MediaItem(
    /** Site-side id: the number in /{creator}/{id}/ or the index inside a post. */
    val id: String,
    /** Canonical page this item lives on. Also the de-duplication key. */
    val pageUrl: String,
    val thumbUrl: String? = null,
    /** Full size image url, or the video url once resolved. Null until resolved for videos. */
    val directUrl: String? = null,
    val kind: MediaKind = MediaKind.IMAGE,
    val title: String? = null,
    /** Creator slug or post id, used to pick the download sub-folder. */
    val owner: String? = null
) {
    public val isVideo: Boolean get() = kind == MediaKind.VIDEO

    public fun fileName(): String {
        val raw = (directUrl ?: thumbUrl ?: pageUrl).substringBefore('?').substringAfterLast('/')
        val cleaned = raw.replace("_300px", "").ifBlank { "${owner ?: "fapello"}_$id" }
        return if (cleaned.contains('.') && cleaned.length > 4) cleaned else "$cleaned.${if (isVideo) "mp4" else "jpg"}"
    }
}

public data class CreatorRef(
    val slug: String,
    val name: String,
    val url: String,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val thumbnails: List<String> = emptyList(),
    val mediaCount: Int? = null,
    val likes: Int? = null
)

public data class CreatorProfile(
    val creator: CreatorRef,
    val altNames: String? = null,
    /** First page of media, as rendered on the profile page itself. */
    val media: List<MediaItem> = emptyList(),
    /** Total media reported by the profile header, when we could read it. */
    val reportedTotal: Int? = null
)

public data class UserPost(
    val id: String,
    val slug: String,
    val title: String,
    val url: String,
    val thumbnails: List<String> = emptyList(),
    val poster: String? = null,
    val category: String? = null,
    val likes: Int? = null,
    val mediaCountHint: Int? = null
)

/** One tag from /tags/, e.g. slug "big-tits-pov", name "Big Tits Pov". */
public data class TagRef(
    val slug: String,
    val name: String
)

/** One page of site search results (`/search_v2/?ajax=1…`, offset paged). */
public data class SearchResult<T>(
    val items: List<T>,
    /** Total matches on the site, as reported by the endpoint. */
    val total: Int,
    val hasMore: Boolean,
    val offset: Int
)

public data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean,
    /** Short human readable note, mostly for the in-app diagnostics screen. */
    val note: String = ""
)

public data class Diagnostic(
    val name: String,
    val url: String,
    val status: String,
    val detail: String
)
