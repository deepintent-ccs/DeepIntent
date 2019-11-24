#!/bin/bash

# Push record, replay and events to AVD.

if [ ! $# == 1 ]; then
    echo "Usage: $0 avd_number"
    exit
fi

if [ $1 != '1.6' -a $1 != '2.1' -a $1 != '2.2' -a $1 != '2.3.3' -a $1 != '4.0.3' -a $1 != '4.1.2' -a $1 != '4.2.2' ]; then
    echo "Usage: $0 avd_number"
    echo "avd_number must be 1.6, 2.1, 2.2, 2.3.3, 4.0.3, or 4.1.2"
    exit
fi

if [ $1 = '1.6' -o $1 = '2.1' -o $1 = '2.2' -o $1 = '2.3.3' ]; then
    echo "Pushing record & replay to $1...."
    adb push libs/armeabi/event_record /data/presto/record
    adb push libs/armeabi/event_replay /data/presto/replay
fi

if [ $1 = '4.0.3' -o $1 = '4.1.2' -o $1 = '4.2.2' ]; then
    echo "Pushing record & replay to $1...."
    adb push libs/armeabi-v7a/event_record /data/presto/record
    adb push libs/armeabi-v7a/event_replay /data/presto/replay
fi

adb shell chmod 777 /data/presto/record
adb shell chmod 777 /data/presto/replay

for f in $1/*; do
    echo "Pushing $f...."
    adb push "$f" /data/presto
done

