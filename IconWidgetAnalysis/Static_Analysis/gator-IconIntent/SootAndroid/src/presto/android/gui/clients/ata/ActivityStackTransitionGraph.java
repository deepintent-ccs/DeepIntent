/*
 * ActivityStackTransitionGraph.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.ActivityStack;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.MethodSequence;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

// ActivityStack:src -> ActivityStack:tgt, annotated with set of possible
// method sequences.
public class ActivityStackTransitionGraph {
  int nextNodeId = 0;
  class Node {
    public int id;
    public ActivityStack stack;
    public Set<Edge> outgoingEdges;
    public Node(ActivityStack stack) {
      id = nextNodeId++;
      this.stack = stack;
      outgoingEdges = Sets.newHashSet();
    }

    @Override
    public String toString() {
      return stack.toString();
    }

    @Override
    public int hashCode() {
      return stack.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Node)) {
        return false;
      }
      Node node = (Node) o;
      return node.stack.equals(this.stack);
    }
  }
  class Edge {
    public Node source;
    public Node target;
    public ArrayList<MethodSequence> sequences;
    public ArrayList<LaunchConfiguration> configs;
    public Edge(Node source, Node target,
        ArrayList<MethodSequence> sequences,
        ArrayList<LaunchConfiguration> configs) {
      this.source = source;
      this.target = target;
      this.sequences = sequences;
      this.configs = configs;
      source.outgoingEdges.add(this);
    }
    public Edge(Node source, Node target) {
      this(source, target,
          Lists.<MethodSequence>newArrayList(),
          Lists.<LaunchConfiguration>newArrayList());
    }

    @Override
    public String toString() {
      return source + " ==> " + target;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Edge)) {
        return false;
      }
      Edge otherEdge = (Edge) other;
      return this.source.equals(otherEdge.source)
          && this.target.equals(otherEdge.target);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(source, target);
    }
  }

  ActivityStack initStack;
  public ActivityStack getInitStack() {
    return initStack;
  }

  public void setInitStack(ActivityStack initStack) {
    this.initStack = initStack;
    // make sure the node is created
    getNode(initStack);
  }

  Map<ActivityStack, Node> stackAndNodes = Maps.newHashMap();

  public Node getNode(ActivityStack s) {
    Node n = stackAndNodes.get(s);
    if (n == null) {
      n = new Node(s);
      stackAndNodes.put(s, n);
    }
    return n;
  }

  public Collection<Node> getNodes() {
    return stackAndNodes.values();
  }

  public Collection<Node> getNodes(String topActivity) {
    Collection<Node> nodes = Lists.newArrayList();
    for (Node n : stackAndNodes.values()) {
      if (topActivity.equals(n.stack.top())) {
        nodes.add(n);
      }
    }
    return nodes;
  }

  public Edge findOrCreateEdgeWithMethodSeq(Node source, Node target,
      MethodSequence methodSeq, LaunchConfiguration config) {
    Edge matchedEdge = null;
    for (Edge edge : source.outgoingEdges) {
      if (edge.target.equals(target)) {
        matchedEdge = edge;
        break;
      }
    }
    if (matchedEdge == null) {
      matchedEdge = new Edge(source, target);
    }
    matchedEdge.sequences.add(methodSeq);
    matchedEdge.configs.add(config);
    return matchedEdge;
  }
}
