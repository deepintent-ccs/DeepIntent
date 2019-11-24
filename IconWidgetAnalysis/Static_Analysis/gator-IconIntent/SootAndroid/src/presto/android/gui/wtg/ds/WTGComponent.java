/*
 * WTGComponent.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.ds;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import presto.android.Logger;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.wtg.RootTag;

/**
 * This class is created for representing a single component of wtg
 * */
public class WTGComponent {
  public static WTGComponent buildComponent(WTGNode startNode) {
    if (!(startNode.getWindow() instanceof NActivityNode)) {
      Logger.err("WTGComponent", "can not build component starting from " + startNode);
    }
    WTGComponent component = new WTGComponent(startNode);
    // step1: find all nodes within current component by only looking for
    // forward edges
    List<WTGNode> worklist = Lists.newArrayList();
    worklist.add(startNode);
    while (!worklist.isEmpty()) {
      WTGNode n = worklist.remove(0);
      if (!component.allComponentNodes.add(n)) {
        continue;
      }
      for (WTGEdge outEdge : n.getOutEdges()) {
        if (outEdge.getRootTag() == RootTag.start_activity
            || outEdge.getRootTag() == RootTag.show_dialog
            || outEdge.getRootTag() == RootTag.open_context_menu
            || outEdge.getRootTag() == RootTag.open_options_menu) {
          WTGNode tgtNode = outEdge.getTargetNode();
          if (tgtNode.getWindow() instanceof NActivityNode) {
            continue;
          }
          worklist.add(tgtNode);
        }
      }
    }
    // check for boundary edges: forward/backward/power/home edges targeting
    // node out of component
    for (WTGNode componentNode : component.allComponentNodes) {
      for (WTGEdge outEdge : componentNode.getOutEdges()) {
        WTGNode tgtNode = outEdge.getTargetNode();
        if (!component.allComponentNodes.contains(tgtNode)) {
          // it is boundary edge
          if (outEdge.getRootTag() == RootTag.start_activity
              || outEdge.getRootTag() == RootTag.show_dialog
              || outEdge.getRootTag() == RootTag.open_context_menu
              || outEdge.getRootTag() == RootTag.open_options_menu) {
            component.forwardBoundaryEdges.add(outEdge);
          } else if (outEdge.getRootTag() == RootTag.finish_activity
              || outEdge.getRootTag() == RootTag.dismiss_dialog
              || outEdge.getRootTag() == RootTag.close_menu
              || outEdge.getRootTag() == RootTag.implicit_back) {
            component.backwardBoundaryEdges.add(outEdge);
          } else if (outEdge.getRootTag() == RootTag.implicit_home
              || outEdge.getRootTag() == RootTag.implicit_power) {
            component.hardwareBoundaryEdges.add(outEdge);
          }
        }
      }
    }
    return component;
  }

  private WTGComponent() {

  }

  private WTGComponent(WTGNode act) {
    this.activity = act;
    this.forwardBoundaryEdges = Sets.newHashSet();
    this.backwardBoundaryEdges = Sets.newHashSet();
    this.hardwareBoundaryEdges = Sets.newHashSet();
    this.allComponentNodes = Sets.newHashSet();
  }

  public Set<WTGEdge> getForwardBoundaryEdges() {
    return this.forwardBoundaryEdges;
  }

  public Set<WTGEdge> getBackwardBoundaryEdges() {
    return this.backwardBoundaryEdges;
  }

  public Set<WTGEdge> getHardwareBoundaryEdges() {
    return hardwareBoundaryEdges;
  }

  public WTGNode getActivityNode() {
    return this.activity;
  }

  public Set<WTGNode> getComponentNodes() {
    return this.allComponentNodes;
  }

  public boolean isBoundaryEdge(WTGEdge edge) {
    return forwardBoundaryEdges.contains(edge)
        || backwardBoundaryEdges.contains(edge)
        || hardwareBoundaryEdges.contains(edge);
  }

  private Set<WTGEdge> forwardBoundaryEdges;
  private Set<WTGEdge> backwardBoundaryEdges;
  // hardware boundary edges only contain edges corresponding to power and home
  // events
  private Set<WTGEdge> hardwareBoundaryEdges;
  private Set<WTGNode> allComponentNodes;
  private WTGNode activity;
}
