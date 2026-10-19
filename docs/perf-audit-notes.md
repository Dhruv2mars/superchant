# Perf audit: state at pause (2026-10-19)

Paused by the owner; resume as the last pass before release. Branch `perf/extreme-audit`, rebased on `main`
1d03770. No PR yet. The top commit is **WIP** (notification timing experiment, see Open questions).

## Commits (each compiles and passes `test` on its own; checked before the WIP commit)

1. trace sections (`SC.*`) + `reportFullyDrawn` (measurement only)
2. debug StrictMode logging + `SC.ringDraw` section
3. stored lists: one streaming decode/encode pass (lenient fallback kept)
4. backup codec off the main thread
5. history and goals in their own DataStore; one-time `moveHistory` (not a DataMigration, see finding 5)
6. history stats once per change on `Dispatchers.Default` (`historyStats`)
7. `LatestWins` for per-count active-session writes
8. binder calls off the count path (haptics thread; notification posted in background with a generation lock)
9. ring `drawWithCache` + own layer; pad press read only in draw (snapshotFlow-driven Animatable)
10. shortcuts/reminder sync off main
11. one pair of date formats for history rows
12. baseline + startup profile, `:baselineprofile` Macrobenchmark module (profile regenerated on rebased code,
    *before* the pad-press fix and the WIP commit: regenerate in the final pass)
13. AGENTS.md
14. WIP: notification posted right after the frame that shows the count (`awaitFrame(); yield()`)

## How it was measured

Release builds on `Pixel_9_API_35_A` (userdebug, root). Before = `b67af32` + commits 1-2 (same instrumentation);
after = this branch. A/B runs alternate (reinstall + recompile per block) because emulator drift between
sessions was larger than most effects. Datasets: `d0` (fresh) and `d10` (10 years, 10,432 sessions, imported from
a generated backup, 2.1 MB store). Scripts in `docs/perf-audit/scripts/` (paths assume `~/perf-superchant`):
`ab_cold.sh` (startup A/B), `ab.sh` + `scenario.sh` (Perfetto + gfxinfo scenarios), `analyze.py`/`cmp.py`
(metrics), `taps.sh` (raw virtio touchscreen taps), `volpress.sh` (uinput volume keys), `restore.sh` (datastore
snapshots). Compilation states: `fresh` = baseline profile only (store install), `profile` = mature.
Caveats: agent-device's snapshot helper resets `enabled_accessibility_services` (use `find.sh`/uiautomator);
emulator frames are GPU-bound (~30 ms swap), so compare main-thread time, latency sections and frame counts.

## Before → after (medians, interleaved A/B)

Startup, `am start -W` TotalTime (TTID) / Fully drawn (TTFD), ms:

| State | Data | Cold TTID | Cold TTFD | Warm | Hot |
|---|---|---|---|---|---|
| fresh | empty | 764.5 → 683 (−11%) | 764.5 → 711 | 225.5 → 273 (+21%) | 161.5 → 129 |
| fresh | 10 y | 722.5 → 683.5 (−5%) | 770 → 793 (+3%) | 231.5 → 282 (+22%) | 145.5 → 162.5 |
| mature | empty | 659.5 → 598 (−9%) | 659.5 → 624 | 253 → 216 (−15%) | 155 → 150.5 |
| mature | 10 y | 745.5 → 719.5 (−3%) | 800 → 816 (+2%) | 283 → 304 (+7%) | 136.5 → 154.5 |

(n = 10 cold/hot, 8 warm per side. Hot is bimodal on the emulator, ±100 ms; treat as noise.)

Interaction scenarios (3 interleaved runs each; 10-year data):

| Scenario | Metric | Before | After |
|---|---|---|---|
| Touch counting 30 s (after pad fix) | count→draw p50 / p90 | 5.85 / 15.6 ms | 3.92 / 15.2 ms |
| | `SC.increment` p50 / p90 (main thread) | 1.28 / 7.42 ms | 0.14 / 1.22 ms |
| | main-thread binder | 714 ms | 0 |
| | `Compose:recompose` | 604 | 451 (−25%) |
| | main-thread CPU | 2,038 ms | 2,074 ms (+2%) |
| Volume counting 10/s, 30 s | count→draw p50 / p90 | 4.80 / 14.4 ms | 2.72 / 12.0 ms |
| | key event p90 | 3.46 ms | 0.42 ms |
| | main-thread CPU / binder | 2,452 / 1,102 ms | 2,215 / 0 ms |
| | GC time | 220 ms | 154 ms |
| Navigation ×9 | longest main task / GC | 88 / 92 ms | 68 / 60 ms |
| History flings | main CPU per frame / longest task | 5.3 / 126 ms | 3.4 / 81 ms |
| Import 10-year backup | main-thread CPU | 506 ms | 106 ms (−79%) |
| Idle 1 press/s, 55 s | `SC.increment` p50 | 8.4–9.4 ms | 0.6–0.7 ms |

Other: settings file 2.1 MB → 43–72 B after the move; JVM bench (10,432 sessions) decode 18.1 → 8.2 ms,
encode 13.9 → 5.3 ms, stats 3.9 → 2.1 ms; StrictMode (debug journey): 0 violations before (after: not yet run).
Memory before (10 y, Home 5 s after cold start): PSS 70 MB, Java 44 MB (JSON garbage), after GC 35 MB; after: not
yet run. APK: 1,869,853 → 1,984,599 B (+6%, dex split for startup layout), methods 18,166 → 23,590 (refs counted
per dex). Upgrade from a `main` release (history, goals, running session) to this branch: nothing lost or
duplicated, session restored, export identical except the new session, re-import adds 0, kill -9 restore OK,
no notification left after Stop (5/5), `logcat -b crash` empty.

## Open questions for the final pass

1. **SystemUI CPU during a slow session (1 press/s).** Posting the notification ≥50 ms after a count:
   +34–45% SystemUI CPU (6 runs, both A/B orders, same number of posts; every SystemUI op ~1.5–1.9× slower,
   i.e. overlap with our animation frames). Posting immediately in the background: parity (−3%, +0.1%), but
   count→draw p50 +11% at 1/s because the build lands before the count's frame. WIP commit posts right after
   that frame: only 2 runs so far, count→draw p50 −7%, SystemUI +27% (not conclusive). Decide with ≥5
   interleaved runs per variant; candidates are WIP vs "dispatched, no delay".
2. **Warm start** (activity recreated in a live process): +21–22% in `fresh`, −15% / +7% in `mature`. Not
   explained; app main-thread work on that path should be lower. Trace both builds with `warmtrace.sh` and
   compare main-thread slices from activity start to first frame.
3. Cold TTFD with 10 years of history is at parity (+2–3%): decode (~200 ms CPU, contended) then stats, then
   a frame. The baseline computed TodayLine inline in that frame. Ideas: start stats earlier, or cheaper decode.
4. Still to do: regenerate the profile on the final code, memory + StrictMode on the final build, functional
   smoke run + light/dark/small screenshots, evidence page (`html.py`) on `ux-evidence/perf-extreme-audit`, PR.

## Findings

See `docs/perf-audit/findings-draft.md` (fixed and rejected findings with evidence).
