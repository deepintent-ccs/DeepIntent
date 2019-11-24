/*
 * NMenuInflateOpNode.java - part of the GATOR project
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

/**
 *
 * This is very similar to Inflate1, but different in subtle ways. So, we create
 * this class for convenience. Due to the subtlety, we don't consider this to
 * have the traditional sense of receivers or parameters.
 *
 */
// MenuInflate: menuInflater.inflate(menuId, menu)
public class NMenuInflateOpNode extends NOpNode {
  public NMenuInflateOpNode(NNode menuId, NNode menu,
      Pair<Stmt, SootMethod> callSite, boolean artificial) {
    super(callSite, artificial);
    menuId.addEdgeTo(this);
    menu.addEdgeTo(this);
  }

  @Override
  public boolean consumesMenuId() {
    return true;
  }

  // no getReceiver
  @Override
  public boolean hasReceiver() {
    return false;
  }

  // no getParameter
  @Override
  public boolean hasParameter() {
    return false;
  }

  // no getLhs
  @Override
  public boolean hasLhs() {
    return false;
  }
}
