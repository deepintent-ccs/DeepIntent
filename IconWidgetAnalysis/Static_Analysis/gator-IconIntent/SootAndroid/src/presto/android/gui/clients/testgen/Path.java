/*
 * Path.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.testgen;

import com.google.common.collect.Lists;
import presto.android.gui.wtg.RootTag;
import presto.android.gui.wtg.WTGAnalysisOutput;
import presto.android.gui.wtg.WindowStack;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Path {
  /**
   * Edges on a path, in order.
   */
  private List<WTGEdge> edges;

  /**
   * Construct a new empty path.
   */
  public Path() {
    edges = Lists.newArrayList();
  }

  /**
   * @param edges list of edges to form a path
   */
  public Path(List<WTGEdge> edges) {
    this.edges = edges;
  }

  /**
   * @return true if there is no edge, false otherwise
   */
  public boolean isEmpty() {
    return edges.isEmpty();
  }

  /**
   * @return length of the path
   */
  public int length() {
    return edges.size();
  }

  /**
   * @return edges on the path
   */
  public List<WTGEdge> getEdges() {
    return edges;
  }

  /**
   * Add an edge to the path.
   *
   * @param e the edge to be added to the path
   * @throws RuntimeException if the edge is broken
   */
  public void add(WTGEdge e) {
    if (!edges.isEmpty() && getEndNode() != e.getSourceNode()) {
      throw new RuntimeException();
    }
    edges.add(e);
  }

  /**
   * Extend a path along with all outgoing edges of its end node.
   *
   * @return a list of extended paths
   */
  public List<Path> extend() {
    List<Path> newPaths = Lists.newArrayList();
    for (WTGEdge outEdge : getEndNode().getOutEdges()) {
      Path newPath = copy();
      newPath.add(outEdge);
      newPaths.add(newPath);
    }
    return newPaths;
  }

  /**
   * Extend a path by depth.
   *
   * @param depth
   * @return a list of extended paths
   */
  public List<Path> extend(int depth) {
    List<Path> newPaths = Lists.newArrayList();
    List<Path> workList = Lists.newLinkedList();
    workList.add(this);
    depth += length();
    while (!workList.isEmpty()) {
      Path path = workList.remove(0);
      if (path.length() < depth) {
        // extend the path along with all outgoing edges of its end node
        workList.addAll(path.extend());
      } else if (path.length() == depth) {
        newPaths.add(path);
      } else {
        // should never be here
        throw new RuntimeException();
      }
    }
    return newPaths;
  }

  /**
   * Extend a path along with forward outgoing edges of its end node.
   *
   * @return a list of extended paths
   */
  public List<Path> extendForward() {
    List<Path> newPaths = Lists.newArrayList();
    for (WTGEdge outEdge : getEndNode().getOutEdges()) {
      RootTag root = outEdge.getRootTag();
      boolean isForward = root == RootTag.start_activity || root == RootTag.show_dialog
          || root == RootTag.open_context_menu || root == RootTag.open_options_menu
          || root == RootTag.implicit_launch;
      if (!isForward) continue;
      Path newPath = copy();
      newPath.add(outEdge);
      newPaths.add(newPath);
    }
    return newPaths;
  }

  /**
   * Extend a path along with only feasible forward outgoing edges of its end node.
   *
   * @return a list of extended paths
   */
  public List<Path> extendFeasibleForward(WTGAnalysisOutput wtgOutput) {
    List<Path> newPaths = Lists.newArrayList();
    List<WTGEdge> outgoingEdges = wtgOutput.expandFeasibleEdge(new WindowStack(edges));
    Collections.sort(outgoingEdges, new Comparator<WTGEdge>() {
      @Override
      public int compare(WTGEdge e1, WTGEdge e2) {
        return e1.toString().compareTo(e2.toString());
      }
    });
    for (WTGEdge outEdge : outgoingEdges) {
      RootTag root = outEdge.getRootTag();
      boolean isForward = root == RootTag.start_activity || root == RootTag.show_dialog
          || root == RootTag.open_context_menu || root == RootTag.open_options_menu
          || root == RootTag.implicit_launch;
      if (!isForward) continue;
      Path newPath = copy();
      newPath.add(outEdge);
      newPaths.add(newPath);
    }
    return newPaths;
  }

  /**
   * Extend a path with only feasible outgoing edges, including both forward and backward edges.
   *
   * @return a list of extended paths
   */
  public List<Path> extendFeasible(WTGAnalysisOutput wtgOutput) {
    List<Path> newPaths = Lists.newArrayList();
    for (WTGEdge feasibleEdge : wtgOutput.expandFeasibleEdge(new WindowStack(edges))) {
      Path newPath = copy();
      newPath.add(feasibleEdge);
      newPaths.add(newPath);
    }
    return newPaths;
  }

  /**
   * Extend a path by depth.
   *
   * @param depth
   * @return a list of extended paths
   */
  public List<Path> extendFeasible(WTGAnalysisOutput wtgOutput, int depth) {
    if (1 == depth)
      return extendFeasible(wtgOutput);
    List<Path> newPaths = Lists.newArrayList();
    List<Path> workList = Lists.newLinkedList();
    workList.add(this);
    depth += length();
    while (!workList.isEmpty()) {
      Path path = workList.remove(0);
      if (path.length() < depth) {
        workList.addAll(path.extendFeasible(wtgOutput));
      } else if (path.length() == depth) {
        newPaths.add(path);
      } else {
        // should never be here
        throw new RuntimeException();
      }
    }
    return newPaths;
  }

  /**
   * @return true if a path contains back event, false if it doesn't
   */
  public boolean containsBackEvent() {
    for (WTGEdge e : edges) {
      if (e.getRootTag() == RootTag.implicit_back) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer("Path <");
    for (WTGEdge e : edges) {
      sb.append(e + " >>> ");
    }
    sb.append(">");
    return sb.toString();
  }

  /**
   * @return a copy of the path
   */
  public Path copy() {
    Path newPath = new Path();
    newPath.edges.addAll(edges);
    return newPath;
  }

  /**
   * @return end node of a path, i.e. the target node of the last edge
   */
  public WTGNode getEndNode() {
    if (edges.isEmpty()) {
      return null;
    }
    return edges.get(edges.size() - 1).getTargetNode();
  }

  /**
   * @return start node of a path, i.e., the source node of the first edge
   */
  public WTGNode getStartNode() {
    if (edges.isEmpty()) {
      return null;
    }
    return edges.get(0).getSourceNode();
  }
}
