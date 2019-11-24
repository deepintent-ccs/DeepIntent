#!/bin/bash

# guiAnalysis.sh - part of the GATOR project
#
# Copyright (c) 2014, 2015 The Ohio State University
#
# This file is distributed under the terms described in LICENSE in the root
# directory.
Minimal=true
function usage {
  echo "Usage: ./guiAnalysis.sh APP_NAME"
  echo "Or ./guiAnalysis.sh runAll to run all applications"
  echo "Or ./guiAnalysis.sh runAllEnergy to run all energy related analysis"
  echo "Available APP_NAME:"
  if [[ Minimal == true ]]; then
    echo "  apv recycle-locator"
  else
    echo "  apv astrid barcodescanner beem connectbot fbreader k9 keepassdroid mileage mytracks notepad npr openmanager opensudoku sipdroid supergenpass tippytipper vlc vudroid xbmc"
    echo "  recycle-locator osmdroid sofia ushahidi droidar osmdroid-fixed recycle-locator-fixed sofia-fixed ushahidi-fixed droidar-fixed speedometer heregps whereami locdemo wigle"
  fi
  exit 2
}

containsElement () {
    local e
    for e in "${@:2}"; do [[ "$e" == "$1" ]] && return 0; done
    return 1
}

if [[ "${GatorRoot}" == "" ]]; then
  export GatorRoot=`pwd`/../
fi

if [[ "${ADK}" == "" ]]; then
  echo "Android SDK location is not defined, please use 'export $ADK=PATH_TO_ANDROID_SDK'" to define the SDK location
  exit 1
fi

GatorRunner="python $GatorRoot/AndroidBench/runGator.py "
BaseDir=$GatorRoot/AndroidBench/
CGOArray=( "apv" "astrid" "barcodescanner" "beem" "connectbot" "fbreader" "k9" "keepassdroid" "mileage" "mytracks" "notepad" "npr" "openmanager" "opensudoku" "sipdroid" "supergenpass" "tippytipper" "vlc" "vudroid" "xbmc" )

#Determine if this script is running in minimal mode
if [[ -d "ConnectBot" ]]; then
  Minimal=false
fi

#Determine if input param is sufficient
if [[ $# < 1 ]]; then
  usage
fi


#Determine if input param is keyword
if [[ $# == 1 && $1 == "runAll" ]]; then
  echo "Run analysis on all available applications"
  if [[ $Minimal == true ]]; then
      $GatorRunner -j cgo.json --base_dir $BaseDir -p apv -e
      $GatorRunner -j cc16.json --base_dir $BaseDir -p recycle-locator -e
  else
      $GatorRunner -j cgo.json --base_dir $BaseDir
      $GatorRunner -j cc16.json --base_dir $BaseDir
  fi
  exit 0
fi

if [[ $# == 1 && $1 == "runAllEnergy" ]]; then
  echo "Run analysis on all energy related applications"
  if [[ $Minimal == true ]]; then
      $GatorRunner -j cc16.json --base_dir $BaseDir -p recycle-locator -e
  else
      $GatorRunner -j cc16.json --base_dir $BaseDir
  fi
  exit 0
fi

if [[ $# == 1 && $1 == "runAllCGO" ]]; then
  echo "Run analysis on all CGO related applications"
  if [[ $Minimal == true ]]; then
      $GatorRunner -j cgo.json --base_dir $BaseDir -p apv -e
  else
      $GatorRunner -j cgo.json --base_dir $BaseDir
  fi
  exit 0
fi



for APP in $*; do
    if containsElement $APP "${CGOArray[@]}"; then
        $GatorRunner -j cgo.json --base_dir $BaseDir -p $APP -e
    else
        $GatorRunner -j cc16.json --base_dir $BaseDir -p $APP -e
    fi
done
