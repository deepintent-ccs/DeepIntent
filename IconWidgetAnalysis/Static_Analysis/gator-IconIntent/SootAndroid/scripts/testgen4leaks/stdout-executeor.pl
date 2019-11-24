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

# Meaning of command line arguments:
#   [0] - test id as per the JUnit standard
#   [1] - package of the application under test
#   [2] - name of the application under test
my $TestId = $ARGV[0];
my $PkgName = $ARGV[1];
my $AppName = $ARGV[2];

# Tag for special logcat output
my $EXE_TAG = "Xewr6chA";
# Replay command
my $replay = "adb shell /data/presto/replay";
# Number of repetitions observed
my $reps = 0;
# Number of threads observed
my $threads = 0;
# Output file name for resource usage data
my $output = "mem.txt";
my $pid;

while (<STDIN>) {
  if (m/$EXE_TAG.*: REPLAY (.*)/) {
    print "$replay $1\n";
    system "$replay $1";
  } elsif (m/$EXE_TAG.*: STAT BEGIN/) {
    system "echo --- BEGIN $AppName $TestId";
    $pid = fork();
    if ($pid == 0) {
      # Child process to collect resource usage data
      while (1) {
        system "adb shell dumpsys meminfo $PkgName >> $output";
        sleep 10;
      }
    }
  } elsif (m/$EXE_TAG.*: STAT END/) {
    kill 9, $pid;
    system "echo --- END   $AppName $TestId \\(REPS: $reps\\)";
    $reps = 0;
    exit 0;
  } elsif (m/$EXE_TAG.*: REP/) {
    $reps = $reps + 1;
    system "echo REP $reps";
  } elsif (m/$EXE_TAG.*: THREAD (.*)/) {
    system "echo THREAD: $1";
  } elsif (m/$EXE_TAG.*: PUSH (.*)/) {
    print "push $1\n";
    system "adb push $1 /sdcard/";
  } elsif (m/$EXE_TAG.*: MKDIR (.*)/) {
    print "mkdir $1\n";
    system "adb shell mkdir $1";
  }
}

