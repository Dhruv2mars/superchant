# SuperChant UX evidence

Screenshots from emulator Pixel 9 API 35 for PR feat/ux-improvements. Not meant to merge.

## feat-touch-toggle-simplify/

Release APK on Pixel_9_API_35_A for PR feat/touch-toggle-simplify (touch-counting toggle, fixed 108).
`00-*` are from the #5-era build (main @ fd4ac73) before the in-place upgrade; `34-toggle-transition.mp4`
is the on/off transition at full frame rate.

## fix-deep-audit-remediation/

Release APKs on Pixel_9_API_35_A for PR fix/deep-audit-remediation (deep-audit findings F1-F16 and the two
session-lifecycle races). "unfixed" = dev @ c34403b, "fixed" = the PR branch. `notes/` holds the command output
behind every check (before/after where the bug reproduces); `notes/f8-*` is the main-thread timing of backup
export/import with 3,076 sessions.

| File | Shows |
|------|-------|
| `f16-unfixed-small-light.png` | 720x1280: the title wraps to "SuperChan / t" |
| `f16-fixed-{small-light,small-dark,light,dark}.png` | the title stays on one line, small and normal screens, both themes |
| `f9-{unfixed,fixed}-{ist,ny}.png` | History open while the timezone changes IST -> New York: unfixed keeps 9:03 PM, fixed shows 11:33 AM |
| `f6-{unfixed,fixed}-{1-dialog,2-after-allow}.png` | Start -> notification prompt -> rotate -> Allow: unfixed drops the start, fixed starts the session |
| `f10-1-before.png`, `f10-2-after-move-actions.png` | mantra order before and after the accessibility "Move down"/"Move up" actions |
| `f1-blank-name-goal.png` | fixed build with a stored empty-name goal: launches and lists it (unfixed crashed at launch) |
| `f3-stale-session-saved.png` | a session idle for 4 h is saved to history on launch, not resumed |
| `f7-f14-reimport.png` | importing the same crafted file again adds 0 sessions (ID-less entries get content IDs, invalid goals skipped) |
| `race-a-fixed.png` | Stop then an immediate reopen: the stopped session stays stopped |
| `race-b-fixed-shortcut.png`, `race-b-fixed-tile.png` | a cold plain shortcut or Quick Settings tile resumes the recoverable goal session |
