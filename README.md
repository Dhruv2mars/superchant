<p align="center">
  <img src="docs/play-store/icon-512.png" alt="SuperChant" width="128" height="128">
</p>

<h1 align="center">SuperChant</h1>

<p align="center">
  <strong>A distraction-free digital mala for your spiritual practice</strong>
</p>

<p align="center">
  <a href="https://github.com/Dhruv2mars/superchant/releases/latest"><img src="https://img.shields.io/github/v/release/Dhruv2mars/superchant?label=latest&color=%23C4956A" alt="Latest Release"></a>
  <img src="https://img.shields.io/badge/kotlin-2.4-blue?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/jetpack--compose-Material%203-blue?logo=jetpackcompose" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/minSdk-26-green?logo=android" alt="Min SDK">
  <img src="https://img.shields.io/badge/license-MIT-brightgreen" alt="License">
</p>

---

## What is SuperChant?

SuperChant is a minimalist Android app to count mantras using your phone's **volume buttons** — no screen needed. Keep your phone in your pocket, face-down, or with the screen off and stay focused on your practice.

Built for japa meditation, mantra chanting, prayer counting, and any repetitive spiritual practice.

## Features

### Core
- Press volume up or down to increment your count
- 108 counts per round — just like a traditional mala
- Works with screen off or in any other app
- Foreground service with persistent notification
- Pause and resume sessions anytime

### Goals & Tracking
- Create structured goals with multiple mantras
- Set target rounds per mantra — auto-advances when complete
- Drag-to-reorder mantras in the goal editor
- Session history with time-based categorization (Today, This Week, This Month, Older)
- All-time stats: total counts, rounds, sessions, and practice time

### Customization
- Light, dark, and system theme modes
- Adjustable vibration intensity for counts and round completions
- Haptic feedback on each count and round milestone
- Celebration ringtone on goal completion

### Privacy
- 100% local — all data stays on your device
- No accounts, no sign-up, no internet permission
- No analytics, no tracking, no ads
- [Read the privacy policy](PRIVACY_POLICY.md)

## Download

Get the latest APK from [GitHub Releases](https://github.com/Dhruv2mars/superchant/releases/latest).

## Build from Source

```bash
git clone https://github.com/Dhruv2mars/superchant.git
cd superchant
./gradlew assembleDebug        # Debug APK
./gradlew buildReleaseApk      # Release APK, copied to ./SuperChant-v{version}.apk
```

**Requirements:** JDK 17 and the Android SDK. Release builds use the keys in `local.properties` when present and fall back to the debug key otherwise.

## Tech Stack

| Layer | Tech |
|-------|------|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation Compose |
| State | StateFlow + Foreground Service |
| Persistence | DataStore Preferences + kotlinx.serialization |
| Architecture | AccessibilityService + Foreground Service |

## How It Works

SuperChant uses Android's [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) to intercept volume button presses system-wide. When a session is active, the service consumes the key event so volume doesn't change — it just increments the counter.

The accessibility service does **not** read screen content (`canRetrieveWindowContent = false`), does not interact with other apps, and only activates during user-initiated sessions.

## License

MIT — see [LICENSE](LICENSE) for details.
