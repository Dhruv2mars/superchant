#!/bin/bash
# warmtrace.sh OUT : Perfetto trace of one warm start (activity destroyed via Recents, process alive)
export ANDROID_SERIAL=emulator-5554
OUT=$1; SD=~/perf-superchant/scripts; A=com.superchant/.MainActivity
adb shell am start -W -n $A >/dev/null; sleep 3
for try in 1 2 3 4; do
  adb shell input keyevent HOME; sleep 1; adb shell input keyevent KEYCODE_APP_SWITCH; sleep 2.5
  adb shell input swipe 540 1600 540 100 300; sleep 1.5
  adb shell dumpsys activity recents | grep -q "Recent #.*com.superchant" || break
done
adb shell input keyevent HOME; sleep 2
(cat $SD/trace.cfg; echo "duration_ms: 5000") > /tmp/sc_warm.cfg; adb push /tmp/sc_warm.cfg /data/misc/perfetto-configs/warm.cfg >/dev/null
adb shell perfetto --background --txt -c /data/misc/perfetto-configs/warm.cfg -o /data/misc/perfetto-traces/warm.pftrace >/dev/null; sleep 1.5
adb shell am start -W -n $A | grep -E "LaunchState|TotalTime" | tr '\n' ' '; echo
while adb shell pidof perfetto >/dev/null; do sleep 0.5; done
adb pull /data/misc/perfetto-traces/warm.pftrace $OUT >/dev/null
