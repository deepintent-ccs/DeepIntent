/*
 * NActivityNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

public class NActivityNode extends NWindowNode {
  @Override
  public String toString() {
    return "ACT[" + c + "]" + id;
  }
}
