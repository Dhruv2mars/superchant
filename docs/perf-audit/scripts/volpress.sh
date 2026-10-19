#!/bin/bash
# usage: volpress.sh N intervalMs  -> N volume-up presses via a virtual uinput device
N=${1:-10}; IV=${2:-100}; HOLD=30; REST=$((IV-HOLD))
{
echo '{"id":1,"command":"register","name":"Volume Keys","vid":4660,"pid":22136,"bus":"usb","configuration":[{"type":"UI_SET_EVBIT","data":[1]},{"type":"UI_SET_KEYBIT","data":[114,115]}]}'
echo '{"id":1,"command":"delay","duration":1500}'
for i in $(seq 1 $N); do
echo '{"id":1,"command":"inject","events":[1,115,1,0,0,0]}'
echo "{\"id\":1,\"command\":\"delay\",\"duration\":$HOLD}"
echo '{"id":1,"command":"inject","events":[1,115,0,0,0,0]}'
echo "{\"id\":1,\"command\":\"delay\",\"duration\":$REST}"
done
echo '{"id":1,"command":"delay","duration":500}'
} | adb -s emulator-5554 shell uinput -
