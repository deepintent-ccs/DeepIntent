/*
 * PatternMatcher.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.util;

/***
 * This class is directly copied from android.os.PatternMatcher
 * **/
public class PatternMatcher {
  // Pattern type: the given pattern must exactly match the string it is tested
  // against.
  public static final int PATTERN_LITERAL = 0;
  // Pattern type: the given pattern must match the beginning of the string it
  // is tested against.
  public static final int PATTERN_PREFIX = 1;
  // Pattern type: the given pattern is interpreted with a simple glob syntax
  // for matching against the string it is tested against. In this syntax, you
  // can use the '*' character to match against zero or more occurrences of the
  // character immediately before. If the character before it is '.' it will
  // match any character. The character '\' can be used as an escape. This
  // essentially provides only the '*' wildcard part of a normal regexp.
  public static final int PATTERN_SIMPLE_GLOB = 2;

  private final String mPattern;
  private final int mType;

  public PatternMatcher(String pattern, int type) {
    mPattern = pattern;
    mType = type;
  }

  public boolean match(String toMatch) {
    return matchPattern(mPattern, toMatch, mType);
  }

  private static boolean matchPattern(String pattern, String match, int type) {
    if (match == null)
      return false;
    if (type == PATTERN_LITERAL) {
      return pattern.equals(match);
    }
    if (type == PATTERN_PREFIX) {
      return match.startsWith(pattern);
    } else if (type != PATTERN_SIMPLE_GLOB) {
      return false;
    }
    final int NP = pattern.length();
    if (NP <= 0) {
      return match.length() <= 0;
    }
    final int NM = match.length();
    int ip = 0, im = 0;
    char nextChar = pattern.charAt(0);
    while ((ip < NP) && (im < NM)) {
      char c = nextChar;
      ip++;
      nextChar = ip < NP ? pattern.charAt(ip) : 0;
      final boolean escaped = (c == '\\');
      if (escaped) {
        c = nextChar;
        ip++;
        nextChar = ip < NP ? pattern.charAt(ip) : 0;
      }
      if (nextChar == '*') {
        if (!escaped && c == '.') {
          if (ip >= (NP - 1)) {
            // at the end with a pattern match, so
            // all is good without checking!
            return true;
          }
          ip++;
          nextChar = pattern.charAt(ip);
          // Consume everything until the next character in the
          // pattern is found.
          if (nextChar == '\\') {
            ip++;
            nextChar = ip < NP ? pattern.charAt(ip) : 0;
          }
          do {
            if (match.charAt(im) == nextChar) {
              break;
            }
            im++;
          } while (im < NM);
          if (im == NM) {
            // Whoops, the next character in the pattern didn't
            // exist in the match.
            return false;
          }
          ip++;
          nextChar = ip < NP ? pattern.charAt(ip) : 0;
          im++;
        } else {
          // Consume only characters matching the one before '*'.
          do {
            if (match.charAt(im) != c) {
              break;
            }
            im++;
          } while (im < NM);
          ip++;
          nextChar = ip < NP ? pattern.charAt(ip) : 0;
        }
      } else {
        if (c != '.' && match.charAt(im) != c)
          return false;
        im++;
      }
    }

    if (ip >= NP && im >= NM) {
      // Reached the end of both strings, all is good!
      return true;
    }

    // One last check: we may have finished the match string, but still
    // have a '.*' at the end of the pattern, which should still count
    // as a match.
    if (ip == NP - 2 && pattern.charAt(ip) == '.'
        && pattern.charAt(ip + 1) == '*') {
      return true;
    }

    return false;
  }
}
