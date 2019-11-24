#!/bin/bash
# jimple.sh - part of the GATOR project
#
# Copyright (c) 2014, 2015 The Ohio State University
#
# This file is distributed under the terms described in LICENSE in the root
# directory.

# Purpose: to generate Jimple representations for an Android project.

if test $# -lt 4; then
  # Usage
  echo "Usage:"
  echo "  $0 AndroidBench SdkDir ProjectDir TargetPlatform"
  echo ""
  echo "Example:"
  echo "  $0 $HOME/MyGit/AndroidBench $HOME/android-sdk-linux $HOME/projects/AndroidDemo android-15"
  exit 1 
fi

D=`dirname $0`/..

AndroidBench=$1
SdkDir=$2
ProjectDir=$3      # root path of the project; contains src, bin, res, ...
TargetPlatform=$4     # target api level (e.g., android-15) specified in project.properties. for those using google api, we use a simpler format like google-15
GoogleApi=

case $TargetPlatform in
  google-*)
    LEVEL=${TargetPlatform:7}
    TargetPlatform=android-$LEVEL
    GoogleApi=$SdkDir/add-ons/addon-google_apis-google-$LEVEL/libs/map.jar:$SdkDir/add-ons/addon-google_apis-google-$LEVEL/libs/usb.jar
  ;;
esac

if test ! -d $SdkDir/platforms/$TargetPlatform; then
  echo "You haven't installed $TargetPlatform in your Android SDK. Run \`android\` to install it."
  $SdkDir/tools/android
  if test ! -d $SdkDir/platforms/$TargetPlatform; then
    echo "You still don't have it. Abort."
    exit 2
  fi
fi

java $ASSERT -Xmx4G -classpath $D/bin:$D/lib/jasminclasses.jar:$D/lib/sootclasses.jar:$D/lib/polyglotclasses-1.3.5.jar:$D/lib/AXMLPrinter2.jar:$D/lib/baksmali-1.3.2.jar soot.Main -f J -cp $ProjectDir/bin/classes:$AndroidBench/platform/$TargetPlatform/framework.jar:$AndroidBench/platform/$TargetPlatform/bouncycastle.jar:$AndroidBench/platform/$TargetPlatform/ext.jar:$D/deps/annotations.jar:$D/deps/android-support-v4.jar:$GoogleApi:$AndroidBench/platform/$TargetPlatform/core.jar -process-dir $ProjectDir/bin/classes

