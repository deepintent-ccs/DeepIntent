/*
 * NSetListenerOpNode.java - part of the GATOR project
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

// SetListener: view.setListener(listener)
// "this.flag == true": it is a context menu SetListener
public class NSetListenerOpNode extends NOpNode {
  public boolean isContextMenuSetListener;
  public NSetListenerOpNode(NVarNode viewNode, NNode listenerNode,
      Pair<Stmt, SootMethod> callSite, boolean isContextMenuSetListener, boolean artificial) {
    super(callSite, artificial);
    this.isContextMenuSetListener = isContextMenuSetListener;
    // hack for x.SetListener(x)
    if (viewNode.equals(listenerNode)) {
      listenerNode.addEdgeTo(this);
      this.pred.add(viewNode);
    } else {
      listenerNode.addEdgeTo(this);
      viewNode.addEdgeTo(this);
    }
  }

  @Override
  public NVarNode getReceiver() {
    return (NVarNode) this.pred.get(1);
  }

  @Override
  public NNode getParameter() {
    return this.pred.get(0);
  }

  @Override
  public boolean hasReceiver() {
    return true;
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
