/*
 * NListenerAllocNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import soot.SootClass;

public class NListenerAllocNode extends NAllocNode {
  public SootClass c;
  public NListenerAllocNode(SootClass c) {
    this.c = c;
  }
  public String toString() {
    return "NEWLISTENER[" + c + "]" + id;
  }
}
