package com.fap.core

public object FapelloUrls {

    public const val DEFAULT_BASE: String = "https://fapello.com"

    public fun normaliseBase(raw: String): String {
        var base = raw.trim().trimEnd('/')
        if (base.isBlank()) base = DEFAULT_BASE
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "https://$base"
        return base
    }

    public fun home(base: String, page: Int): String =
        if (page <= 1) "$base/" else "$base/page-$page/"

    public fun ajaxFeed(base: String, kind: FeedKind, page: Int): String =
        "$base/ajax/${kind.path}/page-$page/"

    public fun creator(base: String, slug: String): String = "$base/$slug/"

    public fun creatorAjax(base: String, slug: String, page: Int): String =
        "$base/ajax/model/$slug/page-$page/"

    public fun posts(base: String, page: Int): String =
        if (page <= 1) "$base/posts/" else "$base/posts-$page/"

    public fun post(base: String, id: String, slug: String): String = "$base/post/$id/$slug/"

    /** Resolve an href found in a page to an absolute url. */
    public fun absolute(base: String, href: String): String {
        val h = href.trim()
        if (h.isBlank()) return ""
        return when {
            h.startsWith("http://", true) || h.startsWith("https://", true) -> h
            h.startsWith("//") -> "https:$h"
            h.startsWith("/") -> base + h
            h.startsWith("#") || h.startsWith("javascript:", true) || h.startsWith("mailto:", true) -> ""
            else -> "$base/$h"
        }
    }

    /** "https://host/" for a page url, used to resolve relative links. */
    public fun origin(url: String): String {
        val withoutScheme = url.substringAfter("://", "")
        val host = withoutScheme.substringBefore('/')
        val scheme = if (url.startsWith("https://")) "https" else "http"
        return if (host.isEmpty()) DEFAULT_BASE else "$scheme://$host"
    }

    private val RESERVED = setOf(
        "posts", "post", "hot", "popular", "video", "forum", "signup", "login", "logout",
        "upload", "submit", "search", "ajax", "content", "data", "assets", "page",
        "models", "categories", "category", "tag", "tags", "top", "new", "about",
        "contact", "dmca", "terms", "privacy", "a", "c", "p", "u", "user", "users"
    )

    /** /some-slug/ is a creator page; anything reserved is not. */
    public fun isCreatorSlug(slug: String): Boolean =
        slug.isNotBlank() && slug !in RESERVED && slug.length <= 64 &&
            slug.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } &&
            !slug.startsWith("page-")

    /** Host part of an absolute url, lowercase, without "www.". */
    public fun host(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        return withoutScheme.substringBefore('/').substringBefore(':').lowercase().removePrefix("www.")
    }

    /** True when [url] points at the same site as [base] (or a language subdomain of it). */
    public fun sameHost(base: String, url: String): Boolean {
        val target = host(base)
        val other = host(url)
        if (target.isEmpty() || other.isEmpty()) return false
        return other == target || other.endsWith(".$target")
    }

    /** Matches https://host/{slug}/ - a creator profile link. */
    public val CREATOR_LINK: Regex = Regex("""^https?://[^/]+/([^/?#]+)/?$""")

    /** Matches https://host/{slug}/{id}/ - one media page inside a creator. */
    public fun creatorMediaLink(base: String, slug: String): Regex =
        Regex("""^https?://(?:[a-z0-9.-]*\.)?${Regex.escape(host(base))}/${Regex.escape(slug)}/(\d+)/?$""", RegexOption.IGNORE_CASE)

    /** Matches https://host/post/{id}/{slug}/ - a user submitted post. */
    public fun postLink(base: String): Regex =
        Regex("""^https?://(?:[a-z0-9.-]*\.)?${Regex.escape(host(base))}/post/(\d+)/([^/?#]+)/?""", RegexOption.IGNORE_CASE)

    public fun prettyName(slug: String): String {
        // "agatha-vega-4" -> "Agatha Vega" (trailing index dropped),
        // "noah-cyrus"   -> "Noah Cyrus" (nothing dropped).
        val parts = slug.split('-', '_', '.').filter { it.isNotBlank() }
        val core = if (parts.size > 1 && parts.last().all { it.isDigit() }) parts.dropLast(1) else parts
        return core.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            .ifBlank { slug }
    }
}
