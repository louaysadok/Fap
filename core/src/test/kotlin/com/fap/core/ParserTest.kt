package com.fap.core

import com.fap.core.html.Html

public object ParserTest {

    public fun run() {
        TestHarness.suite("html parser") {
            val doc = Html.parse(
                """
                <html><body>
                  <div class="card bg-white" id="first" data-x="1">
                    <a href="/a/b/">Hello &amp; welcome</a>
                    <img src="x.jpg" alt='single quoted'>
                    <p>unclosed paragraph
                    <span class="tag">inner</span>
                  </div>
                  <div class="card"><ul><li>one</li><li>two</li></ul></div>
                  <!-- <div class="card">hidden</div> -->
                </body></html>
                """.trimIndent()
            )

            TestHarness.eq("attributes", "1", doc.first("#first")?.attr("data-x"))
            TestHarness.eq("entity decoding", "Hello & welcome", doc.first("a")?.cleanText())
            TestHarness.check("class token", doc.first(".card")?.hasClass("bg-white") == true)
            TestHarness.eq("tag+class", 2, doc.select("div.card").size)
            TestHarness.eq("descendant", 2, doc.select("div ul li").size)
            TestHarness.eq("child combinator", 2, doc.select("ul > li").size)
            TestHarness.eq("attribute contains", 1, doc.select("a[href*=/a/]").size)
            TestHarness.eq("attribute exact", 1, doc.select("a[href=/a/b/]").size)
            TestHarness.eq("void tag not nested", 1, doc.select("img").size)
            TestHarness.check("single quoted attr", doc.first("img")?.attr("alt") == "single quoted")
            TestHarness.check("comment ignored", doc.select("div.card").none { it.cleanText().contains("hidden") })
            TestHarness.check("unclosed tag recovers", doc.first("span.tag")?.cleanText() == "inner")
        }

        TestHarness.suite("feed parsing") {
            val creators = FapelloParser.parseFeed(Fixtures.FEED_PAGE_1, Fixtures.BASE)
            TestHarness.eq("two creator cards", 2, creators.size)
            TestHarness.eq("first slug", "noah-cyrus", creators[0].slug)
            TestHarness.eq("name from span", "Noah Cyrus", creators[0].name)
            TestHarness.eq("second slug", "syuri-sun", creators[1].slug)
            TestHarness.eq("name", "syuri_sun", creators[1].name)
            TestHarness.eq("absolute url", "https://fapello.com/syuri-sun/", creators[1].url)
            TestHarness.eq("relative cover resolved", "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0005.jpg", creators[1].avatarUrl)
            TestHarness.eq("media counter", 128, creators[0].mediaCount)
            TestHarness.eq("likes counter", 4321, creators[0].likes)
            TestHarness.eq("thumbnails from .img_feed", 3, creators[0].thumbnails.size)
            TestHarness.check("avatar excluded from thumbs", creators[0].thumbnails.none { it.contains("_0099.jpg") })
            TestHarness.check("off-site ad ignored", creators.none { it.slug == "ad" })
        }

        TestHarness.suite("creator page parsing") {
            val profile = FapelloParser.parseCreatorPage(Fixtures.CREATOR_PAGE, "syuri-sun", Fixtures.BASE)
            TestHarness.eq("name", "syuri_sun", profile.creator.name)
            TestHarness.eq("alt names", "Nude Leaks", profile.altNames)
            TestHarness.eq("avatar", "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0005.jpg", profile.creator.avatarUrl)
            TestHarness.eq("reported total", 14, profile.reportedTotal)
            TestHarness.eq("likes", 2, profile.creator.likes)
            TestHarness.eq("media on page", 4, profile.media.size)
            TestHarness.eq("first id", "16", profile.media[0].id)
            TestHarness.eq("image direct url", "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0016.jpg", profile.media[0].directUrl)
            TestHarness.eq("image kind", MediaKind.IMAGE, profile.media[0].kind)
            TestHarness.eq("video detected", MediaKind.VIDEO, profile.media[2].kind)
            TestHarness.check("video has no direct url yet", profile.media[2].directUrl == null)
            TestHarness.check("video still has a thumb", profile.media[2].thumbUrl?.contains("_0014") == true)
            TestHarness.check("profile link not treated as media", profile.media.none { it.pageUrl.endsWith("/syuri-sun/") })
        }

        TestHarness.suite("creator media ajax") {
            val items = FapelloParser.parseCreatorMedia(Fixtures.CREATOR_AJAX_PAGE_1, "monna-haddid", Fixtures.BASE)
            TestHarness.eq("three items", 3, items.size)
            TestHarness.eq("owner set", "monna-haddid", items[0].owner)
            TestHarness.eq("direct url strips _300px", "https://fapello.com/content/m/o/monna-haddid/1000/monna-haddid_0139.jpg", items[0].directUrl)
            TestHarness.eq("third is a video", MediaKind.VIDEO, items[2].kind)
            val deduped = FapelloParser.parseCreatorMedia(
                Fixtures.CREATOR_AJAX_PAGE_1 + "\n" + Fixtures.CREATOR_AJAX_PAGE_1, "monna-haddid", Fixtures.BASE
            )
            TestHarness.eq("duplicates removed", 3, deduped.size)
        }

        TestHarness.suite("media item pages") {
            val video = MediaItem(
                id = "14", pageUrl = "https://fapello.com/syuri-sun/14/",
                thumbUrl = "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014_300px.jpg",
                kind = MediaKind.VIDEO, owner = "syuri-sun"
            )
            val resolvedVideo = FapelloParser.parseMediaItemPage(Fixtures.VIDEO_ITEM_PAGE, video, Fixtures.BASE)
            TestHarness.eq("video source found", "https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014.mp4", resolvedVideo.directUrl)
            TestHarness.eq("still a video", MediaKind.VIDEO, resolvedVideo.kind)

            val image = MediaItem(
                id = "1655", pageUrl = "https://fapello.com/noah-cyrus/1655/",
                thumbUrl = "https://fapello.com/content/n/o/noah-cyrus/2000/noah-cyrus_1655_300px.jpg",
                kind = MediaKind.IMAGE, owner = "noah-cyrus"
            )
            val resolvedImage = FapelloParser.parseMediaItemPage(Fixtures.IMAGE_ITEM_PAGE, image, Fixtures.BASE)
            TestHarness.eq("image from og:image", "https://fapello.com/content/n/o/noah-cyrus/2000/noah-cyrus_1655.jpg", resolvedImage.directUrl)
        }

        TestHarness.suite("user posts parsing") {
            val posts = FapelloParser.parseUserPosts(Fixtures.POSTS_PAGE, Fixtures.BASE)
            TestHarness.eq("two posts", 2, posts.size)
            TestHarness.eq("id", "64777", posts[0].id)
            TestHarness.eq("slug", "yoriko-angeline", posts[0].slug)
            TestHarness.eq("title", "Yoriko Angeline", posts[0].title)
            TestHarness.eq("url", "https://fapello.com/post/64777/yoriko-angeline/", posts[0].url)
            TestHarness.eq("poster", "yelyahhxyx", posts[0].poster)
            TestHarness.eq("likes", 1, posts[0].likes)
            TestHarness.eq("thumbnails", 3, posts[0].thumbnails.size)
            TestHarness.eq("second post likes", 2, posts[1].likes)
            TestHarness.check("'See Post' not used as title", posts.none { it.title.startsWith("See Post") })
        }

        TestHarness.suite("user post detail parsing") {
            val media = FapelloParser.parseUserPostPage(
                Fixtures.POST_DETAIL_PAGE, "https://fapello.com/post/64777/yoriko-angeline/", Fixtures.BASE, "64777"
            )
            TestHarness.eq("three media", 3, media.size)
            TestHarness.eq("full size url", "https://fapello.com/data/posts/000/064/777_yoriko-angeline_2.jpg", media[1].directUrl)
            TestHarness.check("thumb kept", media[1].thumbUrl?.contains("_300px") == true)
            TestHarness.eq("owner", "64777", media[0].owner)
            TestHarness.check("all images", media.all { it.kind == MediaKind.IMAGE })
        }

        TestHarness.suite("url helpers") {
            TestHarness.eq("fullSize", "a/b_0001.jpg", FapelloParser.fullSize("a/b_0001_300px.jpg"))
            TestHarness.eq("fullSize webp", "a/b_0001.webp", FapelloParser.fullSize("a/b_0001_300px.webp"))
            TestHarness.eq("home page 1", "https://fapello.com/", FapelloUrls.home(FapelloUrls.DEFAULT_BASE, 1))
            TestHarness.eq("home page 3", "https://fapello.com/page-3/", FapelloUrls.home(FapelloUrls.DEFAULT_BASE, 3))
            TestHarness.eq("ajax feed", "https://fapello.com/ajax/hot/page-2/", FapelloUrls.ajaxFeed(FapelloUrls.DEFAULT_BASE, FeedKind.HOT, 2))
            TestHarness.eq("ajax model", "https://fapello.com/ajax/model/x-y/page-1/", FapelloUrls.creatorAjax(FapelloUrls.DEFAULT_BASE, "x-y", 1))
            TestHarness.eq("posts page 2", "https://fapello.com/posts-2/", FapelloUrls.posts(FapelloUrls.DEFAULT_BASE, 2))
            TestHarness.check("reserved slug rejected", !FapelloUrls.isCreatorSlug("posts"))
            TestHarness.check("real slug accepted", FapelloUrls.isCreatorSlug("noah-cyrus"))
            TestHarness.check("same host", FapelloUrls.sameHost("https://fapello.com", "https://fapello.com/x/"))
            TestHarness.check("other host rejected", !FapelloUrls.sameHost("https://fapello.com", "https://example.com/ad"))
            TestHarness.check("subdomain allowed", FapelloUrls.sameHost("https://fapello.com", "https://cz.fapello.com/x/"))
            TestHarness.eq("pretty name", "Noah Cyrus", FapelloUrls.prettyName("noah-cyrus"))
        }
    }
}
