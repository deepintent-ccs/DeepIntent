/*
 * NAnonymousIdNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

public class NAnonymousIdNode extends NIdNode {
  public NAnonymousIdNode(Integer i) {
    super(i, "AID");
  }

  @Override
  public String getIdName() {
    return "ANONYMOUS";
  }
}
