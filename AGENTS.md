# AGENTS.md - SuperChant

Instructions for coding agents (Codex reads this file; Claude Code reads it through `CLAUDE.md`).

SuperChant is a calm digital japa mala. It counts chants (108 per round) with the volume keys and/or on-screen
taps. The app lives in **`expo/`**: one Expo (React Native) app for Android and iPhone, released on Google
Play as `com.superchant`. It replaced the original Kotlin/Jetpack Compose app (removed from the repo; see the
history before 2026-10-28) as a normal update: same package, same upload key, same data, and the Kotlin
engine lives on as the Android half of the native module.

## Product rules (don't break these)

- **No network except app updates.** The app may go online only to check for and download updates
  (EAS Update, `u.expo.dev` and `assets.eascdn.net`); its Android release build trusts TLS for those hosts
  only. No analytics, crash reporting (expo-updates' `Expo-Fatal-Error` header is blanked), ads or accounts.
  Check the merged release manifest when adding a dependency (`expo-notifications` links Firebase; don't add it).
- **OTA updates are for fixes and polish** (App Review 2.5.2); new features ship in store builds.
- **A round is always 108.** There is no round-size setting.
- **Counting stays simple:** volume buttons count in SuperChant's own screen, and on the lock screen and in other
  apps once the accessibility service is set up; touch counting is one on/off toggle on the session screen. No input-mode chooser, no undo, no hold-to-stop, no focus mode,
  no pocket guard (all removed on purpose; see the 2026-10 PR history).
- **Google Play policy:** the in-app accessibility disclosure (`src/ui/AccessibilitySetup.tsx`) must be shown and
  agreed to before sending anyone to accessibility settings, and volume keys are only consumed with that consent.
  The service never reads screen content (`canRetrieveWindowContent=false`, no event types). If you change what
  the service or the `specialUse` foreground service does, the Play Console declarations, `PRIVACY_POLICY.md`
  and `docs/play-store/STORE_LISTING.md` must be updated to match.
- **iOS never counts volume buttons** (App Review 2.5.9). iPhone counts taps and shows a Live Activity.
- Keep it minimal and quiet: add a feature only if it clearly helps someone while they chant.

## Commands

Expo SDK 58 (React Native 0.88, expo-router), bun, TypeScript. Android needs JDK 17 and the Android SDK; iOS
needs Xcode. `ios/` and `android/` are generated (Continuous Native Generation, gitignored): configure them in
`app.config.ts` and the config plugins, never by hand. A fresh `prebuild` has no `android/local.properties`;
write `sdk.dir=$HOME/Library/Android/sdk` on the maintainer's Mac.

```bash
cd expo
bun install
bun run test            # TS cores in three time zones (UTC, New York, Kolkata)
bun run test:swift      # Swift model against the golden fixtures (swiftc, no Xcode needed)
bunx tsc --noEmit
bun run lint            # oxlint (.oxlintrc.json), warnings fail
python3 -m unittest discover -s scripts   # the release script's version helper
bunx expo prebuild --clean            # regenerate android/ and ios/
cd android && ./gradlew :superchant-native:testDebugUnitTest :app:assembleRelease
(cd android && ../scripts/network-guard.sh)  # after assembleRelease: permissions and the update-only TLS config
bunx expo run:ios --configuration Release   # needs Xcode
```

Release signing reads `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` from the untracked repo-root `local.properties` (`plugins/withSuperChantAndroid.js`).
Without them the release build is signed with the debug key, so it still builds and installs locally.

## Configuration

| Setting | Value |
|---------|-------|
| Package / bundle ID | `com.superchant` (Android and iOS) |
| minSdk / iOS deployment target | 26 / 17.0 (`expo-build-properties` in `app.config.ts`) |
| Version | Date-based, computed in `app.config.ts`: version 2.YYYY.DDD (IST day), versionCode / buildNumber YYDDDNN (`RELEASE_DATE=YYYY-MM-DD`, `BUILD_NUMBER=NN`; `expo/scripts/release-android.sh` takes NN one above the highest code Play has that day, `play_version.py`) |
| Android build types | `release` = `com.superchant` (Play); `debug` = `com.superchant.dev` "SuperChant Dev" (Metro); `preview` = the release code as SuperChant Dev with the debug key |
| Updates | `EAS_PROJECT_ID` (set in EAS) enables expo-updates; runtime version = fingerprint policy |

## Layout

```
expo/
├── app.config.ts                  # App config: versions, updates, plugins
├── plugins/withSuperChantAndroid.js  # Dev package, preview build type, shortcuts, backup rules, signing, icon, update-only TLS
├── fingerprint.config.js          # Runtime fingerprint skips (package.json scripts, .gitignore, Expo defaults)
├── scripts/                       # release-android.sh, network-guard.sh, play_version.py (+ test)
├── src/
│   ├── app/                       # expo-router screens: Home (index), Goals, goal-editor, History, Settings
│   ├── ui/                        # UI kit: Dialog, Menu, Snackbar, AccessibilitySetup, insights, home, native controls
│   ├── core/                      # Pure cores: kotlinx-compatible JSON, model and counting rules, stored lists,
│   │                              #   backup codec, stats, reminder plan, iOS repository; __fixtures__/golden.json
│   ├── native/                    # platform.{android,ios}.ts (the platform layer), updates.ts (early updates)
│   ├── state/store.ts, theme/, i18n/strings.ts
├── modules/superchant-native/
│   ├── android/src/main/java/com/superchant/   # The Android engine (Kotlin), class names from the Kotlin app
│   │   ├── AppShortcuts.kt        # Launcher shortcuts (static + per goal) and the START_COUNTING intent
│   │   ├── bridge/                # SuperchantNativeModule (the JS bridge), CounterView (native ring + tap pad)
│   │   ├── data/                  # ChantState (ROUND_SIZE = 108), ChantCounter, StoredList, Backup, PracticeStats,
│   │   │                          #   PreferencesRepository (DataStore; StateFlows + ordered `write {}` queue)
│   │   ├── service/               # ChantingService (foreground, owns the session), VolumeKeyAccessibilityService,
│   │   │                          #   VolumeKeyFilter, BeadClick, ChantTileService
│   │   ├── reminder/Reminder.kt   # Daily reminder: ReminderTime (pure), inexact alarm scheduler, receivers
│   │   └── ui/                    # Compose mala ring (CounterDisplay), TapToCountPad, theme and Motion tokens
│   ├── android/src/test/          # JVM tests (JUnit 4), incl. GoldenFixturesTest
│   ├── ios/                       # Swift: SessionEngine, ChantModel, Feedback (haptics, click), CounterView, LiveActivity
│   └── swift-tests/main.swift     # Swift model against golden.json
└── targets/widget/                # Live Activity extension (@bacons/apple-targets)
docs/play-store/, docs/app-store/  # Store listings, Play declarations, screenshots and the tools that made them
```

## How counting works

**The count loop is native on each platform; JavaScript never runs per count.**

Android (`modules/superchant-native/android`, the Kotlin app's engine moved into a local Expo module with its
class names unchanged, so an enabled accessibility service, an added tile and the DataStore files carried over):

1. Volume buttons count during a session in SuperChant's own screen, always. Outside it (lock screen, other
   apps) they count once the accessibility service is enabled and the user agreed to the disclosure
   (`accessibility_consent`). Google Pay (India) refuses to work while any unverified accessibility service is
   enabled, even one installed from Play, so the service is optional and the app says so. Touch counting is a
   per-session switch, `ChantState.touchCounting`, toggled from the hand icon on the session screen. Start never
   waits on a permission: when volume buttons can't count outside the app, the session starts with touch
   counting on and Home offers "Count with volume buttons outside the app" (disclosure first). The notification permission is requested, not
   required. Starts from launcher shortcuts and the Quick Settings tile arrive as a START_COUNTING intent
   and go through Home.
2. `ChantingService.start(context, goal, touchCounting)` starts the foreground service. Rounds are always
   `ChantState.ROUND_SIZE` (108).
3. Volume: `VolumeKeyAccessibilityService.onKeyEvent` (main thread) passes key events to `VolumeKeyFilter`, which
   offers each new press (DOWN, repeatCount 0) to `ChantingService.onVolumeKeyPressed()`. A counted press is
   consumed whole (DOWN, repeats, UP); presses that aren't counted (no session, paused, goal complete, or no
   disclosure consent) pass through to the system untouched. In the app, `bridge/SuperchantPackage.kt` (an
   autolinked Expo `ReactActivityHandler`) runs the activity's key events through another `VolumeKeyFilter`
   into `ChantingService.onForegroundVolumeKeyPressed()`; a press the service already counted (same
   `downTime`, delivered again after a stalled main thread) is consumed without counting twice.
   While the screen is off, Android sends volume keys straight to the media session and never to accessibility
   services, so volume counting needs the screen on (lock screen and other apps are fine). Don't claim
   screen-off counting in the app, listing or Play declarations.
   Touch: while touch counting is on, `TapToCountPad` calls `ChantingService.countFromScreen()` (one count per
   touch, extra fingers and repeats under 150 ms ignored). Home keeps the screen awake while touch counting is
   on, or while the accessibility service is off (in-app volume presses need the screen), unless paused or the
   goal is complete. Otherwise the screen may sleep.
4. Both inputs go through one `incrementCount()`, which applies `ChantCounter.increment` (rounds of 108,
   a milestone every 27 beads, goal mantra advance, no counting after the goal completes) and plays the same
   haptics/sounds (plus the optional bead click). There is no undo; Stop is a single tap.
5. Every change to an active session is written to the `active_session` DataStore. After process death the
   service restores it (START_STICKY restart, or the bridge when the app starts), and stopping saves it to
   history under its `sessionId`, so a session is recorded once. Sessions idle for 3+ hours are saved instead
   of resumed.
6. The ring and tap pad are a native Compose view (`CounterView`) that collects `ChantingService.chantStateFlow`
   itself; the bridge sends JS only coarse session changes (start, stop, pause, a round, goal progress),
   settings, goals and history. Keep per-count work out of JS and out of composition (animated values are read
   in draw/layer lambdas).

iOS (`modules/superchant-native/ios`): the session runs in Swift (`SessionEngine`, Core Haptics, the bead
click, a SwiftUI `CounterView`, the Live Activity with a `LiveActivityIntent` pause/resume button). Storage is
TypeScript (`src/core/repository.ts`, the Android repository's rules over UserDefaults). `targets/widget` is the
Live Activity extension; its `Shared.swift` copies `ChantActivityAttributes` and `TogglePauseIntent` from
`ios/LiveActivity.swift`, keep them identical.

Both: insights (streak, heatmap, per-mantra totals) are computed in JS from the stored history
(`src/core/stats.ts`); each goal session's `SessionRecord.mantras` holds its per-mantra breakdown. Removed
features leave stored data behind: prefs `input_mode`, `round_size`, `pocket_guard` and
`roundSize`/`inputMode`/`pocketGuard` fields in old sessions and backups are never read (JSON uses
`ignoreUnknownKeys`). Don't reuse those keys. A restored active session's rounds and goal progress are
recomputed from its count at 108 (`ChantState.normalizedRounds`).

## Data rules across Kotlin, TypeScript and Swift

- **Golden fixtures.** `modules/superchant-native/android/src/test/.../GoldenFixturesTest.kt` runs the Kotlin
  decoders, encoders and counting rules over edge cases and checks them against
  `src/core/__fixtures__/golden.json`; the TS and Swift tests replay the same file byte for byte. Change a rule
  in Kotlin, regenerate (in `expo/android`: `GOLDEN_WRITE=1 ./gradlew :superchant-native:testDebugUnitTest
  --tests "com.superchant.data.GoldenFixturesTest" --rerun`), and make TS and Swift match.
- New persisted fields need defaults so old JSON still decodes (`@Serializable` with defaults in Kotlin, the
  same in `src/core/model.ts` and `ChantModel.swift`). IDs are UUID strings; `LenientIdSerializer` still reads
  the numeric IDs of v1.1.0 data.
- Write Android preferences with `preferencesRepository.write { ... }`, not a caller's coroutine scope.

## Updates and the runtime fingerprint

Set `EAS_PROJECT_ID` (and optionally `EAS_UPDATE_CHANNEL`) to enable expo-updates; without it the build runs only
its embedded bundle. `runtimeVersion` is the fingerprint policy (`fingerprint.config.js` skips package.json
scripts and `.gitignore` on top of Expo's defaults, which skip the date-based version): an update reaches only
builds with the same fingerprint, so native changes (Kotlin/Swift including the module's tests, config plugins,
native dependencies) need a store build. Store builds always use the `production` channel; **early updates**
(tap the version in Settings 7 times in a row, `src/native/updates.ts`) move one install to `preview` with a saved header
override.

## Design and motion skills

Project-level agent skills live in `.agents/skills/` (tracked; `.claude/skills/` holds symlinks to them for
Claude Code, Codex reads `.agents/skills/` directly). `skills-lock.json` records their sources; update with
`bunx skills update -p`. They are web-oriented: in React Native follow `animate-expo`; in the native Compose ring translate CSS advice
to Compose (cubic-bezier → `CubicBezierEasing`, `:active` → `pressScale`, springs → `spring()`).

| Skill | Use it when |
|-------|-------------|
| `emil-design-eng`, `animate` | Adding or changing any animation or interaction: whether it should animate, easing, duration, enter/exit |
| `apple-design` | Springs, gestures, interruptible motion, haptics + visual timing, reduced motion |
| `animate-expo` | Mobile specifics: press-in feedback, haptics rules, 48dp targets, spring configs (ignore the Reanimated APIs) |
| `animation-performance` | Motion that janks; keep per-frame work out of composition |
| `find-animation-opportunities` | Asked where motion would help (it mostly says where it wouldn't) |
| `review-animations` | Reviewing a diff that touches motion (invoke explicitly) |
| `frontend-design` | Visual design of new or reshaped screens |

House rules from them: UI motion under 300 ms with `EaseOutStrong` (`src/theme/motion.ts`, `ui/Motion.kt` in the module), never ease-in, no
bounce outside gestures, nothing scales from 0, and the per-count path stays near-imperceptible (it runs 108
times a round).

## Conventions

- TypeScript: 2-space indent; Kotlin: 4-space indent, trailing-lambda Compose style.
- Reuse `src/ui` (Dialog, Menu, Snackbar, settings rows, controls) instead of copying UI.
- All UI strings go in `src/i18n/strings.ts` (plurals for counts); none are hardcoded. Android notification,
  tile and accessibility strings live in the module's `res/values/strings.xml`.
- Text and icons meet WCAG AA (4.5:1) in both themes; touch targets are at least 48dp.

## Verifying changes

Done means verified, not just compiled:

1. The commands above pass: typecheck, oxlint, TS tests, Swift vectors, the module's JVM tests, the release
   build and the network guard. Add tests for logic changes (`src/core/__tests__`, the module's `src/test`).
2. Exercise the change on an emulator with the **release** (or `preview`) APK (R8 can break serialization), and
   check that `adb logcat -b crash` stays empty. The maintainer's AVD is `Pixel_9_API_35_A`; in T3 Code open it
   with the device tools (`device_open`) and drive it with the returned agent-device CLI. Pass `-s <serial>` to
   adb. For iOS changes, run the Release build in the simulator.
3. For UI changes, take screenshots (light, dark, a small screen such as `adb shell wm size 720x1280`, and reset after).
4. Nothing but the native counter may change on each count. Don't add per-count work in JS or composition.

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
  push to `dev` or `main` directly. The repo lives on Entire (the old GitHub repo is gone); a PR is an Entire
  Trail. CI is Buildkite (`.buildkite/pipeline.yml`), which reports to the trail.
  - Every push: the typecheck, oxlint, TS tests and the version helper's tests (under a minute); when native code,
    plugins, dependencies or the golden fixtures changed, also the native module's JVM tests (`linux-large` with a
    cached Gradle home). The pipeline's default branch is `dev`, which `if_changed` compares against. The Expo CLI runs on Node there: under Bun the runtime fingerprint
    skips the config plugins and stops matching the store build. Run the Swift vectors, the release build and the
    network guard yourself before opening a trail.
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
- Releases go through Google Play only. **The owner keeps one app: the Play Store install.** Two gates put it
  ahead of everyone else: Play's internal testing list (the owner's Google account) gets every store build first,
  and early updates (on once, from Settings) put that install on the `preview` channel, so it runs `dev` within
  minutes of a merge while everyone else gets `main`. Updates can't target an email: no accounts, and Play
  promotes the same bundle to every track, so the channel is chosen on the device. Store builds are signed with the upload key
  that the repo-root `local.properties` points to (`RELEASE_*`; keystore and the Play service account JSON live in
  `~/.superchant-keys`, never committed). Play App Signing is on. A personal developer account needs a closed
  test (12+ testers, 14 days) before production.
- Try a branch before merging on the emulator (the `preview` or release APK). The owner's phone gets it after the
  merge into `dev`. Putting SuperChant Dev on the phone over adb (below) is only for a change that needs the real
  device, and it comes off again afterwards.

### Getting a build on the owner's phone

The phone is a Realme RMX3392 in India. Play Protect's enhanced fraud protection there blocks installing any APK
that declares an accessibility service from a browser, messaging app or file manager, so a downloaded
SuperChant APK never installs. Only Play and adb installs work.

1. **Play (the only app kept, `com.superchant`):** with early updates on, a merge into `dev` reaches the Play
   install as an update on its next launch (or two: it downloads, then applies). Native changes arrive with the next
   `release-android.sh` upload (Play may take a while to offer it; opening its Play page fetches it). Until an
   update exists for a new fingerprint, expo-updates logs "No launchable update" on each start and runs the
   embedded bundle; harmless. A sideloaded `com.superchant` (upload- or debug-signed) can't be updated by Play
   later, so never sideload the release package.
2. **SuperChant Dev (`com.superchant.dev`) over wireless adb, only when needed:** phone and Mac on the same Wi-Fi, then
   `./gradlew :app:assemblePreview` in `expo/android` and `adb -s <ip:port> install -r
   app/build/outputs/apk/preview/app-preview.apk`. Pairing is done once per phone:
   Developer options → Wireless debugging → Pair device with pairing code, then `adb pair <ip>:<pairing port> <code>`.
   The connect port changes when wireless debugging restarts, and adb's built-in mDNS discovery doesn't see the
   phone from this Mac. Find the phone's IP with a ping sweep and `arp -an`, and its open ports with a quick TCP
   scan of 1024-65535 (the connect port is the one Wireless debugging shows), then `adb connect <ip>:<port>`.
   QR pairing failed here (`protocol fault`), so use the pairing code. The pairing popup closes as soon as the
   phone leaves Settings, so read the code off the screen; the pairing port is the extra open port while the
   popup shows. Run adb outside the agent sandbox, which blocks local-network connections (`No route to host`).
   A USB cable with USB debugging also works.

Play release state (2026-10-28): the Expo build is on internal testing (versionCode 2630100 onwards); the
last Kotlin build, 2629900, is on closed testing (alpha). The app is still a "draft app" until its first review,
and the privacy policy URL in Play Console still points at the deleted GitHub repo.

- For JavaScript tooling (e.g. `bunx skills`), use bun.
