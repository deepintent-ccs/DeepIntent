#!/bin/bash

ScriptDir=`dirname $0`
RunScript=$ScriptDir/run.sh
BenchDir=$ScriptDir/../../../AndroidBench

setup_test_prj () {
  prj_dir=$1       #   [1] - directory of an application project, use absolute path
  test_prj_name=$2 #   [2] - name of the test project
  test_prj_dir=$3  #   [3] - directory of the new test project for [1]

  if [[ $prj_dir != /* ]]; then
    echo "Use absolute path for project directory."
    exit -1
  fi

  if [ -z "$test_prj_dir" ]; then
    test_prj_dir="$prj_dir/tests"
  fi

  # Print directory info
  echo "Project directory: $prj_dir"
  echo "Test project name: $test_prj_name"
  echo "Test project directory: $test_prj_dir"

  android=`which android`

  $android create test-project -m $prj_dir -n $test_prj_name -p $test_prj_dir

  cp robotium-solo-5.3.1.jar $test_prj_dir/libs
  echo "Added file $test_prj_dir/libs/robotium-solo-5.3.1.jar"
  cp pai.jar $test_prj_dir/libs
  echo "Added file $test_prj_dir/libs/pai.jar"
}

VuDroidDir=$BenchDir/VuDroid/vudroid-1.4
TippyTipperDir=$BenchDir/TippyTipper/tippytipper-1.2
SuperGenPassDir=$BenchDir/SuperGenPass/SuperGenPass-2.2.2
OpenManagerDir=$BenchDir/OpenManager/Android-File-Manager-master
ApvDir=$BenchDir/apv/pdfview

setup_test_prj $VuDroidDir vudroid_tests
setup_test_prj $TippyTipperDir tippytipper_tests
setup_test_prj $SuperGenPassDir supergenpass_tests
setup_test_prj $OpenManagerDir openmanager_tests
setup_test_prj $ApvDir apv_tests

