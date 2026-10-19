# SuperChant - Play Console declarations

Texts for **Policy and programs > App content** in Play Console, checked against the code and the release build
on the `Pixel_9_API_35_A` emulator (2026-10-19). If the accessibility service or the foreground service
changes, update this file, `PRIVACY_POLICY.md` and `STORE_LISTING.md` together (AGENTS.md, product rules).

Free-text answers are kept under 500 characters each so they fit Play's text boxes.

Demo videos (both recorded on the emulator with the release APK, captioned, under 60 s) live in
`docs/play-store/videos/` (gitignored) and are uploaded to YouTube as **Unlisted**:

| Video | File | YouTube title | Length |
|-------|------|---------------|--------|
| Accessibility API | `superchant-accessibility-api.mp4` | SuperChant – Accessibility API use | 52 s |
| Foreground service | `superchant-foreground-service.mp4` | SuperChant – Foreground service use | 41 s |

Play needs a link it can open, so use the `https://www.youtube.com/watch?v=...` form, not `/shorts/`.

## 1. Accessibility API

What the app does (verified):

- `VolumeKeyAccessibilityService` with `accessibility_service_config.xml`: `flagRequestFilterKeyEvents`,
  `canRequestFilterKeyEvents="true"`, `canRetrieveWindowContent="false"`, no `accessibilityEventTypes`, no
  `isAccessibilityTool` (so false).
- `onKeyEvent` hands key events to `VolumeKeyFilter`. Any key other than volume up/down returns `false`
  right away and isn't stored. A volume press is offered to `ChantingService.onVolumeKeyPressed()`, which counts
  it only while a session is running, not paused, the goal isn't complete, **and** the user agreed to the in-app
  disclosure (`accessibility_consent`). A counted press is consumed whole (down, repeats, up). Every other press
  passes through and changes the volume as usual.
- `onAccessibilityEvent` does nothing. The service never performs gestures or global actions.
- **Prominent disclosure first:** "Count with volume buttons" (Home) or "Set up" (Settings) opens
  `AccessibilityDisclosureDialog` ("Before you turn on volume counting", with what it does, what it never does,
  and the buttons I agree / Not now). Only "I agree" stores consent and opens Accessibility settings.
- Works in the app, in other apps and on the lock screen **while the screen is on**. With the screen off,
  Android sends volume keys to the media session and never to accessibility services (verified in logcat:
  `MediaSessionService: Nothing is playing on the music stream. Skipping volume event`), so the declaration
  doesn't claim screen-off counting.

### Is your app an accessibility tool?
**No.** SuperChant isn't designed for people with disabilities, so `isAccessibilityTool` stays false and the
app relies on the prominent disclosure and consent instead.

### Core functionality that uses the Accessibility API
```
SuperChant is a japa mala (chant counter). Its core feature is counting each repetition of a mantra with a press of the phone's volume up or down button, so the user can chant without looking at or touching the screen, including from the lock screen or while another app is open. The accessibility service only filters volume key events: during a running, unpaused chanting session it turns each press into one count instead of a volume change.
```

### Why the Accessibility API is needed (no alternative)
```
No other public API lets an app receive volume button presses while it isn't the focused window. Activity key callbacks only work while SuperChant's own screen is focused. MediaSession volume handling only reaches an app that is actively playing audio, which would mean playing silent audio all session and taking audio focus from the user's chant recordings. Key-event filtering (flagRequestFilterKeyEvents) is the only way to count presses on the lock screen or in other apps.
```

### What the service does not access or do
```
The service declares no accessibility event types, sets canRetrieveWindowContent="false", and never reads the screen or other apps. It doesn't perform gestures or actions and doesn't change settings. It ignores every key other than volume up/down and keeps no record of any key. It only consumes volume presses that it counts during a running, unpaused session; at all other times the buttons change the volume as usual. SuperChant has no internet permission and collects no data.
```

### Prominent disclosure and consent (if asked)
```
Before the user is sent to Accessibility settings, SuperChant shows a full-screen dialog, "Before you turn on volume counting". It says what the service does (detects volume presses only during a running session and turns them into counts) and what it never does (read the screen or other apps, collect or share data, perform actions). Only after the user taps "I agree" does the app open settings and count volume presses. "Not now" keeps touch counting, which needs no permission.
```

### Data collected through the Accessibility API
None. Nothing is stored, logged or sent (the app has no INTERNET permission).

### Video
"SuperChant – Accessibility API use": Start → touch counting on by default → "Count with volume buttons" →
disclosure → I agree → setup dialog → Accessibility settings → SuperChant Volume Counter → On → Allow → back in
the app, volume presses count without changing the volume → app in the background → live count in the
notification → lock screen. 23 presses, 23 counts.

## 2. Foreground service: `FOREGROUND_SERVICE_SPECIAL_USE`

What the app does (verified):

- `ChantingService`, `android:foregroundServiceType="specialUse"`, property
  `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE = chanting_counter`. Permissions: `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE` (merged release manifest checked with `aapt2 dump permissions`).
- Started only by a user action: Start on Home, Start Session on a goal, a launcher shortcut or the Quick
  Settings tile (the tile opens the app, which starts the session). It enters the foreground immediately
  (`FOREGROUND_SERVICE_IMMEDIATE`), with an ongoing, silent notification showing `count / 108`, the round or
  goal mantra, a progress bar, and Pause/Resume and Stop.
- It ends on Stop (notification, app or tile flow), which saves the session to history and calls
  `stopForeground(REMOVE)` and `stopSelf()`. A session idle for 3+ hours is saved, not resumed, after a restart.

### Foreground service type / task
- Type: **Special use** (`specialUse`), subtype `chanting_counter`.
- If Play lists task types, choose **Other** and use the texts below.

### Describe the user-initiated task
```
A chanting session is started by the user (Start button, a goal, a launcher shortcut or the Quick Settings tile). While it runs, the user counts mantras with the volume buttons, often from the lock screen or while another app is open. The foreground service holds the session state, turns each press into a count with a vibration, and shows the live count with Pause/Resume and Stop in its notification. It runs only until the user taps Stop.
```

### Why no other foreground service type or API fits
```
Counting happens in real time while SuperChant isn't visible, and every press must give immediate vibration feedback, so the work can't be deferred or batched (WorkManager, JobScheduler). No standard foreground service type fits: it isn't media playback, location, a phone call, health or fitness sensor data, connected-device, data sync or short-service work, and a session lasts as long as the user chants, often more than an hour.
```

### Impact if the task is deferred or interrupted
```
If the service were stopped or deferred, volume presses would stop counting mid-session without the user noticing, and they would lose their place in a round of 108 or in a goal of several mantras. Presses would change the phone volume instead.
```

### Video
"SuperChant – Foreground service use": Start → session notification → volume presses count in the app and with
the app in the background → live count in the notification → Pause and Resume from the notification → live count
on the Quick Settings tile → pause/resume from the tile → Stop from the notification ends the service and saves
the session.

## 3. Re-check of the 10 declarations completed on Jan 3, 2026

Recommended answers for today's app (`2.YYYY.DDD`, target SDK 36). "Saved" is what the Console showed in the
Oct 19 read-only audit, where captured.

| Declaration | Correct answer today | Why (evidence) |
|-------------|----------------------|----------------|
| Privacy policy | `https://github.com/dhruv2mars/superchant/blob/main/PRIVACY_POLICY.md` | URL returns 200; repo is public. Policy updated Oct 19 (on `dev` until merged to `main`) |
| Ads | No, the app has no ads | No ad SDKs; no INTERNET permission |
| App access | All functionality is available without special access | No login or account; accessibility setup is user-controlled and shown in the videos |
| Content rating | Reference/utility-type app; "No" to violence, sexual content, language, controlled substances, gambling; no user interaction or content sharing, no location sharing, no digital purchases → Everyone / PEGI 3 | Static counter; no user-generated content |
| Target audience and content | 13+ is fine (saved: 13–15, 16–17, 18+). Not designed for children; no appeal-to-children elements | Avoids the Families policy; keep as is |
| News app | No | |
| Data safety | **No data collected, no data shared** (keep) | See below |
| Government apps | No | |
| Financial features | None | |
| Health apps | My app has no health features (keep) | A mantra counter: no health data, sensors or health claims. Judgement call: meditation-type features are listed in Play's health categories, but SuperChant tracks counts of a religious practice, not wellbeing |
| Advertising ID | No, the app doesn't use advertising ID | No `com.google.android.gms.permission.AD_ID` in the merged manifest; no Play services |

### Data safety reasoning ("does not collect or share")
- **No collection.** Play defines collection as transmitting data off the device from your app. SuperChant has no
  INTERNET permission and no SDKs that send data, and all processing stays on the device.
- **Android Auto Backup** (`allowBackup=true`, `datastore/` and shared prefs included in cloud backup and
  device transfer) is done by Android into the user's own Google account. The developer never receives or can
  access it, the same as the "uploads to the user's own cloud storage" case in Play's Data safety FAQ, where the
  app "never collects or accesses the data in question". The privacy policy discloses it.
- **Export/Import** writes or reads a JSON file only when the user taps Export/Import and picks the location in
  the system file picker (SAF). That is a user-initiated transfer, which Play exempts from "sharing" ("a specific
  user-initiated action, where the user reasonably expects the data to be shared").
- Notifications, the daily reminder (inexact alarm, local notification) and `RECEIVE_BOOT_COMPLETED` (re-arm
  the reminder) all stay on the device.
- Unconfirmed: Play's help doesn't mention Android Auto Backup specifically. The treatment above is the common
  reading (checked support.google.com/googleplay/android-developer/answer/10787469 on Oct 19).
