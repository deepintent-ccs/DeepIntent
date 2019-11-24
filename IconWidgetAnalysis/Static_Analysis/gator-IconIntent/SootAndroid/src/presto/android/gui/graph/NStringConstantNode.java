/*
 * NStringConstantNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import soot.Scene;
import soot.SootClass;

public class NStringConstantNode extends NObjectNode {
  public String value;

  @Override
  public SootClass getClassType() {
    return Scene.v().getSootClass("java.lang.String");
  }

  @Override
  public String toString() {
    return "StringConst[" + (value.isEmpty() ? "<an-empty-string>" : value)
        + "]" + id;
  }
}
