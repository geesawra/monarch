# Monarch - Bluesky Client for Android

## Building & Running

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew compileDebugKotlin   # Fast compile check (no full APK)
./gradlew installDebug         # Install on connected device
./gradlew test                 # Run unit tests
```

The worktree may not have `local.properties` — copy it from the main repo root if the build fails with "SDK location not found".

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

## Guidelines

- Use Material 3 / Material You components and patterns
- Don't add comments to code
- Don't edit more than what's explicitly requested
- Compile with `./gradlew compileDebugKotlin` to verify changes
- Reuse existing components — avoid building verticals that are hard to reuse
- Prefer `fromPostView` over `fromPost` when `PostView` data is available
