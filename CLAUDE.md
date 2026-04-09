# Monarch - Bluesky Client for Android

## Session bootstrap (Claude Code)

**At the very start of every session**, before any other ozone-related work, run this once so SDK source lookups don't waste tokens on repeated unzips:

```bash
mkdir -p /tmp/bluesky-sdk/oauth-src /tmp/bluesky-sdk/new-sources \
  && cd /tmp/bluesky-sdk/new-sources \
  && UNZIP_DISABLE_ZIPBOMB_DETECTION=TRUE unzip -o -q \
       /home/geesawra/AndroidStudio/projects/monarch/libs/ozone-artifacts/sh/christian/ozone/bluesky-jvm/0.3.3-local/bluesky-jvm-0.3.3-local-sources.jar \
  && cd /tmp/bluesky-sdk/oauth-src \
  && UNZIP_DISABLE_ZIPBOMB_DETECTION=TRUE unzip -o -q \
       /home/geesawra/AndroidStudio/projects/monarch/libs/ozone-artifacts/sh/christian/ozone/oauth-jvm/0.3.3-local/oauth-jvm-0.3.3-local-sources.jar
```

After that, grep `/tmp/bluesky-sdk/new-sources/commonMain/` for bluesky / actor / feed / embed sources, and `/tmp/bluesky-sdk/oauth-src/commonMain/` for OAuth + DPoP sources, instead of re-extracting per query. If those directories are missing or stale (e.g. after `OZONE_REF` bump), re-run the snippet above.

## Building & Running

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew compileDebugKotlin   # Fast compile check (no full APK)
./gradlew installDebug         # Install on connected device
./gradlew test                 # Run unit tests
```

The worktree may not have `local.properties` — copy it from the main repo root if the build fails with "SDK location not found".

### Ozone prerequisite (first build + after OZONE_REF bump)

Monarch depends on `sh.christian.ozone:*:0.3.3-local`, a version that **only exists after you run `scripts/publish-ozone-local.sh`**. It is not on Maven Central. Before the first build (and after bumping `OZONE_REF` at the top of the script), run:

```bash
scripts/publish-ozone-local.sh
```

The script clones ozone to `~/.cache/monarch-ozone-src/` (outside the repo), checks out the pinned `OZONE_REF`, generates a throwaway GPG key on first run (cached at `~/.local/share/monarch/ozone-publish-key/`), publishes the four modules Monarch needs (`:bluesky`, `:oauth`, `:api-gen-runtime`, `:api-gen-runtime-internal`) as `0.3.3-local`, and stages the resulting Maven layout into `libs/ozone-artifacts/`. That directory is `.gitignored` and registered as a `maven {}` repo (filtered to the `sh.christian.ozone` group) in `settings.gradle.kts`, so Gradle resolves ozone locally without touching `~/.m2`.

Uses JDK 21 by default — override with `JAVA_HOME=/path/to/jdk21`.

If you skip this step, Gradle will fail loudly on `sh.christian.ozone:bluesky:0.3.3-local` instead of silently picking up the upstream Maven Central `0.3.3` artifact, whose API doesn't match Monarch's source. That loud failure is intentional — the `-local` suffix is the guard against API drift between the pinned ozone ref and whatever is published to Maven Central under `0.3.3`.

To move to a newer ozone commit or a fork, edit `OZONE_REPO` / `OZONE_REF` at the top of the script and rerun it. The clone at `~/.cache/monarch-ozone-src/` is reused and fast-forwarded on subsequent runs.

## Architecture

MVVM with Jetpack Compose. Single-activity app (`MainActivity`) with NavHost navigation.

**Navigation routes** are defined in `ViewList` enum in `MainActivity.kt`: Login, Main, ShowThread, Profile. Navigation uses slide animations. New screens should follow this pattern.

**Dependency injection**: Hilt. `TimelineViewModel` uses assisted injection via `BlueskyConn`.

**State management**: `TimelineUiState` data class in `TimelineViewModel`, exposed as `mutableStateOf`. All UI state lives here — profile, search, timeline, notifications.

## Tech Stack

| Concern | Library | Version |
|---------|---------|---------|
| UI | Jetpack Compose + Material 3 | BOM 2025.10.01, M3 1.5.0-alpha07 |
| Networking | Ktor (OkHttp engine) | 3.3.1 |
| Bluesky SDK | sh.christian.ozone:bluesky | 0.3.3 |
| Images | Coil 3 | 3.3.0 |
| Video | Media3 ExoPlayer | 1.8.0 |
| DI | Hilt | 2.57.2 |
| Zoomable images | Telephoto | 0.18.0 |
| Serialization | KotlinX Serialization JSON | 1.9.0 |
| Data persistence | DataStore Preferences | 1.1.7 |

## Project Structure

```
app/src/main/java/industries/geesawra/monarch/
├── MainActivity.kt              # Entry point, NavHost, Coil setup
├── MainView.kt                  # Main screen: tabs, top bar, bottom nav, feeds drawer
├── LoginView.kt                 # Authentication screen
├── ThreadView.kt                # Post thread viewer
├── ProfileView.kt               # Profile viewer + editor
├── SearchView.kt                # Search tab (posts + people)
├── SkeetView.kt                 # Single post rendering (reused everywhere)
├── ShowSkeets.kt                # LazyColumn of posts with thread context
├── ComposeView.kt               # Post composition bottom sheet
├── NotificationsView.kt         # Notifications tab
├── TimelinePostActionsView.kt   # Like/repost/reply/share buttons
├── PostImageGallery.kt          # Image grid display
├── GalleryViewer.kt             # Full-screen zoomable image viewer
├── ConditionalCard.kt           # Card wrapper utility
├── LikeRepostRowView.kt         # Avatar row for grouped notifications
├── VectorImages.kt              # Custom vector icons
├── datalayer/
│   ├── Bluesky.kt               # API client (BlueskyConn), all network calls
│   ├── TimelineViewModel.kt     # ViewModel, all UI state, business logic
│   ├── Models.kt                # SkeetData, Notification, ThreadPost, etc.
│   ├── LinkPreview.kt           # OpenGraph metadata fetching
│   └── Compressor.kt            # Image compression for uploads
├── ui/theme/
│   ├── Theme.kt                 # Material 3 dynamic color theme
│   ├── Color.kt                 # Color definitions
│   └── Type.kt                  # Typography scale
└── thirdpartyforks/             # Forked third-party compose libraries
```

## Key Patterns

### API Layer (Bluesky.kt)

All API methods follow this pattern:
```kotlin
suspend fun someMethod(): Result<T> {
    return runCatching {
        create().onFailure {
            return Result.failure(LoginException(it.message))
        }
        val ret = client!!.someXrpcCall(params)
        return when (ret) {
            is AtpResponse.Failure<*> -> Result.failure(Exception("..."))
            is AtpResponse.Success<T> -> Result.success(ret.response)
        }
    }
}
```

`create()` initializes the authenticated client from DataStore if not already set. Always call it first.

### ViewModel (TimelineViewModel.kt)

Methods that update UI launch coroutines via `viewModelScope.launch` and update `uiState` via `copy()`. Error handling distinguishes `LoginException` (auth failure → redirect to login) from general errors (show snackbar).

### Post Data (Models.kt)

`SkeetData` is the universal post model. Three factory methods:
- `fromFeedViewPost()` — timeline items (has resolved embeds)
- `fromPostView()` — thread/profile posts (has resolved embeds)
- `fromPost()` — raw record conversion (manually constructs embed URLs via CDN)

**Important**: `fromPostView` should be preferred over `fromPost` when a `PostView` is available, because it uses the already-hydrated `embed` field. `fromPost` manually converts `PostEmbedUnion` → `PostViewEmbedUnion` and doesn't handle all types.

### Composable Reuse

- `SkeetView` is the core post renderer — used in timeline, threads, profiles, search, notifications
- `ShowSkeets` wraps `SkeetView` in a `LazyColumn` with thread context rendering
- `Card` wraps individual posts in both `ShowSkeets` and standalone contexts
- When adding new views that show posts, reuse `SkeetView` + `Card` rather than building custom layouts

### Adding New Tabs

1. Add entry to `TabBarDestinations` enum in `MainView.kt`
2. Add string resource in `res/values/strings.xml`
3. Add `when` cases for: title, navigationIcon, actions, floatingActionButton, and content rendering
4. Create `LazyListState` for the new tab in `InnerTimelineView`

### Adding New Screens (Full Navigation)

1. Add route to `ViewList` enum in `MainActivity.kt`
2. Add `composable(route = ...)` block in the `NavHost`
3. Pass callbacks for navigation (back, thread tap, profile tap, etc.)

## Ozone SDK Notes

The Bluesky SDK (`sh.christian.ozone:bluesky:0.3.3`) provides typed XRPC methods on `BlueskyApi`. Key types:

- `AtpResponse<T>` — sealed: `Success` or `Failure`
- `AtUri`, `Did`, `Cid`, `Handle`, `RKey`, `Nsid` — AT Protocol identifiers
- `ProfileViewDetailed` — full profile with viewer state (following, muted, blocked)
- `PostView` — post with resolved embeds
- `FeedViewPost` — timeline item (post + reason + reply context)
- `ViewerState` — relationship metadata (following, followedBy, muted, blocking)

For record creation, use `BlueskyJson.encodeAsJsonContent()` to convert typed records to `JsonContent` for `createRecord`/`putRecord`.

## Material 3 Design Guidelines

Reference: https://m3.material.io/

### Color System

Five key colors, each with a tonal palette of 13 tones:

| Role | Purpose |
|------|---------|
| **Primary** | Main components, prominent buttons, active states, elevated surface tint |
| **Secondary** | Less prominent components (filter chips), color expression |
| **Tertiary** | Contrasting accents to balance primary/secondary |
| **Surface** | Backgrounds and container surfaces |
| **Error** | Error states and destructive actions |

Color pairing rules — always use matching on-colors:
- `onPrimary` on `primary`, `onPrimaryContainer` on `primaryContainer`
- `onSurface` for high-emphasis text, `onSurfaceVariant` for medium-emphasis
- Never mix incompatible pairs (e.g. `tertiaryContainer` + `primaryContainer`)

Dynamic color (Android 12+): use `dynamicLightColorScheme()` / `dynamicDarkColorScheme()` with static fallback.

### Typography Scale (15 styles)

| Category | Large | Medium | Small |
|----------|-------|--------|-------|
| **Display** | 57/64sp | 45/52sp | 36/44sp |
| **Headline** | 32/40sp | 28/36sp | 24/32sp |
| **Title** | 22/28sp | 16/24sp, w500 | 14/20sp, w500 |
| **Body** | 16/24sp | 14/20sp | 12/16sp |
| **Label** | 14/20sp, w500 | 12/16sp, w500 | 11/16sp, w500 |

Access via `MaterialTheme.typography.titleLarge`, `.bodyMedium`, etc.

### Shape Scale

| Size | Corner Radius |
|------|--------------|
| Extra Small | 4.dp |
| Small | 8.dp |
| Medium | 12.dp |
| Large | 16.dp |
| Extra Large | 24.dp |

Access via `MaterialTheme.shapes.medium`, etc. Also: `RectangleShape`, `CircleShape`.

### Elevation

M3 uses **tonal color overlays** (surface tint) instead of shadows. Use `tonalElevation` for visual hierarchy, `shadowElevation` sparingly for floating elements.

### Button Emphasis Hierarchy (high → low)

1. `ExtendedFloatingActionButton` / `FloatingActionButton` — highest emphasis
2. `Button` (filled) — high emphasis
3. `FilledTonalButton` — medium-high
4. `OutlinedButton` — medium
5. `TextButton` — low emphasis

### Navigation by Screen Size

| Device | Component |
|--------|-----------|
| Compact (phone) | `NavigationBar` (bottom, ≤5 destinations) |
| Medium (tablet landscape) | `NavigationRail` (side) |
| Large (tablet/desktop) | `PermanentNavigationDrawer` |

### Component Color Customization

Use `*Defaults` objects: `CardDefaults.cardColors()`, `ButtonDefaults.buttonColors()`, `CardDefaults.cardElevation()`, etc.

### Key Principles

- Tonal palettes ensure accessible contrast automatically
- Support both light and dark themes
- Use `MaterialTheme.colorScheme.*` for all colors — avoid hardcoded values
- Pair container colors with their on-container counterparts
- Use typography scale roles semantically (display for hero text, body for content, label for buttons)

## Guidelines

- Use Material 3 / Material You components and patterns
- Don't add comments to code
- Don't edit more than what's explicitly requested
- Compile with `./gradlew compileDebugKotlin` to verify changes
- Reuse existing components — avoid building verticals that are hard to reuse
- When the user sends "pp" as the sole message, commit all changes and push to main
- Prefer `fromPostView` over `fromPost` when `PostView` data is available
