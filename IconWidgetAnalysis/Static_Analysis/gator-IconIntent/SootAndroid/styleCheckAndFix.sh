#!/bin/bash
function checkDanglingElse() {
  local File=$1
  if [ "AndroidView.java" == `basename $File` ]; then
    return
  fi
  local DanglingElse=`grep -n "  else" $File`
  if [ "" != "$DanglingElse" ]; then
    echo "[WARNING] Dangling else in $File"
    echo "$DanglingElse"
    echo ""
  fi
}


function checkUnderscoreVariable() {
  local File=$1
  local UnderscoreVariable=`grep -n "[a-z]\+_[a-z]\+" $File`
  if [ "" != "$UnderscoreVariable" ]; then
    echo "[WARNING] Variable name with underscore in $File"
    echo "$UnderscoreVariable"
    echo ""
  fi
}


# Print lines with more than 100 characters
function checkExtremelyLongLines() {
  local File=$1
  local ExtremelyLongLines=`awk '  { if (length($0) > 100) print } ' $File`
  if [ "" != "$ExtremelyLongLines" ]; then
    echo "[WARNING] $File has extremely long lines"
    echo "$ExtremelyLongLines"
    echo ""
  fi
}


# main
set -e # Abort on error

# Use gnu-sed
GnuSed="sed"
if [ "`uname`" == "Darwin" ]; then
  GnuSed="gsed"
fi

# Look at each file
for i in `find ./src/presto -name "*\.java"`; do
  # --- Fixes

  # Trailing whitespace
  $GnuSed -i"" "s/ *$//g" $i

  # Missing whitespace
  $GnuSed -i"" "s/){/) {/g" $i
  $GnuSed -i"" "s/if(/if (/g" $i
  $GnuSed -i"" "s/for(/for (/g" $i
  $GnuSed -i"" "s/while(/while (/g" $i
  $GnuSed -i"" "s/\([a-zA-Z]\){/\1 {/g" $i

  # Add whitespace after comment symbols //
  $GnuSed -i"" -e "s@^//\([a-zA-Z0-9]\)@// \1@g" \
    -e "s@\([^:]\)//\([a-zA-Z0-9]\)@\1// \2@g" \
    -e "s@\(/\*\**\)\([a-zA-Z0-9]\)@\1 \2@g" \
    -e "s@\([a-zA-Z0-9]\)\(\*\**/\)\$@\1 \2@g" \
    $i

  # --- Checks for things we don't know how to fix yet
  checkDanglingElse $i
  # TODO(tony): turn this on when we fix most of the violations.
  #checkUnderscoreVariable $i
  # TODO(tony): turn on at some point...
  #checkExtremelyLongLines $i
done

