# AGENTS.md - SuperChant

Instructions for coding agents (Codex reads this file; Claude Code reads it through `CLAUDE.md`).

SuperChant is a native Kotlin/Jetpack Compose Android app: a calm, offline digital japa mala. It counts
chants (108 per round) with the volume keys and/or on-screen taps. An AccessibilityService intercepts
volume key presses, a foreground service owns the session state, and DataStore Preferences persists
settings, goals and history (as JSON via kotlinx.serialization).

## Product rules (don't break these)

- **No INTERNET permission, ever.** No network, analytics, crash reporting, ads or accounts. Check the merged
  release manifest when adding a dependency.
- **A round is always 108.** There is no round-size setting.
- **Counting stays simple:** volume buttons count whenever the accessibility service is set up; touch counting
  is one on/off toggle on the session screen. No input-mode chooser, no undo, no hold-to-stop, no focus mode,
  no pocket guard (all removed on purpose; see the 2026-10 PR history).
- **Google Play policy:** the in-app accessibility disclosure (`AccessibilityDisclosureDialog`) must be shown and
  agreed to before sending anyone to accessibility settings, and volume keys are only consumed with that consent.
  The service never reads screen content (`canRetrieveWindowContent=false`, no event types). If you change what
  the service or the `specialUse` foreground service does, the Play Console declarations, `PRIVACY_POLICY.md`
  and `docs/play-store/STORE_LISTING.md` must be updated to match.
- Keep it minimal and quiet: add a feature only if it clearly helps someone while they chant.

## Commands

Needs JDK 17 and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`; a fresh worktree has no
`local.properties`, so write `sdk.dir=$HOME/Library/Android/sdk` on the maintainer's Mac).

```bash
./gradlew assembleDebug assembleRelease lint test   # full check
./gradlew installDebug                              # install on a device/emulator
./gradlew test --tests "com.superchant.data.ChantStateTest"
./gradlew buildReleaseApk                           # assembleRelease, copied to ./SuperChant-v{version}.apk
./gradlew buildReleaseBundle                        # bundleRelease, copied to ./SuperChant-v{version}.aab
./gradlew :app:generateReleaseBaselineProfile       # regenerate the baseline/startup profile (emulator running)
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # Macrobenchmark: cold start and touch counting
```

Release signing reads `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` from the untracked `local.properties`. Without them the release build is
signed with the debug key, so it still builds and installs locally.

## Configuration

| Setting | Value |
|---------|-------|
| Package / namespace | `com.superchant` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |
| Version | `versionName`/`versionCode` in `app/build.gradle.kts` (bump only when a release is approved) |
| JVM target | 17 |
| Toolchain | AGP 9 (built-in Kotlin), Gradle 9, Kotlin 2.4 |

All versions live in `gradle/libs.versions.toml`.

## Layout

```
app/src/main/kotlin/com/superchant/
├── MainActivity.kt                  # NavHost: home, settings, history, goals; handles START_COUNTING intents
├── AppShortcuts.kt                  # Launcher shortcuts (static + per goal) and the START_COUNTING intent
├── PerfTrace.kt                     # SC.* trace sections (count path, ring draw, write queue) for Perfetto
├── DebugStrictMode.kt               # Debug builds log StrictMode violations
├── data/
│   ├── ChantState.kt                # ChantState (session, persisted while active; ROUND_SIZE = 108), Goal, MantraGoal, SessionRecord; String IDs
│   ├── PracticeStats.kt             # HistoryStats (totals, daily, per-mantra), streak, heatmap levels (pure)
│   ├── Backup.kt                    # Export/import JSON format, lenient decode, merge rules
│   ├── StoredList.kt                # Fail-safe JSON list decode/encode (keeps unreadable elements), ID dedupe
│   ├── HistoryBuckets.kt            # Today / This Week / This Month / Older grouping
│   ├── ChantCounter.kt              # Pure increment rules shared by all inputs; CountDebouncer
│   ├── LatestWins.kt                # Coalesces queued saves (the per-count active-session write)
│   └── PreferencesRepository.kt     # Singleton; StateFlows (null = loading) + ordered `write {}` queue; 3 DataStores
├── service/
│   ├── ChantingService.kt           # Foreground service; owns ChantState via a companion StateFlow
│   ├── VolumeKeyAccessibilityService.kt  # onKeyEvent -> VolumeKeyFilter -> ChantingService.onVolumeKeyPressed
│   ├── VolumeKeyFilter.kt           # Which key events to consume (whole press, never repeats)
│   ├── BeadClick.kt                 # Synthesized bead click (static AudioTrack, no asset)
│   └── ChantTileService.kt          # Quick Settings tile: start, or pause/resume with the live count
├── reminder/Reminder.kt             # Daily reminder: ReminderTime (pure), inexact alarm scheduler, receivers
└── ui/
    ├── components/
    │   ├── Common.kt                # AppTopBar, BackButton, EmptyState, ConfirmDestructiveDialog, ...
    │   ├── CounterDisplay.kt        # The mala ring (beads, meru bead, round glow)
    │   ├── OnScreenCounter.kt       # TapToCountPad, TouchCountingToggle, KeepScreenOn
    │   ├── StartStopButton.kt       # StartButton / StopButton
    │   ├── AccessibilitySetup.kt    # AccessibilitySetupDialog incl. the Play-required disclosure
    │   ├── Insights.kt              # InsightsCard (streak, heatmap, per-mantra totals), TodayLine
    │   └── ThemeToggle.kt
    ├── screens/                     # Home, Goals, History, Settings (+ BackupControls, ReminderControls used by Settings)
    └── theme/                       # Material 3 colors, typography, ThemeMode, Motion (easing tokens, pressScale)
app/src/test/kotlin/com/superchant/  # JVM unit tests (JUnit 4)
app/src/release/generated/baselineProfiles/  # Generated baseline + startup profile (checked in)
baselineprofile/                     # Build/test-only module: profile generator and Macrobenchmarks
```

Storage is three Preferences DataStores: `superchant_prefs` (small settings), `history` (session history and
goals as JSON; moved out of `superchant_prefs` once by `moveHistory`, so settings writes and the
theme read before the first frame never touch megabytes of history) and `active_session`.

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
   a window flag) unless paused or the goal is complete. Off, the pad ignores touches and the screen may sleep.
4. Both inputs go through one `incrementCount()`, which applies `ChantCounter.increment` (rounds of 108,
   a milestone every 27 beads, goal mantra advance, no counting after the goal completes) and plays the same
   haptics/sounds (plus the optional bead click). There is no undo; Stop is a single tap.
5. Every change to an active session is written to the `active_session` DataStore (off the main thread;
   snapshots that pile up behind a slow write collapse into the newest, `LatestWins`). After process death the
   service restores it (START_STICKY restart, or `MainActivity.onStart`), and stopping saves it to history
   under its `sessionId`, so a session is recorded once. Sessions idle for 3+ hours are saved instead of resumed.
6. UI screens collect `ChantingService.chantStateFlow`. There are no ViewModels. Home reads the count lazily
   (`SessionUi.state` is a lambda) so only the counter recomposes per count; keep it that way. Animated values
   (pad reveal, toggle fill, press scale) are read in draw/layer lambdas so they never recompose.
7. Insights (streak, heatmap, per-mantra totals) are computed from the stored history by `PracticeStats`,
   once per history change and off the main thread (`PreferencesRepository.historyStats`); never derive
   them from the session list in composition. Each goal session's `SessionRecord.mantras` holds its
   per-mantra breakdown.
8. Removed features leave stored data behind: prefs `input_mode`, `round_size`, `pocket_guard` and
   `roundSize`/`inputMode`/`pocketGuard` fields in old sessions and backups are never read (JSON uses
   `ignoreUnknownKeys`). Don't reuse those keys. A restored active session's rounds and goal progress are
   recomputed from its count at 108 (`ChantState.normalizedRounds`).

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
- All UI strings go in `res/values/strings.xml` (plurals for counts); none are hardcoded.
- Text and icons meet WCAG AA (4.5:1) in both themes; touch targets are at least 48dp.

## Verifying changes

Done means verified, not just compiled:

1. `./gradlew assembleDebug assembleRelease lint test` passes. Add JVM tests for logic changes (`app/src/test/...`;
   counting rules, persistence, backup, stats and reminders all have pure, testable cores).
2. Exercise the change on an emulator with the **release** APK (R8 can break serialization), and check that
   `adb logcat -b crash` stays empty. The maintainer's AVD is `Pixel_9_API_35_A`; in T3 Code open it with the
   device tools (`device_open`) and drive it with the returned agent-device CLI. Pass `-s <serial>` to adb.
3. For UI changes, take screenshots (light, dark, a small screen such as `adb shell wm size 720x1280`, and reset after).
4. Only the counter may recompose on each count (Home reads the count lazily). Don't add per-frame work in composition.
5. For performance work, measure the release build with Perfetto: the `SC.*` sections (`SC.increment`,
   `SC.countToDraw` from a count to the ring drawing it, `SC.ringDraw`, `SC.write`) show the count path.
   Emulator frames are dominated by the emulated GPU, so compare main-thread time and frame counts, not jank %.
   Note: the app's own accessibility service makes Compose treat accessibility as on (semantics diffing on every
   change), so measure with it enabled, as volume-button users run.

### Simulating volume keys

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

## Workflow

- Work on a branch and open a PR against `main`; don't push to `main` directly. Put verification evidence in the
  PR description. Screenshots go on the orphan `ux-evidence` branch (one folder per PR), never in `main`.
- Never commit `local.properties`, keystores, APK/AAB files, `TASKS.md` or local agent state (`.claude/settings*.json`).
- Release builds: `./gradlew buildReleaseBundle` with the upload key in `local.properties`. Play App Signing is on;
  the app is a draft on Google Play, and a personal developer account needs a closed test (12+ testers, 14 days)
  before production.
- For JavaScript tooling (e.g. `bunx skills`), use bun.
