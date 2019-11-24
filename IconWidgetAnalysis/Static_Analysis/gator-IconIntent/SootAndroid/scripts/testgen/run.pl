#!/usr/bin/perl -w
# run.pl - part of the Gator project
#
# Copyright (c) 2015, The Ohio State University
#
# This file is distributed under the terms described in LICENSE in the root
# directory.

use POSIX(qw/getpid/);

my $ThisPid = POSIX::getpid();
my $ScriptDir = `dirname $0`;
chomp $ScriptDir;

my $LogId;
for (0..7) {
  $LogId .= chr( int(rand(25) + 65) );
}

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
#   [4] - suffix of the testcases declared in [3].
#   [5] - total number of testcases.
sub main {
  my $AppName = $ARGV[0];
  my $PkgName = $ARGV[1];
  my $TestPkg = $ARGV[2];
  my $Cls = $ARGV[3];
  my $TestSuite = $ARGV[4];
  my $NumOfCases = $ARGV[5];

  # Path var print out
  print "\n\n~~~~~ Gator TestGen ~~~~~\n";
  print "  ScriptDir=$ScriptDir\n";
  print "  TestSuite=$TestSuite\n\n";

  # Init
  my $start = time;

  if ($AppName =~ m/apv/) {
    $AvdName = "2.3.3";
  } elsif ($AppName =~ m/barcodescanner/) {
    $AvdName = "4.2.2";
  } elsif ($AppName =~ m/openmanager/) {
    $AvdName = "2.2";
  } elsif ($AppName =~ m/tippytipper/) {
    $AvdName = "2.2";
  } elsif ($AppName =~ m/supergenpass/) {
    $AvdName = "4.1.2";
  } elsif ($AppName =~ m/vudroid/) {
    $AvdName = "2.1";
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

  run($AppName, $TestPkg, $Cls);

  # Finish up
  my $end = time;
  my $delta = $end - $start;
  CleanUp();
  print "Total: $delta s.\n";
}


# Run a test case
#
# Meaning of arguments:
#   [0] - application name.
#   [1] - package name of the test project.
#   [2] - class name of the JUnit TestCase class in the test project.
sub run {
  local $AppName = $_[0];
  local $Pkg = $_[1];
  local $Cls = $_[2];

  # Clear logcat before listening on it
  system "adb logcat -c";
  # fork an executor process
  my $pid = fork();
  if ($pid == 0) {
    # Child process
    exec "adb logcat | $ScriptDir/executor.pl $AppName";
    exit 0;
  }
  # Parent process continues here...
  # Setup is done; ready to launch the test
  my $c = "adb shell am instrument -w $Pkg/$TestRunner"; # Test all cases
  my $start = time;
  print "--- BEGIN $AppName\n";
  print $c;
  print "\n";
  system $c;
  print "--- END   $AppName\n";
  my $end = time;
  my $delta = $end - $start;
  print "--- TIME  $AppName $delta sec.\n";
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
    sleep 20;
  }
  # Unlock screen
  system "adb wait-for-device";
  system "adb shell /data/presto/replay /data/presto/unlock_event";
}
