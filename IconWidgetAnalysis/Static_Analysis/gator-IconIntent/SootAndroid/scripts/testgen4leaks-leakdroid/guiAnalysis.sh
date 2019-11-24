#!/bin/bash

if [[ "${GatorRoot}" == "" ]]; then
  GatorRoot=${HOME}/software/ResearchProjects/ccfg/trunk
fi

LeakDroidRoot=${HOME}/workspace/leakdroid-workspace

# This is the root directory of AndroidBench.
AB=$GatorRoot/AndroidBench

# This is the directory of SDK
if [[ "${ADK}" == "" ]]; then
  ADK="${HOME}/software/Android/SDK/android-sdk-linux"
fi

CMD="$GatorRoot/SootAndroid/scripts/guiAnalysis.sh $AB $ADK"

# For each program, build its own specific arguments to fill in the command
# template, and construct the complete command for invocation.

run-apv() {
  echo "=== START analyzing APV"
  pushd $LeakDroidRoot/benchmarks/APV
  $CMD ApplicationProject android-10 APV 2>&1 | tee log.apv
  popd
  echo "=== END analyzing APV; result saved to $LeakDroidRoot/benchmarks/APV/log.apv"
}

run-astrid() {
  echo "=== START analyzing Astrid"
  pushd $LeakDroidRoot/benchmarks/Astrid
  $CMD ApplicationProject/astrid google-8 Astrid 2>&1 | tee log.astrid
  popd
  echo "=== END analyzing Astrid; result saved to $LeakDroidRoot/benchmarks/astrid/log.astrid"
}

run-connectbot() {
  echo "=== START analyzing ConnectBot"
  pushd $LeakDroidRoot/benchmarks/ConnectBot
  $CMD ApplicationProject android-8 ConnectBot 2>&1 | tee log.connectbot
  popd
  echo "=== END analyzing ConnectBot; result saved to $LeakDroidRoot/benchmarks/ConnectBot/log.connectbot"
}

run-fbreader() {
  echo "=== START analyzing FBReader"
  pushd $LeakDroidRoot/benchmarks/FBReader
  $CMD ApplicationProject android-8 FBReader 2>&1 | tee log.fbreader
  popd
  echo "=== END analyzing FBReader; result saved to $LeakDroidRoot/benchmarks/FBReader/log.fbreader"
}

run-keepassdroid() {
  echo "=== START analyzing KeePassDroid"
  pushd $LeakDroidRoot/benchmarks/KeePassDroid
  $CMD ApplicationProject android-4 KeePassDroid 2>&1 | tee log.keepassdroid
  popd
  echo "=== END analyzing KeePassDroid; result saved to $LeakDroidRoot/benchmarks/KeePassDroid/log.keepassdroid"
}

run-k9() {
  echo "=== START analyzing K9"
  pushd $LeakDroidRoot/benchmarks/K9
  $CMD ApplicationProject android-4 K9 2>&1 | tee log.k9
  popd
  echo "=== END analyzing K9; result saved to $LeakDroidRoot/benchmarks/k9/log.k9"
}

run-vlc() {
  echo "=== START analyzing VLC"
  pushd $LeakDroidRoot/benchmarks/VLC
  $CMD ApplicationProject/vlc-android android-15 VLC 2>&1 | tee log.vlc
  popd
  echo "=== END analyzing VLC; result saved to $LeakDroidRoot/benchmarks/VLC/log.vlc"
}


run-vudroid() {
  echo "=== START analyzing VuDroid"
  pushd $LeakDroidRoot/benchmarks/VuDroid
  $CMD ApplicationProject android-8 VuDroid 2>&1 | tee log.vudroid
  popd
  echo "=== END analyzing VuDroid; result saved to $LeakDroidRoot/benchmarks/VuDroid/log.vudroid"
}

# A simple usage printout
usage() {
  echo "Options: apv astrid connectbot fbreader keepassdroid k9 vlc vudroid"
  echo "Example: $0 apv connectbot"
}

# "main"

# At least one program should be specified
if test $# -lt 1 ; then
  usage
  exit 1
fi

# For each specified program, run the analysis
for p in $*; do
  run-$p
done

