# Fapello Grabber

An Android app that browses and bulk-downloads media from `fapello.com`, built
around **one dropdown: `Creators` or `Posts`**.

That dropdown is not decoration — the site really has two different content
models with two different paginators, and the app is organised around that split:

| Mode | What it browses | Listing | Detail |
|---|---|---|---|
| **Creators** | Model galleries | `/ajax/index/page-N/`, `/ajax/hot/…`, `/ajax/popular/…` | `/{slug}/` → `/ajax/model/{slug}/page-N/` → `/{slug}/{id}/` |
| **Posts** | User submitted posts | `/posts/`, `/posts-2/` … | `/post/{id}/{slug}/` |

Pick a mode at the top of the Browse screen; it drives what gets paged, what
search filters, and what "download all" means. The choice is remembered.

## What it does

- **Creators mode** — infinite grid of creators (New / Hot / Popular), media
  count, follow, tap through to a full media grid with video badges, long-press
  to multi-select, "download all" that crawls *every* page of the gallery.
- **Following ★** — the chip next to the New / Hot / Popular tabs (with the
  count) opens everyone you follow (star on a Creator screen): avatar, name
  and media count, with open, download-all and unfollow per row. It re-reads
  the `favorites` table every time it appears, so it is always current.
- **In-app playback** — videos play inline in the full-screen viewer with
  Media3 ExoPlayer (play/pause, scrub, mute, buffering), resolving the real
  file from the item page first when needed, and the player is released when
  you leave. GIFs animate in place (coil-gif). Playback never opens a
  browser; only the "Open in browser" button on Creator/Post headers does.
- **Posts mode** — infinite feed of user posts (title, poster, likes), tap for
  the full image set, download the whole post in one tap.
- **Search** — filters whatever mode you are in, and doubles as a URL/slug
  opener (paste `https://fapello.com/some-model/`, a bare `some-model`, or a
  post URL and it opens the right screen).
- **Downloads** — queue with progress, pause / resume / cancel / retry,
  Wi-Fi-only, configurable concurrency, live notification, foreground service.
- **Library** — because files are stored app-private (invisible to Gallery),
  the app ships its own viewer over everything you have downloaded.
- **Resume & skip** — the queue lives in SQLite, so a crash or a restart keeps
  it; re-downloading something you already have is skipped by default.
- **Site-wide sweep** — walks the creator feed and crawls every creator it
  finds, in full. Heavy, deliberately slow, and behind a confirmation dialog.
- **Private mode** — hide thumbnails, lock the app behind a PIN.
- **Self-test** — one screen that hits every endpoint and reports *exactly*
  what each parser found.

## Politeness and boundaries

Public pages only, and only for your own personal use:

- Every request goes through a token-bucket rate limiter (default **2 req/s**
  plus a random 250 ms delay), configurable in Settings.
- Retries back off; the crawler stops as soon as a gallery page comes back
  empty instead of hammering.
- No login, no paywall bypass, no age-gate bypass, no re-upload features.
- Most of this media belongs to the people in it. Do not redistribute it.

## Building it

The app is a normal Gradle project: **Kotlin + Jetpack Compose (Material 3)**,
minSdk 26, targetSdk 35.

```bash
./gradlew :core:parserSelfTest   # scraper tests, no network, no emulator
./gradlew :app:assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
```

Or just open the folder in Android Studio and hit Run.

**Do not want to install the SDK?** Every push runs
`.github/workflows/build-apk.yml`, which builds a **debug APK** and attaches it
to the workflow run as an artifact named `fapello-grabber-debug`. Download it,
install it, done (you'll need to allow "install unknown apps" for your browser
or file manager).

## Layout

```
core/   pure Kotlin, ZERO dependencies — the whole scraper, unit tested on a JVM
  html/         tiny dependency-free HTML parser + CSS subset (no jsoup)
  FapelloParser every selector, with a fallback for each field
  FapelloClient one call per screen, rate limited, retrying
  RateLimiter   politeness
app/    Android UI
  data/         settings (DataStore) + SQLite (favourites, library, queue)
  net/          OkHttp plugged into the core's HttpClient interface
  download/     queue, foreground service, notifications, private storage
  ui/           Compose screens + one ViewModel each
```

The core has **no third-party and no Android imports** on purpose: it compiles
and runs on a bare JVM, which is why `./gradlew :core:parserSelfTest` can verify
every parsing rule (103 checks) without a device, an emulator, or network.

## If the site changes

Parsing rules live in one file (`core/…/FapelloParser.kt`) and each field has a
primary path plus a fallback. If grids ever come back empty:

1. Settings → **Self-test**. It fetches one real page per endpoint and prints
   what each parser extracted.
2. Whatever row says `FAIL` tells you which endpoint moved; fix that one
   function.

## Notes

- Files are saved to app-private external storage
  (`Android/data/com.fap.app/files/Fapello/…`): hidden from Gallery and other
  apps, and it needs no storage permission.
- Videos only expose their real file on their own page, so the downloader
  resolves them on demand (images are derived straight from the grid thumbnail,
  which is the full-size URL minus `_300px`).
- The scraper reads whatever host you point it at (Settings → Site host);
  nothing else about it is site-specific beyond the selectors.
