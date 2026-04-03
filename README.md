# Monarch

A Bluesky client for Android, built because the official app is fine, but I wanted something that feels more like *mine*.

Monarch is a personal project — it's opinionated, a little rough around the edges, and built for how I use Bluesky. It supports multiple accounts, custom appviews (like Blacksky), feed browsing, post composition with images and video, and all the usual social stuff: likes, reposts, replies, threads, notifications, search.

It uses Material You, so it picks up your wallpaper colors and looks different on every phone.

## Installing

Monarch isn't on the Play Store. The easiest way to install it (and keep it updated) is with [Obtanium](https://github.com/ImranR98/Obtainium).

1. Install Obtanium from [GitHub](https://github.com/ImranR98/Obtainium) or F-Droid
2. Open Obtanium, tap **Add App**
3. Paste this URL: `https://monarch.geesawra.industries`
4. Obtanium will find the latest APK and install it
5. Future updates show up automatically — Obtanium checks periodically and lets you know

To make Obtanium's life easier, set `monarch-release-(.+)` as custom APK link filter.

That's it. 

### Manual install

If you'd rather not use Obtanium, go to [monarch.geesawra.industries](http://monarch.geesawra.industries/index.html) in your phone's browser, tap the APK link, and install it. You'll need to allow installs from unknown sources.

## Building from source

```bash
./gradlew assembleDebug
```

You'll need the Android SDK with platform 36 and build-tools 36.1.0. The project uses JDK 21.

## Requirements

Android 16+ (API 36). This is a bleeding-edge target — it runs on Pixel phones with the latest updates and recent Samsung/OnePlus flagships. Older phones won't be able to install it.

## Credits

- App icon uses [Iosevka SS08](https://github.com/be5invis/Iosevka) — [SIL Open Font License 1.1](https://github.com/be5invis/Iosevka/blob/main/LICENSE.md)
- Video player forked from [dealicious-inc/compose-video](https://github.com/dealicious-inc/compose-video) — [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

Vendored libraries in `thirdpartyforks/` are subject to their own license terms.
