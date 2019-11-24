#!/bin/bash
# guiAnalysis.sh - part of the GATOR project
#
# Copyright (c) 2014, 2015 The Ohio State University
#
# This file is distributed under the terms described in LICENSE in the root
# directory.

# Purpose: to apply the analysis on an Android project.

# Exactly 5 command arguments are needed.
if test $# -lt 5; then
  # Usage
  echo "Usage:"
  echo "  $0 AndroidBench SdkDir ProjectDir TargetPlatform BenchmarkName"
  echo ""
  echo "Example:"
  echo "  $0 $HOME/MyGit/AndroidBench $HOME/android-sdk-linux $HOME/projects/AndroidDemo android-15 AndroidDemo"
  exit 1
fi

# Root path of SootAndroid
D=`dirname $0`/..

# Retrieve the command line arguments.
AndroidBench=$1
SdkDir=$2
ProjectDir=$3      # root path of the project; contains src, bin, res, ...
TargetPlatform=$4     # target api level (e.g., android-15) specified in project.properties. for those using google api, we use a simpler format like google-15
BenchmarkName=$5

# Compute the Google API JAR string if necessary.
GoogleApi=
case $TargetPlatform in
  google-*)
    LEVEL=${TargetPlatform:7}
    TargetPlatform=android-$LEVEL
    GoogleApiDir=$SdkDir/add-ons/addon-google_apis-google-$LEVEL
    if test ! -d $GoogleApiDir; then
      echo "You haven't installed Google API for $TargetPlatform. Run \`android\` to install it."
      $SdkDir/tools/android
      if test ! -d $GoogleApiDir; then
        echo "You still don't have it. Abort."
        exit 2
      fi
    fi
    GoogleApi=$GoogleApiDir/libs/maps.jar:$GoogleApiDir/libs/usb.jar
  ;;
esac

# Check to see if SDK files for the requested target platform are installed. If
# not, try to bring up the SDK management GUI and let users install those
# files. Check again, and fail if still not available.
if test ! -d $SdkDir/platforms/$TargetPlatform; then
  echo "You haven't installed $TargetPlatform in your Android SDK. Run \`android\` to install it."
  $SdkDir/tools/android
  if test ! -d $SdkDir/platforms/$TargetPlatform; then
    echo "You still don't have it. Abort."
    exit 2
  fi
fi

# Now, invoke SootAndroid - Main.main() in Main.java. Accepted flags are
# defined in Configs.java.
#
# Environment variables affecting behavior:
#   * $SootAndroidAssert: either empty or "-ea". If "-ea", assertion is turned
#                         on.
#   * $SootAndroidOptions: used to pass additional flag values. For example, it
#                          can be "-nosilent" to allow printout of additional
#                          debug information.

PlatformJar=$AndroidBench/platform/$TargetPlatform/framework.jar:$AndroidBench/platform/$TargetPlatform/bouncycastle.jar:$AndroidBench/platform/$TargetPlatform/ext.jar:$AndroidBench/platform/$TargetPlatform/android-policy.jar:$AndroidBench/platform/$TargetPlatform/services.jar:$D/deps/annotations.jar:$D/deps/android-support-v4.jar:$GoogleApi
if [[ $ProjectDir == *.apk ]]; then
  PlatformJar=$SdkDir/platforms/$TargetPlatform/android.jar
fi
echo $PlatformJar
java $SootAndroidAssert -Xmx${SootAndroidMaxHeap:=12G} \
-classpath $D/bin:$D/lib/jasminclasses.jar:$D/lib/sootclasses.jar:$D/lib/polyglotclasses-1.3.5.jar:\
$D/lib/AXMLPrinter2.jar:$D/lib/guava-14.0.1.jar:$D/lib/baksmali-2.0.3.jar:$D/lib/baksmali-1.3.2.jar:\
$D/google-gson-2.2.4/gson-2.2.4.jar:$D/lib/velocity-1.7-dep.jar \
presto.android.Main \
-project $ProjectDir \
-android $PlatformJar \
-jre $AndroidBench/platform/$TargetPlatform/core.jar \
-sdkDir $SdkDir \
-apiLevel $TargetPlatform \
-benchmarkName $BenchmarkName \
-guiAnalysis \
-listenerSpecFile $D/listeners.xml \
-wtgSpecFile $D/wtg.xml \
$SootAndroidOptions
