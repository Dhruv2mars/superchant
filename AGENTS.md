# AGENTS.md - SuperChant

Kotlin/Jetpack Compose Android app that counts chants (108 per round) with the volume keys and/or on-screen taps. An
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
├── MainActivity.kt                  # NavHost: home, settings, history, goals; handles START_COUNTING intents
├── AppShortcuts.kt                  # Launcher shortcuts (static + per goal) and the START_COUNTING intent
├── data/
│   ├── ChantState.kt                # ChantState (session, persisted while active; ROUND_SIZE = 108), Goal, MantraGoal, SessionRecord; String IDs
│   ├── PracticeStats.kt             # Daily totals, streak, per-mantra totals, heatmap levels (pure)
│   ├── Backup.kt                    # Export/import JSON format, lenient decode, merge rules
│   ├── StoredList.kt                # Fail-safe JSON list decode/encode (keeps unreadable elements), ID dedupe
│   ├── HistoryBuckets.kt            # Today / This Week / This Month / Older grouping
│   ├── ChantCounter.kt              # Pure increment rules shared by all inputs; CountDebouncer
│   └── PreferencesRepository.kt     # Singleton; StateFlows (null = loading) + ordered `write {}` queue
├── service/
│   ├── ChantingService.kt           # Foreground service; owns ChantState via a companion StateFlow
│   ├── VolumeKeyAccessibilityService.kt  # onKeyEvent -> VolumeKeyFilter -> ChantingService.onVolumeKeyPressed
│   ├── VolumeKeyFilter.kt           # Which key events to consume (whole press, never repeats)
│   ├── BeadClick.kt                 # Synthesized bead click (static AudioTrack, no asset)
│   └── ChantTileService.kt          # Quick Settings tile: start, or pause/resume with the live count
├── reminder/Reminder.kt             # Daily reminder: ReminderTime (pure), inexact alarm scheduler, receivers
└── ui/
    ├── components/                  # Shared composables (CounterDisplay ring, TapToCountPad + TouchCountingToggle, Start/Stop
    │                                #   buttons, AccessibilitySetupDialog incl. the prominent disclosure, Insights, ...)
    ├── screens/                     # Home, Goals, History, Settings
    └── theme/                       # Material 3 colors, typography, ThemeMode, Motion (easing tokens, pressScale)
app/src/test/kotlin/com/superchant/  # JVM unit tests (JUnit 4)
```

## How counting works

1. Volume buttons count whenever a session runs, once the accessibility service is enabled and the user
   agreed to the disclosure (`accessibility_consent`). Touch counting is a per-session switch,
   `ChantState.touchCounting`, toggled from the hand icon on the session screen. Start never waits on a
   permission: when volume buttons can't count yet, the session starts with touch counting on and Home
   offers "Count with volume buttons" (disclosure first). The notification permission is requested, not
   required. Starts from the Goals screen, launcher shortcuts and the Quick Settings tile arrive as a
   `StartRequest` and go through Home.
2. `ChantingService.start(context, goal, touchCounting)` starts the foreground service. Rounds are always
   `ChantState.ROUND_SIZE` (108); there is no round-size setting.
3. Volume: `VolumeKeyAccessibilityService.onKeyEvent` (main thread) passes key events to `VolumeKeyFilter`, which
   offers each new press (DOWN, repeatCount 0) to `ChantingService.onVolumeKeyPressed()`. A counted press is
   consumed whole (DOWN, repeats, UP); presses that aren't counted (no session, paused, goal complete, or no
   disclosure consent) pass through to the system untouched.
   Touch: while touch counting is on, `TapToCountPad` calls `ChantingService.countFromScreen()` (one count per
   touch, extra fingers and repeats under 150 ms ignored) and Home keeps the screen awake (`KeepScreenOn`,
   a window flag). Off, the pad ignores touches and the screen may sleep.
4. Both inputs go through one `incrementCount()`, which applies `ChantCounter.increment` (rounds of 108,
   a milestone every 27 beads, goal mantra advance, no counting after the goal completes) and plays the same
   haptics/sounds (plus the optional bead click). There is no undo; Stop is a single tap.
5. Every change to an active session is written to the `active_session` DataStore. After process death the
   service restores it (START_STICKY restart, or `MainActivity.onStart`), and stopping saves it to history
   under its `sessionId`, so a session is recorded once. Sessions idle for 3+ hours are saved instead of resumed.
6. UI screens collect `ChantingService.chantStateFlow`. There are no ViewModels. Home reads the count lazily
   (`SessionUi.state` is a lambda) so only the counter recomposes per count; keep it that way. Animated values
   (pad reveal, toggle fill, press scale) are read in draw/layer lambdas so they never recompose.
7. Insights (streak, heatmap, per-mantra totals) are computed from the stored history by `PracticeStats`;
   each goal session's `SessionRecord.mantras` holds its per-mantra breakdown.
8. Removed features leave stored data behind: prefs `input_mode`, `round_size`, `pocket_guard` and
   `roundSize`/`inputMode`/`pocketGuard` fields in old sessions and backups are never read (JSON uses
   `ignoreUnknownKeys`). Don't reuse those keys.

## Design and motion skills

Project-level agent skills live in `.agents/skills/` (tracked; `.claude/skills/` holds symlinks to them for
Claude Code, Codex reads `.agents/skills/` directly). `skills-lock.json` records their sources; update with
`bunx skills update -p`. They are web-oriented, so translate CSS/React advice to Compose (cubic-bezier →
`CubicBezierEasing`, `:active` → `pressScale`, transitions → `animate*AsState`, springs → `spring()`).

| Skill | Use it when |
|-------|-------------|
| `emil-design-eng`, `animate` | Adding or changing any animation or interaction: whether it should animate, easing, duration, enter/exit |
| `apple-design` | Springs, gestures, interruptible motion, haptics + visual timing, reduced motion |
| `animate-expo` | Mobile specifics: press-in feedback, haptics rules, 48dp targets, spring configs (ignore the Reanimated APIs) |
| `animation-performance` | Motion that janks; keep per-frame work out of composition |
| `find-animation-opportunities` | Asked where motion would help (it mostly says where it wouldn't) |
| `review-animations` | Reviewing a diff that touches motion (invoke explicitly) |
| `frontend-design` | Visual design of new or reshaped screens |

House rules from them: UI motion under 300 ms with `EaseOutStrong` (ui/theme/Motion.kt), never ease-in, no
bounce outside gestures, nothing scales from 0, and the per-count path stays near-imperceptible (it runs 108
times a round).

## Conventions

- 4-space indent, trailing-lambda Compose style.
- Reuse `ui/components` (e.g. `AppTopBar`, `BackButton`, `EmptyState`, `ConfirmDestructiveDialog`) instead of copying UI.
- New persisted data classes need `@Serializable` and defaults for new fields so old JSON still decodes.
  IDs are UUID strings (`newId()`); `LenientIdSerializer` still reads the numeric IDs of v1.1.0 data.
- Write preferences with `preferencesRepository.write { ... }`, not a screen's coroutine scope.
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
