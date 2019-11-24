/*
 * NMenuIdNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import presto.android.gui.IDNameExtractor;

public class NMenuIdNode extends NIdNode {
  public NMenuIdNode(Integer i) {
    super(i, "MID");
  }

  @Override
  public String getIdName() {
    return IDNameExtractor.v().menuIdName(i);
  }
}
