# AGENTS.md - SuperChant

Kotlin/Jetpack Compose Android app that counts chants with the volume keys and/or on-screen taps. An
AccessibilityService intercepts volume key presses, a foreground service owns the session state, and
DataStore Preferences persists settings, goals and history (as JSON via kotlinx.serialization).

## Commands

Needs JDK 17 and the Android SDK (`ANDROID_HOME` or `sdk.dir` in `local.properties`).

```bash
./gradlew assembleDebug assembleRelease lint test   # full check
./gradlew installDebug                              # install on a device/emulator
./gradlew test --tests "com.superchant.data.ChantStateTest"
./gradlew buildReleaseApk                           # assembleRelease, copied to ./SuperChant-v{version}.apk
./gradlew buildReleaseBundle                        # bundleRelease, copied to ./SuperChant-v{version}.aab
```

Release signing reads `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` from the untracked `local.properties`. Without them the release build is
signed with the debug key, so it still builds and installs locally.

## Configuration

| Setting | Value |
|---------|-------|
| Package / namespace | `com.superchant` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |
| JVM target | 17 |
| Toolchain | AGP 9 (built-in Kotlin), Gradle 9, Kotlin 2.4 |

All versions live in `gradle/libs.versions.toml`.

## Layout

```
app/src/main/kotlin/com/superchant/
├── MainActivity.kt                  # NavHost: home, settings, history, goals
├── data/
│   ├── ChantState.kt                # ChantState (session), Goal, MantraGoal, SessionRecord
│   ├── ChantCounter.kt              # Pure increment/undo rules shared by all inputs; CountDebouncer
│   ├── InputMode.kt                 # VOLUME / ON_SCREEN / BOTH
│   └── PreferencesRepository.kt     # DataStore flows + setters
├── service/
│   ├── ChantingService.kt           # Foreground service; owns ChantState via a companion StateFlow
│   └── VolumeKeyAccessibilityService.kt  # onKeyEvent -> SharedFlow consumed by ChantingService
└── ui/
    ├── components/                  # Shared composables (AppTopBar, CounterDisplay, TapToCountPad, HoldToUndoButton, ...)
    ├── screens/                     # Home, Goals, History, Settings
    └── theme/                       # Material 3 colors, typography, ThemeMode
app/src/test/kotlin/com/superchant/  # JVM unit tests (JUnit 4)
```

## How counting works

1. The input mode (`InputMode`, persisted; Home and Settings) decides how to count. Starting in on-screen
   mode needs neither the accessibility service nor the notification permission (it is requested, not
   required). Volume mode requires both. Goals started from the Goals screen go through Home's checks.
2. `ChantingService.start()/startWithGoal()` starts the foreground service with the mode and marks the
   session active.
3. Volume: `VolumeKeyAccessibilityService.onKeyEvent` consumes `VOLUME_UP/DOWN` key-downs only while a
   session is active, not paused and `ChantingService.acceptsVolumeKeys`, and emits them on a `SharedFlow`.
   On-screen: `TapToCountPad` calls `ChantingService.countFromScreen()` (one count per touch, extra fingers
   and repeats under 150 ms ignored). `ChantingService.undo()` removes the last count.
4. Both inputs go through one `incrementCount()`, which applies `ChantCounter.increment` (108 per round, goal
   mantra advance, no counting after the goal completes) and plays the same haptics/sounds.
5. UI screens collect `ChantingService.chantStateFlow`. There are no ViewModels.

## Conventions

- 4-space indent, trailing-lambda Compose style.
- Reuse `ui/components` (e.g. `AppTopBar`, `BackButton`, `EmptyState`, `ConfirmDestructiveDialog`) instead of copying UI.
- New persisted data classes need `@Serializable` and defaults for new fields so old JSON still decodes.
- New UI strings go in `res/values/strings.xml` (plurals for counts). Older screens still hardcode some.

## Testing on an emulator

Shell-injected keys (`adb shell input keyevent KEYCODE_VOLUME_UP`) skip the accessibility key filter, so
they never reach `onKeyEvent`. Use a virtual uinput keyboard so the keys take the real hardware path:

```bash
adb shell settings put secure enabled_accessibility_services com.superchant/com.superchant.service.VolumeKeyAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell pm grant com.superchant android.permission.POST_NOTIFICATIONS
# Register a device with KEY_VOLUMEDOWN/UP (114/115), then press VOLUME_UP once (EV_KEY=1).
printf '%s\n' \
  '{"id":1,"command":"register","name":"Volume Keys","vid":4660,"pid":22136,"bus":"usb","configuration":[{"type":"UI_SET_EVBIT","data":[1]},{"type":"UI_SET_KEYBIT","data":[114,115]}]}' \
  '{"id":1,"command":"delay","duration":1500}' \
  '{"id":1,"command":"inject","events":[1,115,1,0,0,0]}' \
  '{"id":1,"command":"inject","events":[1,115,0,0,0,0]}' \
  '{"id":1,"command":"delay","duration":500}' | adb shell uinput -
```
