#!/bin/bash
# startup_matrix.sh LABEL : startup medians for d0/d10 x verify/profile x cold/warm/hot
S=~/perf-superchant/scripts; L=$1; OUT=~/perf-superchant/$L/startup.txt; : > $OUT
for comp in verify profile; do
  $S/restore.sh d10; $S/compile.sh $comp >/dev/null
  for data in d0 d10; do
    for mode in cold warm hot; do
      $S/restore.sh $data
      echo "== $comp $data $mode" >> $OUT
      N=10; [ $mode = warm ] && N=8; $S/startup.sh $mode $N >> $OUT 2>&1
    done
  done
done
grep -E "^==|MEDIAN" $OUT
