#!/bin/bash
# ab_startup.sh OUT APK_A SUF_A APK_B SUF_B : startup medians, A and B interleaved per (compilation, data)
SRC=~/perf-superchant/scripts; SD=$(mktemp -d); cp $SRC/*.sh $SD/
sed -i '' "s|S=~/perf-superchant/scripts|S=$SD|" $SD/*.sh
OUT=$1; : > $OUT
export ANDROID_SERIAL=emulator-5554
for comp in fresh profile; do
  for data in d0 d10; do
    for side in A B; do
      if [ $side = A ]; then APK=$2; SUF=$3; else APK=$4; SUF=$5; fi
      adb install -r $APK >/dev/null
      DATA_SUFFIX=$SUF $SD/restore.sh d10; $SD/compile.sh $comp >/dev/null
      for mode in cold warm hot; do
        DATA_SUFFIX=$SUF $SD/restore.sh $data
        N=10; [ $mode = warm ] && N=8
        echo "== $side $comp $data $mode" >> $OUT
        $SD/startup.sh $mode $N >> $OUT 2>&1
      done
    done
  done
done
grep -E "^==|MEDIAN" $OUT
