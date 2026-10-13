package com.fap.core

/** Search (/search_v2 ajax JSON) and tags endpoints, against real-shaped fixtures. */
public object SearchTagsTest {

    public fun run() {
        TestHarness.suite("json parser") {
            val parsed = JsonParser.parse("""{"a":1,"b":[true,null,"x/y"],"c":{"d":"A"}}""").asJsonObject()
            TestHarness.check("object parsed", parsed != null)
            TestHarness.eq("number", 1, parsed?.jsonInt("a"))
            TestHarness.eq("nested string", "A", parsed?.get("c").asJsonObject()?.jsonString("d"))
            TestHarness.eq("array size", 3, parsed?.jsonList("b")?.size)
            TestHarness.check("malformed -> null", JsonParser.parse("{not json") == null)
            TestHarness.check("trailing garbage -> null", JsonParser.parse("{} extra") == null)
        }

        TestHarness.suite("search: creators") {
            val result = FapelloParser.parseSearchCreators(Fixtures.SEARCH_MODELS_JSON, Fixtures.BASE)
            TestHarness.eq("two creators", 2, result.items.size)
            TestHarness.eq("total is string-typed on the site", 252, result.total)
            TestHarness.check("has more", result.hasMore)
            TestHarness.eq("first slug from url", "payton-ramolla", result.items[0].slug)
            TestHarness.eq("name", "Payton Ramolla", result.items[0].name)
            TestHarness.eq("media count", 2251, result.items[0].mediaCount)
            TestHarness.eq("likes", 331, result.items[0].likes)
            TestHarness.eq(
                "escaped slashes decoded",
                "https://fapello.com/content/p/a/payton-ramolla/1000/payton-ramolla_0027.jpg",
                result.items[0].avatarUrl
            )
            TestHarness.eq("cover is preview", "https://fapello.com/content/p/a/payton-ramolla/3000/payton-ramolla_2261_300px.jpg", result.items[0].coverUrl)
        }

        TestHarness.suite("search: posts") {
            val result = FapelloParser.parseSearchPosts(Fixtures.SEARCH_POSTS_JSON, Fixtures.BASE)
            TestHarness.eq("one post", 1, result.items.size)
            TestHarness.eq("post id", "41324", result.items[0].id)
            TestHarness.eq("post slug", "payton-delulu", result.items[0].slug)
            TestHarness.eq("title", "Payton delulu", result.items[0].title)
            TestHarness.eq("poster", "b690987", result.items[0].poster)
            TestHarness.eq("likes", 4, result.items[0].likes)
            TestHarness.eq(
                "canonical url rebuilt",
                "https://fapello.com/post/41324/payton-delulu/",
                result.items[0].url
            )
            TestHarness.eq("one thumbnail", 1, result.items[0].thumbnails.size)
        }

        TestHarness.suite("search: junk in, empty out") {
            val creators = FapelloParser.parseSearchCreators("not json at all", Fixtures.BASE)
            TestHarness.check("bad json -> no creators", creators.items.isEmpty() && !creators.hasMore)
            val posts = FapelloParser.parseSearchPosts("""{"success":false}""", Fixtures.BASE)
            TestHarness.check("no results key -> no posts", posts.items.isEmpty())
        }

        TestHarness.suite("tags: index + suggest") {
            val tags = FapelloParser.parseTags(Fixtures.TAGS_INDEX_HTML, Fixtures.BASE)
            TestHarness.eq("language duplicates removed", 3, tags.size)
            TestHarness.eq("first tag", TagRef("ballerina", "Ballerina"), tags[0])
            TestHarness.eq("multiword name kept", "Big Tits Pov", tags[1].name)

            val suggestions = FapelloParser.parseTagSuggestions(Fixtures.TAG_SUGGEST_JSON)
            TestHarness.eq("three suggestions", 3, suggestions.size)
            TestHarness.eq("suggestion slug", "ball-gag", suggestions[0].slug)
            TestHarness.eq("suggestion name title-cased", "Ball Gag", suggestions[0].name)
            TestHarness.check("bad json -> no suggestions", FapelloParser.parseTagSuggestions("[").isEmpty())
        }

        TestHarness.suite("tags: media page") {
            val items = FapelloParser.parseTagMedia(Fixtures.TAG_MEDIA_HTML, "ballerina", Fixtures.BASE)
            TestHarness.eq("two items", 2, items.size)
            TestHarness.eq("canonical page url, not the /video/ wrapper", "https://fapello.com/carolina-guerrero/544/", items[0].pageUrl)
            TestHarness.eq("media id", "544", items[0].id)
            TestHarness.eq("owner is the creator", "carolina-guerrero", items[0].owner)
            TestHarness.eq("creator name as title", "Carolina Guerrero", items[0].title)
            TestHarness.eq(
                "thumb kept",
                "https://fapello.com/content/c/a/carolina-guerrero/1000/carolina-guerrero_0544_300px.jpg",
                items[0].thumbUrl
            )
            TestHarness.eq(
                "direct is full size",
                "https://fapello.com/content/c/a/carolina-guerrero/1000/carolina-guerrero_0544.jpg",
                items[0].directUrl
            )
            TestHarness.eq("second item", "https://fapello.com/elisamoder/1/", items[1].pageUrl)
        }

        TestHarness.suite("client: search + tags") {
            val modelsUrl = FapelloUrls.search(Fixtures.BASE, "payton delu", "models", 30, 0)
            TestHarness.eq(
                "search url encodes the query",
                "https://fapello.com/search_v2/?ajax=1&q=payton%20delu&type=models&limit=30&offset=0",
                modelsUrl
            )
            val http = FakeHttpClient(
                mapOf(
                    modelsUrl to RawResponse(200, Fixtures.SEARCH_MODELS_JSON),
                    FapelloUrls.tagAjax(Fixtures.BASE, "ballerina", 1) to RawResponse(200, Fixtures.TAG_MEDIA_HTML),
                    FapelloUrls.tagAjax(Fixtures.BASE, "ballerina", 2) to RawResponse(200, "")
                )
            )
            val client = FapelloClient(http, Fixtures.BASE, RateLimiter(1000.0, 0L..0L), maxRetries = 0)

            val found = client.searchCreators("payton delu")
            TestHarness.eq("client search parses", 2, found.items.size)
            TestHarness.check("blank query makes no request", run {
                val before = http.requests.size
                client.searchCreators("   ")
                http.requests.size == before
            })

            val all = client.tagMediaAll("ballerina")
            TestHarness.eq("tag crawl stops at the empty page", 2, all.size)
            TestHarness.eq("two tag pages requested", 2, http.requests.count { it.contains("/ajax/tags/") })
        }
    }
}
