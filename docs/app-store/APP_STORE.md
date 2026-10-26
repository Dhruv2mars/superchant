# App Store (iOS) notes

The iPhone app is the Expo app in `expo/` (bundle ID `com.superchant`). Nothing is submitted yet: it needs an
Apple Developer Program membership (paid, Dhruv's decision), then `eas build -p ios` and `eas submit`.

## What differs from Android, and why

- **No volume-button counting.** App Review Guideline 2.5.9 rejects apps that change standard switches
  (volume buttons included), and the same rules apply to notarized apps outside the App Store. iPhone counts with
  taps on the counter (large target, haptics, screen kept awake), and a Live Activity shows the session on the
  Lock Screen and in the Dynamic Island, with pause/resume.
- **No Quick Settings tile, launcher shortcuts or accessibility service.** Android-only features.
- **Reminders** are local notifications planned a week ahead and re-planned whenever practice, the setting
  or a session changes (`expo/src/core/reminders.ts`), since iOS can't run code when a reminder is due.

## Over-the-air updates (EAS Update)

Guideline 2.5.2 doesn't allow downloaded code that "introduces or changes features or functionality". Use
EAS Update on iOS for fixes and polish only; ship new features in a store build. (Google Play allows
interpreted JS updates, so Android has more room, but keep the two platforms on the same rule.) The runtime
version uses the `fingerprint` policy, so an update is only offered to builds with the same native code.

## App Privacy ("nutrition label"), recommendation

- **Data used to track you:** none.
- **Data linked to you:** none.
- **Data not linked to you:** *Identifiers → Device ID*, collected by EAS Update. Each update check sends a
  random per-install ID (`EAS-Client-ID`) that Expo keeps to count monthly updated installs. Purpose: App
  Functionality. This is the conservative reading; the ID isn't tied to the hardware or a person, so
  "Data Not Collected" is arguable, but Expo retains it, so declare it.
- No crash data: the `Expo-Fatal-Error` header (the text of the last fatal error) is blanked in
  `app.config.ts`.

The privacy policy (`PRIVACY_POLICY.md`) covers both platforms.

## Review notes to include

- The app has no account and needs no login.
- Counting is on-screen only. The Live Activity's button pauses and resumes the running session.
