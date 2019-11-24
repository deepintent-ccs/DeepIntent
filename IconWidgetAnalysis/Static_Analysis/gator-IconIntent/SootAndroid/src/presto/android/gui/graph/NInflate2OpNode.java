/*
 * NInflate2OpNode.java - part of the GATOR project
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

// Inflate2: window.setContentView(id)
public class NInflate2OpNode extends NOpNode {
  public NInflate2OpNode(NNode layoutIdNode, NVarNode receiverNode,
      Pair<Stmt, SootMethod> callSite, boolean artificial) {
    super(callSite, artificial);
    layoutIdNode.addEdgeTo(this);
    receiverNode.addEdgeTo(this);
  }

  @Override
  public boolean consumesLayoutId() {
    return true;
  }

  @Override
  public NVarNode getReceiver() {
    return (NVarNode) this.pred.get(1);
  }

  @Override
  public NNode getParameter() {
    return this.pred.get(0);
  }

  // no getLhs()

  @Override
  public boolean hasReceiver() {
    return true;
  }

  @Override
  public boolean hasParameter() {
    return true;
  }

  @Override
  public boolean hasLhs() {
    return true;
  }
}
