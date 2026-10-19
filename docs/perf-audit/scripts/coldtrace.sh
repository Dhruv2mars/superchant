#!/bin/bash
# coldtrace.sh OUT APK SUFFIX DATA : Perfetto trace of one cold start (app already installed + compiled)
export ANDROID_SERIAL=emulator-5554
OUT=$1; SD=~/perf-superchant/scripts
DATA_SUFFIX=$3 $SD/restore.sh $4; sleep 1
(cat $SD/trace.cfg; echo "duration_ms: 6000") > /tmp/sc_cold.cfg; adb push /tmp/sc_cold.cfg /data/misc/perfetto-configs/cold.cfg >/dev/null
adb shell perfetto --background --txt -c /data/misc/perfetto-configs/cold.cfg -o /data/misc/perfetto-traces/cold.pftrace >/dev/null; sleep 1.5
adb shell am start -W -n com.superchant/.MainActivity | grep TotalTime
while adb shell pidof perfetto >/dev/null; do sleep 0.5; done
adb pull /data/misc/perfetto-traces/cold.pftrace $OUT >/dev/null
