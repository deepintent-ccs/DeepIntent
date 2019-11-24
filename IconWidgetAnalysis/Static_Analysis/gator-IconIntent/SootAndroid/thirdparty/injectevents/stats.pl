#!/usr/bin/perl

my $TAG = "h2vuc5uF";
my @actions = (
  "empty",
  "rotate",
  "home",
  "power",
);

while (<STDIN>) {
  #print $_, "\n";
  my $i = index $_, "BEGIN";
  if (m/I\/$TAG.*: (.*)/) {
    if ($i > 0) {
      my $ss = substr($_, $i);
      my @s = split(/ /, $ss);
      my $testId = $s[1];
      my $action = $actions[$s[2]];
      my $queryString = $s[3];
      print "--- $ss";
    } else {
      print $1, "\n";
      #system $1;
    }
  }
}

