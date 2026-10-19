#!/bin/bash
# session.sh start|touchon|stop : drive a counting session through the UI
export ANDROID_SERIAL=emulator-5554
SD=~/perf-superchant/scripts
case $1 in
  start) adb shell am start -a com.superchant.action.START_COUNTING -n com.superchant/.MainActivity >/dev/null; sleep 2.5;;
  touchon) adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb shell cat /sdcard/u.xml | tr '>' '\n' | grep 'content-desc="Touch counting"' | grep -q 'checked="true"' || $SD/tapl.sh "Touch counting"; sleep 1;;
  stop) $SD/tapl.sh Stop; sleep 1.5;;
esac
