/*
 * AndroidCallGraph.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.flowgraph;

import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import soot.jimple.Stmt;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class AndroidCallGraph {
  public static class Edge {
    public SootMethod source;
    public SootMethod target;
    public Stmt callSite;
    public Edge(SootMethod source, SootMethod target, Stmt callSite) {
      this.source = source;
      this.target = target;
      this.callSite = callSite;
    }
    public Stmt getCallSite() {
      return callSite;
    }
  }

  public static class Node {
    public Set<Edge> incomings;
    public Set<Edge> outgoings;
    public SootMethod current;
    public Node(SootMethod mtd) {
      this.current = mtd;
      this.incomings = Sets.newHashSet();
      this.outgoings = Sets.newHashSet();
    }
  }

  private Map<SootMethod, Node> sm2nodeMap;
  // callsite -> <callee, edge>
  private Map<Stmt, Map<SootMethod, Edge>> allEdges;

  private static AndroidCallGraph theInstance;
  private AndroidCallGraph() {
    this.sm2nodeMap = Maps.newHashMap();
    this.allEdges = Maps.newHashMap();
  }
  public static synchronized AndroidCallGraph v() {
    if (theInstance == null) {
      theInstance = new AndroidCallGraph();
    }
    return theInstance;
  }

  public Node getNode(SootMethod sm) {
    return this.sm2nodeMap.get(sm);
  }

  public Set<Edge> getEdge(Stmt s) {
    Set<Edge> edges = Sets.newHashSet();
    Map<SootMethod, Edge> mtdToEdge = this.allEdges.get(s);
    if (mtdToEdge == null) {
      return edges;
    }
    for (Edge e : mtdToEdge.values()) {
      edges.add(e);
    }
    return edges;
  }

  public int numberOfNodes() {
    return this.sm2nodeMap.size();
  }
  public int numberOfEdges() {
    return this.allEdges.size();
  }

  public Edge add(SootMethod source, SootMethod target, Stmt callSite) {
    Preconditions.checkNotNull(source);
    Preconditions.checkNotNull(target);
    Preconditions.checkNotNull(callSite);
    Map<SootMethod, Edge> calleeToEdge = this.allEdges.get(callSite);
    if (calleeToEdge == null) {
      calleeToEdge = Maps.newHashMap();
      allEdges.put(callSite, calleeToEdge);
    }
    Edge e = calleeToEdge.get(target);
    if (e != null) {
      return e;
    }
    e = new Edge(source, target, callSite);
    calleeToEdge.put(target, e);
    Node srcNode = this.sm2nodeMap.get(source);
    if (srcNode == null) {
      srcNode = new Node(source);
      this.sm2nodeMap.put(source, srcNode);
    }
    Node tgtNode = this.sm2nodeMap.get(target);
    if (tgtNode == null) {
      tgtNode = new Node(target);
      this.sm2nodeMap.put(target, tgtNode);
    }
    srcNode.outgoings.add(e);
    tgtNode.incomings.add(e);
    return e;
  }

  public Set<Edge> getOutgoingEdges(SootMethod source) {
    Node current = this.sm2nodeMap.get(source);
    // don't forget the boundary edge (app,library)
    if (current == null) {
      return Sets.newHashSet();
      // current = new Node(source);
      // this.sm2nodeMap.put(source, current);
    }
    return current.outgoings;
  }

  public Set<Edge> getIncomingEdges(SootMethod tgt) {
    Node current = this.sm2nodeMap.get(tgt);
    // don't forget the boundary edge (app,library)
    if (current == null) {
      return Sets.newHashSet();
      // current = new Node(tgt);
      // this.sm2nodeMap.put(tgt, current);
    }
    return current.incomings;
  }
}
