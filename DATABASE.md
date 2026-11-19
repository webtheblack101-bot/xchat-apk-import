# X Chat Database Schema (Cloud Firestore)

All documents live in the provisioned named Firestore database (`R.string.firestore_database_id`) and follow `firebase-blueprint.json`.

## Collections

### 1. Public & Private User Identity
- `users/{userId}` — Public social profile (`userId`, `username`, `displayName`, `bio`, `avatarData?`, `interests`, `languages`, `accountStatus`, `onlineVisible`, `lastActiveAt`, `createdAt`, `updatedAt`). Zero PII.
- `users/{userId}/private/account` — Owner-only private PII & preferences (`email`, `birthYear`, `ageVerified`, `showOnlineStatus`, `allowFriendRequests`, `notifyMessages`, `notifyMatches`, `notifyGroups`, `notifyBattles`).
- `users/{userId}/friends/{friendUid}` — Bidirectional social connections (`connected` / `pending`).
- `users/{userId}/group_memberships/{groupId}` — Owner-scoped denormalized group membership index for fast Home feed hydration.

### 2. Dating & Safety
- `dating_profiles/{userId}` — Separate 18+ discovery profile (`datingEnabled`, `datingBio`, `age >= 18`, `city` approximate location only, `relationshipIntent`, `conversationStyle`, `availabilityStatus`, `interests`, `languages`, `preferredAgeMin..Max`, `preferredCity`, `preferredIntents`, `preferredLanguages`, `photos`).
- `blocks/{blockerId}_{blockedId}` — Mutual exclusion pairs preventing discovery, chats, and friend visibility.

### 3. Communities & Invitations
- `groups/{groupId}` — Community rooms (`name`, `description`, `visibility: public|private`, `ownerId`, `imageData?`, `memberCount`).
- `groups/{groupId}/members/{memberUid}` — Membership & roles (`owner`, `admin`, `moderator`, `member`) with status (`active`, `pending`).
- `group_invites/{inviteCode}` — Time-limited (`expiresAt`), revocable (`revoked`) group invite codes.

### 4. Real-Time Messaging
- `conversations/{convId}` — Direct (`dm_{minUid}_{maxUid}`) and group (`grp_{groupId}`) rooms with denormalized `memberUids` and `unreadCounts`.
- `conversations/{convId}/messages/{msgId}` — Messages containing denormalized `memberUids`, `messageType` (`text|image|system`), `content`, `mediaData?`, `readBy`, and `deleted` flag.

### 5. Discovery, Mutual Matches & Telemetry
- `likes/{fromUid}_{toUid}` — Anti-duplicate swipe record (`like`, `superlike`, `pass`).
- `matches/{minUid}_{maxUid}` — Created atomically in a batch only when both reciprocal `likes` documents exist (`existsAfter` verified in rules).
- `recommendation_events/{eventId}` — Append-only interaction telemetry (`profile_impression`, `like`, `superlike`, `pass`, `more_like_this`, `less_like_this`, `hourOfDay`, `dayOfWeek`, `position`, `modelVersion`).
- `recommendation_preferences/{userId}` — User-controllable `personalizedEnabled`, `boostedTags`, `suppressedTags`.

### 6. Live Battles, Notifications & Moderation
- `battles/{battleId}` — Interactive room (`waiting` -> `live` -> `completed`), scores (`challengerScore`, `opponentScore`), `durationSeconds`, `winnerId`.
- `battles/{battleId}/comments/{commentId}` — Spectator real-time comments.
- `battles/{battleId}/gifts/{giftId}` — Non-monetary gift audit log (`heart: 1`, `fire: 3`, `rose: 5`, `crown: 10`).
- `notifications/{notifId}` — User-scoped notifications (`readFlag`).
- `reports/{reportId}`, `moderation_actions/{actionId}`, `admins/{uid}` — Abuse reporting and admin audit trail.
