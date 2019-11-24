#!/usr/bin/perl -w
# run.pl - part of the Gator project
#
# Copyright (c) 2015, The Ohio State University
#
# This file is distributed under the terms described in LICENSE in the root
# directory.

use POSIX(qw/getpid/);

my $ThisPid = POSIX::getpid();
# my $ScriptDir = `dirname $0`;
my $ScriptDir = `pwd`;
chomp $ScriptDir;
my $RootDir = "$ScriptDir/../../..";
my $LogDir = "$RootDir/logs";

my $LogId;
for (0..7) {
  $LogId .= chr( int(rand(25) + 65) );
}

if (! -d "$LogDir") {
  system "mkdir -p $LogDir";
}

# my $MemLog = "$LogDir/mem.txt.$LogId";
my $AvdName = "";
my $TestRunner = "android.test.InstrumentationTestRunner";

main();

# Entrypoint of this script.
#
# Command line arguments in ARGV array:
#   [0] - name of the application. This is for printing purpose.
#   [1] - package name of the application.
#   [2] - package name of the test project.
#   [3] - class name of JUnit TestCase class in the test project.
#   [4] - comma seperated list of names of the test case methods to execute.
sub main {
  my $AppName = $ARGV[0];
  my $PkgName = $ARGV[1];
  my $TestPkg = $ARGV[2];
  my $Cls = $ARGV[3];
  my $TestSuite = $ARGV[4];

  my $MemLog = "$LogDir/$AppName.$TestSuite.mem.$LogId";

  # Path var print out
  print "\n\n~~~~~ Gator TestGen ~~~~~\n";
  print "ScriptDir=$ScriptDir\n";
  print "DataDir=$ScriptDir\n";
  print "LogDir=$LogDir\n";
  print "MemLog=$MemLog\n";
  print "TestSuite=$TestSuite\n\n";

  # Init
  my $start = time;
  system "echo > mem.txt";

  if ($AppName =~ m/apv/) {
    $AvdName = "2.3.3";
  } elsif ($AppName =~ m/vudroid/) {
    $AvdName = "2.2";
  } else {
    $AvdName = "2.2";
  }
  my $EnvAvdName = $ENV{'AvdName'};
  if (defined $EnvAvdName) {
    print "ENV{AvdName}=$EnvAvdName\n";
    $AvdName = $EnvAvdName;
  }

  # Prepare the emulator
  RebootEmulatorAndWait();

  # # Main loop
  # foreach my $i (1..$NumOfCases) {
  #   my $Test = "$TestSuite$i";
  #   print "=== start $Test\n";
  #   run($AppName, $PkgName, $TestPkg, $Cls, $Test);
  # }

  # Main loop
  for my $Test (split(",", $TestSuite)) {
    run($AppName, $PkgName, $TestPkg, $Cls, $Test);
  }

  # Finish up
  my $end = time;
  my $delta = $end - $start;
  CleanUp();
  print "Total: $delta sec.\n";
  system "echo Total: $delta sec. >> mem.txt\n";
  system "cp mem.txt $MemLog";
  print "Resource usage data saved to $MemLog\n";
}


# Run a test case
#
# Meaning of arguments:
#   [0] - application name.
#   [1] - package name of the test project.
#   [2] - class name of the JUnit TestCase class in the test project.
#   [3] - name of the test method in the test class specified by [3].
sub run {
  local $AppName = $_[0];
  local $PkgName = $_[1];
  local $Pkg = $_[2];
  local $Cls = $_[3];
  local $Test = $_[4];

  # Test id as per JUnit standard
  my $testId = "$Pkg.$Cls#$Test";

  # Clear logcat before listening on it
  system "adb logcat -c";
  # fork an executor process
  my $pid = fork();
  if ($pid == 0) {
    # Child process
    exec "adb logcat | $ScriptDir/executor.pl $testId $PkgName $AppName";
    exit 0;
  }
  # Parent process continues here...
  # Setup is done; ready to launch the test
  my $c = "adb shell am instrument -w -e class $testId $Pkg/$TestRunner";
  my $start = time;
  print "--- BEGIN $AppName $testId\n";
  print $c;
  print "\n";
  system $c;
  print "--- END   $AppName $testId\n";
  my $end = time;
  my $delta = $end - $start;
  print "--- TIME  $AppName $testId $delta sec.\n";
  # Clean up
  killchild($ThisPid);
}


# Kill any child processes forked by this
sub killchild {
  local $p = $_[0];
  my $s = `ps -ef | awk '\$3 == '$p' { print \$2 }' | tr '\n' ' '`;
  chomp $s;
  if (length($s) == 0) {
    return;
  }
  for my $t (split(/ /, $s)) {
    killchild($t);
    kill -9, $t;
  }
}


# Reset everything
sub CleanUp {
  system "pkill adb";
  system "pkill executor.pl";
  system "pkill emulator";
}


# Reboot emulator & wait until it is ready
sub RebootEmulatorAndWait {
  CleanUp();
  # Launch emulator
  system "emulator -avd $AvdName & ";
  sleep 5;

  # Wait for initialization
  while (1) {
    print "waiting for emulator boot-up...\n";
    sleep 20;
    system "adb kill-server";
    system "adb start-server";
    my $res = `adb shell ls`;
    last if length($res) != 0;
  }
  if ($AvdName =~ m/4.0/) {
    sleep 25;
  } else {
    sleep 10;
  }
  # Unlock screen
  system "adb wait-for-device";
  system "adb shell /data/presto/replay /data/presto/unlock_event";
}
