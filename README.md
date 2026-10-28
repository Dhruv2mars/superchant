<p align="center">
  <img src="docs/play-store/icon-512.png" alt="SuperChant" width="128" height="128">
</p>

<h1 align="center">SuperChant</h1>

<p align="center">
  <strong>A distraction-free digital mala for your spiritual practice</strong>
</p>

---

## What is SuperChant?

SuperChant counts mantras, 108 beads to a round. On Android you can count with the phone's **volume buttons**
without looking at the screen: from the lock screen or while another app is open. On Android and iPhone you can
also tap the screen to count.

Built for japa meditation, mantra chanting, prayer counting, and any repetitive spiritual practice.

## Features

### Core
- Android: press volume up or down to count, on the lock screen or in any other app while the screen is on
  (Android doesn't deliver button presses to accessibility services while the screen is off)
- Tap the screen to count (no permissions needed): one switch on the session screen; on, the counter is the tap
  target and the screen stays awake
- 108 beads per round, like a traditional mala, in a calm mala ring with a meru bead and a soft glow per round
- Pause and resume from the app, the notification, the Quick Settings tile (Android) or the Live Activity
  (iPhone Lock Screen and Dynamic Island)
- Optional milestone pulse every 27 beads and an optional soft bead click
- Start in one tap from a launcher shortcut (or a shortcut per goal) or the Quick Settings tile (Android)
- A running session survives the system closing the app and picks up where it left off

### Goals & Tracking
- Goals with several mantras and target rounds per mantra; the session moves on when one is done
- Session history grouped by Today, This Week, This Month and Older, with all-time totals
- Your practice: daily target, streak, an 18-week heatmap and lifetime totals per mantra
- A gentle optional daily reminder, skipped once you've practiced

### Privacy
- No servers and no accounts. Your history, goals and settings stay on your phone (and in its own device
  backup); the app goes online only to check for app updates
- Export everything to a JSON file you keep, and import it on another phone
- No analytics, no tracking, no ads
- [Read the privacy policy](PRIVACY_POLICY.md)

## Download

SuperChant is released through Google Play. It is in testing and not public yet.

## Build from Source

```bash
cd expo
bun install
bunx expo run:android      # debug build "SuperChant Dev" on a device or emulator (JDK 17, Android SDK)
bunx expo run:ios          # needs Xcode
```

`expo/scripts/release-android.sh` builds and uploads the Play bundle. See [AGENTS.md](AGENTS.md) for tests,
updates and the release flow.

## Tech Stack

| Layer | Tech |
|-------|------|
| App | Expo SDK 58, React Native, expo-router, TypeScript |
| Counting (Android) | Kotlin: AccessibilityService, foreground service, DataStore, a Jetpack Compose mala ring |
| Counting (iPhone) | Swift: session engine, Core Haptics, SwiftUI ring, Live Activity |
| Updates | EAS Update (fixes and polish between store builds) |

## How It Works

On Android, SuperChant uses an [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
to receive volume button presses system-wide. During a running session the service consumes each counted press
(key down, repeats and key up) so the volume doesn't change. When no session is running, it's paused, or its
goal is complete, the buttons change the volume as usual.

The accessibility service does **not** read screen content (`canRetrieveWindowContent = false`), does not
interact with other apps, and only counts during sessions you start. iPhone counts taps only.

## License

MIT — see [LICENSE](LICENSE) for details.
