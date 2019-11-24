/*
 * NIdNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import presto.android.gui.IDNameExtractor;

// TODO(tony): move the toString() implementation up into this class.
public abstract class NIdNode extends NNode {
  protected Integer i;
  protected String tag;

  protected NIdNode(Integer i, String tag) {
    if (i == null) {
      throw new RuntimeException("Null id!");
    }
    this.i = i;
    this.tag = tag;
  }

  public Integer getIdValue() {
    return i;
  }

  public String getIdName() {
    return IDNameExtractor.v().idName(i);
  }

  @Override
  public String toString() {
    return tag + "[" + i + "|" + getIdName() + "]" + id;
  }
}
