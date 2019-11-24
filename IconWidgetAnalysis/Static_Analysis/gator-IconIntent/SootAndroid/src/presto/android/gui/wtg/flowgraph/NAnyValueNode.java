/*
 * NAnyValueNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.flowgraph;

import soot.SootClass;

/**
 * this class is created for debugging purpose.
 * it will be used to represent the values which are not modelled by our wtg analysis,
 * but essential in determining the content of intents, e.g., String lhs = r.f();
 * */
public class NAnyValueNode extends NSpecialNode {
  // we only have one instance for this class
  // but should we create one for any stmt?
  SootClass fakeClass;

  private NAnyValueNode() {
    fakeClass = new SootClass("presto.android.gui.stubs.PrestoFakeAnyValueNodeClass");
  }

  public String toString() {
    return "ANY_VALUE_NODE[]" + id;
  }

  @Override
  public SootClass getClassType() {
    return fakeClass;
  }

  public static final NAnyValueNode ANY = new NAnyValueNode();
}
