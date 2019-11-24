/*
 * NFindView3OpNode.java - part of the GATOR project
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

// FindView3: lhs = view.m()
public class NFindView3OpNode extends NOpNode {
  public FindView3Type type;

  public NFindView3OpNode(NNode receiverNode, NNode lhsNode,
      Pair<Stmt, SootMethod> callSite, FindView3Type type, boolean artificial) {
    super(callSite, artificial);
    receiverNode.addEdgeTo(this);
    this.addEdgeTo(lhsNode);
    this.type = type;
  }

  @Override
  public NVarNode getReceiver() {
    return (NVarNode) this.pred.get(0);
  }

  // no getParameter()

  @Override
  public NVarNode getLhs() {
    return (NVarNode) this.getSuccessor(0);
  }

  @Override
  public boolean hasReceiver() {
    return true;
  }

  @Override
  public boolean hasParameter() {
    return false;
  }

  @Override
  public boolean hasLhs() {
    return true;
  }

  public enum FindView3Type {
    FindDescendantsAndSelf,
    FindChildren,
    FindDescendantsNoSelf,
  }
}
