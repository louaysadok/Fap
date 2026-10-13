package com.fap.core

import com.fap.core.html.Element
import com.fap.core.html.Html

/**
 * Every selector in one place.
 *
 * The site is a Tailwind soup, so nothing here relies on a single fragile class
 * name alone: each field has a primary extraction path plus one or two
 * fallbacks, and the app ships a diagnostics screen that reports which path
 * actually fired so a layout change can be fixed in minutes.
 */
public object FapelloParser {

    private val THUMB_SUFFIX = Regex("_300px(?=\\.[A-Za-z0-9]+$)")
    private val IMAGE_EXT = Regex("\\.(jpe?g|png|webp|gif|avif|bmp)$", RegexOption.IGNORE_CASE)
    private val VIDEO_EXT = Regex("\\.(mp4|webm|mov|m4v|mkv)$", RegexOption.IGNORE_CASE)
    private val MP4_IN_HTML = Regex("""https?://[^"'\s<>]+?\.(?:mp4|webm)(?:\?[^"'\s<>]*)?""")
    // Only the header style counters ("14 Media", "2 Likes"). The "+6 photos"
    // badges on feed cards count *extra* media and are deliberately ignored.
    private val COUNTER = Regex("""([\d][\d.,]*)\s*(Media|Likes?)(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    private val POSTED_BY = Regex("""(?im)^[ \t]*Posted by[ \t]+(.+?)[ \t]*$""")
    private val BLOCKED_TEXT = setOf("select your language", "see all content", "see post", "follow free")

    /** Thumbnail (…_300px.jpg) -> full size (….jpg). */
    public fun fullSize(url: String?): String? =
        url?.let { THUMB_SUFFIX.replace(it, "") }

    /** Some galleries also exist at /2000/; returns best-first candidates. */
    public fun sizeCandidates(url: String?): List<String> {
        val full = fullSize(url) ?: return emptyList()
        val bigger = full.replace("/1000/", "/2000/")
        return if (bigger != full) listOf(bigger, full) else listOf(full)
    }

    public fun isImageUrl(url: String): Boolean = IMAGE_EXT.containsMatchIn(url.substringBefore('?'))
    public fun isVideoUrl(url: String): Boolean = VIDEO_EXT.containsMatchIn(url.substringBefore('?'))
    public fun isMediaUrl(url: String): Boolean = isImageUrl(url) || isVideoUrl(url)

    // ---------------------------------------------------------------- feed --

    /** Home / hot / popular / ajax feed pages: a list of creator cards. */
    public fun parseFeed(html: String, base: String): List<CreatorRef> {
        val doc = Html.parse(html)
        val anchorsBySlug = LinkedHashMap<String, MutableList<Element>>()

        for (a in doc.select("a[href]")) {
            val href = FapelloUrls.absolute(base, a.attr("href") ?: continue)
            if (href.isEmpty()) continue
            if (!FapelloUrls.sameHost(base, href)) continue
            val match = FapelloUrls.CREATOR_LINK.matchEntire(href) ?: continue
            val slug = match.groupValues[1]
            if (!FapelloUrls.isCreatorSlug(slug)) continue
            anchorsBySlug.getOrPut(slug) { ArrayList() }.add(a)
        }

        val out = ArrayList<CreatorRef>(anchorsBySlug.size)
        for ((slug, anchors) in anchorsBySlug) {
            val first = anchors.first()
            val card = first.closest(10) { it.hasClass("bg-white") }
                ?: first.closest(10) { it.select("img").size >= 2 }
                ?: first

            out.add(
                CreatorRef(
                    slug = slug,
                    name = pickName(anchors, slug),
                    url = FapelloUrls.creator(base, slug),
                    avatarUrl = card.select("img[src]").asSequence()
                        .mapNotNull { FapelloUrls.absolute(base, it.attr("src") ?: return@mapNotNull null) }
                        .firstOrNull { it.contains("/content/") && isImageUrl(it) },
                    coverUrl = card.select("img[src]").asSequence()
                        .mapNotNull { FapelloUrls.absolute(base, it.attr("src") ?: return@mapNotNull null) }
                        .filter { it.contains("/content/") && isImageUrl(it) }
                        .map { fullSize(it)!! }
                        .firstOrNull(),
                    thumbnails = feedThumbnails(card, base),
                    mediaCount = counter(card.text(), "media"),
                    likes = counter(card.text(), "like")
                )
            )
        }
        return out
    }

    private fun pickName(anchors: List<Element>, slug: String): String {
        val candidates = anchors.mapNotNull { anchor ->
            val span = anchor.select("span").firstOrNull()?.cleanText()?.takeIf { it.isNotBlank() }
            val own = anchor.cleanText().takeIf { it.isNotBlank() }
            (span ?: own)?.takeIf { isPlausibleName(it) }
        }
        return candidates.firstOrNull() ?: FapelloUrls.prettyName(slug)
    }

    private fun isPlausibleName(value: String): Boolean {
        val lower = value.lowercase()
        if (BLOCKED_TEXT.any { lower.startsWith(it) }) return false
        if (value.length > 60) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun feedThumbnails(card: Element, base: String): List<String> {
        val out = LinkedHashSet<String>()
        val preferred = card.select("img.img_feed")
        val source = if (preferred.isNotEmpty()) preferred else card.select("img[src]")
        for (img in source) {
            val src = img.attr("src") ?: continue
            val abs = FapelloUrls.absolute(base, src)
            if (abs.isEmpty()) continue
            if (!abs.contains("/content/")) continue
            if (abs.contains("icon-play")) continue
            if (!isImageUrl(abs)) continue
            out.add(if (img.hasClass("img_feed")) abs else fullSize(abs) ?: abs)
        }
        return out.toList()
    }

    // ------------------------------------------------------------- creator --

    public fun parseCreatorPage(html: String, slug: String, base: String): CreatorProfile {
        val doc = Html.parse(html)
        val text = doc.text()

        val name = doc.select("h1, h2").map { it.cleanText() }
            .firstOrNull { it.isNotBlank() && !it.equals("Select your language", ignoreCase = true) && it.length < 80 }
            ?: doc.title()?.substringBefore(" - ")?.trim()?.takeIf { it.isNotBlank() }
            ?: FapelloUrls.prettyName(slug)

        val altNames = doc.select("p").map { it.cleanText() }
            .firstOrNull { it.length in 3..120 && !it.contains("cookie", ignoreCase = true) }

        val avatar = doc.select("img[src]").asSequence()
            .mapNotNull { FapelloUrls.absolute(base, it.attr("src") ?: return@mapNotNull null) }
            .firstOrNull { it.contains("/content/") && isImageUrl(it) }

        val media = parseCreatorMedia(html, slug, base)
        val reported = counter(text, "media")

        val creator = CreatorRef(
            slug = slug,
            name = name,
            url = FapelloUrls.creator(base, slug),
            avatarUrl = avatar,
            coverUrl = media.firstOrNull()?.thumbUrl?.let { fullSize(it) },
            thumbnails = media.take(6).mapNotNull { it.thumbUrl },
            mediaCount = reported ?: media.size,
            likes = counter(text, "like")
        )
        return CreatorProfile(creator, altNames, media, reported)
    }

    /**
     * Media grid. Works both on the profile page (the #content block) and on the
     * /ajax/model/{slug}/page-N/ fragments, because both use the same markup:
     * <a href="/{slug}/{id}/"><div><img …></div><img src=…/icon-play.svg></a>
     */
    public fun parseCreatorMedia(html: String, slug: String, base: String): List<MediaItem> {
        val doc = Html.parse(html)
        val root = doc.first("#content") ?: doc
        val link = FapelloUrls.creatorMediaLink(base, slug)
        val seen = LinkedHashSet<String>()
        val out = ArrayList<MediaItem>()

        for (a in root.select("a[href]")) {
            val href = FapelloUrls.absolute(base, a.attr("href") ?: continue)
            if (href.isEmpty()) continue
            val match = link.matchEntire(href) ?: continue
            val id = match.groupValues[1]
            if (!seen.add(id)) continue

            val images = a.select("img[src]").mapNotNull { FapelloUrls.absolute(base, it.attr("src") ?: return@mapNotNull null) }
            val isVideo = images.any { it.contains("icon-play") }
            val thumb = images.firstOrNull { it.contains("/content/") && isImageUrl(it) && !it.contains("icon-play") }

            out.add(
                MediaItem(
                    id = id,
                    pageUrl = href,
                    thumbUrl = thumb,
                    directUrl = if (isVideo) null else thumb?.let { fullSize(it) },
                    kind = if (isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
                    title = a.select("img[alt]").firstOrNull()?.attr("alt")?.takeIf { it.isNotBlank() },
                    owner = slug
                )
            )
        }
        return out
    }

    /**
     * Resolve one item page. Images are normally already known from the grid
     * (the grid thumb is the full size url minus "_300px"), so this is mostly
     * needed for videos, where the real file only exists on the item page.
     */
    public fun parseMediaItemPage(html: String, fallback: MediaItem, base: String): MediaItem {
        val doc = Html.parse(html)

        val video = doc.select("source[src]").asSequence()
            .mapNotNull { FapelloUrls.absolute(base, it.attr("src") ?: return@mapNotNull null) }
            .firstOrNull { isVideoUrl(it) }
            ?: doc.select("video[src]").asSequence()
                .mapNotNull { FapelloUrls.absolute(base, it.attr("src") ?: return@mapNotNull null) }
                .firstOrNull { isVideoUrl(it) }
            ?: MP4_IN_HTML.find(html)?.value

        if (video != null) {
            return fallback.copy(directUrl = video, kind = MediaKind.VIDEO)
        }

        val image = doc.metaContent("og:image")?.let { FapelloUrls.absolute(base, it) }
            ?.takeIf { isImageUrl(it) }
            ?: doc.select("img[src]").asSequence()
                .mapNotNull { FapelloUrls.absolute(base, it.attr("src") ?: return@mapNotNull null) }
                .filter { it.contains("/content/") && isImageUrl(it) && !it.contains("icon-play") }
                .map { fullSize(it)!! }
                .firstOrNull()

        return fallback.copy(directUrl = image ?: fallback.directUrl, kind = fallback.kind)
    }

    // ----------------------------------------------------------- user posts --

    /** /posts/ and /posts-N/ listing pages. */
    public fun parseUserPosts(html: String, base: String): List<UserPost> {
        val doc = Html.parse(html)
        val anchorsById = LinkedHashMap<String, MutableList<Element>>()

        for (a in doc.select("a[href]")) {
            val href = FapelloUrls.absolute(base, a.attr("href") ?: continue)
            if (href.isEmpty()) continue
            if (!FapelloUrls.sameHost(base, href)) continue
            val match = FapelloUrls.postLink(base).find(href) ?: continue
            anchorsById.getOrPut(match.groupValues[1]) { ArrayList() }.add(a)
        }

        val out = ArrayList<UserPost>(anchorsById.size)
        for ((id, anchors) in anchorsById) {
            val first = anchors.first()
            val href = FapelloUrls.absolute(base, first.attr("href") ?: continue)
            val slug = FapelloUrls.postLink(base).find(href)?.groupValues?.get(2) ?: id
            val card = first.closest(10) { it.hasClass("bg-white") }
                ?: first.closest(10) { it.select("img").isNotEmpty() }
                ?: first
            val cardText = card.text()

            val title = anchors.asSequence()
                .map { it.cleanText() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("See Post", ignoreCase = true) && it.length in 1..140 }
                ?: FapelloUrls.prettyName(slug)

            val thumbnails = LinkedHashSet<String>()
            for (img in card.select("img[src]")) {
                val abs = FapelloUrls.absolute(base, img.attr("src") ?: continue)
                if (abs.contains("/data/posts/") && isImageUrl(abs)) thumbnails.add(abs)
            }

            out.add(
                UserPost(
                    id = id,
                    slug = slug,
                    title = title,
                    url = FapelloUrls.post(base, id, slug),
                    thumbnails = thumbnails.toList(),
                    poster = POSTED_BY.find(cardText)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 1..40 },
                    category = card.select("a[href*=/category/]").firstOrNull()?.cleanText()
                        ?: card.select("a[href*=/posts/]").firstOrNull()?.cleanText(),
                    likes = counter(cardText, "like"),
                    mediaCountHint = thumbnails.size.takeIf { it > 0 }
                )
            )
        }
        return out
    }

    /** /post/{id}/{slug}/ detail page: the actual image set. */
    public fun parseUserPostPage(html: String, url: String, base: String, owner: String? = null): List<MediaItem> {
        val doc = Html.parse(html)
        val out = LinkedHashSet<MediaItem>()
        val ownerId = owner ?: FapelloUrls.postLink(base).find(url)?.groupValues?.get(1) ?: "post"

        val videos = LinkedHashSet<String>()
        for (source in doc.select("source[src]")) {
            val abs = FapelloUrls.absolute(base, source.attr("src") ?: continue)
            if (isVideoUrl(abs)) videos.add(abs)
        }
        MP4_IN_HTML.findAll(html).forEach { videos.add(it.value) }

        val fullImages = LinkedHashSet<String>()
        for (link in doc.select("a[href]")) {
            val abs = FapelloUrls.absolute(base, link.attr("href") ?: continue)
            if (abs.contains("/data/posts/") && isImageUrl(abs)) fullImages.add(abs)
        }
        val thumbs = LinkedHashSet<String>()
        for (img in doc.select("img[src]")) {
            val abs = FapelloUrls.absolute(base, img.attr("src") ?: continue)
            if (abs.contains("/data/posts/") && isImageUrl(abs)) {
                thumbs.add(abs)
                fullImages.add(fullSize(abs)!!)
            }
        }

        val thumbList = thumbs.toList()
        fullImages.forEachIndexed { index, direct ->
            out.add(
                MediaItem(
                    id = "${index + 1}",
                    pageUrl = url,
                    thumbUrl = thumbList.getOrNull(index),
                    directUrl = direct,
                    kind = MediaKind.IMAGE,
                    title = null,
                    owner = ownerId
                )
            )
        }
        videos.forEachIndexed { index, direct ->
            out.add(
                MediaItem(
                    id = "v${index + 1}",
                    pageUrl = url,
                    thumbUrl = thumbList.getOrNull(index),
                    directUrl = direct,
                    kind = MediaKind.VIDEO,
                    owner = ownerId
                )
            )
        }
        return out.toList()
    }

    // ------------------------------------------------------------- helpers --

    public fun counter(text: String, key: String): Int? {
        for (match in COUNTER.findAll(text)) {
            val label = match.groupValues[2].lowercase()
            val wanted = when {
                key.equals("media", true) -> label.startsWith("media")
                key.equals("like", true) -> label.startsWith("like")
                else -> label.startsWith(key.lowercase())
            }
            if (wanted) {
                val value = match.groupValues[1].replace(",", "").replace(".", "")
                return value.toIntOrNull()
            }
        }
        return null
    }
}
