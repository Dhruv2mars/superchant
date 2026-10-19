#!/bin/bash
# startup.sh cold|warm|hot N : am start -W TotalTime and Fully drawn per run, then medians
export ANDROID_SERIAL=emulator-5554
P=com.superchant; A=$P/.MainActivity; MODE=$1; N=${2:-10}
A11Y=com.superchant/com.superchant.service.VolumeKeyAccessibilityService
adb shell settings put secure enabled_accessibility_services $A11Y
if [ "$MODE" = warm ]; then adb shell am start -W -n $A >/dev/null; sleep 2; fi
adb shell settings put secure accessibility_enabled 1
tt=(); fd=()
for i in $(seq 1 $N); do
  case $MODE in
    cold) adb shell am force-stop $P; sleep 1;;
    warm) for try in 1 2 3 4; do
            adb shell input keyevent HOME; sleep 1; adb shell input keyevent KEYCODE_APP_SWITCH; sleep 2.5
            adb shell input swipe 540 1600 540 100 300; sleep 1.5
            adb shell dumpsys activity recents | grep -q "Recent #.*com.superchant" || break
          done
          adb shell input keyevent HOME
          sleep 2; adb shell pidof $P >/dev/null || echo "warn: no process";;
    hot) adb shell am start -W -n $A >/dev/null; sleep 2; adb shell input keyevent HOME; sleep 1.5;;
  esac
  adb logcat -c
  out=$(adb shell am start -W -n $A)
  t=$(echo "$out" | awk '/TotalTime/{print $2}'); ls=$(echo "$out" | awk '/LaunchState/{print $2}')
  sleep 2.5
  f=$(adb logcat -d -s ActivityTaskManager:I | grep -o "Fully drawn $A: +[0-9s]*ms" | tail -1 | sed 's/.*+//; s/ms//')
  # convert e.g. 1s23 -> 1023
  if [[ "$f" == *s* ]]; then f=$(( ${f%%s*}*1000 + 10#${f##*s} )); fi
  echo "run $i $ls TotalTime=$t FullyDrawn=${f:-NA}"
  want=$(echo $MODE | tr a-z A-Z)
  if [ "$ls" = "$want" ]; then tt+=($t); [ -n "$f" ] && fd+=($f); else echo "  (skipped: $ls)"; fi
done
med(){ printf "%s\n" "$@" | sort -n | awk '{a[NR]=$1} END{ if(NR%2) print a[(NR+1)/2]; else print (a[NR/2]+a[NR/2+1])/2 }'; }
echo "MEDIAN $MODE TotalTime=$(med ${tt[@]}) FullyDrawn=$(med ${fd[@]}) n=${#tt[@]}"
adb shell settings put secure enabled_accessibility_services $A11Y
