#!/usr/bin/perl -w
# executor.pl - part of the Gator project
#
# Copyright (c) 2015, The Ohio State University
#
# This file is distributed under the terms described in LICENSE in the root
# directory.

# Listens on standard input, and do the following when special lines are
# observed:
#   1) issue replay commands; and
#   2) collect resource usage data.

# Tag for special logcat output
my $EXE_TAG = "Xewr6chA";
# Replay command
my $replay = "adb shell /data/presto/replay";

while (<STDIN>) {
  if (m/$EXE_TAG.*: REPLAY (.*)/) {
    print "replay $1\n";
    system "$replay $1";
  } elsif (m/$EXE_TAG.*: PUSH (.*)/) {
    print "push $1\n";
    system "adb push $1 /sdcard/";
  } elsif (m/$EXE_TAG.*: MKDIR (.*)/) {
    print "mkdir $1\n";
    system "adb shell mkdir $1";
  } elsif (m/$EXE_TAG.*: END/) {
    system "echo --- END";
    exit 0;
  }
}

