package com.fap.core

public object ClientTest {

    private fun client(responses: Map<String, RawResponse>): Pair<FapelloClient, FakeHttpClient> {
        val http = FakeHttpClient(responses)
        val client = FapelloClient(
            http = http,
            baseUrl = Fixtures.BASE,
            rateLimiter = RateLimiter(jitterMs = 0L..0L, requestsPerSecond = 1000.0),
            maxRetries = 0
        )
        return client to http
    }

    public fun run() {
        TestHarness.suite("client: feed pagination") {
            val (c, http) = client(
                mapOf(
                    FapelloUrls.ajaxFeed(Fixtures.BASE, FeedKind.NEW, 1) to RawResponse(200, Fixtures.FEED_PAGE_1),
                    FapelloUrls.ajaxFeed(Fixtures.BASE, FeedKind.NEW, 2) to RawResponse(200, Fixtures.FEED_PAGE_2),
                    FapelloUrls.ajaxFeed(Fixtures.BASE, FeedKind.NEW, 3) to RawResponse(500, "<html>nginx</html>")
                )
            )
            val page1 = c.feed(FeedKind.NEW, 1)
            TestHarness.eq("page 1 items", 2, page1.items.size)
            TestHarness.check("page 1 has more", page1.hasMore)
            val page3 = c.feed(FeedKind.NEW, 3)
            TestHarness.check("error page has no more", !page3.hasMore)
            TestHarness.check("error note records the code", page3.note.contains("500")) { page3.note }
            TestHarness.check("three requests made", http.requests.size == 3)
            TestHarness.eq(
                "end of gallery note",
                true,
                c.creatorMedia("noah-cyrus", 9).note.contains("end of gallery")
            )
        }

        TestHarness.suite("client: discover creators") {
            val (c, _) = client(
                mapOf(
                    FapelloUrls.ajaxFeed(Fixtures.BASE, FeedKind.NEW, 1) to RawResponse(200, Fixtures.FEED_PAGE_1),
                    FapelloUrls.ajaxFeed(Fixtures.BASE, FeedKind.NEW, 2) to RawResponse(200, Fixtures.FEED_PAGE_2),
                    FapelloUrls.ajaxFeed(Fixtures.BASE, FeedKind.NEW, 3) to RawResponse(500, "")
                )
            )
            val creators = c.discoverCreators(FeedKind.NEW, 1, 5)
            TestHarness.eq("three unique creators", 3, creators.size)
            TestHarness.check("stops after error page", creators.map { it.slug } == listOf("noah-cyrus", "syuri-sun", "cassie-clarke"))
        }

        TestHarness.suite("client: creator media crawl") {
            val (c, http) = client(
                mapOf(
                    FapelloUrls.creator(Fixtures.BASE, "syuri-sun") to RawResponse(200, Fixtures.CREATOR_PAGE),
                    FapelloUrls.creatorAjax(Fixtures.BASE, "monna-haddid", 1) to RawResponse(200, Fixtures.CREATOR_AJAX_PAGE_1),
                    FapelloUrls.creatorAjax(Fixtures.BASE, "monna-haddid", 2) to RawResponse(200, Fixtures.CREATOR_AJAX_PAGE_2),
                    FapelloUrls.creatorAjax(Fixtures.BASE, "monna-haddid", 3) to RawResponse(500, "")
                )
            )
            val profile = c.creator("syuri-sun")
            TestHarness.eq("profile name", "syuri_sun", profile.creator.name)
            TestHarness.eq("profile media", 4, profile.media.size)

            val all = c.creatorMediaAll("monna-haddid")
            TestHarness.eq("four media across two pages", 4, all.size)
            TestHarness.eq("one video", 1, all.count { it.isVideo })
            TestHarness.check("stopped before page 3 failure", all.map { it.id } == listOf("139", "138", "137", "136"))
            TestHarness.check("page 3 was requested once", http.requests.count { it.contains("page-3") } == 1)
        }

        TestHarness.suite("client: resolve only fetches when needed") {
            val (c, http) = client(
                mapOf(
                    "https://fapello.com/syuri-sun/14/" to RawResponse(200, Fixtures.VIDEO_ITEM_PAGE),
                    "https://fapello.com/syuri-sun/16/" to RawResponse(200, Fixtures.IMAGE_ITEM_PAGE)
                )
            )
            val image = MediaItem(
                id = "16", pageUrl = "https://fapello.com/syuri-sun/16/",
                thumbUrl = "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0016_300px.jpg",
                directUrl = "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0016.jpg",
                owner = "syuri-sun"
            )
            TestHarness.check("no request for a known image", c.resolve(image) === image)
            TestHarness.eq("no http call", 0, http.requests.size)

            val video = MediaItem(
                id = "14", pageUrl = "https://fapello.com/syuri-sun/14/",
                thumbUrl = "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014_300px.jpg",
                kind = MediaKind.VIDEO, owner = "syuri-sun"
            )
            val resolved = c.resolve(video)
            TestHarness.eq("video resolved", "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014.mp4", resolved.directUrl)
            TestHarness.eq("one http call", 1, http.requests.size)
        }

        TestHarness.suite("client: user posts") {
            val (c, _) = client(
                mapOf(
                    FapelloUrls.posts(Fixtures.BASE, 1) to RawResponse(200, Fixtures.POSTS_PAGE),
                    "https://fapello.com/post/64777/yoriko-angeline/" to RawResponse(200, Fixtures.POST_DETAIL_PAGE)
                )
            )
            val page = c.userPosts(1)
            TestHarness.eq("two posts", 2, page.items.size)
            val media = c.userPostMedia(page.items[0])
            TestHarness.eq("post media", 3, media.size)
            TestHarness.check("media urls are full size", media.all { it.directUrl?.contains("_300px") == false })
        }

        TestHarness.suite("client: opening pasted input") {
            val (c, _) = client(
                mapOf(
                    FapelloUrls.creator(Fixtures.BASE, "syuri-sun") to RawResponse(200, Fixtures.CREATOR_PAGE),
                    "https://fapello.com/post/64777/yoriko-angeline/" to RawResponse(200, Fixtures.POST_DETAIL_PAGE),
                    "https://fapello.com/syuri-sun/14/" to RawResponse(200, Fixtures.VIDEO_ITEM_PAGE)
                )
            )
            TestHarness.check("bare slug → creator", c.open("syuri-sun") is FapelloClient.Opened.Creator)
            TestHarness.check("full creator url → creator", c.open("https://fapello.com/syuri-sun/") is FapelloClient.Opened.Creator)
            val post = c.open("https://fapello.com/post/64777/yoriko-angeline/")
            TestHarness.check("post url → post", post is FapelloClient.Opened.Post)
            TestHarness.eq("post media loaded", 3, (post as FapelloClient.Opened.Post).media.size)
            val media = c.open("https://fapello.com/syuri-sun/14/")
            TestHarness.check("media url → media", media is FapelloClient.Opened.Media)
            TestHarness.eq(
                "media resolved",
                "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014.mp4",
                (media as FapelloClient.Opened.Media).item.directUrl
            )
            TestHarness.check("garbage → unknown", c.open("not a url at all!!") is FapelloClient.Opened.Unknown)
        }

        TestHarness.suite("rate limiter") {
            var now = 0L
            var slept = 0L
            val limiter = RateLimiter(
                requestsPerSecond = 2.0,
                jitterMs = 0L..0L,
                sleeper = { slept += it },
                clock = { now }
            )
            limiter.acquire()
            val firstSleep = slept
            now += 100
            limiter.acquire()
            TestHarness.check("second call waits ~half a second at 2 rps", slept - firstSleep >= 300L) {
                "waited ${slept - firstSleep}ms"
            }
            now += 1000
            slept = 0
            limiter.acquire()
            TestHarness.eq("no wait after refill", 0L, slept)
        }
    }
}
