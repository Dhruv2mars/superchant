#!/bin/bash
# find.sh LABEL : centre "x y" of the first node whose content-desc or text starts with LABEL (uiautomator)
adb -s emulator-5554 shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
adb -s emulator-5554 shell cat /sdcard/u.xml | tr '>' '\n' | grep -E "(content-desc|text)=\"$1" | head -1 | grep -o 'bounds="[^"]*"' | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
