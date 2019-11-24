/*
 * WTG.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.ds;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import presto.android.Logger;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.EventHandler.EventHandlerSig;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;
import soot.SootMethod;

public class WTG {
  // represent launcher node
  private WTGNode launcher;
  // all wtg nodes
  private Map<NObjectNode, WTGNode> allNodes;
  // all wtg edges
  private Map<WTGEdgeSig, WTGEdge> allEdges;
  // all components in wtg
  private Map<WTGNode, WTGComponent> allComponents;
  // map from forward edge to back edge
  private Multimap<WTGEdge, WTGEdge> backEdgeMap;
  // map from EventHandlerSig to event handler
  private Map<EventHandlerSig, EventHandler> allHandlers;
  // map from window back to owners
  private Multimap<WTGNode, NActivityNode> ownership;

  public String apkname;

  public WTG() {
    allNodes = Maps.newHashMap();
    allEdges = Maps.newHashMap();
    allComponents = Maps.newHashMap();
    allHandlers = Maps.newHashMap();
    backEdgeMap = HashMultimap.create();
  }

  public WTGNode addNode(NObjectNode objNode) {
    Preconditions.checkNotNull(objNode);
    WTGNode wtgNode = this.allNodes.get(objNode);
    if (wtgNode == null) {
      wtgNode = new WTGNode(objNode);
      this.allNodes.put(objNode, wtgNode);
    }
    return wtgNode;
  }

  public WTGNode addLauncherNode(NObjectNode objNode) {
    WTGNode launcherNode = addNode(objNode);
    if (launcher == null) {
      launcher = launcherNode;
    } else if (launcher != null && launcher != launcherNode) {
      Logger.err(getClass().getSimpleName(), "try to set multiple launchers");
    }
    return launcher;
  }

  public WTGNode getLauncherNode() {
    return launcher;
  }

  public WTGEdge addEdge(WTGEdge newEdge) {
    WTGEdgeSig sig = newEdge.getSig();
    WTGEdge existEdge = allEdges.get(sig);
    if (existEdge == null) {
      allEdges.put(sig, newEdge);
      newEdge.getSourceNode().addOutEdge(newEdge);
      newEdge.getTargetNode().addInEdge(newEdge);
      return newEdge;
    } else {
      return existEdge;
    }
  }

  public WTGEdge removeEdge(WTGEdge e) {
    if (!allEdges.containsKey(e.getSig())) {
      return null;
    }
    // remove this edge from edge set
    allEdges.remove(e.getSig());
    // remove the pair back edge
    backEdgeMap.removeAll(e);
    // remove the inEdge and outEdge
    e.getSourceNode().removeOutEdge(e);
    e.getTargetNode().removeInEdge(e);
    // if it is back edge, remove the corresponding pair relationship
    Set<WTGEdge> toRemoveForwardEdges = Sets.newHashSet();
    for (WTGEdge forward : backEdgeMap.keySet()) {
      if (backEdgeMap.get(forward).contains(e)) {
        toRemoveForwardEdges.add(forward);
      }
    }
    for (WTGEdge forward : toRemoveForwardEdges) {
      backEdgeMap.remove(forward, e);
    }
    return e;
  }

  public Collection<WTGNode> getNodes() {
    return allNodes.values();
  }

  public Collection<WTGEdge> getEdges() {
    return allEdges.values();
  }

  public WTGNode getNode(NObjectNode window) {
    WTGNode wtgNode = allNodes.get(window);
    if (wtgNode == null) {
      Logger.err(getClass().getSimpleName(), "wtg node doesn't exist: " + window);
    }
    return wtgNode;
  }

  public void addBackEdge(WTGEdge forwardEdge, WTGEdge backEdge) {
    Preconditions.checkNotNull(forwardEdge);
    Preconditions.checkNotNull(backEdge);
    if (allEdges.containsKey(forwardEdge.getSig())
        && allEdges.containsKey(backEdge.getSig())) {
      backEdgeMap.put(forwardEdge, backEdge);
    }
  }

  public void removeBackEdge(WTGEdge forwardEdge, WTGEdge backEdge) {
    Preconditions.checkNotNull(forwardEdge);
    Preconditions.checkNotNull(backEdge);
    backEdgeMap.remove(forwardEdge, backEdge);
  }

  public Collection<WTGEdge> getBackEdge(WTGEdge forwardEdge) {
    return backEdgeMap.get(forwardEdge);
  }

  public void generateComponents() {
    for (NObjectNode objNode : allNodes.keySet()) {
      if (!(objNode instanceof NActivityNode)) {
        continue;
      }
      WTGNode wtgNode = allNodes.get(objNode);
      WTGComponent newComponent = WTGComponent.buildComponent(wtgNode);
      allComponents.put(wtgNode, newComponent);
    }
  }

  public Map<WTGNode, WTGComponent> getComponents() {
    return this.allComponents;
  }

  public synchronized EventHandler getHandler(
      NObjectNode window, NObjectNode guiWidget, EventType event, SootMethod callback) {
    EventHandler eventHandler = new EventHandler(window, guiWidget, event, callback);
    EventHandlerSig sig = eventHandler.getSig();
    EventHandler existingHandler = this.allHandlers.get(sig);
    if (existingHandler == null) {
      this.allHandlers.put(sig, eventHandler);
      existingHandler = eventHandler;
    }
    return existingHandler;
  }

  public Collection<NActivityNode> getOwnerActivity(WTGNode window) {
    if (ownership == null) {
      // build ownership by forward traversing from each activity node
      // this is done only once
      ownership = HashMultimap.create();
      Set<WTGNode> visitNodes = Sets.newHashSet();
      List<WTGNode> worklist = Lists.newArrayList();
      for (NObjectNode obj : allNodes.keySet()) {
        visitNodes.clear();
        worklist.clear();
        if (obj instanceof NActivityNode) {
          WTGNode start = allNodes.get(obj);
          worklist.add(start);
          visitNodes.add(start);
          while (!worklist.isEmpty()) {
            WTGNode node = worklist.remove(0);
            for (WTGEdge outEdge : node.getOutEdges()) {
              WTGNode target = outEdge.getTargetNode();
              if (!(target.getWindow() instanceof NActivityNode)
                  && visitNodes.add(target)) {
                ownership.put(target, (NActivityNode) obj);
                worklist.add(target);
              }
            }
          }
        }
      }
    }
    return ownership.get(window);
  }

  /****************** debug purpose ***********************/
  public void dump() {
    String dotFile = null;
    try {
      dotFile = new File(".").getCanonicalPath() + "/dot_output/"+apkname+".wtg.dot";
      FileWriter output = new FileWriter(dotFile);

      BufferedWriter writer = new BufferedWriter(output);

      writer.write("digraph G {");
      writer.write("\n rankdir=LR;");
      writer.write("\n node[shape=box];");
      // draw window nodes
      for (NObjectNode objNode : allNodes.keySet()) {
        WTGNode wtgNode = allNodes.get(objNode);
        Integer label = wtgNode.getId();
        writer.write("\n n" + label + " [label=\"");
        writer.write(wtgNode + "\"];");
      }
      for (NObjectNode objNode : allNodes.keySet()) {
        WTGNode wtgNode = allNodes.get(objNode);
        for (WTGEdge outgoing : wtgNode.getOutEdges()) {
          WTGNode targetWtgNode = outgoing.getTargetNode();
          writer.write("\n n" + wtgNode.getId() + " -> n"
              + targetWtgNode.getId());
          writer.write(" [label=\"" + outgoing + "\\n" + "\"];");
        }
      }
      // end of .dot file
      writer.write("\n}");
      writer.close();
      Logger.verb(getClass().getSimpleName(), "wtg dump to file: " + dotFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
