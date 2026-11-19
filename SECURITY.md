# X Chat Security & Privacy Design

## Authentication & Session Isolation
- **Google Sign-In via Credential Manager**: Uses `GetSignInWithGoogleOption` and `GetGoogleIdOption` without hardcoded secrets or anonymous login.
- **Session Keying**: Authenticated UI and ViewModels are wrapped in `key(user.uid)` and keyed ViewModel factories so signing out or switching accounts immediately clears previous user streams.

## Privacy-by-Design Controls
- **PII Segregation**: Email, birth year, and notification toggles live under `users/{userId}/private/account`, readable and writable strictly by `isOwner(userId)`.
- **No GPS / Exact Location**: Dating profiles store city-level strings only (`city`), never latitude/longitude coordinates.
- **Strict 18+ Dating Gate**:
  - Onboarding and Profile screens require verified `ageVerified == true` (`birthYear` >= 18 years prior) before enabling dating.
  - `firestore.rules` rejects any `dating_profiles` document with `age < 18`.
  - `HybridRecommendationEngine` enforces `age >= 18` and `datingEnabled == true` during Stage A filtering in `DATING` mode.

## Zero-Trust Firestore Security Rules (`firestore.rules`)
- **Default Deny**: Global `match /{document=**} { allow read, write: if false; }`.
- **Anti-Spoofing & Immutability**: Ownership fields (`userId`, `creatorId`, `reporterId`, `fromUserId`, `createdAt`) are pinned on `create` and immutable on `update`.
- **Single-Doc `get` vs `list` Isolation**: Subcollection queries on `conversations/{convId}/messages` filter by `.whereArrayContains("memberUids", uid)` matching `allow list: if isSignedIn() && request.auth.uid in existing().memberUids`.
- **Atomic Mutual Matches**: Creating `matches/{minUid}_{maxUid}` requires `existsAfter(...)` on both directional `likes` documents.
- **Anti-Cheat Battle Integrity**:
  - `status` transitions strictly `waiting -> live -> completed`; `completed` is terminal.
  - Virtual gift updates must stay within `affectedKeys().hasOnly(['challengerScore', 'opponentScore', 'updatedAt'])` and exact non-negative point increments `[1, 3, 5, 10]`.
- **Verified Admin Gate**: `isAdmin()` requires `admins/{uid}` or verified admin email (`request.auth.token.email_verified == true`).

## Verified Emulator Test Suite (`firestore.test.js`)
8/8 automated security tests validate:
1. Unauthenticated read/write rejection.
2. Owner-only private PII isolation.
3. Cross-user profile overwrite denial.
4. Minor (`age < 18`) dating profile rejection.
5. Non-member chat message read & list denial.
6. Mutual-like enforcement prior to match creation.
7. Battle gift score delta tampering & post-completion mutation denial.
8. Non-admin moderation privilege escalation denial.
