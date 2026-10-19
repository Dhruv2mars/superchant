#!/bin/bash
# compile.sh verify|fresh|profile : put the installed app in a known compilation state
#   verify  : no AOT (sideload without a profile)
#   fresh   : what a store install does: only the APK's baseline profile, AOT-compiled (Macrobenchmark Partial)
#   profile : mature: baseline profile plus what JIT saw during an 8 s run, AOT-compiled
export ANDROID_SERIAL=emulator-5554
P=com.superchant
if [ "$1" = verify ]; then adb shell cmd package compile -f -m verify $P >/dev/null
elif [ "$1" = fresh ]; then
adb shell cmd package compile --reset $P >/dev/null
adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n $P/androidx.profileinstaller.ProfileInstallReceiver >/dev/null
sleep 2; adb shell am force-stop $P
adb shell cmd package compile -f -m speed-profile $P >/dev/null
else
adb shell cmd package compile --reset $P >/dev/null
adb shell am start -W -n $P/.MainActivity >/dev/null; sleep 8
adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n $P/androidx.profileinstaller.ProfileInstallReceiver >/dev/null
sleep 1; adb shell am force-stop $P
adb shell cmd package compile -f -m speed-profile $P >/dev/null
fi
adb shell dumpsys package dexopt | grep -A3 "\[$P\]" | grep status
