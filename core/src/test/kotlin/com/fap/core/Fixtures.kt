package com.fap.core

/**
 * HTML fixtures shaped like the real pages (same tag nesting, same class names,
 * same url patterns) so the parsers can be tested without hitting the site.
 */
public object Fixtures {

    public const val BASE: String = "https://fapello.com"

    public val FEED_PAGE_1: String = """
<html><head><title>Fapello</title></head>
<body class="bg-gray-100">
<div class="bg-white shadow-md rounded-lg overflow-hidden mb-6">
  <div class="p-2">
    <div class="flex items-center justify-between">
      <a href="https://fapello.com/noah-cyrus/" class="flex items-center">
        <img src="https://fapello.com/content/n/o/noah-cyrus/1000/noah-cyrus_0099.jpg" alt="Noah Cyrus">
      </a>
      <a href="https://fapello.com/noah-cyrus/" class="text-lg font-semibold"><span>Noah Cyrus</span></a>
      <a href="https://fapello.com/signup/" class="text-sm">Follow Free</a>
    </div>
    <div class="grid grid-cols-3 gap-1 mt-2">
      <a href="https://fapello.com/noah-cyrus/1655/"><div><img class="img_feed" src="https://fapello.com/content/n/o/noah-cyrus/2000/noah-cyrus_1655.jpg"></div></a>
      <a href="https://fapello.com/noah-cyrus/1654/"><div><img class="img_feed" src="https://fapello.com/content/n/o/noah-cyrus/2000/noah-cyrus_1654_300px.jpg"></div></a>
      <a href="https://fapello.com/noah-cyrus/1653/"><div><img class="img_feed" src="https://fapello.com/content/n/o/noah-cyrus/2000/noah-cyrus_1653_300px.jpg"></div><img src="https://fapello.com/assets/images/icon-play.svg" class="absolute"></a>
    </div>
    <a href="https://fapello.com/noah-cyrus/" class="block mt-2">See all content of Noah Cyrus</a>
    <div class="flex text-xs text-gray-500"><span>128 Media</span><span>4321 Likes</span></div>
  </div>
</div>
<div class="bg-white shadow-md rounded-lg overflow-hidden mb-6">
  <div class="p-2">
    <div class="flex items-center justify-between">
      <a href="/syuri-sun/" class="flex items-center">
        <img src="/content/s/y/syuri-sun/1000/syuri-sun_0005.jpg" alt="syuri_sun">
      </a>
      <a href="/syuri-sun/" class="text-lg font-semibold"><span>syuri_sun</span></a>
    </div>
    <div class="grid grid-cols-3 gap-1 mt-2">
      <a href="/syuri-sun/16/"><div><img class="img_feed" src="/content/s/y/syuri-sun/1000/syuri-sun_0016_300px.jpg"></div></a>
      <a href="/syuri-sun/14/"><div><img class="img_feed" src="/content/s/y/syuri-sun/1000/syuri-sun_0014_300px.jpg"></div><img src="/assets/images/icon-play.svg"></a>
    </div>
    <a href="/syuri-sun/">See all content of syuri_sun</a>
    <div class="flex text-xs"><span>14 Media</span><span>2 Likes</span></div>
  </div>
</div>
<div class="bg-white ad"><a href="https://example.com/ad"><img src="https://media.livemediahost.com/ad.png"></a><span>AD</span></div>
</body></html>
""".trimIndent()

    public val FEED_PAGE_2: String = """
<html><body>
<div class="bg-white shadow-md mb-6">
  <div class="p-2">
    <div class="flex">
      <a href="https://fapello.com/cassie-clarke/"><img src="https://fapello.com/content/c/a/cassie-clarke/1000/cassie-clarke_0008.jpg"></a>
      <a href="https://fapello.com/cassie-clarke/"><span>Cassie Clarke</span></a>
    </div>
    <div class="grid"><a href="https://fapello.com/cassie-clarke/92/"><div><img class="img_feed" src="https://fapello.com/content/c/a/cassie-clarke/1000/cassie-clarke_0092_300px.jpg"></div></a></div>
  </div>
</div>
</body></html>
""".trimIndent()

    public val CREATOR_PAGE: String = """
<html><head><title>syuri_sun Nude Leaks  - Fapello</title>
<meta property="og:image" content="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0005.jpg">
</head>
<body class="bg-gray-100">
<div class="pro-container mx-auto">
  <div class="flex-col items-center">
    <div class="flex"><div><a href="https://fapello.com/syuri-sun/"><img src="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0005.jpg" alt="syuri_sun"></a></div></div>
    <h2 class="text-2xl font-semibold">syuri_sun</h2>
    <p class="text-sm text-gray-500">Nude Leaks</p>
    <div class="divide-gray-300 divide-transparent divide-x grid grid-cols-2 lg:text-left mt-3 text-center w-full">
      <div><span>14</span><span>Media</span></div>
      <div><span>2</span><span>Likes</span></div>
    </div>
  </div>
  <div id="content" class="grid grid-cols-3 gap-1">
    <a href="https://fapello.com/syuri-sun/16/"><div><img src="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0016_300px.jpg"></div></a>
    <a href="https://fapello.com/syuri-sun/15/"><div><img src="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0015_300px.jpg"></div></a>
    <a href="https://fapello.com/syuri-sun/14/"><div><img src="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014_300px.jpg"></div><img src="https://fapello.com/assets/images/icon-play.svg"></a>
    <a href="https://fapello.com/syuri-sun/13/"><div><img src="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0013_300px.jpg"></div><img src="/assets/images/icon-play.svg"></a>
  </div>
</div>
<div class="bg-white ad"><a href="https://example.com/ad"><img src="https://media.livemediahost.com/ad.png"></a></div>
</body></html>
""".trimIndent()

    public val CREATOR_AJAX_PAGE_1: String = """
<a href="https://fapello.com/monna-haddid/139/"><div><img src="https://fapello.com/content/m/o/monna-haddid/1000/monna-haddid_0139_300px.jpg"></div></a>
<a href="https://fapello.com/monna-haddid/138/"><div><img src="https://fapello.com/content/m/o/monna-haddid/1000/monna-haddid_0138_300px.jpg"></div></a>
<a href="https://fapello.com/monna-haddid/137/"><div><img src="https://fapello.com/content/m/o/monna-haddid/1000/monna-haddid_0137_300px.jpg"></div><img src="https://fapello.com/assets/images/icon-play.svg"></a>
""".trimIndent()

    public val CREATOR_AJAX_PAGE_2: String = """
<a href="https://fapello.com/monna-haddid/136/"><div><img src="https://fapello.com/content/m/o/monna-haddid/1000/monna-haddid_0136_300px.jpg"></div></a>
""".trimIndent()

    public val VIDEO_ITEM_PAGE: String = """
<html><head><title>syuri_sun Nude Leaked Video #14 - Fapello</title>
<meta property="og:image" content="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014_300px.jpg">
</head>
<body>
<div class="pro-container">
  <video class="w-full" controls playsinline poster="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014_300px.jpg">
     <source src="https://fapello.com/content/s/y/syuri-sun/1000/syuri-sun_0014.mp4" type="video/mp4">
  </video>
</div>
</body></html>
""".trimIndent()

    public val IMAGE_ITEM_PAGE: String = """
<html><head><title>Noah Cyrus #1655 - Fapello</title>
<meta property="og:image" content="https://fapello.com/content/n/o/noah-cyrus/2000/noah-cyrus_1655.jpg">
</head><body><div class="pro-container"><img src="https://fapello.com/content/n/o/noah-cyrus/2000/noah-cyrus_1655.jpg"></div></body></html>
""".trimIndent()

    public val POSTS_PAGE: String = """
<html><head><title>Posts - Fapello</title></head><body>
<div class="bg-white rounded mb-4">
  <div class="p-3">
    <div class="flex items-center">
      <img src="https://fapello.com/data/avatars/default/1521276.png">
      <div class="text-sm">Posted by yelyahhxyx</div>
      <div class="text-xs">Celebs</div>
    </div>
    <a href="https://fapello.com/post/64777/yoriko-angeline/" class="font-bold text-lg">Yoriko Angeline</a>
    <div class="grid grid-cols-2 gap-1">
      <a href="https://fapello.com/post/64777/yoriko-angeline/"><img src="https://fapello.com/data/posts/000/064/777_yoriko-angeline_1.jpg"></a>
      <a href="https://fapello.com/post/64777/yoriko-angeline/"><img src="https://fapello.com/data/posts/000/064/777_yoriko-angeline_2_300px.jpg"></a>
      <a href="https://fapello.com/post/64777/yoriko-angeline/"><img src="https://fapello.com/data/posts/000/064/777_yoriko-angeline_3_300px.jpg"></a>
    </div>
    <a href="https://fapello.com/post/64777/yoriko-angeline/">See Post</a>
    <div class="text-xs">1 Like</div>
  </div>
</div>
<div class="bg-white rounded mb-4">
  <div class="p-3">
    <div class="flex items-center"><img src="https://fapello.com/data/avatars/default/1197916.png"><div>Posted by Novato86</div><div>Requests</div></div>
    <a href="https://fapello.com/post/64784/fortaleza/" class="font-bold">Fortaleza</a>
    <div class="grid"><a href="https://fapello.com/post/64784/fortaleza/"><img src="https://fapello.com/data/posts/000/064/784_fortaleza_1_300px.jpg"></a></div>
    <div class="text-xs">2 Likes</div>
  </div>
</div>
</body></html>
""".trimIndent()

    public val POST_DETAIL_PAGE: String = """
<html><head><title>Yoriko Angeline - Fapello</title></head><body>
<div class="max-w-2xl mx-auto">
  <a href="https://fapello.com/data/posts/000/064/777_yoriko-angeline_1.jpg"><img src="https://fapello.com/data/posts/000/064/777_yoriko-angeline_1_300px.jpg" alt="Yoriko Angeline #1"></a>
  <a href="https://fapello.com/data/posts/000/064/777_yoriko-angeline_2.jpg"><img src="https://fapello.com/data/posts/000/064/777_yoriko-angeline_2_300px.jpg" alt="Yoriko Angeline #2"></a>
  <a href="https://fapello.com/data/posts/000/064/777_yoriko-angeline_3.jpg"><img src="https://fapello.com/data/posts/000/064/777_yoriko-angeline_3_300px.jpg" alt="Yoriko Angeline #3"></a>
</div>
</body></html>
""".trimIndent()

    /** Trimmed from a real /search_v2/?ajax=1&q=payton&type=models response (note the \/ escapes). */
    public val SEARCH_MODELS_JSON: String = """
{"success":true,"query":"payton","total":"252","has_more":true,"offset":0,"results":{"models":[{"id":"69972","name":"Payton Ramolla","url":"https:\/\/fapello.com\/payton-ramolla\/","avatar":"https:\/\/fapello.com\/content\/p\/a\/payton-ramolla\/1000\/payton-ramolla_0027.jpg","preview":"https:\/\/fapello.com\/content\/p\/a\/payton-ramolla\/3000\/payton-ramolla_2261_300px.jpg","content_count":2251,"likes":331,"comments":7,"views":1709043},{"id":"661068","name":"Payton Delu Myler","url":"https:\/\/fapello.com\/payton-delu-myler\/","avatar":"https:\/\/fapello.com\/content\/p\/a\/payton-delu-myler\/1000\/payton-delu-myler_0004.jpg","preview":"https:\/\/fapello.com\/content\/p\/a\/payton-delu-myler\/1000\/payton-delu-myler_0007_300px.jpg","content_count":6,"likes":1,"comments":0,"views":872}]},"exec_time":1.5}
""".trimIndent()

    /** Trimmed from a real /search_v2/?ajax=1&q=payton&type=posts response. */
    public val SEARCH_POSTS_JSON: String = """
{"success":true,"query":"payton","total":"18","has_more":true,"offset":0,"results":{"posts":[{"id":"41324","title":"Payton delulu","text":"","url":"https:\/\/fapello.com\/post\/41324\/payton-delulu\/","username":"b690987","preview":"https:\/\/fapello.com\/data\/posts\/000\/041\/324_payton-delulu_1.jpg","likes":4,"comments":2,"date":"2025-10-06 07:04:29"}]},"exec_time":1.44}
""".trimIndent()

    /** Real /ajax/tags/suggest/?q=ball response shape. */
    public val TAG_SUGGEST_JSON: String = """
[{"tag":"ball gag","url":"ball-gag"},{"tag":"ballerina","url":"ballerina"},{"tag":"ballet shoes","url":"ballet-shoes"}]
""".trimIndent()

    /** /tags/ index page shape: hashtag anchors. */
    public val TAGS_INDEX_HTML: String = """
<html><head><title>Tags - Fapello</title></head><body>
<a href="https://fapello.com/tags/">Tags</a>
<div class="tags-list">
  <a href="https://fapello.com/tags/ballerina/">#Ballerina</a>
  <a href="https://fapello.com/tags/big-tits-pov/">#Big Tits Pov</a>
  <a href="https://cz.fapello.com/tags/ballerina/">#Ballerina</a>
  <a href="https://fapello.com/tags/masseur/">#Masseur</a>
</div>
</body></html>
""".trimIndent()

    /** /ajax/tags/ballerina/page-1/ fragment shape: corner link = real content page. */
    public val TAG_MEDIA_HTML: String = """
<div class="mt-6 grid lg:grid-cols-3 grid-cols-2 gap-3">
  <div>
    <div class="bg-yellow-400 max-w-full lg:h-60 h-48 rounded-lg relative overflow-hidden shadow">
      <a href="https://fapello.com/video/ballerina/32361855/">
        <img src="https://fapello.com/content/c/a/carolina-guerrero/1000/carolina-guerrero_0544_300px.jpg" class="w-full h-full absolute object-cover inset-0">
      </a>
      <a href="https://fapello.com/carolina-guerrero/544/" class="tag-corner-link absolute top-2 right-2" title="Open content page"></a>
      <div class="flex flex-1 items-center absolute bottom-0 w-full p-3 text-white">
        <a href="https://fapello.com/carolina-guerrero/"><div><img src="https://fapello.com/content/c/a/carolina-guerrero/1000/carolina-guerrero_0019.jpg" class="rounded-full w-8"></div></a>
        <a href="https://fapello.com/video/ballerina/32361855/" class="lg:flex flex-1 items-center"><div>Carolina Guerrero</div></a>
      </div>
    </div>
  </div>
  <div>
    <div class="bg-yellow-400 max-w-full lg:h-72 h-48 rounded-lg relative overflow-hidden shadow">
      <a href="https://fapello.com/video/ballerina/32351340/">
        <img src="https://fapello.com/content/e/l/elisamoder/1000/elisamoder_0001_300px.jpg" class="w-full h-full absolute object-cover inset-0">
      </a>
      <a href="https://fapello.com/elisamoder/1/" class="tag-corner-link absolute top-2 right-2" title="Open content page"></a>
      <div class="flex flex-1 items-center absolute bottom-0 w-full p-3 text-white">
        <a href="https://fapello.com/elisamoder/"><div><img src="https://fapello.com/content/e/l/elisamoder/1000/elisamoder_0001.jpg" class="rounded-full w-8"></div></a>
        <a href="https://fapello.com/video/ballerina/32351340/" class="lg:flex flex-1 items-center"><div>ElisaModer</div></a>
      </div>
    </div>
  </div>
</div>
""".trimIndent()
}
