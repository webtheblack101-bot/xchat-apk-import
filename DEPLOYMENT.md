# X Chat Deployment & Firebase Configuration Guide

## Provisioned Components
- **Firebase Project**: Provisioned with Firebase Authentication (Google Sign-In via Credential Manager) and Cloud Firestore Enterprise (`R.string.firestore_database_id`).
- **Firestore Security Rules**: Deployed from `/firestore.rules` after passing the local Firestore emulator test suite (`firestore.test.js`).
- **Admin Access**: Bootstrapped for verified admin email (`webtheblack101@gmail.com`) and `/admins/{uid}` documents.

## Enabling Optional Production Upgrades in Firebase Console

The current build operates strictly within free-tier constraints without paid external APIs. To enable additional production capabilities in the future:

1. **Email/Password Sign-In & Password Reset**
   - In **Firebase Console -> Authentication -> Sign-in method**, enable **Email/Password**.
   - Wire `auth.createUserWithEmailAndPassword`, `auth.signInWithEmailAndPassword`, and `auth.sendPasswordResetEmail` in `AuthRepository` alongside the existing Google Sign-In flow.

2. **Phone Number SMS Authentication**
   - Phone SMS verification carries per-SMS carrier fees beyond Firebase's free daily quota (10 free SMS/day on Spark, Blaze billing required for production volume).
   - Enable **Phone** in Firebase Console -> Authentication -> Sign-in method and configure App Check / Play Integrity before invoking `PhoneAuthProvider`.

3. **Cloud Storage for High-Resolution Media**
   - Currently, X Chat compresses selected photos via `ImageCompressor` (`<= 140 KB` data URIs) using Android's zero-permission Photo Picker.
   - To store full-resolution avatars or videos, activate **Firebase Storage** (Blaze plan) and update `ImageCompressor` / repositories to upload to `gs://<bucket>/users/{uid}/...` with Storage security rules matching `firestore.rules`.

4. **Push Notifications (FCM)**
   - In-app notifications currently update live via Firestore `notifications` collection listeners.
   - Add `firebase-messaging` and a Cloud Function trigger on `notifications/{notifId}` creation (respecting `users/{uid}/private/account.notify*` flags) to deliver background push notifications.
