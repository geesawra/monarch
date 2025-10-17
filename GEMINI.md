# Gemini Workspace

This document provides instructions for interacting with this project using Gemini.

## Getting Started

This is an Android application written in Kotlin using Jetpack Compose.

### Prerequisites

- Android SDK
- JDK

### Building the Application

To build the application, run the following command:

```bash
./gradlew assembleDebug
```

### Running the Application

To run the application on a connected device or emulator, use the following command:

```bash
./gradlew installDebug
```

### Running Tests

To run the unit tests, use the following command:

```bash
./gradlew test
```

## Key Libraries

This project uses several key libraries:

- **Jetpack Compose:** For building the UI.
- **Ktor:** For networking with the Bluesky API.
- **Coil:** For image loading.
- **Media3:** For video playback.
- **Hilt:** For dependency injection.
- **Telephoto:** For zoomable images.

## Component Overview

### UI Views

- **`ComposeView.kt`**: This is the main composable for creating a new post. It includes a text
  field for the post content, character count, and buttons for attaching media and sending the post.
  It also handles replies and quote posts.
- **`ConditionalCard.kt`**: A simple composable that wraps its content in an `OutlinedCard` only if
  a `wrapWithCard` parameter is true.
- **`GalleryViewer.kt`**: A full-screen image gallery that allows users to view images in a pager.
  It uses the Telephoto library for zoomable images.
- **`LikeRowView.kt`**: A composable that displays a row of avatars of users who have liked a post.
- **`LoginView.kt`**: The login screen for the application. It includes fields for the user's handle
  and password, and it uses a debounce mechanism to look up the user's PDS (Personal Data Server) as
  they type their handle.
- **`MainActivity.kt`**: The main activity of the application. It sets up the navigation graph and
  the main theme.
- **`MainView.kt`**: The main view of the application, which contains the bottom navigation bar, the
  top app bar, and the main content area. It switches between the timeline and notifications views.
- **`NotificationsView.kt`**: This view displays a list of notifications for the user. It can
  display different types of notifications, such as likes, reposts, follows, and mentions.
- **`PostImageGallery.kt`**: A component that displays a gallery of images attached to a post. It
  can display up to four images in a grid layout.
- **`ShowSkeets.kt`**: This composable is responsible for displaying a list of "skeets" (posts) in
  the timeline. It uses a `LazyColumn` to efficiently display a potentially long list of posts.
- **`SkeetView.kt`**: This is the main composable for displaying a single "skeet" (post). It shows
  the author's avatar, name, handle, the content of the post, and any embedded media.
- **`TimelinePostActionsView.kt`**: This component displays the action buttons for a post, such as
  reply, repost, like, and share.

### Data Layer

- **`Bluesky.kt`**: This file contains the `BlueskyConn` class, which is responsible for all
  communication with the Bluesky API. It handles authentication, fetching the timeline, posting, and
  other API interactions.
- **`Compressor.kt`**: This file contains the `Compressor` class, which is responsible for
  compressing images and videos before uploading them to the server.
- **`Models.kt`**: This file defines the data models used in the application, such as `SkeetData`,
  `Notification`, and `TimelineUiState`.
- **`TimelineViewModel.kt`**: This is the ViewModel for the main timeline view. It is responsible
  for fetching the timeline data from the `BlueskyConn` and managing the UI state.

## Gemini Instructions

- Always suggest Material 3 You compliant edits.
- Never edit more than what's being explicitly suggested.
- Leave no comments in the code.
