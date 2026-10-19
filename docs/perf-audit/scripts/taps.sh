#!/bin/bash
# taps.sh N intervalSec : N taps at the counter centre, raw events written to the virtio touchscreen
N=${1:-10}; IV=${2:-0.1}
adb -s emulator-5554 shell "i=0; while [ \$i -lt $N ]; do i=\$((i+1)); cat /data/local/tmp/tap.bin > /dev/input/event1; sleep $IV; done"
