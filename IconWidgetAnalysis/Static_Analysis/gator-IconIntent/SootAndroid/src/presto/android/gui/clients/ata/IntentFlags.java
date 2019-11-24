/*
 * IntentFlags.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.Set;

import com.google.common.collect.Sets;

public class IntentFlags {
  public static Set<Integer> invalidFlagMasks = Sets.newHashSet(
      0x00400000, 0x00008000, 0x00080000, 0x08000000,
      0x10000000, 0x01000000, 0x00200000, 0x00004000);

  public static final int FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
  public static final int FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00020000;
  public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;

  public int flags;
  public boolean invalid;
  public boolean empty;
  public boolean clearTop;
  public boolean reorderToFront;
  public boolean singleTop;

  enum Type {
    AddFlags,
    SetFlags,
  }

  Type type;

  private IntentFlags(int flags, Type type) {
    this.flags = flags;
    this.type = type;
  }

  public static IntentFlags v(int i, Type type) {
    IntentFlags intentFlags = new IntentFlags(i, type);
    for (int invalidMask : invalidFlagMasks) {
      if ((i & invalidMask) != 0) {
        intentFlags.invalid = true;
        return intentFlags;
      }
    }

    boolean nonEmpty = false;
    if ((i & FLAG_ACTIVITY_CLEAR_TOP) != 0) {
      intentFlags.clearTop = true;
      nonEmpty = true;
    }
    if ((i & FLAG_ACTIVITY_REORDER_TO_FRONT) != 0) {
      intentFlags.reorderToFront = true;
      nonEmpty = true;
    }
    if ((i & FLAG_ACTIVITY_SINGLE_TOP) != 0) {
      intentFlags.singleTop = true;
      nonEmpty = true;
    }
    intentFlags.empty = !nonEmpty;

    return intentFlags;
  }

  public void merge(IntentFlags other) {
    flags |= other.flags;
    if (invalid) {
      return;
    }
    if (other.invalid) {
      invalid = true;
      empty = false;
      clearTop = false;
      reorderToFront = false;
      singleTop = false;
      return;
    }
    empty &= other.empty;
    clearTop |= other.clearTop;
    reorderToFront |= other.reorderToFront;
    singleTop |= other.singleTop;
  }

  @Override
  public String toString() {
    String hex = "(0x" + Integer.toHexString(flags) + ")";
    if (invalid) {
      return "invalid " + hex;
    }
    if (empty) {
      return "empty " + hex;
    }
    String flagString = "";
    if (clearTop) {
      flagString += "clearTop,";
    }
    if (singleTop) {
      flagString += "singleTop,";
    }
    if (reorderToFront) {
      flagString += "reorderToFront,";
    }
    if (flagString.isEmpty()) {
      throw new RuntimeException(hex);
    }
    return flagString.substring(0, flagString.length() - 1) + hex;
  }
}
