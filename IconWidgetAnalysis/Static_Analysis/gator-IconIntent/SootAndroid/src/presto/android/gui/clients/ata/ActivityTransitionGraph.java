/*
 * ActivityTransitionGraph.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import presto.android.Configs;
import presto.android.MultiMapUtil;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/*
 * A digraph representing activity transition relationships. Node is activity
 * class. Edge is launch configurations.
 */
public class ActivityTransitionGraph {
  int nextNodeId = 0;

  int nodeCount = 0;
  int edgeCount = 0;

  class Node {
    public int id;
    public String activityClassName;
    public Set<Edge> outgoingEdges;

    public Node(String activityClassName) {
      nodeCount++;
      id = nextNodeId++;
      this.activityClassName = activityClassName;
      outgoingEdges = Sets.newHashSet();
    }

    @Override
    public String toString() {
      return "Node(" + activityClassName + ")";
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Node)) {
        return false;
      }
      Node otherNode = (Node) other;
      return this.activityClassName.equals(otherNode.activityClassName);
    }

    @Override
    public int hashCode() {
      return activityClassName.hashCode();
    }
  }

  class Edge {
    public Node source;
    public Node target;
    public Set<LaunchConfiguration> configs;

    public Edge(Node source, Node target, Set<LaunchConfiguration> configs) {
      this.source = source;
      this.target = target;
      this.configs = configs;
      source.outgoingEdges.add(this);
      edgeCount++;
    }

    public Edge(Node source, Node target) {
      this(source, target, Sets.<LaunchConfiguration>newHashSet());
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

  interface Printer {
    public void print(ActivityTransitionGraph atg, PrintWriter out);
  }

  static class DotPrinter implements Printer {
    @Override
    public void print(ActivityTransitionGraph atg, PrintWriter out) {
      // head
      String graphName = Configs.benchmarkName;
      out.println("digraph " + graphName + " {");

      // nodes
      Collection<Node> nodes = atg.activityClassNameAndNodes.values();
      for (Node node : nodes) {
        out.printf("  n%d [label=\"%s\"]\n", node.id, node.activityClassName);
      }

      // edges
      for (Node source : nodes) {
        String sourceName = "n" + source.id;
        for (Edge edge : source.outgoingEdges) {
          String targetName = "n" + edge.target.id;
          String label = Joiner.on("}, {").join(edge.configs);
          out.printf("  %s -> %s [label=\"[{%s}]\"]\n", sourceName, targetName, label);
        }
      }

      // tail
      out.println("}\n");

      out.flush();
    }
  }

  Map<String, Node> activityClassNameAndNodes = Maps.newHashMap();

  public Node getNode(String activityClassName) {
    Node node = activityClassNameAndNodes.get(activityClassName);
    if (node == null) {
      node = new Node(activityClassName);
      activityClassNameAndNodes.put(activityClassName, node);
    }
    return node;
  }

  public int nodeCount() {
    return activityClassNameAndNodes.size();
  }

  public Edge findOrCreateEdgeWithConfig(Node source, Node target,
      LaunchConfiguration config) {
    // ignore invalid config
    if (config.isInvalid()) {
      return null;
    }
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
    matchedEdge.configs.add(config);
    return matchedEdge;
  }

  public Map<Node, Set<Node>> finishEdges = Maps.newHashMap();

  public void addFinishEdge(Node source, Node target) {
    MultiMapUtil.addKeyAndHashSetElement(finishEdges, source, target);
  }

  public void addFinishEdge(String source, String target) {
    addFinishEdge(getNode(source), getNode(target));
  }

  public boolean hasInverseFinishEdge(Edge edge) {
    Node sourceOfFinish = edge.target;
    Node targetOfFinish = edge.source;
    return MultiMapUtil.contains(finishEdges, sourceOfFinish, targetOfFinish);
  }

  public boolean containsNonStandard(Edge edge) {
    Node source = edge.source;
    Node target = edge.target;
    for (LaunchConfiguration config : edge.configs) {
      if (isNonStandard(source, target, config)) {
        return true;
      }
    }
    return false;
  }

  public int countNonStandard(Edge edge) {
    int n = 0;
    Node source = edge.source;
    Node target = edge.target;
    for (LaunchConfiguration config : edge.configs) {
      if (isNonStandard(source, target, config)) {
        n++;
      }
    }
    return n;
  }

  public boolean isNonStandard(Node source, Node target, LaunchConfiguration config) {
    boolean selfTransition = source.equals(target);
    return config.isSet(LaunchConfiguration.FLAG_CLEAR_TOP)
        || config.isSet(LaunchConfiguration.FLAG_REORDER_TO_FRONT)
        || (config.isSet(LaunchConfiguration.FLAG_SINGLE_TOP)
            && selfTransition);
  }
}
