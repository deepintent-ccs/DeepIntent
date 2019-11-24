/*
 * ProcessTracker.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.util;

import presto.android.Logger;

public class ProcessTracker {
  private ProcessTracker() {
    indent = 0;
    count = 0;
  }
  
  public void trackProcess(boolean cond, String msg, int entry, int sampling) {
    if (!cond) {
      return;
    }
    if (entry < 0) {
      indent--;
    }
    if (entry < 0 || entry > 0) {
      long time = System.currentTimeMillis();
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < indent; i++) {
        sb.append("\t");
      }
      if (count != 0) {
        Logger.verb(getClass().getSimpleName(), "");
        count = 0;
      }
      Logger.verb(getClass().getSimpleName(), sb.toString() + msg
          + ", Time: " + time);
    } else {
      if (++count % sampling == 0) {
        Logger.verb(getClass().getSimpleName(), ".");
      }
      if (count >= 100 * sampling) {
        count = 0;
        Logger.verb(getClass().getSimpleName(), "");
      }
    }
    if(entry > 0) {
      indent++;
    }
  }

  public static synchronized ProcessTracker v() {
    if (tracker == null) {
      tracker = new ProcessTracker();
    }
    return tracker;
  }
  private int indent;
  private int count;
  private static ProcessTracker tracker;
}
