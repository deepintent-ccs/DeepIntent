/*
 * NAllocNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import soot.ArrayType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.Type;
import soot.jimple.Expr;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;

public class NAllocNode extends NObjectNode {
  public Expr e;

  @Override
  public SootClass getClassType() {
    if (e instanceof NewExpr) {
      Type type = ((NewExpr)e).getType();
      return ((RefType)type).getSootClass();
    } else if (e instanceof NewArrayExpr || e instanceof NewMultiArrayExpr) {
      Type type = e.getType();
      return Scene.v().getSootClass(((ArrayType)type).toString());
    }
    return null;
  }

  @Override
  public String toString() {
    return "NEW[" + e + "]" + id;
  }
}
