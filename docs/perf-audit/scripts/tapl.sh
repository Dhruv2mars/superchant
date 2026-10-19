#!/bin/bash
# tapl.sh LABEL : tap the node labelled LABEL
xy=$(~/perf-superchant/scripts/find.sh "$1"); [ -z "$xy" ] && { echo "not found: $1" >&2; exit 1; }
adb -s emulator-5554 shell input tap $xy
