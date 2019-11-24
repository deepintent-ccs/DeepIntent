/*
 * NLauncherNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.flowgraph;

import soot.Scene;
import soot.SootClass;

public class NLauncherNode extends NSpecialNode {

  SootClass fakeClass;

  private NLauncherNode() {
    fakeClass = new SootClass("presto.android.gui.stubs.PrestoFakeLauncherNodeClass");
    Scene.v().addClass(fakeClass);
  }

  public String toString() {
    return "LAUNCHER_NODE[]" + id;
  }

  @Override
  public SootClass getClassType() {
    return fakeClass;
  }

  public final static NLauncherNode LAUNCHER = new NLauncherNode();
}
