/*
 * NVarNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import soot.Local;

public class NVarNode extends NPointerNode {
  public Local l;

  public NVarNode() {

  }

  public String toString() {
    return "VAR[" + l + "]" + id;
  }
}
