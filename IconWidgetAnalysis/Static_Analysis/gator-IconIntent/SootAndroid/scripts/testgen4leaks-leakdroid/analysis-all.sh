#!/bin/bash

ScriptDir=`dirname $0`
LogDir="$ScriptDir/../../../logs"
AnalysisScript="$ScriptDir/analysis.sh"

analysis () {
  prj_name=$1
  num=$2
  mtd_prefix=$3 # Home, Rotate, Back

  mtd_postfix="NeutralCycle"
  mtd="$prj_name.test$mtd_prefix$mtd_postfix"

  for i in `seq -w 001 $num`; do
    method="$mtd$i"
    logfile=`ls $LogDir/$method.log*`
    memfile=`ls $LogDir/$method.mem*`
    cmd="$AnalysisScript $memfile $logfile"
    echo "$cmd"
  done
}

analysis_rotate () {
  echo "# Start Analyzing ROTATE Neutral Cycles..."
  prj=$1 # project name
  num=$2 # number of cases
  analysis $prj $num Rotate
}

analysis_home () {
  echo "# Start Analyzing HOME Neutral Cycles..."
  prj=$1 # project name
  num=$2 # number of cases
  analysis $prj $num Home
}

analysis_back () {
  echo "# Start Analyzing BACK Neutral Cycles..."
  prj=$1 # project name
  num=$2 # number of cases
  analysis $prj $num Back
}

analysis_apv () {
  analysis_rotate apv 4
  analysis_home apv 8
  analysis_back apv 14
}

analysis_vudroid () {
  analysis_rotate vudroid 3
  analysis_home vudroid 4
  analysis_back vudroid 6
}

usage() {
  echo "Options: apv openmanager supergenpass tippytipper vudroid"
  echo "Example: $0 apv"
}

###########################################
#           Entry of the script           #
###########################################
if test $# -lt 1 ; then
  usage
  exit 1
fi

for p in $*; do
  analysis_$p
done
