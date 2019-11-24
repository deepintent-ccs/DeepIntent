#!/bin/bash

# ScriptDir=`dirname $0`
ScriptDir="$PWD"
RunScript=$ScriptDir/run.pl
LeakDroidDir=$HOME/workspace/leakdroid-workspace
BenchDir=$LeakDroidDir/benchmarks
LogDir=$ScriptDir/../../../logs

run () {
  prj=$1  #   [1] - name of application.
  pkg=$2  #   [2] - package name.
  tpkg=$3 #   [3] - test package name.
  cls=$4  #   [4] - test class name.
  mtd=$5  #   [5] - test method name.
  num=$6  #   [6] - number of testcases.
  for i in `seq -w 001 $num`; do
    method="$mtd$i"
    $RunScript $prj \
    $pkg \
    $tpkg \
    $cls \
    $method \
    | tee "$LogDir/$prj.$method.log"
    sleep 5
  done
}

setup () {
  test_dir=$1 # test project directory
  avd=$2      # avd version
  emulator -avd $avd &
  adb wait-for-device
  cd $test_dir
  ant clean
  ant debug install
  sleep 5s
  pkill -P $$
  cd $ScriptDir
}

run-apv () {
  ApvDir=$BenchDir/APV/ApplicationProject
  ApvTestDir=$BenchDir/APV/tests
  # setup
  setup $ApvTestDir 2.3.3
  # Rotate
  run apv cx.hell.android.pdfview cx.hell.android.pdfview.tests TestRotateNeutralCycles testRotateNeutralCycle 4
  # Home
  run apv cx.hell.android.pdfview cx.hell.android.pdfview.tests TestHomeNeutralCycles testHomeNeutralCycle 8
  # Back
  run apv cx.hell.android.pdfview cx.hell.android.pdfview.tests TestBackNeutralCycles testBackNeutralCycle 13
}


run-vudroid () {
  VuDroidDir=$BenchDir/VuDroid/ApplicationProject
  VudroidTestDir=$BenchDir/VuDroid/tests
  #setup
  setup $VudroidTestDir 2.2
  # Rotate
  run vudroid org.vudroid org.vudroid.tests TestRotateNeutralCycles testRotateNeutralCycle 3
  # Home
  run vudroid org.vudroid org.vudroid.tests TestHomeNeutralCycles testHomeNeutralCycle 4
  # Back
  run vudroid org.vudroid org.vudroid.tests TestBackNeutralCycles testBackNeutralCycle 6
}

usage() {
  echo "Options: apv astrid connectbot fbreader k9 keepassdroid vlc vudroid"
  echo "Example: $0 apv"
}

###########################################
#           Entry of the script           #
###########################################
if test $# -lt 1 ; then
  usage
  exit 1
fi

# For each specified project, run the analysis
for p in $*; do
  run-$p
done
