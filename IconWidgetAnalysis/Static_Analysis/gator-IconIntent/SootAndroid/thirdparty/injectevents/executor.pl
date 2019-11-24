#!/usr/bin/perl

while (<STDIN>) {
  if (m/Xewr6chA.*: REPLAY (.*)/) {
    print $1, "\n";
    system "adb shell /data/presto/replay $1";
  }
}

