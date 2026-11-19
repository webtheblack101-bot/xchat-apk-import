# X Chat

**X Chat** is a native Android social networking, community, real-time messaging, interactive battle, and mindful 18+ dating discovery application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Firebase (Authentication + Cloud Firestore)**.

## Core Features

1. **Unified Social & Opt-In 18+ Dating Identity**
   - Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + `GetGoogleIdOption`) backed by Firebase Auth.
   - 3-step onboarding capturing public social identity, interests/languages, birth-year 18+ verification, city-level location (no GPS), and optional dating discovery.
   - Separate Social Profile, Dating Profile, and Privacy/Notification Preferences; dating discovery can be paused anytime without affecting the social account.

2. **Communities & Groups**
   - Create **public** or **private** groups with custom cover photos via the zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`).
   - Role-based member management (`owner`, `admin`, `moderator`, `member`), pending access approval for private groups, and revocable time-limited invite codes (`XC-*`).

3. **Real-Time Private & Group Messaging**
   - 1:1 direct chats and group conversations synced via Firestore snapshot listeners.
   - Unread counters, read receipts (`readBy`), message soft-deletion, and compressed image sharing.

4. **Hybrid Recommendation Engine (`hybrid-v1.0`)**
   - Combines explicit profile similarity, bidirectional mutual compatibility, cyclical time-of-day affinity, intent/style alignment, availability, behavioral tag weights (`More like this` / `Fewer like this`), and diversity-aware greedy re-ranking.
   - Separate **Dating** (strict 18+ & `datingEnabled == true`) and **Friends & Casual** discovery modes.

5. **Interactive Live Battles**
   - Challenger vs. opponent live rooms with real-time countdown timers, spectator chat, and non-monetary virtual gifts (`Heart +1`, `Fire +3`, `Rose +5`, `Crown +10`) validated against server rules.

6. **Safety, Notifications & Moderation**
   - In-app notifications for messages, likes, mutual matches, and completed battles.
   - Mutual user blocking, abuse reporting (`user`, `group`, `message`, `battle`), and an admin moderation console restricted by verified admin claims.

## Local Build & Verification

- Build Kotlin debug target: `gradle :app:compileDebugKotlin`
- Run unit & Robolectric tests: `gradle :app:testDebugUnitTest`
- Run Firestore security rules tests (with Firestore emulator on `127.0.0.1:8085`):
  `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
