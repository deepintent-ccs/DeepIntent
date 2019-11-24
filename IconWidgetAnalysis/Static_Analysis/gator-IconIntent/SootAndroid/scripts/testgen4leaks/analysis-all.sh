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

analysis_openmanager () {
  analysis_rotate openmanager 6
  analysis_home openmanager 14
  analysis_back openmanager 10
}

analysis_supergenpass () {
  analysis_rotate supergenpass 2
  analysis_home supergenpass 6
  analysis_back supergenpass 7
}

analysis_tippytipper () {
  analysis_rotate tippytipper 6
  analysis_home tippytipper 6
  analysis_back tippytipper 8
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

prj=$1

if [[ $prj = "apv" ]]; then
  analysis_apv
elif [[ $prj = "openmanager" ]]; then
  analysis_openmanager
elif [[ $prj = "supergenpass" ]]; then
  analysis_supergenpass
elif [[ $prj = "tippytipper" ]]; then
  analysis_tippytipper
elif [[ $prj = "vudroid" ]]; then
  analysis_vudroid
else
  usage
fi
