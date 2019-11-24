/*
 * NAddView2OpNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

// AddView2: parent.addView(child)
public class NAddView2OpNode extends NOpNode {
  public NAddView2OpNode(NVarNode parent, NVarNode child,
      Pair<Stmt, SootMethod> callSite, boolean artificial) {
    super(callSite, artificial);
    child.addEdgeTo(this);
    parent.addEdgeTo(this);
  }

  @Override
  public NVarNode getReceiver() {
    return (NVarNode) this.pred.get(1);
  }

  @Override
  public boolean hasReceiver() {
    return true;
  }

  @Override
  public NNode getParameter() {
    return this.pred.get(0);
  }

  @Override
  public boolean hasParameter() {
    return true;
  }

  // no getLhs()
  @Override
  public boolean hasLhs() {
    return false;
  }
}
