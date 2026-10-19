#!/bin/bash
# ab_cold.sh OUT DATA MODE BLOCKS PER APK_A SUF_A APK_B SUF_B [COMP] : A/B startup in alternating blocks
SRC=~/perf-superchant/scripts; SD=$(mktemp -d); cp $SRC/*.sh $SD/
OUT=$1; DATA=$2; MODE=$3; BLOCKS=$4; PER=$5; COMP=${10:-fresh}; : > $OUT
export ANDROID_SERIAL=emulator-5554
for b in $(seq 1 $BLOCKS); do
  for side in A B; do
    if [ $side = A ]; then APK=$6; SUF=$7; else APK=$8; SUF=$9; fi
    adb install -r -d $APK >/dev/null; DATA_SUFFIX=$SUF $SD/restore.sh $DATA; $SD/compile.sh $COMP >/dev/null
    DATA_SUFFIX=$SUF $SD/restore.sh $DATA
    $SD/startup.sh $MODE $PER | sed "s/^/$side /" >> $OUT
  done
done
for side in A B; do
  for k in TotalTime FullyDrawn; do
    vals=$(grep "^$side run" $OUT | grep -v skipped | grep -o "$k=[0-9]*" | cut -d= -f2)
    echo "$side $k median=$(printf '%s\n' $vals | sort -n | awk '{a[NR]=$1} END{if(NR%2) print a[(NR+1)/2]; else print (a[NR/2]+a[NR/2+1])/2}') n=$(echo $vals | wc -w)"
  done
done | tee -a $OUT
