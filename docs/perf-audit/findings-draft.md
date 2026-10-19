## Findings

| # | Finding | Evidence (baseline) | Fix | Measured effect (after, A/B) |
|---|---|---|---|---|
| 1 | Every count made binder calls on the main thread inside the key event/tap: the vibrate call and, up to 4×/s, the notification post (plus ringtone load at round/goal ends) | `SC.increment` p90 6.6–7.4 ms; main-thread binder 680–1,100 ms per 30 s of counting | Haptics + bead click on a dedicated high-priority thread; notification built on main right after the event and posted from a background thread (generation counter + lock so a post can never resurrect it after Stop); ringtones load off main after the haptic | `SC.increment` p50 −89–93%; main-thread binder −100%; count→draw p50 −33 to −44%; key event p90 −88% |
| 2 | Pad press state was read in composition: 2 extra recompositions (and semantics passes) per tap | `Compose:recompose` 604 per 150 taps | Press written by the gesture, read only by a `snapshotFlow` that drives an `Animatable` read in draw (same-frame press/release still cancel out like `animateFloatAsState`) | recompositions −25%; main-thread CPU at parity |
| 3 | Ring recomputed geometry, 108 bead positions (216 sin/cos) and strokes every frame, and was re-recorded by the pad's press overlay | code + `SC.ringDraw` | `drawWithCache` (per size/count) + own `graphicsLayer` | per-frame ring work cached; ring re-records only for its own changes |
| 4 | History and goals lived in `superchant_prefs`: the first frame (held until the theme is read) waited for megabytes, and every settings toggle rewrote them | settings file 2.1 MB with 10 years of history | Own `history` DataStore; `moveHistory` moves 1.1.x data once (copy, merge by ID if needed, then remove) | settings file 2.1 MB → 43–72 B; cold TTID −26 to −81 ms |
| 5 | (Rejected design) a DataStore `DataMigration` for the move re-reads its file under a lock at every start | +87 ms TTID, +51 ms TTFD (10 runs) | replaced by the one-time move | regression removed |
| 6 | History JSON decoded via a JsonElement tree, and encoded the same way | JVM bench: tree decode 18.1 ms, encode 13.9 ms (10,432 sessions) | single streaming `ListSerializer` pass, lenient fallback | decode 8.2 ms (2.2×), encode 5.3 ms (2.6×); GC time −17 to −35% |
| 7 | Stats (daily totals, streak, mantra totals, all-time totals) recomputed in composition on main: every return to Home, every Stop, every History visit/scroll-back | nav max main task 88 ms; history max 126 ms | `PreferencesRepository.historyStats` once per history change on `Dispatchers.Default`; one-pass, run-length daily totals | history: main CPU/frame −36%, longest task −35%; nav longest task −23% |
| 8 | Backup import merge (DataStore edit transform runs in the caller's context) and backup encode/decode ran on main | import: 506 ms main CPU, 104 ms longest task | `Dispatchers.Default` for codec + repository import/export | import main CPU −79% |
| 9 | Each history row built two `SimpleDateFormat`s as it scrolled in | code | one pair per screen | (part of #7's history numbers) |
| 10 | Goal shortcuts republished and reminder alarm re-armed on the main thread on every activity start | binder calls on main at startup | off main; shortcuts only when goals/locale changed in this process | fewer main-thread binder calls at start |
| 11 | No app baseline/startup profile (only library rules) | 7.7 KB `baseline.prof` | Macrobenchmark `:baselineprofile` module, profile + startup profile (dex layout) checked in; profileinstaller declared | store-install cold TTID −39 to −81 ms |
| 12 | Per-count active-session writes queue up behind slow writes (e.g. a 2 MB history save) | 1 fsync'd write per count | `LatestWins`: a waiting write saves the newest state; order and durability unchanged | no change at normal pace (by design); collapses backlogs |

### Rejected or not fixable

| Candidate | Why not |
|---|---|
| Debounce the active-session write | Would lose the last counts on process death (AGENTS.md durability rule) |
| Skip notification updates with the screen off | No counts arrive with the screen off on this device (0 `SC.increment` in 3 runs) |
| Post the notification ≥50 ms after a count | Measured +34–45% SystemUI CPU (6 runs, both orders); posting right after the event is at parity |
| `localeFilters = en` (−200 KB resources) | Would change library UI strings (behavior change) |
| Compose accessibility semantics diffing on every count | Happens because the app's own a11y service makes Compose treat accessibility as on; framework behavior, fixing it would hurt TalkBack |
| Fewer frames per count | Frames come from the pulse/progress/press motion; motion must not change |
| Lenient numeric-ID parsing cost | JVM bench: no difference vs plain strings |
| Remove root `Surface` overdraw | Emulator is GPU-bound regardless; visual risk without measurable win |
| History bucketing on main | 0.19 ms for 10,432 sessions (JVM) |
| material-icons-extended | R8 strips it (0 icon classes in the release mapping) |
