/*
 * NDialogNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;

public class NDialogNode extends NWindowNode {

  public Stmt allocStmt;
  public SootMethod allocMethod;
  public boolean cancelable;

  public NDialogNode(SootClass dialogClass, Stmt allocStmt, SootMethod allocMethod) {
    this.c = dialogClass;
    this.allocStmt = allocStmt;
    this.allocMethod = allocMethod;
    this.cancelable = true;
  }

  @Override
  public String toString() {
    return "DIALOG[" + c + "]" + id + ", alloc: " + allocMethod;
  }
}
