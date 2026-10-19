# Store asset tools

How the Play Store screenshots and declaration videos were made (Oct 19, 2026). Python 3 with Pillow,
ffmpeg, and adb against the `Pixel_9_API_35_A` emulator running the **release** APK.

1. **Seed data.** `python3 gen_seed.py store-seed.json` writes a SuperChant backup: 67 sessions from Aug 24 to
   Oct 19 2026 (IST), a 23-day streak against a daily target of 4 rounds, and two goals ("Morning Sadhana":
   Om Namah Shivaya x4, Gayatri Mantra x1, Mahamrityunjaya Mantra x2; "Evening Japa": Hare Krishna Mahamantra
   x4, Om Namo Narayanaya x2). Push it to `/sdcard/Download/` and import it from Settings > Your data > Import.
   The dates are fixed, so set the emulator date to Oct 19, 2026 or edit `today` in the script.
2. **Clean status bar.** `adb shell settings put global sysui_demo_allowed 1`, then the
   `com.android.systemui.demo` broadcasts: `enter`, `clock -e hhmm 0941`, `battery -e level 100 -e plugged false`,
   `network -e wifi show -e level 4 -e fully true`, `network -e mobile hide`, `notifications -e visible false`.
3. **Volume keys.** Use the uinput recipe in AGENTS.md. Keep one `adb shell uinput -` process fed from a FIFO
   so presses can be sent on demand. Stop the agent-device snapshot helper first
   (`adb shell am force-stop com.callstack.agentdevice.snapshothelper`): its UiAutomation connection
   suppresses other accessibility services, so volume presses stop counting while it runs.
4. **Raw captures.** `adb exec-out screencap -p` at 1080 x 2424, saved as lossless WebP in `../screenshots/raw/`.
   Dark variants: `adb shell cmd uimode night yes` with the app theme on System.
5. **Store frames.** `frame_spec.json` lists `[raw png, output png, caption]` (decode the raw WebP to PNG
   first); `python3 frame.py frame_spec.json` renders 1080 x 1920 (9:16) frames into `../screenshots/phone/`:
   teak canvas, Iowan Old Style caption, the capture at 72 % bleeding off the bottom edge.
6. **Videos.** `adb shell screenrecord --time-limit 60`, driven by `adb shell input tap` (not agent-device
   snapshots, see step 3). Install with `adb install -i com.android.vending` so the app shows the Play-install
   setup dialog instead of the sideload "restricted settings" one. `python3 caption.py in.mp4 out.mp4 spec.json`
   adds a caption band above the recording (`spec.json`: `[[start, end, "text"], ...]` in seconds).
