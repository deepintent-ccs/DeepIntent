/*
 * EventHandler.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.listener;

import soot.SootClass;

public class EventHandler {
  String subsig;
  int position;
  SootClass viewClass;

  public EventHandler(String subsig, int position, SootClass viewClass) {
    this.subsig = subsig;
    this.position = position;
    this.viewClass = viewClass;
  }

  // For type and parameter value, use return value directly. For local variable
  // representing this parameter, use position+1. Check if return value is -1
  // before use.
  public int getPosition() {
    return position;
  }
}
