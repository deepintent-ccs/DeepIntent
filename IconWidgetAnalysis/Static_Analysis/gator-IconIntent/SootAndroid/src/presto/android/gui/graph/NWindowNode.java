/*
 * NWindowNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import java.util.Set;

import soot.SootClass;

import com.google.common.collect.Sets;

/**
 *  A window node is a GUI-related object node that can contain a root View
 *  object to be displayed. Activity and Dialog are two typical instances of
 *  the window nodes.
 *
 */
public abstract class NWindowNode extends NObjectNode {
  public static Set<NWindowNode> windowNodes = Sets.newHashSet();

  public SootClass c;

  NWindowNode() {
    windowNodes.add(this);
  }

  @Override
  public SootClass getClassType() {
    return c;
  }

}
