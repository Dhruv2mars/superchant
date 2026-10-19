#!/bin/bash
# run_all.sh LABEL [scenario reps ...] : interaction scenarios, then metrics (runs a frozen copy of the scripts)
L=$1; shift; SRC=~/perf-superchant/scripts; SD=$(mktemp -d); cp $SRC/*.sh $SRC/*.cfg $SD/
sed -i '' "s|SD=~/perf-superchant/scripts|SD=$SD|" $SD/scenario.sh
SPECS=("$@"); [ ${#SPECS[@]} -eq 0 ] && SPECS=("tap 5" "vol 5" "toggle 3" "nav 3" "history 3" "idle 3" "screenoff 3" "import 3")
for spec in "${SPECS[@]}"; do
  set -- $spec; rm -rf ~/perf-superchant/$L/$1
  $SD/scenario.sh $L $1 $2 2>&1 | grep --line-buffered "rep"
  (cd ~/perf-superchant && ./venv/bin/python scripts/analyze.py $L/$1 > $L/$1/summary.txt 2>&1)
done
echo ALLDONE
