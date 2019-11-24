/*
 * NOptionsMenuNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import soot.Scene;
import soot.SootClass;

public class NOptionsMenuNode extends NMenuNode {
  public SootClass ownerActivity;

  @Override
  public SootClass getClassType() {
    return Scene.v().getSootClass("android.view.Menu");
  }

  @Override
  public String toString() {
    return "OptionsMenu[" + ownerActivity + "]" + id;
  }
}
