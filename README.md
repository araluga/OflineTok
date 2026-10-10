# OflineTok

A TikTok-style video feed for Android that works **completely offline**: it plays the videos from your own gallery in a random, swipeable feed. The app doesn't even request internet permission.

## Features

- **For You feed**: full-screen vertical swiping through your videos in random order. The feed reshuffles itself endlessly. Tap **Home** again or the shuffle button for a fresh order.
- **Filtered feed**: the switch at the top of Home toggles between **For You** (everything) and your own filter, e.g. **#cats ▾**. Tap the filter tab to choose any mix of tags and gallery folders. Any selected tag matches; if you also pick folders, a video must be in one of them too. The filter is remembered between launches.
- **Like**: tap the heart, or double-tap the video.
- **Favorites**: tap the star.
- **Tags**: tap **#** to tag a video (separate several with commas, e.g. `cats, funny`). Tap a `#tag` under a video to watch everything with that tag.
- **Search by tag**: type to filter tags, then select one or more (videos must have *all* selected tags). There's also an **Untagged** filter for finding videos you haven't tagged yet. **Manage** renames, merges, or deletes tags.
- **Me**: stats plus grids of your Favorites, Liked, and All videos.
- Tap to pause/play. Drag the thin bar at the bottom to seek. Videos loop.

Tags, likes, and favorites are stored in a small JSON file inside the app's private storage. Your video files are never modified.

## Install

1. Open the [Releases](../../releases) page, or the latest run under **Actions** → *Build APK*, and download `OflineTok.apk`.
2. Open it on your phone and allow "Install unknown apps" when asked.
3. Grant access to videos on first launch.

> From v1.1 on, releases are signed with one permanent key, so each new version installs as a normal update and keeps your tags. v1.0 used a temporary key: if you have v1.0, uninstall it once before installing v1.1.

## Build locally

Requirements: JDK 17+ and the Android SDK (API 36).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Tech: Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer, Coil (video thumbnails), MediaStore. Minimum Android 8.0 (API 26).
