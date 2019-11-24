#!/bin/bash

BenchDir=/home/zhanhail/workspace/leakdroid-workspace/benchmarks

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

  cp robotium-solo-5.4.1.jar $test_prj_dir/libs
  echo "Added file $test_prj_dir/libs/robotium-solo-5.4.1.jar"
  cp pai.jar $test_prj_dir/libs
  echo "Added file $test_prj_dir/libs/pai.jar"
}

setup_apv () {
  prj_dir=$BenchDir/APV
  setup_test_prj $prj_dir/ApplicationProject apv_tests $prj_dir/tests
}

setup_astrid () {
  prj_dir=$BenchDir/Astrid
  setup_test_prj $prj_dir/ApplicationProject/astrid astrid_tests $prj_dir/tests
}

setup_connectbot () {
  prj_dir=$BenchDir/ConnectBot
  setup_test_prj $prj_dir/ApplicationProject connectboot_tests $prj_dir/tests
}

setup_fbreader () {
  prj_dir=$BenchDir/FBReader
  setup_test_prj $prj_dir/ApplicationProject fbreader_tests $prj_dir/tests
}

setup_k9 () {
  prj_dir=$BenchDir/K9
  setup_test_prj $prj_dir/ApplicationProject k9_tests $prj_dir/tests
}
setup_keepassdroid () {
  prj_dir=$BenchDir/KeePassDroid
  setup_test_prj $prj_dir/ApplicationProject keepassdroid_tests $prj_dir/tests
}

setup_vlc () {
  prj_dir=$BenchDir/VLC
  setup_test_prj $prj_dir/ApplicationProject/vlc-android vlc_tests $prj_dir/tests
}

setup_vudroid () {
  prj_dir=$BenchDir/VuDroid
  setup_test_prj $prj_dir/ApplicationProject vudroid_tests $prj_dir/tests
}

usage() {
  echo "Options: apv astrid connectbot fbreader keepassdroid k9 vlc vudroid"
  echo "Example: $0 apv connectbot"
}

###########################################
#           Entry of the script           #
###########################################
if test $# -lt 1 ; then
  usage
  exit 1
fi

for p in $*; do
  setup_$p
done

