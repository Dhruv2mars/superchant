#!/bin/bash
# ui.sh dump : print content-desc/text of visible nodes (uiautomator)
adb -s emulator-5554 shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
adb -s emulator-5554 shell cat /sdcard/u.xml | tr '>' '\n' | grep -oE '(content-desc|text)="[^"]+"' | sed 's/^[a-z-]*=//' | tr '\n' '|'; echo
