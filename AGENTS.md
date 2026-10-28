# AGENTS.md - SuperChant

Instructions for coding agents (Codex reads this file; Claude Code reads it through `CLAUDE.md`).

SuperChant is a calm digital japa mala. It counts chants (108 per round) with the volume keys and/or on-screen
taps. Two apps live in this repo:

- **`expo/`: the Expo (React Native) app for iOS and Android, the app that ships.** It replaced the Kotlin app
  on Play as a normal update (same package `com.superchant`, same upload key, same data). See "The Expo app"
  and "Workflow" below.
- **`app/`: the original Kotlin/Jetpack Compose Android app**, no longer released. An AccessibilityService
  intercepts volume key presses, a foreground service owns the session state, and DataStore Preferences
  persists settings, goals and history (as JSON via kotlinx.serialization). Its engine lives on in the Expo
  app's native module; `app/` stays for its JVM tests and the golden fixtures until it is removed.

## Product rules (don't break these)

- **No network except app updates.** The Expo app may go online only to check for and download updates
  (EAS Update, `u.expo.dev` and `assets.eascdn.net`); its Android release build trusts TLS for those hosts
  only. No analytics, crash reporting (expo-updates' `Expo-Fatal-Error` header is blanked), ads or accounts.
  The Kotlin app still has no INTERNET permission. Check the merged release manifest when adding a dependency
  (`expo-notifications` links Firebase; don't add it).
- **OTA updates are for fixes and polish** (App Review 2.5.2); new features ship in store builds.
- **A round is always 108.** There is no round-size setting.
- **Counting stays simple:** volume buttons count whenever the accessibility service is set up; touch counting
  is one on/off toggle on the session screen. No input-mode chooser, no undo, no hold-to-stop, no focus mode,
  no pocket guard (all removed on purpose; see the 2026-10 PR history).
- **Google Play policy:** the in-app accessibility disclosure (`AccessibilityDisclosureDialog`) must be shown and
  agreed to before sending anyone to accessibility settings, and volume keys are only consumed with that consent.
  The service never reads screen content (`canRetrieveWindowContent=false`, no event types). If you change what
  the service or the `specialUse` foreground service does, the Play Console declarations, `PRIVACY_POLICY.md`
  and `docs/play-store/STORE_LISTING.md` must be updated to match.
- **iOS never counts volume buttons** (App Review 2.5.9). iPhone counts taps and shows a Live Activity.
- Keep it minimal and quiet: add a feature only if it clearly helps someone while they chant.

## Commands

Needs JDK 17 and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`; a fresh worktree has no
`local.properties`, so write `sdk.dir=$HOME/Library/Android/sdk` on the maintainer's Mac).

```bash
./gradlew assembleDebug assembleRelease lint test   # full check
./gradlew installDebug                              # install "SuperChant Dev" (com.superchant.dev) on a device/emulator
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
| Version | Date-based, computed in `app/build.gradle.kts`: `versionName` 2.YYYY.DDD, `versionCode` YYDDDNN (`-PreleaseDate=YYYY-MM-DD`, `-PbuildNumber=NN`; CI takes NN one above the highest code Play has that day, `scripts/play_version.py`) |
| Variants | `release` = `com.superchant` (Play Store); `debug` = `com.superchant.dev` "SuperChant Dev", installs side by side |
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
   While the screen is off, Android sends volume keys straight to the media session and never to accessibility
   services, so volume counting needs the screen on (lock screen and other apps are fine). Don't claim
   screen-off counting in the app, listing or Play declarations.
   Touch: while touch counting is on, `TapToCountPad` calls `ChantingService.countFromScreen()` (one count per
   touch, extra fingers and repeats under 150 ms ignored) and Home keeps the screen awake (`KeepScreenOn`,
   a window flag) unless paused or the goal is complete. Off, the pad ignores touches and the screen may sleep.
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
   `ignoreUnknownKeys`). Don't reuse those keys. A restored active session's rounds and goal progress are
   recomputed from its count at 108 (`ChantState.normalizedRounds`).

## The Expo app (`expo/`)

Expo SDK 58 (React Native 0.88, expo-router), bun, TypeScript. `ios/` and `android/` are generated
(Continuous Native Generation): configure them in `app.config.ts` and the config plugins, never by hand.

**The count loop is native on each platform; JavaScript never runs per count.**
- Android: `modules/superchant-native/android` is the Kotlin app's engine moved into a local Expo module with
  its class names unchanged (`com.superchant.service.*`, `com.superchant.data.*`, ...), so an enabled
  accessibility service, an added tile and the DataStore files carry over on update. The Compose ring and tap
  pad are a native view (`CounterView`) that collects `ChantingService.chantStateFlow` itself. The bridge
  (`com/superchant/bridge/SuperchantNativeModule.kt`) sends JS only coarse session changes (start, stop,
  pause, a round, goal progress), settings, goals and history.
- iOS: `modules/superchant-native/ios` runs the session in Swift (`SessionEngine`, Core Haptics, the bead
  click, a SwiftUI `CounterView`, the Live Activity with a `LiveActivityIntent` pause/resume button). Storage
  is TypeScript (`src/core/repository.ts`, the Kotlin repository's rules over UserDefaults).
  `targets/widget` is the Live Activity extension (`@bacons/apple-targets`); its `Shared.swift` copies
  `ChantActivityAttributes` and `TogglePauseIntent` from `ios/LiveActivity.swift`, keep them identical.
- JS (`src/`): screens in `src/app` (Home, Goals, goal-editor, History, Settings), UI kit in `src/ui`, pure
  cores in `src/core` (kotlinx-compatible JSON, model and counting rules, stored lists, backup codec, stats,
  reminder plan), platform layer in `src/native/platform.{android,ios}.ts`, strings in `src/i18n/strings.ts`.

```bash
cd expo
bun install
bun run test            # TS cores in three time zones (UTC, New York, Kolkata)
bun run test:swift      # Swift model against the Kotlin vectors (swiftc, no Xcode needed)
bunx tsc --noEmit
bun run lint            # oxlint (.oxlintrc.json), warnings fail
bunx expo prebuild --clean            # regenerate android/ and ios/
cd android && ./gradlew :superchant-native:testDebugUnitTest :app:assembleRelease
bunx expo run:ios --configuration Release   # needs Xcode
(cd android && ../scripts/network-guard.sh)  # after assembleRelease: permissions and the update-only TLS config
```

Buildkite runs the typecheck, oxlint and TS tests on every push (`.buildkite/pipeline.yml`), on Node: under Bun the
runtime fingerprint skips the config plugins and stops matching the store build. Run the Swift vectors, the
module's JVM tests, the release build and the network guard yourself before opening a trail.

- **Golden fixtures.** `app/src/test/.../GoldenFixturesTest.kt` runs the Kotlin decoders, encoders and counting
  rules over edge cases and writes `expo/src/core/__fixtures__/golden.json`
  (`GOLDEN_WRITE=1 ./gradlew test --tests "com.superchant.data.GoldenFixturesTest" --rerun`); the TS and Swift
  tests replay it byte for byte. Change a rule in Kotlin, regenerate, and make TS and Swift match.
- **Kotlin in two places.** Engine changes go into `expo/modules/superchant-native/android` (the shipping code)
  and the same change into `app/src/main/kotlin`, whose JVM tests and golden fixtures cover it, until `app/` is
  removed.
- Debug builds are `com.superchant.dev` ("SuperChant Dev") like the Kotlin app; the release is signed from the
  repo-root `local.properties` `RELEASE_*` keys, or the debug key without them. `./gradlew :app:assemblePreview`
  (in `expo/android`) builds the release code as SuperChant Dev with the debug key: no Metro needed, so it is
  the build to put on a phone over adb.
- Versions use the same date scheme (`RELEASE_DATE`, `BUILD_NUMBER` env vars), so an Expo build is a normal
  Play update over the Kotlin app.
- Updates: set `EAS_PROJECT_ID` (and optionally `EAS_UPDATE_CHANNEL`) to enable expo-updates; without it the
  build runs only its embedded bundle. `runtimeVersion` is the fingerprint policy (`fingerprint.config.js`
  skips package.json scripts on top of Expo's defaults, which skip the date-based version): an update reaches
  only builds with the same fingerprint, so native changes (Kotlin/Swift, config plugins, native dependencies)
  need a store build. Store builds always use the `production` channel; **early updates** (long-press the version
  in Settings, `src/native/updates.ts`) move one install to `preview` with a saved header override.

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

### Simulating volume keys

Shell-injected keys (`adb shell input keyevent KEYCODE_VOLUME_UP`) skip the accessibility key filter, so
they never reach `onKeyEvent`. Use a virtual uinput keyboard so the keys take the real hardware path:

```bash
PKG=com.superchant      # release APK; use com.superchant.dev for debug builds
adb shell settings put secure enabled_accessibility_services $PKG/com.superchant.service.VolumeKeyAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
# Register a device with KEY_VOLUMEDOWN/UP (114/115), then press VOLUME_UP once (EV_KEY=1).
printf '%s\n' \
  '{"id":1,"command":"register","name":"Volume Keys","vid":4660,"pid":22136,"bus":"usb","configuration":[{"type":"UI_SET_EVBIT","data":[1]},{"type":"UI_SET_KEYBIT","data":[114,115]}]}' \
  '{"id":1,"command":"delay","duration":1500}' \
  '{"id":1,"command":"inject","events":[1,115,1,0,0,0]}' \
  '{"id":1,"command":"inject","events":[1,115,0,0,0,0]}' \
  '{"id":1,"command":"delay","duration":500}' | adb shell uinput -
```

## Workflow

- Branches: `dev` is the default and integration branch; `main` is production. Open trails against `dev`; never
  push to `dev` or `main` directly. The repo lives on Entire; CI is Buildkite (`.buildkite/pipeline.yml`). The
  GitHub workflows in `.github/workflows` belong to the old GitHub repo and no longer run.
  - Every push: the Kotlin app's build, lint, tests and INTERNET guard, and the Expo typecheck, oxlint and TS tests.
  - A push to `dev` publishes an EAS Update to the **preview** channel; a push to `main` publishes to
    **production** (everyone). Updates are JavaScript and assets only: fixes and polish (App Review 2.5.2).
    Buildkite's cluster secret `EXPO_TOKEN` belongs to the Expo robot `superchant-buildkite` (publish only).
    Robots may not upload fingerprint records, so the step resolves each platform's fingerprint and passes it as
    `SUPERCHANT_RUNTIME_VERSION` with `EAS_SKIP_AUTO_FINGERPRINT=1`; to publish by hand, `eas update` works as is.
  - A native change (the fingerprint changed; CI prints it) needs a store build: `expo/scripts/release-android.sh`
    builds the bundle on the Mac (~9 min clean; EAS Build's free tier took ~24 min) and uploads it to Play
    internal testing with EAS Submit. Promote it to closed testing or production in Play Console.
- Put verification evidence in the PR description. Screenshots go on the orphan `ux-evidence` branch (one folder
  per PR), never in `dev`/`main`. Play Store listing assets under `docs/play-store/` are the exception.
- Never commit `local.properties`, keystores, APK/AAB files, `TASKS.md` or local agent state (`.claude/settings*.json`).
- Releases go through Google Play only. The owner's daily app is the Play Store install (internal testing track)
  with early updates on, so it runs `dev` within minutes of a merge. Store builds are signed with the upload key
  that the repo-root `local.properties` points to (`RELEASE_*`; keystore and the Play service account JSON live in
  `~/.superchant-keys`, never committed). Play App Signing is on. A personal developer account needs a closed
  test (12+ testers, 14 days) before production.
- To try a branch before merging, put the preview build ("SuperChant Dev") on the phone over wireless adb; it never
  touches the Play install or its data. See "Getting a build on the owner's phone" below.

### Getting a build on the owner's phone

The phone is a Realme RMX3392 in India. Play Protect's enhanced fraud protection there blocks installing any APK
that declares an accessibility service from a browser, messaging app or file manager, so a downloaded
SuperChant APK never installs. Only Play and adb installs work.

1. **Play (the real app, `com.superchant`):** with early updates on, a merge into `dev` reaches the Play install as
   an update on its next launch (or two: it downloads, then applies). Native changes arrive with the next
   `release-android.sh` upload. A sideloaded `com.superchant` (upload- or debug-signed) can't be updated by Play
   later, so never sideload the release package.
2. **SuperChant Dev (`com.superchant.dev`) over wireless adb:** phone and Mac on the same Wi-Fi, then
   `./gradlew :app:assemblePreview` in `expo/android` and `adb -s <ip:port> install -r
   app/build/outputs/apk/preview/app-preview.apk`. The Expo debug key differs from the Kotlin app's, so an old
   Kotlin SuperChant Dev has to be uninstalled first. Pairing is done once per phone:
   Developer options → Wireless debugging → Pair device with pairing code, then `adb pair <ip>:<pairing port> <code>`.
   The connect port changes when wireless debugging restarts, and adb's built-in mDNS discovery doesn't see the
   phone from this Mac. Find the phone's IP with a ping sweep and `arp -an`, and its open ports with a quick TCP
   scan of 1024-65535 (the connect port is the one Wireless debugging shows), then `adb connect <ip>:<port>`.
   QR pairing failed here (`protocol fault`), so use the pairing code. The pairing popup closes as soon as the
   phone leaves Settings, so read the code off the screen; the pairing port is the extra open port while the
   popup shows. Run adb outside the agent sandbox, which blocks local-network connections (`No route to host`).
   A USB cable with USB debugging also works.

Play release state (2026-10-26): uploads to internal testing work again (the reset upload key went active on
2026-10-21) and `PLAY_RELEASES_ENABLED` is `true`. The app is still a "draft app" until its first review, so
`promote.yml` (internal → alpha) can fail until the closed-testing track has a release set up in Play Console.

- For JavaScript tooling (e.g. `bunx skills`), use bun.
