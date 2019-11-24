/*
 * NAddView1OpNode.java - part of the GATOR project
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

// AddView1: act.setContentView(view)
public class NAddView1OpNode extends NOpNode {
  public NAddView1OpNode(NNode parameterNode, NNode receiverNode,
      Pair<Stmt, SootMethod> callSite, boolean artificial) {
    super(callSite, artificial);
    parameterNode.addEdgeTo(this);
    receiverNode.addEdgeTo(this);
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
