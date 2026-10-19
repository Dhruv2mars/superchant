#!/bin/bash
# mem.sh LABEL REPS : meminfo after cold start on Home (10y data), after opening History, and after 200 counts
export ANDROID_SERIAL=emulator-5554
L=$1; REPS=${2:-3}; SD=~/perf-superchant/scripts; OUT=~/perf-superchant/$L/mem; mkdir -p $OUT; : > $OUT/mem.txt
m(){ adb shell dumpsys meminfo com.superchant | awk '/TOTAL PSS:/{print "pss_kb="$3} /Java Heap:/{print "java_kb="$3} /Native Heap:/{print "native_kb="$3} /Graphics:/{print "gfx_kb="$2}' | tr '\n' ' '; }
for i in $(seq 1 $REPS); do
  adb shell settings put secure enabled_accessibility_services com.superchant/com.superchant.service.VolumeKeyAccessibilityService
  $SD/restore.sh d10; adb shell am start -W -n com.superchant/.MainActivity >/dev/null; sleep 5
  echo "rep $i home $(m)" >> $OUT/mem.txt
  adb shell kill -10 $(adb shell pidof com.superchant); sleep 3; echo "rep $i home_after_gc $(m)" >> $OUT/mem.txt
  $SD/tapl.sh History; sleep 4; echo "rep $i history $(m)" >> $OUT/mem.txt
  adb shell input keyevent BACK; sleep 2
  $SD/session.sh start; $SD/session.sh touchon; $SD/taps.sh 200 0.06; sleep 3
  echo "rep $i counted $(m)" >> $OUT/mem.txt
  $SD/session.sh stop
done
cat $OUT/mem.txt
