#! /bin/sh

appDir=$1
forceAndroidJar=/Users/shaoyang/Library/Android/sdk/platforms/android-18/android.jar

rm -rf testspace
mkdir testspace
rm -rf output
mkdir output
var=1
for appPath in `ls $appDir/*.apk`
do
appName=`basename $appPath .apk`
retargetedPath=testspace/$appName.apk

mysql -uroot -pjiaozhuys05311 -e 'drop database if exists cc; create database cc'
mysql -uroot -pjiaozhuys05311 cc < schema

rm -rf output/$appName
mkdir output/$appName

gtimeout 1800 java -Xmx24000m -jar RetargetedApp.jar $forceAndroidJar $appPath $retargetedPath
gtimeout 1800 java -Xmx24000m -jar ic3-0.2.0-full.jar -apkormanifest $appPath -input $retargetedPath -cp $forceAndroidJar -db cc.properties -dbname cc1 -protobuf output/$appName
var=$((var+1))
done
echo "finished $var apk files."
