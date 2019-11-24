#!/bin/bash
# analysis.sh - part of the LeakDroid project
#
# Copyright (c) 2013, The Ohio State University
#
# This file is distributed under the terms described in LICENSE in the root
# directory.

# Processes the test output logs and produces resource usage plots.
ScriptDir=`dirname $0`
ProjectDir="$ScriptDir/../../tools/ResourceUsageAnalysis"
CP="$ProjectDir/bin:$ProjectDir/lib/RCaller-2.0.7.jar"
MainClass="rua.DumpsysMemInfoAnalysis"

if test $# -lt 2 ; then
  echo "Usage: $0 MemoryFile LogFile"
  echo "Example: $0 $ScriptDir/../../demo/mem.txt.JGJQFQMA $ScriptDir/../../demo/apv-home-demo.log.txt"
  exit 1
fi

# memory usage
java -DScriptDir=$ScriptDir -classpath $CP $MainClass -memFile $1 -logFile $2
# binder usage
java -DScriptDir=$ScriptDir -classpath $CP $MainClass -memFile $1 -logFile $2 -binderOnly
# thread usage
java -DScriptDir=$ScriptDir -classpath $CP $MainClass -memFile $1 -logFile $2 -threadOnly

