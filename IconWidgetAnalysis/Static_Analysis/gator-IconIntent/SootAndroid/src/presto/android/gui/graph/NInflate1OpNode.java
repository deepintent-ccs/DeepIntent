/*
 * NInflate1OpNode.java - part of the GATOR project
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

// view = inflater.inflate(id)
public class NInflate1OpNode extends NOpNode {
  public NInflate1OpNode(NNode layoutIdNode, NNode lhsNode,
      Pair<Stmt, SootMethod> callSite, boolean artificial) {
    super(callSite, artificial);
    layoutIdNode.addEdgeTo(this);
    this.addEdgeTo(lhsNode);
  }

  @Override
  public boolean consumesLayoutId() {
    return true;
  }

  // no getReceiver

  @Override
  public NNode getParameter() {
    return this.pred.get(0);
  }

  @Override
  public NVarNode getLhs() {
    return (NVarNode) this.getSuccessor(0);
  }

  @Override
  public boolean hasReceiver() {
    return false;
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
