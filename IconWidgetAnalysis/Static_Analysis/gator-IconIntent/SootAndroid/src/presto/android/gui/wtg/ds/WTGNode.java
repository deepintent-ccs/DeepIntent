/*
 * WTGNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.ds;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import presto.android.gui.graph.NObjectNode;

public class WTGNode {
  private static int ID_COUNT = 0;
  private final int id;
  private final NObjectNode window;
  private List<WTGEdge> incomingEdges;
  private List<WTGEdge> outgoingEdges;
  
  public WTGNode(final NObjectNode window) {
    this.window = window;
    incomingEdges = Lists.newArrayList();
    outgoingEdges = Lists.newArrayList();
    id = ++ID_COUNT;
  }
  
  public void addOutEdge(final WTGEdge out) {
    Preconditions.checkNotNull(out);
    if (!outgoingEdges.contains(out)) {
      outgoingEdges.add(out);
    }
  }
  
  public void addInEdge(final WTGEdge in) {
    Preconditions.checkNotNull(in);
    if (!incomingEdges.contains(in)) {
      incomingEdges.add(in);
    }
  }
  
  public WTGEdge removeOutEdge(final WTGEdge out) {
    boolean succ = outgoingEdges.remove(out);
    if (succ) {
      return out;
    } else {
      return null;
    }
  }
  
  public WTGEdge removeInEdge(final WTGEdge in) {
    boolean succ = incomingEdges.remove(in);
    if (succ) {
      return in;
    } else {
      return null;
    }
  }

  public NObjectNode getWindow() {
    return this.window;
  }

  public Collection<WTGEdge> getOutEdges() {
    return this.outgoingEdges;
  }

  public Collection<WTGEdge> getInEdges() {
    return this.incomingEdges;
  }

  public int countInEdges() {
    return incomingEdges.size();
  }

  public int countOutEdges() {
    return outgoingEdges.size();
  }

  public int getId() {
    return id;
  }

  public String toString() {
    return String.valueOf(window);
  }
}
