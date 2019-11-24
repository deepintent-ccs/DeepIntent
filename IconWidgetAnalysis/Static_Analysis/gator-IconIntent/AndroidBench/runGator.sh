#! /bin/sh

export ADK=/Users/shaoyang/Library/Android/sdk
export GatorRoot=/Users/shaoyang/Downloads/test/gator-IconIntent

var=1
for app in `ls *.apk`
do
echo "analyzing $app"

python runGatorOnApk.py $app -client WTGDemoClient
echo "done analyzing $app"
var=$((var+1))
done
echo "analyzed $var files"
