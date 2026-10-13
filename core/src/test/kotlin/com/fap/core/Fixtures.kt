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
}
