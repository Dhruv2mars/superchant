#!/bin/bash
# scenario.sh LABEL NAME REPS : run a scenario REPS times with Perfetto + gfxinfo + /proc counters
export ANDROID_SERIAL=emulator-5554
L=$1; NAME=$2; REPS=${3:-5}; SD=~/perf-superchant/scripts; OUT=~/perf-superchant/$L/$NAME; mkdir -p $OUT
P=com.superchant; A=$P/.MainActivity
case $NAME in
  tap|vol|tapsoff) DUR=34000; DATA=d10;;
  idle|screenoff) DUR=60000; DATA=d10;;
  nav) DUR=40000; DATA=d10;;
  history) DUR=30000; DATA=d10;;
  toggle) DUR=20000; DATA=d10;;
  import) DUR=16000; DATA=d0;;
  *) DUR=30000; DATA=d10;;
esac
for j in $(seq 1 $REPS); do
  i=$((j + ${REP_OFFSET:-0}))
  $SD/restore.sh $DATA
  adb shell settings put secure enabled_accessibility_services com.superchant/com.superchant.service.VolumeKeyAccessibilityService; adb shell settings put secure accessibility_enabled 1
  adb shell am start -W -n $A >/dev/null; sleep 3
  case $NAME in
    tap|toggle) $SD/session.sh start; $SD/session.sh touchon;;
    import) $SD/tapl.sh Settings; sleep 1.5; adb shell input swipe 540 2000 540 600 200; sleep 1; adb shell input swipe 540 2000 540 600 200; sleep 1;;
    vol|idle|tapsoff) $SD/session.sh start;;
    screenoff) $SD/session.sh start; adb shell input keyevent KEYCODE_SLEEP; sleep 2;;
  esac
  sleep 1
  declare -A NAVXY; [ $NAME = nav ] && for dest in Goals History Settings; do NAVXY[$dest]="$($SD/find.sh $dest)"; done
  adb shell settings put secure enabled_accessibility_services com.superchant/com.superchant.service.VolumeKeyAccessibilityService
  pid=$(adb shell pidof $P); sui=$(adb shell pidof com.android.systemui); ss=$(adb shell pidof system_server)
  cpu(){ adb shell cat /proc/$1/stat | awk '{print $14+$15}'; }
  io(){ adb shell cat /proc/$1/io | awk '/syscw|write_bytes|wchar/{printf "%s=%s ", $1, $2}'; }
  c0=$(cpu $pid); s0=$(cpu $sui); y0=$(cpu $ss); io0=$(io $pid)
  adb shell dumpsys gfxinfo $P reset >/dev/null
  (cat $SD/trace.cfg; echo "duration_ms: $DUR") > /tmp/sc_trace.cfg; adb push /tmp/sc_trace.cfg /data/misc/perfetto-configs/sc.cfg >/dev/null
  adb shell perfetto --background --txt -c /data/misc/perfetto-configs/sc.cfg -o /data/misc/perfetto-traces/sc.pftrace >/dev/null
  sleep 2
  case $NAME in
    tap) $SD/taps.sh 300 0.06;;
    vol) $SD/volpress.sh 300 100 >/dev/null;;
    idle|screenoff) $SD/volpress.sh 55 1000 >/dev/null;;
    toggle) xy=$($SD/find.sh "Touch counting"); for k in $(seq 1 10); do adb shell input tap $xy; sleep 0.8; done;;
    nav) for k in 1 2 3; do for dest in Goals History Settings; do adb shell input tap ${NAVXY[$dest]}; sleep 1.5; adb shell input keyevent BACK; sleep 1.5; done; done;;
    import) $SD/tapl.sh Import; sleep 3; $SD/tapl.sh superchant-10y; sleep 9;;
    history) $SD/tapl.sh History; sleep 2
             for k in 1 2 3 4 5 6; do adb shell input swipe 540 1900 540 400 120; sleep 1.2; done
             for k in 1 2 3 4 5 6; do adb shell input swipe 540 400 540 1900 120; sleep 1.2; done;;
  esac
  while adb shell pidof perfetto >/dev/null; do sleep 1; done
  c1=$(cpu $pid); s1=$(cpu $sui); y1=$(cpu $ss); io1=$(io $pid)
  adb shell dumpsys gfxinfo $P > $OUT/gfx_$i.txt
  echo "app_cpu_ticks=$((c1-c0)) systemui_ticks=$((s1-s0)) system_server_ticks=$((y1-y0)) io_before=[$io0] io_after=[$io1]" > $OUT/proc_$i.txt
  adb pull /data/misc/perfetto-traces/sc.pftrace $OUT/trace_$i.pftrace >/dev/null
  [ $NAME = screenoff ] && { adb shell input keyevent KEYCODE_WAKEUP; sleep 1; adb shell wm dismiss-keyguard; sleep 1; }
  case $NAME in tap|vol|idle|toggle|tapsoff|screenoff) $SD/session.sh stop;; esac
  echo "$NAME rep $i done: $(grep -E 'Total frames|Janky frames:' $OUT/gfx_$i.txt | tr '\n' ' ') $(cat $OUT/proc_$i.txt | cut -c1-80)"
done
