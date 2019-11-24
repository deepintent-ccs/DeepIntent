/*
 * LaunchConfiguration.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.Collections;
import java.util.Set;

import presto.android.xml.XMLParser.ActivityLaunchMode;

// Not convinced that this is the right thing to do... why not simply a set of
// boolean variables?
public class LaunchConfiguration {
  // masks
//  private final static int FLAG_EMPTY = 0x0001;
  private final static int FLAG_INVALID = 0x0002;

  // launchMode
  public final static int FLAG_STANDARD = 0x0004;
  public final static int FLAG_SINGLE_TOP = 0x0008;

  // task manipulation
  public final static int FLAG_CLEAR_TOP = 0x0010;
  public final static int FLAG_REORDER_TO_FRONT = 0x0020;

  public final static int FLAG_FINISH = 0x0040;

  public final static LaunchConfiguration INVALID_CONFIGURATION =
      new LaunchConfiguration(FLAG_INVALID);

  public final static Set<LaunchConfiguration> INVALID_CONFIGURATION_SET =
      Collections.singleton(INVALID_CONFIGURATION);

  public final static LaunchConfiguration FINISH_CONFIGURATION =
      new LaunchConfiguration(FLAG_FINISH);

  int flags;

  public LaunchConfiguration() {
    this(FLAG_STANDARD);
  }

  public LaunchConfiguration(ActivityLaunchMode launchMode) {
    if (launchMode == ActivityLaunchMode.standard) {
      flags = FLAG_STANDARD;
    } else if (launchMode == ActivityLaunchMode.singleTop) {
      flags = FLAG_SINGLE_TOP;
    } else {
      throw new RuntimeException("launchMode=" + launchMode);
    }
  }

  public LaunchConfiguration(int flags) {
    setFlags(flags);
  }

  public int getFlags() {
    return flags;
  }

  public void setFlags(int flags) {
    this.flags = flags;
  }

  public void addFlags(int f) {
    flags |= f;
  }

  public boolean isSet(int f) {
    return (flags & f) != 0;
  }

  public boolean isInvalid() {
    return this == INVALID_CONFIGURATION;
  }

  public boolean isFinish() {
    return this == FINISH_CONFIGURATION;
  }

  public boolean isNonStandard(boolean selfTransition) {
    return isSet(LaunchConfiguration.FLAG_CLEAR_TOP)
        || isSet(LaunchConfiguration.FLAG_REORDER_TO_FRONT)
        || (isSet(LaunchConfiguration.FLAG_SINGLE_TOP)
            && selfTransition);
  }

  @Override
  public String toString() {
    if (isInvalid()) {
      return "invalid";
    }
    if (isFinish()) {
      return "finish";
    }
    boolean standard = (flags & FLAG_STANDARD) != 0;
    boolean singleTop = (flags & FLAG_SINGLE_TOP) != 0;
    boolean clearTop = (flags & FLAG_CLEAR_TOP) != 0;
    boolean reorderToFront = (flags & FLAG_REORDER_TO_FRONT) != 0;
    if (!(standard ^ singleTop)) {
      throw new RuntimeException("0x" + Integer.toHexString(flags));
    }
    String s = standard ? "standard" : "singleTop";
    if (clearTop) {
      s += ",clearTop";
    }
    if (reorderToFront) {
      s += ",reorderToFront";
    }
    return s;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LaunchConfiguration)) {
      return false;
    }
    return flags == ((LaunchConfiguration)o).flags;
  }

  @Override
  public int hashCode() {
    return flags;
  }
}
