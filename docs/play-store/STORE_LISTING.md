# SuperChant - Play Store listing

Source of truth for the Play Console main store listing (en-US). Keep it in sync with the app: every claim
here was checked against the code and on the `Pixel_9_API_35_A` emulator (release build, 2026-10-19).

Don't claim that volume buttons count **with the screen off**. While the screen is off, Android sends volume
keys straight to the media session and never to accessibility services (see AGENTS.md, "How counting works").
Lock screen and other apps are fine as long as the screen is on.

## App name (30 max)
```
SuperChant
```

## Short description (80 max)
```
A calm japa mala: count your mantras with the volume buttons or a gentle tap.
```
(77 characters. No "free", "best", "#1", "top", "discount" or other promotional words; the old
"Distraction-free" triggered Play's promotional-language warning because of "free".)

## Full description (4000 max)
```
SuperChant is a quiet digital japa mala. Count your mantras with your phone's volume buttons, without looking at the screen, or tap the screen to count. Every round is 108 beads, just like a mala.

COUNT WITHOUT LOOKING
• Press volume up or down to count one bead. During a session the buttons count instead of changing the volume.
• Keep counting from the lock screen or while another app is open, as long as the screen is on.
• A short vibration on each bead, a fuller pulse every 27 beads, and a stronger one when a round is complete.
• An optional soft bead click.
• Pause or resume from the app, the notification or the Quick Settings tile. The tile and the notification show your live count.

TOUCH COUNTING
• Turn on touch counting with one switch on the session screen, then tap anywhere on the counter.
• While touch counting is on, the screen stays awake.
• Touch counting needs no special permission.

108 BEADS, ONE ROUND
• A mala ring of beads with a meru bead fills as you chant.
• Rounds are always 108, so your counts mean the same thing every day.

GOALS FOR YOUR PRACTICE
• Create a goal with one or more mantras, each with its own number of rounds.
• SuperChant moves to the next mantra when one is done and tells you when the goal is complete.
• Start a goal from the Goals screen or from its launcher shortcut.

SEE YOUR PRACTICE GROW
• Set a daily target in rounds. Days that reach it build your streak.
• A heatmap of the last four months shows the days you practiced.
• Lifetime totals for each mantra, and a history of every session with its counts, rounds and time.

A GENTLE REMINDER
• An optional daily reminder at the time you choose. It stays quiet once today's practice is done.

YOUR DATA STAYS WITH YOU
• No account, no ads and no analytics. SuperChant has no internet permission, so it can't send your data anywhere.
• Export your history, goals and settings to a file you choose, and import it on a new phone.
• Android's own device backup can also carry your practice to a new phone.

ABOUT THE VOLUME BUTTONS
To count with the volume buttons, SuperChant uses an Android accessibility service. Before you turn it on, the app explains what the service does and asks you to agree. The service only listens for volume button presses during a session that is running and not paused. It can't read your screen or see what you type, and it collects nothing. You can use touch counting without it.

For japa meditation, mantra chanting, naam jap, prayer counting and any practice of repetition.
```
(2523 characters.)

## Release notes (500 max per language)

For the first internal-testing release, version `2.YYYY.DDD` (date-based, see AGENTS.md). Paste with the
language tags as shown:
```
<en-US>
SuperChant 2.0
• Count with the volume buttons, or turn on touch counting with one switch
• Every round is 108 beads, with an optional fuller pulse every 27
• Goals with several mantras and lifetime totals per mantra
• Daily target, streak and a heatmap of the days you practiced
• Optional daily reminder
• Export and import a backup file
• Quick Settings tile and launcher shortcuts
</en-US>
```

## Graphics

| Asset | File | Notes |
|-------|------|-------|
| App icon | `docs/play-store/icon-512.png` | 512 x 512, 32-bit PNG, already uploaded |
| Feature graphic | (in Console) | 1024 x 500, already uploaded |
| Phone screenshots | `docs/play-store/screenshots/phone/01..08-*.png` | 8 x 1080 x 1920 (9:16), 24-bit PNG, upload in file order |
| Raw captures | `docs/play-store/screenshots/raw/*.webp` | 1080 x 2424 originals, not for upload |

The raw emulator captures are 1080 x 2424. Play rejects phone screenshots whose long side is more than twice
the short side (2424 / 1080 = 2.24), and promotion needs 9:16, so the upload set frames each capture in a
1080 x 1920 canvas with a short caption. How they were made is in `docs/play-store/tools/README.md`.

| # | File | Caption | Shows |
|---|------|---------|-------|
| 1 | `01-volume-buttons.png` | Count each bead with a volume button | Goal session "Morning Sadhana", 41 / 108, mantra list open |
| 2 | `02-touch-counting.png` | Or turn on touch counting and tap | Touch counting on, 33 / 108 |
| 3 | `03-insights.png` | Your streak, and every day you practiced | All-time stats, 23-day streak, heatmap, per-mantra totals |
| 4 | `04-history.png` | Every session, kept on your phone | History list grouped by week and month |
| 5 | `05-goals.png` | Goals across several mantras | Two goals with real mantras |
| 6 | `06-settings.png` | A daily target and a gentle reminder | Settings: volume buttons, daily target, reminder, theme |
| 7 | `07-dark-session.png` | A dark theme for early mornings | The goal session in dark theme |
| 8 | `08-private.png` | No account, no ads, no internet | Insights in dark theme |

## Category

**Recommendation: Lifestyle** (currently Tools).

Google Play has no Religion & Spirituality category; prayer, scripture and mala/tasbih counter apps sit
mostly in Lifestyle, so that's where people browse for them. Tools is meant for device utilities (file
managers, flashlights), where a chanting app is out of place. Health & Fitness, where meditation apps like
timers live, is the other option, but SuperChant makes no health claims. Health & Fitness also puts it under
Play's health-app scrutiny, with nothing to gain. Changing category doesn't affect the content rating or any
declaration. (This category survey is from general knowledge of the store, not a Play data source;
unconfirmed.)

## Tags
Choose up to 5 from Play's predefined list in Store settings (the list varies; pick the closest matches):
meditation, mindfulness, religion/spirituality, habit tracker, counter.

## Contact details
Email is set in Play Console (Store settings > Store listing contact details); not kept in this repo.
No website or phone is needed.

## Privacy policy URL
```
https://github.com/dhruv2mars/superchant/blob/main/PRIVACY_POLICY.md
```
The URL points at `main`, so policy changes go live only once they're merged to `main` (the default branch is
now `dev`).

## Declarations
Accessibility API, foreground service and the re-check of the other App content answers:
see [`DECLARATIONS.md`](DECLARATIONS.md).
