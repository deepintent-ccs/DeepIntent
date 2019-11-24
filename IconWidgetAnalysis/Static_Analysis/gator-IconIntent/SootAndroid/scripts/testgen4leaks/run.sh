#!/bin/bash

# ScriptDir=`dirname $0`
ScriptDir="$PWD"
RunScript=$ScriptDir/run.pl
BenchDir=$ScriptDir/../../../AndroidBench
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

run_apv () {
  ApvDir=$BenchDir/apv/pdfview
  ApvTestDir=$ApvDir/tests
  # setup
  setup $ApvTestDir 2.3.3
  # Rotate
  run apv cx.hell.android.pdfview cx.hell.android.pdfview.tests TestRotateNeutralCycles testRotateNeutralCycle 4
  # Home
  run apv cx.hell.android.pdfview cx.hell.android.pdfview.tests TestHomeNeutralCycles testHomeNeutralCycle 8
  # Back
  run apv cx.hell.android.pdfview cx.hell.android.pdfview.tests TestBackNeutralCycles testBackNeutralCycle 14
}

run_openmanager () {
  OpenManagerDir=$BenchDir/OpenManager/Android-File-Manager-master
  OpenManagerTestDir=$OpenManagerDir/tests
  #setup
  setup $OpenManagerTestDir 2.2
  # Rotate
  run openmanager com.nexes.manager com.nexes.manager.tests TestRotateNeutralCycles testRotateNeutralCycle 6
  # Home
  run openmanager com.nexes.manager com.nexes.manager.tests TestHomeNeutralCycles testHomeNeutralCycle 14
  # Back
  run openmanager com.nexes.manager com.nexes.manager.tests TestBackNeutralCycles testBackNeutralCycle 10
}

run_supergenpass () {
  SuperGenPassDir=$BenchDir/SuperGenPass/SuperGenPass-2.2.2
  SuperGenPassTestDir=$SuperGenPassDir/tests
  #setup
  setup $SuperGenPassTestDir 4.1.2
  # Rotate
  run supergenpass info.staticfree.SuperGenPass info.staticfree.SuperGenPass.tests TestRotateNeutralCycles testRotateNeutralCycle 2
  # Home
  run supergenpass info.staticfree.SuperGenPass info.staticfree.SuperGenPass.tests TestHomeNeutralCycles testHomeNeutralCycle 6
  # Back
  run supergenpass info.staticfree.SuperGenPass info.staticfree.SuperGenPass.tests TestBackNeutralCycles testBackNeutralCycle 7
}

run_tippytipper () {
  TippyTipperDir=$BenchDir/TippyTipper/tippytipper-1.2
  TippyTipperTestDir=$TippyTipperDir/tests
  #setup
  setup $TippyTipperTestDir 2.2
  # Rotate
  run tippytipper net.mandaria.tippytipper net.mandaria.tippytipper.tests TestRotateNeutralCycles testRotateNeutralCycle 6
  # Home
  run tippytipper net.mandaria.tippytipper net.mandaria.tippytipper.tests TestHomeNeutralCycles testHomeNeutralCycle 6
  # Back
  run tippytipper net.mandaria.tippytipper net.mandaria.tippytipper.tests TestBackNeutralCycles testBackNeutralCycle 8
}

run_vudroid () {
  VuDroidDir=$BenchDir/VuDroid/vudroid-1.4
  VudroidTestDir=$VuDroidDir/tests
  #setup
  setup $VudroidTestDir 2.1
  # Rotate
  run vudroid org.vudroid org.vudroid.tests TestRotateNeutralCycles testRotateNeutralCycle 3
  # Home
  run vudroid org.vudroid org.vudroid.tests TestHomeNeutralCycles testHomeNeutralCycle 4
  # Back
  run vudroid org.vudroid org.vudroid.tests TestBackNeutralCycles testBackNeutralCycle 6
}

run_all () {
  run_apv
  run_openmanager
  run_supergenpass
  run_tippytipper
  run_vudroid
}

usage() {
  echo "Options: all apv openmanager supergenpass tippytipper vudroid"
  echo "Example: $0 apv"
}

###########################################
#           Entry of the script           #
###########################################
if test $# -lt 1 ; then
  usage
  exit 1
fi

prj=$1

if [[ $prj = "apv" ]]; then
  run_apv
elif [[ $prj = "openmanager" ]]; then
  run_openmanager
elif [[ $prj = "supergenpass" ]]; then
  run_supergenpass
elif [[ $prj = "tippytipper" ]]; then
  run_tippytipper
elif [[ $prj = "vudroid" ]]; then
  run_vudroid
elif [[ $prj = "all" ]]; then
  run_all
fi
