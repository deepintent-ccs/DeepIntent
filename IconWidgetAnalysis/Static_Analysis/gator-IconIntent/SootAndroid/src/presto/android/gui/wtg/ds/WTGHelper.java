/*
 * WTGHelper.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.ds;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import presto.android.Configs;
import presto.android.Hierarchy;
import presto.android.Logger;
import presto.android.gui.graph.NMenuNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NWindowNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.RootTag;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.StackOperation.OpType;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;

import soot.SootClass;
import soot.SootMethod;

public class WTGHelper {
  private Hierarchy hier = Hierarchy.v();

  private WTGHelper() {

  }

  public List<EventHandler> getCallbacks(WTG wtg, Object... arr) {
    List<EventHandler> callbacks = Lists.newArrayList();
    NObjectNode guiWidget = null;
    SootClass cls = null;
    for (int i = 0; i < arr.length; i++) {
      Object o = arr[i];
      if (o instanceof NObjectNode) {
        if (o instanceof NMenuNode || o instanceof NWindowNode) {
          guiWidget = (NObjectNode) o;
          cls = ((NObjectNode) o).getClassType();
        } else {
          Logger.err(getClass().getSimpleName(), "unexpected input: " + o);
        }
      } else if (o instanceof String) {
        SootMethod callback = hier.virtualDispatch((String) o, cls);
        if (callback == null) {
          Logger.verb(getClass().getSimpleName(), "can not get callback: " + o + ", cls: " + cls);
        }
        if (hier.appClasses.contains(callback.getDeclaringClass())) {
          EventHandler eventHandler = wtg.getHandler(
              guiWidget,
              guiWidget,
              EventType.implicit_lifecycle_event,
              callback);
          callbacks.add(eventHandler);
        }
      } else {
        Logger.err(getClass().getSimpleName(), "unexpected input: " + o);
      }
    }
    return callbacks;
  }

  public SootMethod getCallback(SootClass sc, String subsig) {
    SootMethod mtd = hier.virtualDispatch(subsig, sc);
    if (mtd == null) {
      return null;
    }
    if (isAppClass(mtd.getDeclaringClass())) {
      return mtd;
    }
    return null;
  }
  
  public List<SootMethod> getCallbacks(SootClass sc, String... subsigs) {
    List<SootMethod> callbacks = Lists.newArrayList();
    for (String subsig : subsigs) {
      SootMethod mtd = hier.virtualDispatch(subsig, sc);
      if (mtd == null) {
        continue;
      }
      if (!isAppClass(mtd.getDeclaringClass())) {
        continue;
      }
      callbacks.add(mtd);
    }
    return callbacks;
  }

  public Set<WTGNode> getSuccNode(
      WTG wtg, Multimap<WTGNode, WTGEdge> inEdges, WTGNode startNode) {
    Stack<StackOperation> stackOps = new Stack<StackOperation>();
    Stack<WTGNode> path = new Stack<WTGNode>();
    stackOps.push(new StackOperation(OpType.pop, startNode.getWindow()));
    Set<WTGNode> successors = Sets.newHashSet();
    getSuccNode(wtg, inEdges, startNode, stackOps, Configs.sDepth, path, successors);
    successors.remove(startNode);
    return successors;
  }

  private void getSuccNode(WTG wtg, Multimap<WTGNode, WTGEdge> inEdges, WTGNode n,
      Stack<StackOperation> stackOps, int depth, Stack<WTGNode> path, Set<WTGNode> successors) {
    if (depth <= 0) {
      return;
    }
    if (n == wtg.getLauncherNode()) {
      if (stackOps.isEmpty()) {
        successors.add(n);
      }
      return;
    }
    if (path.contains(n)) {
      return;
    }
    path.push(n);
    boolean hasForwardInEdge = false;
    for (WTGEdge inEdge : inEdges.get(n)) {
      if (!isForwardEdge(inEdge) && inEdge.getRootTag() != RootTag.fake_interim_edge) {
        continue;
      }
      hasForwardInEdge = true;
      WTGNode source = inEdge.getSourceNode();
      WTGNode nextN = source;
      List<StackOperation> ops = inEdge.getStackOps();
      Stack<StackOperation> newStackOps = new Stack<StackOperation>();
      newStackOps.addAll(stackOps);
      boolean cont = true;
      for (int i = ops.size()-1; i >= 0; i--) {
        StackOperation op = ops.get(i);
        if (newStackOps.isEmpty() && op.isPushOp()) {
          successors.add(wtg.getNode(op.getWindow()));
          cont = false;
          break;
        } else if (!op.isPushOp()
            || (op.isPushOp() && !newStackOps.isEmpty() && newStackOps.peek().getWindow() == op.getWindow())) {
          if (!op.isPushOp()) {
            newStackOps.push(op);
            nextN = wtg.getNode(op.getWindow());
            break;
          } else {
            newStackOps.pop();
          }
        } else {
          // unmatched push/pop sequence
          cont = false;
          break;
        }
      }
      if (cont) {
        getSuccNode(wtg, inEdges, nextN, newStackOps, depth-1, path, successors);
      }
    }
    if (!hasForwardInEdge) {
      successors.add(n);
    }
    path.pop();
  }

  public WTGEdge addEdge(Map<WTGEdgeSig, WTGEdge> allEdges, WTGEdge newEdge) {
    WTGEdgeSig sig = newEdge.getSig();
    WTGEdge existEdge = allEdges.get(sig);
    if (existEdge == null) {
      allEdges.put(sig, newEdge);
      return newEdge;
    } else {
      return existEdge;
    }
  }

  /**
   * @param wtg wtg
   * @param src source wtg node
   * @param tgt target wtg node
   * @param widget GUI widget
   * @param event event type
   * @param eventHandlers first triggered handler
   * @param root reason this edge happens
   * @param stackOps push/pop stack operations
   * @param callbacks callback sequence
   * */
  public WTGEdge createEdge(
      WTG wtg, WTGNode src, WTGNode tgt, NObjectNode widget, EventType event,
      Set<SootMethod> eventHandlers, RootTag root, List<StackOperation> stackOps,
      List<EventHandler> callbacks) {
    Set<EventHandler> wtgHandlers = Sets.newHashSet();
    if (eventHandlers.isEmpty()) {
      EventHandler wtgHandler = wtg.getHandler(src.getWindow(), widget, event, null);
      wtgHandlers.add(wtgHandler);
    } else {
      for (SootMethod eventHandler : eventHandlers) {
        EventHandler wtgHandler = wtg.getHandler(src.getWindow(), widget, event, eventHandler);
        wtgHandlers.add(wtgHandler);
      }
    }
    WTGEdge newEdge = new WTGEdge(src, tgt, wtgHandlers, root, stackOps, callbacks);
    return newEdge;
  }

  public boolean isAppClass(SootClass sc) {
    return hier.appClasses.contains(sc);
  }

  public boolean isSubClassOf(SootClass subClz, SootClass superClz) {
    return hier.isSubclassOf(subClz, superClz);
  }

  public boolean isForwardEdge(WTGEdge edge) {
    RootTag root = edge.getRootTag();
    boolean isForward = root == RootTag.start_activity || root == RootTag.show_dialog
        || root == RootTag.open_context_menu || root == RootTag.open_options_menu
        || root == RootTag.implicit_launch;
    return isForward;
  }

  public boolean isCyclicEdge(WTGEdge edge) {
    boolean isCyclic = edge.getRootTag() == RootTag.cyclic_edge;
    if (isCyclic && edge.getSourceNode() == null) {
      Logger.err(getClass().getSimpleName(), "cyclic edge can not have null source/target");
    }
    return isCyclic;
  }

  public boolean isHardwareEdge(WTGEdge edge) {
    RootTag root = edge.getRootTag();
    return root == RootTag.implicit_home || root == RootTag.implicit_power
        || root == RootTag.implicit_rotate;
  }

  public boolean isBackEdge(WTGEdge edge) {
    return edge.getRootTag() == RootTag.implicit_back;
  }

  public static synchronized WTGHelper v() {
    if (helper == null) {
      helper = new WTGHelper();
    }
    return helper;
  }

  private static WTGHelper helper;
}
