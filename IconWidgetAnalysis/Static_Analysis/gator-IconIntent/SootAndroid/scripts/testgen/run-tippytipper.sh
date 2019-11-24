#!/bin/sh

ScriptDir=`dirname $0`
RunScript=$ScriptDir/run.pl

$RunScript tippytipper \
net.mandaria.tippytipper.activities \
net.mandaria.tippytipper.test \
TestAllEdgeCoverage \
testAllEdge \
63

