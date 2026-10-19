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
| Version | Date-based, computed in `app/build.gradle.kts`: `versionName` 2.YYYY.DDD, `versionCode` YYDDDNN (`-PbuildNumber=NN` for a second upload the same day) |
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

- Branches: `dev` is the default and integration branch; `main` is production. Open PRs against `dev`; never
  push to `dev` or `main` directly.
  - CI (`.github/workflows/ci.yml`) runs build, lint, test and an INTERNET-permission guard on every PR.
  - Every push to `dev` builds a signed bundle and uploads it to the Play **internal testing** track
    (`release-internal.yml`), so the owner's Play Store install updates within minutes.
  - Merging `dev` into `main` promotes that same bundle (`promote.yml`, `scripts/play_promote.py`) to the track
    in repo variable `PLAY_PROMOTE_TRACK` (`alpha` = closed testing until production access is granted).
  - Secrets: `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`.
- Put verification evidence in the PR description. Screenshots go on the orphan `ux-evidence` branch (one folder
  per PR), never in `dev`/`main`. Play Store listing assets under `docs/play-store/` are the exception.
- Never commit `local.properties`, keystores, APK/AAB files, `TASKS.md` or local agent state (`.claude/settings*.json`).
- Releases go through Google Play only. The owner uses the Play Store install (internal testing track) as their
  daily app, so every upload to internal testing updates it. Build with `./gradlew bundleRelease`, signed with the
  upload key that `local.properties` points to (`RELEASE_*`; keystore kept outside the repo, never committed).
  Play App Signing is on. A personal developer account needs a closed test (12+ testers, 14 days) before production.
- Day-to-day testing on the owner's phone uses the debug build ("SuperChant Dev"), so it never touches the Play
  install or its data. See "Getting a build on the owner's phone" below.

### Getting a build on the owner's phone

1. **Play (the real app, `com.superchant`):** merge a PR into `dev`. `release-internal.yml` uploads it to internal
   testing and the Play Store install updates itself. Nothing else to do. This is the only way to update the real
   app: it is signed by Play App Signing, so a sideloaded `com.superchant` can't install over it, and uninstalling
   it loses practice history.
2. **SuperChant Dev (`com.superchant.dev`), no cable:** every push to `dev` also runs `dev-build.yml`, which puts
   `SuperChant-Dev.apk` on a private draft release (tag `dev-latest`). On the phone, signed in to GitHub, open
   https://github.com/Dhruv2mars/superchant/releases, take the draft at the top and install it (allow the browser
   to install apps once). Link to the Releases page, not the draft's own URL: that one changes on every edit.
   CI signs it with the Mac's debug key (secret `DEBUG_KEYSTORE_BASE64`), so it and local builds update each other.
3. **SuperChant Dev, cable or wireless adb:** `./gradlew installDebug`. The Mac's Wi-Fi is usually behind Proton VPN,
   which blocks LAN connections, so serving the APK over Wi-Fi from the Mac doesn't reach the phone.

Play release state (2026-10-19): the upload key was reset and Google hadn't activated it yet, so uploads failed with
"The upload certificate ... is not yet valid because it has been recently reset". `PLAY_RELEASES_ENABLED` stays
`false` until a dispatched `release-internal.yml` succeeds. `promote.yml` (internal → alpha) also fails until a first
closed-testing release is created by hand in Play Console (the app is still a "draft app").
- For JavaScript tooling (e.g. `bunx skills`), use bun.
