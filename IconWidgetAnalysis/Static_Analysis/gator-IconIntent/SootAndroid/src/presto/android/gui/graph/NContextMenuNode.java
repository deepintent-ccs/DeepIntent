/*
 * NContextMenuNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import java.util.Set;

import soot.Scene;
import soot.SootClass;

import com.google.common.collect.Sets;

public class NContextMenuNode extends NMenuNode {
  public NVarNode menuParameterNode;
  public Set<NVarNode> varNodesForRegisteredViews = Sets.newHashSet();
//  public Set<NObjectNode> resolvedRegisteredViews = Sets.newHashSet();

  // TODO(tony): record the allocation site represented by onCreateContextMenu
  //             if necessary.

  @Override
  public SootClass getClassType() {
    return Scene.v().getSootClass("android.view.ContextMenu");
  }

  @Override
  public String toString() {
    // TODO(tony): print out registered views
    return "ContextMenu[" + menuParameterNode + "]" + id;
  }
}
