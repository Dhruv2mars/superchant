#!/bin/bash
# strict.sh LABEL : debug build StrictMode violations over a fixed journey (10y data)
export ANDROID_SERIAL=emulator-5554
L=$1; SD=~/perf-superchant/scripts; OUT=~/perf-superchant/$L/strict; mkdir -p $OUT
adb shell settings put secure enabled_accessibility_services com.superchant/com.superchant.service.VolumeKeyAccessibilityService
$SD/restore.sh d10; adb logcat -G 16M; adb logcat -c; (adb logcat -v brief StrictMode:D *:S > $OUT/strict_live.txt &) ; sleep 1
adb shell am start -W -n com.superchant/.MainActivity >/dev/null; sleep 4
$SD/session.sh start; $SD/session.sh touchon; $SD/taps.sh 30 0.2; $SD/volpress.sh 10 200 >/dev/null; $SD/session.sh stop; sleep 2
for d in History Goals Settings; do $SD/tapl.sh $d; sleep 2; adb shell input keyevent BACK; sleep 1.5; done
adb shell am force-stop com.superchant; $SD/restore.sh d10; adb shell am start -W -n com.superchant/.MainActivity >/dev/null; sleep 4
adb logcat -d > $OUT/logcat.txt
pkill -f "logcat -v brief StrictMode"; cat $OUT/strict_live.txt | grep -oE "policy=[0-9]+ violation=[0-9]+|StrictMode policy violation[^:]*: [a-zA-Z.]+|android.os.strictmode.[A-Za-z]+" | sort | uniq -c | sort -rn
echo "total violations: $(grep -c 'StrictMode policy violation' $OUT/strict_live.txt)"
