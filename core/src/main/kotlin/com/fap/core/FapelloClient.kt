package com.fap.core

/**
 * The site API, one call per screen.
 *
 * Deliberately synchronous: the Android app wraps these in Dispatchers.IO, and
 * it keeps this module free of coroutines (so it compiles and runs on a plain
 * JVM with zero dependencies).
 */
public class FapelloClient(
    private val http: HttpClient,
    baseUrl: String = FapelloUrls.DEFAULT_BASE,
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val userAgent: String = DEFAULT_USER_AGENT,
    public var maxRetries: Int = 2,
    public var retryBackoffMs: LongRange = 600L..1500L
) {
    public val base: String = FapelloUrls.normaliseBase(baseUrl)

    // ------------------------------------------------------------- plumbing --

    public fun headers(ajax: Boolean = false): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        put("Accept-Language", "en-US,en;q=0.9")
        put("Referer", "$base/")
        if (ajax) {
            put("X-Requested-With", "XMLHttpRequest")
            put("Referer", "$base/")
        }
    }

    /** One GET with rate limiting + retry. Throws the last error if all attempts fail. */
    public fun get(url: String, ajax: Boolean = false): RawResponse {
        var lastError: Exception? = null
        var lastResponse: RawResponse? = null
        for (attempt in 0..maxRetries) {
            rateLimiter.acquire()
            val response = try {
                http.get(url, headers(ajax))
            } catch (e: Exception) {
                lastError = e
                null
            }
            if (response != null) {
                lastResponse = response
                if (response.isSuccessful) return response
                if (response.code == 429 || response.code >= 500) {
                    Thread.sleep(retryBackoffMs.first * (attempt + 1))
                    continue
                }
                return response
            }
            Thread.sleep(retryBackoffMs.first * (attempt + 1))
        }
        lastResponse?.let { return it }
        throw lastError ?: IllegalStateException("Request failed: $url")
    }

    // ---------------------------------------------------------------- feeds --

    public fun feed(kind: FeedKind, page: Int): PageResult<CreatorRef> {
        val url = FapelloUrls.ajaxFeed(base, kind, page)
        var response = get(url, ajax = true)
        var items = if (response.isSuccessful) FapelloParser.parseFeed(response.body.orEmpty(), base) else emptyList()
        var note = "ajax ${response.code} · ${items.size} cards"

        if (items.isEmpty()) {
            // Some deployments serve the ajax routes differently; fall back to
            // the plain paginated html page.
            val fallbackUrl = if (kind == FeedKind.NEW) FapelloUrls.home(base, page) else "$base/${kind.path}/page-$page/"
            val fallback = get(fallbackUrl)
            val fallbackItems = if (fallback.isSuccessful) FapelloParser.parseFeed(fallback.body.orEmpty(), base) else emptyList()
            if (fallbackItems.isNotEmpty()) {
                items = fallbackItems
                note = "html ${fallback.code} · ${items.size} cards"
            }
        }
        return PageResult(items, page, items.isNotEmpty(), note)
    }

    // -------------------------------------------------------------- creator --

    public fun creator(slug: String): CreatorProfile {
        val url = FapelloUrls.creator(base, slug)
        val response = get(url)
        if (!response.isSuccessful) throw HttpStatusException(response.code, url)
        return FapelloParser.parseCreatorPage(response.body.orEmpty(), slug, base)
    }

    public fun creatorMedia(slug: String, page: Int): PageResult<MediaItem> {
        val url = FapelloUrls.creatorAjax(base, slug, page)
        val response = get(url, ajax = true)
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return PageResult(emptyList(), page, false, "page $page → HTTP ${response.code} (end of gallery)")
        }
        val items = FapelloParser.parseCreatorMedia(response.body!!, slug, base)
        return PageResult(items, page, items.isNotEmpty(), "page $page → ${items.size} items")
    }

    /**
     * Every media of a creator: walks /ajax/model/{slug}/page-N/ until a page
     * comes back empty or errors (that is how the site signals "no more").
     */
    public fun creatorMediaAll(
        slug: String,
        maxPages: Int = Int.MAX_VALUE,
        onPage: ((page: Int, found: Int, total: Int) -> Unit)? = null
    ): List<MediaItem> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<MediaItem>()
        var page = 1
        while (page <= maxPages) {
            val result = creatorMedia(slug, page)
            var fresh = 0
            for (item in result.items) {
                if (seen.add(item.id)) {
                    out.add(item)
                    fresh++
                }
            }
            onPage?.invoke(page, fresh, out.size)
            if (!result.hasMore || fresh == 0) break
            page++
        }
        return out
    }

    /**
     * Walk the creator feeds and pull every creator we can see, up to
     * [maxFeedPages] listing pages. Used by the site-wide sweep.
     */
    public fun discoverCreators(
        kind: FeedKind = FeedKind.NEW,
        startPage: Int = 1,
        maxFeedPages: Int = 10,
        onPage: ((page: Int, found: Int, total: Int) -> Unit)? = null
    ): List<CreatorRef> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<CreatorRef>()
        var page = startPage
        var fetched = 0
        while (fetched < maxFeedPages) {
            fetched++
            val result = feed(kind, page)
            var fresh = 0
            for (creator in result.items) {
                if (seen.add(creator.slug)) {
                    out.add(creator)
                    fresh++
                }
            }
            onPage?.invoke(page, fresh, out.size)
            if (!result.hasMore || fresh == 0) break
            page++
        }
        return out
    }

    // ---------------------------------------------------------------- items --

    /** Fetch the item page when needed (videos hide their file there). */
    public fun resolve(item: MediaItem): MediaItem {
        if (item.directUrl != null) return item
        val response = get(item.pageUrl)
        if (!response.isSuccessful) return item
        return FapelloParser.parseMediaItemPage(response.body.orEmpty(), item, base)
    }

    public fun resolveAll(items: List<MediaItem>, onEach: ((Int, Int) -> Unit)? = null): List<MediaItem> {
        val out = ArrayList<MediaItem>(items.size)
        items.forEachIndexed { index, item ->
            out.add(resolve(item))
            onEach?.invoke(index + 1, items.size)
        }
        return out
    }

    // -------------------------------------------------------------- search --

    /**
     * The site's own search (the endpoint its results page calls with
     * `fetch`): creators matching [query], offset-paged, 30 at a time.
     */
    public fun searchCreators(query: String, limit: Int = 30, offset: Int = 0): SearchResult<CreatorRef> {
        val q = query.trim()
        if (q.isEmpty()) return SearchResult(emptyList(), 0, false, offset)
        val response = get(FapelloUrls.search(base, q, "models", limit, offset), ajax = true)
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return SearchResult(emptyList(), 0, false, offset)
        }
        return FapelloParser.parseSearchCreators(response.body!!, base)
    }

    /** Same endpoint, user-post results (`type=posts`). */
    public fun searchPosts(query: String, limit: Int = 30, offset: Int = 0): SearchResult<UserPost> {
        val q = query.trim()
        if (q.isEmpty()) return SearchResult(emptyList(), 0, false, offset)
        val response = get(FapelloUrls.search(base, q, "posts", limit, offset), ajax = true)
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return SearchResult(emptyList(), 0, false, offset)
        }
        return FapelloParser.parseSearchPosts(response.body!!, base)
    }

    // ---------------------------------------------------------------- tags --

    /** The /tags/ index: the tags the site currently lists. */
    public fun tags(): List<TagRef> {
        val response = get(FapelloUrls.tags(base))
        if (!response.isSuccessful || response.body.isNullOrBlank()) return emptyList()
        return FapelloParser.parseTags(response.body!!, base)
    }

    /** Tag autocomplete, as the tag pages themselves do it. */
    public fun tagSuggestions(query: String): List<TagRef> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val response = get(FapelloUrls.tagSuggest(base, q), ajax = true)
        if (!response.isSuccessful || response.body.isNullOrBlank()) return emptyList()
        return FapelloParser.parseTagSuggestions(response.body!!)
    }

    /**
     * One page of a tag's media via `/ajax/tags/{slug}/page-N/`. An empty
     * fragment (or the `tags-more:0` marker) is how the site says "no more".
     */
    public fun tagMedia(slug: String, page: Int): PageResult<MediaItem> {
        val url = FapelloUrls.tagAjax(base, slug, page)
        val response = get(url, ajax = true)
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return PageResult(emptyList(), page, false, "page $page → HTTP ${response.code} (end of tag)")
        }
        val items = FapelloParser.parseTagMedia(response.body!!, slug, base)
        val hasMore = items.isNotEmpty() && !response.body!!.contains("tags-more:0")
        return PageResult(items, page, hasMore, "page $page → ${items.size} items")
    }

    /** Every media item under a tag: walks the ajax pages until one ends it. */
    public fun tagMediaAll(
        slug: String,
        maxPages: Int = Int.MAX_VALUE,
        onPage: ((page: Int, found: Int, total: Int) -> Unit)? = null
    ): List<MediaItem> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<MediaItem>()
        var page = 1
        while (page <= maxPages) {
            val result = tagMedia(slug, page)
            var fresh = 0
            for (item in result.items) {
                if (seen.add(item.pageUrl)) {
                    out.add(item)
                    fresh++
                }
            }
            onPage?.invoke(page, fresh, out.size)
            if (!result.hasMore || fresh == 0) break
            page++
        }
        return out
    }

    // ----------------------------------------------------------- user posts --

    public fun userPosts(page: Int): PageResult<UserPost> {
        val url = FapelloUrls.posts(base, page)
        val response = get(url)
        if (!response.isSuccessful || response.body.isNullOrBlank()) {
            return PageResult(emptyList(), page, false, "HTTP ${response.code}")
        }
        val items = FapelloParser.parseUserPosts(response.body!!, base)
        return PageResult(items, page, items.isNotEmpty(), "page $page → ${items.size} posts")
    }

    public fun userPostMedia(post: UserPost): List<MediaItem> {
        val response = get(post.url)
        if (!response.isSuccessful || response.body.isNullOrBlank()) return emptyList()
        return FapelloParser.parseUserPostPage(response.body!!, post.url, base, post.id)
    }

    /** Walk /posts/, /posts-2/ … */
    public fun userPostsAll(
        startPage: Int = 1,
        maxPages: Int = Int.MAX_VALUE,
        onPage: ((page: Int, found: Int, total: Int) -> Unit)? = null
    ): List<UserPost> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<UserPost>()
        var page = startPage
        while (page < startPage + maxPages) {
            val result = userPosts(page)
            var fresh = 0
            for (post in result.items) {
                if (seen.add(post.id)) {
                    out.add(post)
                    fresh++
                }
            }
            onPage?.invoke(page, fresh, out.size)
            if (!result.hasMore || fresh == 0) break
            page++
        }
        return out
    }

    // ------------------------------------------------------------ url opener --

    public sealed class Opened {
        public data class Creator(val profile: CreatorProfile) : Opened()
        public data class Post(val post: UserPost, val media: List<MediaItem>) : Opened()
        public data class Media(val item: MediaItem) : Opened()
        public data object Unknown : Opened()
    }

    /** Accepts a pasted url or a bare slug and figures out what it points at. */
    public fun open(raw: String): Opened {
        val input = raw.trim()
        if (input.isEmpty()) return Opened.Unknown

        val candidate = when {
            input.startsWith("http", ignoreCase = true) -> input
            input.startsWith("//") -> "https:$input"
            FapelloUrls.isCreatorSlug(input) -> FapelloUrls.creator(base, input)
            else -> "$base/${input.trim('/')}/"
        }

        val postMatch = FapelloUrls.postLink(base).find(candidate)
        if (postMatch != null) {
            val post = UserPost(
                id = postMatch.groupValues[1],
                slug = postMatch.groupValues[2],
                title = FapelloUrls.prettyName(postMatch.groupValues[2]),
                url = FapelloUrls.post(base, postMatch.groupValues[1], postMatch.groupValues[2])
            )
            return Opened.Post(post, userPostMedia(post))
        }

        val creatorMatch = FapelloUrls.CREATOR_LINK.matchEntire(candidate)
        if (creatorMatch != null && FapelloUrls.sameHost(base, candidate) && FapelloUrls.isCreatorSlug(creatorMatch.groupValues[1])) {
            val slug = creatorMatch.groupValues[1]
            return try {
                Opened.Creator(creator(slug))
            } catch (e: Exception) {
                Opened.Unknown
            }
        }

        val mediaMatch = Regex(
            """^https?://(?:[a-z0-9.-]*\.)?${Regex.escape(FapelloUrls.host(base))}/([^/?#]+)/(\d+)/?$""",
            RegexOption.IGNORE_CASE
        ).find(candidate)
        if (mediaMatch != null) {
            val slug = mediaMatch.groupValues[1]
            val id = mediaMatch.groupValues[2]
            val item = MediaItem(id = id, pageUrl = candidate, kind = MediaKind.IMAGE, owner = slug)
            return Opened.Media(resolve(item))
        }
        return Opened.Unknown
    }

    // ----------------------------------------------------------- diagnostics --

    /** Used by the app's Self-test screen: one live probe per endpoint. */
    public fun diagnostics(): List<Diagnostic> {
        val out = ArrayList<Diagnostic>()

        fun probe(name: String, url: String, ajax: Boolean = false, extract: (String) -> String) {
            val status = try {
                val response = get(url, ajax)
                if (response.isSuccessful) {
                    val detail = extract(response.body.orEmpty())
                    "OK · $detail"
                } else {
                    "HTTP ${response.code}"
                }
            } catch (e: Exception) {
                "ERROR · ${e.message}"
            }
            out.add(Diagnostic(name, url, if (status.startsWith("OK")) "OK" else "FAIL", status))
        }

        probe("Feed (ajax, new) · /ajax/index/page-1/", FapelloUrls.ajaxFeed(base, FeedKind.NEW, 1), true) { html ->
            "${FapelloParser.parseFeed(html, base).size} creators"
        }
        probe("Feed (html) · /", FapelloUrls.home(base, 1)) { html ->
            "${FapelloParser.parseFeed(html, base).size} creators"
        }
        probe("Feed (ajax, hot) · /ajax/hot/page-1/", FapelloUrls.ajaxFeed(base, FeedKind.HOT, 1), true) { html ->
            "${FapelloParser.parseFeed(html, base).size} creators"
        }
        probe("Feed (ajax, popular) · /ajax/popular/page-1/", FapelloUrls.ajaxFeed(base, FeedKind.POPULAR, 1), true) { html ->
            "${FapelloParser.parseFeed(html, base).size} creators"
        }

        val firstSlug = try {
            FapelloParser.parseFeed(get(FapelloUrls.ajaxFeed(base, FeedKind.NEW, 1), true).body.orEmpty(), base)
                .firstOrNull()?.slug
        } catch (e: Exception) {
            null
        }

        if (firstSlug != null) {
            probe("Creator page · /$firstSlug/", FapelloUrls.creator(base, firstSlug)) { html ->
                val profile = FapelloParser.parseCreatorPage(html, firstSlug, base)
                "${profile.creator.name} · ${profile.media.size} media · ${profile.creator.mediaCount ?: "?"} reported"
            }
            probe("Creator media · /ajax/model/$firstSlug/page-1/", FapelloUrls.creatorAjax(base, firstSlug, 1), true) { html ->
                val items = FapelloParser.parseCreatorMedia(html, firstSlug, base)
                "${items.size} items · ${items.count { it.isVideo }} video"
            }
            val firstItem = try {
                FapelloParser.parseCreatorMedia(
                    get(FapelloUrls.creatorAjax(base, firstSlug, 1), true).body.orEmpty(), firstSlug, base
                ).firstOrNull()
            } catch (e: Exception) {
                null
            }
            if (firstItem != null) {
                probe("Media item · /$firstSlug/${firstItem.id}/", firstItem.pageUrl) { html ->
                    val resolved = FapelloParser.parseMediaItemPage(html, firstItem, base)
                    "${resolved.kind} · ${resolved.directUrl ?: "unresolved"}"
                }
            }
        }

        probe("Posts feed · /posts/", FapelloUrls.posts(base, 1)) { html ->
            "${FapelloParser.parseUserPosts(html, base).size} posts"
        }
        val firstPost = try {
            FapelloParser.parseUserPosts(get(FapelloUrls.posts(base, 1)).body.orEmpty(), base).firstOrNull()
        } catch (e: Exception) {
            null
        }
        if (firstPost != null) {
            probe("Post detail · /post/${firstPost.id}/${firstPost.slug}/", firstPost.url) { html ->
                "${FapelloParser.parseUserPostPage(html, firstPost.url, base, firstPost.id).size} media"
            }
        }

        probe("Search creators · /search_v2/?ajax=1&type=models", FapelloUrls.search(base, "a", "models", 5, 0), true) { json ->
            val result = FapelloParser.parseSearchCreators(json, base)
            "${result.items.size} of ${result.total} creators"
        }
        probe("Search posts · /search_v2/?ajax=1&type=posts", FapelloUrls.search(base, "a", "posts", 5, 0), true) { json ->
            val result = FapelloParser.parseSearchPosts(json, base)
            "${result.items.size} of ${result.total} posts"
        }
        probe("Tags · /tags/", FapelloUrls.tags(base)) { html ->
            "${FapelloParser.parseTags(html, base).size} tags"
        }
        return out
    }

    public companion object {
        public const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
