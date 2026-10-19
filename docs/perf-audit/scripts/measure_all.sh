#!/bin/bash
# measure_all.sh : interleaved baseline (A) vs final (B): startup matrix, then interaction scenarios
SD=~/perf-superchant/scripts; F=~/perf-superchant/final
A=~/perf-superchant/base/app-release-instr2.apk; B=~/perf-superchant/after/final-release.apk
for comp in fresh profile; do
  for data in d0 d10; do
    $SD/ab_cold.sh $F/startup_${comp}_${data}_cold.txt $data cold 2 5 $A "" $B m $comp > /dev/null
    $SD/ab_cold.sh $F/startup_${comp}_${data}_warm.txt $data warm 2 4 $A "" $B m $comp > /dev/null
    $SD/ab_cold.sh $F/startup_${comp}_${data}_hot.txt $data hot 2 5 $A "" $B m $comp > /dev/null
    echo "startup $comp $data done"
  done
done
for spec in "tap 3" "vol 3" "toggle 3" "nav 3" "history 3" "idle 3" "import 3"; do
  set -- $spec
  $SD/ab.sh $2 $1 fbase $A "" ffinal $B m > /dev/null 2>&1
  echo "scenario $1 done"
done
echo MEASURE_ALL_DONE
