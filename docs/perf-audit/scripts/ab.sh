#!/bin/bash
# ab.sh REPS SCENARIO LABEL_A APK_A SUFFIX_A LABEL_B APK_B SUFFIX_B : interleaved A/B runs of one scenario
# (each rep reinstalls and recompiles with the profile procedure, so emulator drift hits both alike)
export ANDROID_SERIAL=emulator-5554
REPS=$1; SC=$2; SRC=~/perf-superchant/scripts; SD=$(mktemp -d); cp $SRC/*.sh $SRC/*.cfg $SD/
sed -i '' "s|SD=~/perf-superchant/scripts|SD=$SD|" $SD/scenario.sh
shift 2
for r in $(seq 1 $REPS); do
  for side in A B; do
    if [ $side = A ]; then L=$1; APK=$2; SUF=$3; else L=$4; APK=$5; SUF=$6; fi
    [ $r = 1 ] && rm -rf ~/perf-superchant/$L/$SC
    adb install -r -d $APK >/dev/null; $SD/compile.sh profile >/dev/null
    REP_OFFSET=$((r-1)) DATA_SUFFIX=$SUF $SD/scenario.sh $L $SC 1 2>&1 | grep --line-buffered "rep" | sed "s/^/[$L] /"
  done
done
for L in $1 $4; do (cd ~/perf-superchant && ./venv/bin/python scripts/analyze.py $L/$SC > $L/$SC/summary.txt 2>&1); done
echo ABDONE
