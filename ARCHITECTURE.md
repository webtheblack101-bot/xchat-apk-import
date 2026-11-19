# X Chat Architecture

## Stack & Architectural Pattern

- **UI Layer**: Jetpack Compose + Material 3 (`XChatTheme`), adaptive navigation shell (`NavigationBar` on compact screens `< 600.dp`, `NavigationRail` on medium/expanded screens `>= 600.dp`) with `enableEdgeToEdge()` and `WindowInsets.safeDrawing`.
- **State Layer (MVVM + UDF)**: ViewModels (`AppSessionViewModel`, `HomeGroupsViewModel`, `DiscoverViewModel`, `BattlesChatsViewModel`, `AdminModerationViewModel`) expose immutable `StateFlow<UiState<T>>` streams using `SharingStarted.WhileSubscribed(5000L)`.
- **Domain Layer**: Pure-Kotlin `HybridRecommendationEngine` implementing candidate eligibility filtering, multi-factor scoring, cold-start weight redistribution, and diversity-aware re-ranking.
- **Data Layer**: Modular repositories (`AuthRepository`, `UserRepository`, `GroupRepository`, `MessagingRepository`, `DiscoveryRepository`, `BattleRepository`, `SafetyNotificationRepository`) wired to Firebase Auth and named Cloud Firestore Enterprise database (`R.string.firestore_database_id`).

## Module Structure

- `com.example.ui.theme` — Obsidian & Crimson palette, bundled Google Fonts (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`), Material 3 shapes.
- `com.example.data.model` — `@Keep`, `@IgnoreExtraProperties`, `@DocumentId`, `@ServerTimestamp` Firestore POJOs and sealed `UiState<T>`.
- `com.example.data.media` — `ImageCompressor` normalizing Photo Picker URIs into bounded JPEG data URIs (`<= 140 KB`) without requiring storage permissions or paid storage buckets.
- `com.example.domain.recommendation` — `HybridRecommendationEngine`.
- `com.example.data.repository` — Real-time `snapshots()` streams with `ServerTimestampBehavior.ESTIMATE` and batched atomic writes.
- `com.example.ui.state` — Scoped ViewModels keyed by `user.uid` inside `XChatRoot` to guarantee zero cross-account state leakage on sign-out/account switch.
- `com.example.ui.screens` & `com.example.ui.navigation` — Feature screens and responsive 5-tab shell.

## Trade-offs & Free-Tier Decisions

- **Live Battles without Video Streaming**: Uses real-time Firestore scoreboards, timers, spectator comments, and non-monetary virtual gifts instead of paid WebRTC/SFU video infrastructure.
- **Compressed Media Data URIs vs. Paid Object Storage**: Keeps initial deployment free-tier compatible without provisioning Blaze-tier Cloud Storage buckets, while using `ActivityResultContracts.PickVisualMedia` for Google Play policy compliance.
- **Client-Side Hybrid Ranking**: Executes in-memory over bounded candidate pools (`limit(60)`), avoiding dedicated ML inference servers while logging telemetry to `recommendation_events` for future offline/batch training.
