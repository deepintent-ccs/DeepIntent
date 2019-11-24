/*
 * NTabSpecNode.java - part of the GATOR project
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

public class NTabSpecNode extends NObjectNode {
  public SootClass c;
  public Stmt allocStmt;
  public SootMethod allocMethod;

  public NTabSpecNode(SootClass tabSpecClass, Stmt allocStmt, SootMethod allocMethod) {
    c = tabSpecClass;
    this.allocStmt = allocStmt;
    this.allocMethod = allocMethod;
  }

  @Override
  public SootClass getClassType() {
    return c;
  }

}
