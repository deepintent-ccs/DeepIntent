/*
 * WTGEdge.java - part of the GATOR project
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import presto.android.Logger;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.RootTag;
import presto.android.gui.wtg.StackOperation;
import soot.SootMethod;

public class WTGEdge {
  private final WTGNode srcNode;
  private final WTGNode tgtNode;
  private final Set<EventHandler> handlers;
  // show the reason why this edge is created
  // note: an edge may open/close several windows
  // field "root" only provides the most recent reason
  private final RootTag root;
  private final WTGEdgeSig sig;
  // callback sequence
  private final List<EventHandler> callbacks;
  // window stack operations
  private final List<StackOperation> stackOps;

  public WTGEdge(
      final WTGNode srcNode,
      final WTGNode tgtNode,
      final Set<EventHandler> handlers,
      final RootTag root,
      final List<StackOperation> stackOps,
      final List<EventHandler> callbacks) {
    Preconditions.checkNotNull(srcNode);
    Preconditions.checkNotNull(tgtNode);
    Preconditions.checkNotNull(root);
    Preconditions.checkNotNull(callbacks);
    Preconditions.checkNotNull(stackOps);
    Preconditions.checkArgument(!callbacks.contains(null));
    Preconditions.checkArgument(!handlers.isEmpty());
    Preconditions.checkArgument(!handlers.contains(null));
    NObjectNode window = null, widget = null;
    EventType evt = null;
    for (EventHandler handler : handlers) {
      if (window == null && widget == null && evt == null) {
        window = handler.getWindow();
        widget = handler.getWidget();
        evt = handler.getEvent();
      } else if (window != handler.getWindow() || widget != handler.getWidget()
          || evt != handler.getEvent()) {
        Logger.err(getClass().getSimpleName(), "unmatched event handler");
      }
    }
    this.srcNode = srcNode;
    this.tgtNode = tgtNode;
    this.handlers = Sets.newHashSet(handlers);
    this.root = root;
    this.stackOps = Lists.newArrayList(stackOps);
    this.callbacks = Lists.newArrayList(callbacks);
    this.sig = new WTGEdgeSig();
  }

  public List<EventHandler> getCallbacks() {
    return Lists.newArrayList(this.callbacks);
  }

  public List<StackOperation> getStackOps() {
    return Lists.newArrayList(this.stackOps);
  }

  public WTGEdgeSig getSig() {
    return this.sig;
  }

  public RootTag getRootTag() {
    return this.root;
  }

  public WTGNode getSourceNode() {
    return this.srcNode;
  }

  public WTGNode getTargetNode() {
    return this.tgtNode;
  }

  public NObjectNode getGUIWidget() {
    return this.handlers.iterator().next().getWidget();
  }

  public EventType getEventType() {
    return this.handlers.iterator().next().getEvent();
  }

  public Set<EventHandler> getWTGHandlers() {
    // the order is not guaranteed
    return Sets.newHashSet(this.handlers);
  }

  public Set<SootMethod> getEventHandlers() {
    Set<SootMethod> eventHandlers = Sets.newHashSet();
    for (EventHandler handler : this.handlers) {
      SootMethod eventHandler = handler.getEventHandler();
      if (eventHandler != null) {
        eventHandlers.add(eventHandler);
      }
    }
    return eventHandlers;
  }

  public NObjectNode getFinalTarget() {
    if (stackOps.isEmpty()) {
      return null;
    }
    Map<NObjectNode, Integer> popWindows = Maps.newHashMap();
    for (int i = stackOps.size()-1; i >= 0; i--) {
      StackOperation op = stackOps.get(i);
      NObjectNode window = op.getWindow();
      if (op.isPushOp()) {
        if (!popWindows.containsKey(window)) {
          return window;
        } else {
          Integer freq = popWindows.get(window);
          if (--freq == 0) {
            popWindows.remove(window);
          } else {
            popWindows.put(window, freq);
          }
        }
      } else {
        Integer freq = popWindows.get(window);
        if (freq == null) {
          freq = 0;
        }
        popWindows.put(window, ++freq);
      }
    }
    NObjectNode sourceWindow = srcNode.getWindow();
    if (!popWindows.containsKey(sourceWindow)) {
      return sourceWindow;
    } else {
      return null;
    }
  }

  public Map<NObjectNode, Integer> getInterimTarget() {
    Map<NObjectNode, Integer> pushWindows = Maps.newHashMap();
    if (stackOps.isEmpty()) {
      return pushWindows;
    }
    for (int i = 0; i < stackOps.size(); i++) {
      StackOperation op = stackOps.get(i);
      NObjectNode window = op.getWindow();
      if (op.isPushOp()) {
        pushWindows.put(window, i);
      }
    }
    for (int i = 0; i < stackOps.size(); i++) {
      StackOperation op = stackOps.get(i);
      NObjectNode window = op.getWindow();
      if (!op.isPushOp()) {
        pushWindows.remove(window);
      }
    }    
    pushWindows.remove(srcNode.getWindow());
    pushWindows.remove(tgtNode.getWindow());
    return pushWindows;
  }

  public NObjectNode getPopSelf() {
    if (stackOps.isEmpty()) {
      return null;
    }
    StackOperation op = stackOps.get(0);
    if (!op.isPushOp()) {
      return op.getWindow();
    }
    return null;
  }

  public NObjectNode getPopOwner() {
    if (stackOps.size() < 2) {
      return null;
    }
    StackOperation op1 = stackOps.get(0);
    StackOperation op2 = stackOps.get(1);
    if (!op2.isPushOp()) {
      if (op1.isPushOp()) {
        Logger.err(getClass().getSimpleName(), "unexpected case");
      }
      return op2.getWindow();
    }
    return null;
  }

  public List<NObjectNode> getPushWindows() {
    List<NObjectNode> pushWindows = Lists.newArrayList();
    for (StackOperation stackOp : getStackOps()) {
      if (stackOp.isPushOp()) {
        pushWindows.add(stackOp.getWindow());
      }
    }
    return pushWindows;
  }

  public List<NObjectNode> getPopWindows() {
    List<NObjectNode> popWindows = Lists.newArrayList();
    for (StackOperation stackOp : getStackOps()) {
      if (!stackOp.isPushOp()) {
        popWindows.add(stackOp.getWindow());
      }
    }
    return popWindows;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("src: ");
    sb.append(srcNode);
    sb.append("\\n");
    sb.append("tgt: ");
    sb.append(tgtNode);
    sb.append("\\n");
    sb.append("tag: ");
    sb.append(root.name());
    sb.append("\\n");
    sb.append("evt: ");
    sb.append(this.getEventType().name());
    sb.append("\\n");
    sb.append("widget: ");
    sb.append(this.getGUIWidget());
    sb.append("\\n");
    sb.append("handler: ");
    sb.append(this.getEventHandlers());
    sb.append("\\n");
    sb.append("stack: ");
    sb.append(stackOps);
    return sb.toString();
  }

  /**
   * What can be used to identically reflect a wtg edge?
   * */
  public class WTGEdgeSig {
    public WTGEdgeSig() {
    }

    public WTGNode getSourceNode() {
      return srcNode;
    }

    public WTGNode getTargetNode() {
      return tgtNode;
    }

    public Set<EventHandler> getWTGHandlers() {
      return handlers;
    }

    public RootTag getRootTag() {
      return root;
    }

    public List<EventHandler> getCallbacks() {
      return Lists.newArrayList(callbacks);
    }

    public List<StackOperation> getStackOps() {
      return Lists.newArrayList(stackOps);
    }

    public WTGEdge getEdge() {
      return WTGEdge.this;
    }

    @Override
    public int hashCode() {
      return srcNode.hashCode() + tgtNode.hashCode() + root.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof WTGEdgeSig)) {
        return false;
      }
      WTGEdgeSig another = (WTGEdgeSig) o;
      return srcNode == another.getSourceNode()
          && tgtNode == another.getTargetNode()
          && root == another.getRootTag()
          && handlers.equals(another.getWTGHandlers())
          && stackOps.equals(another.getStackOps());
    }

    @Override
    public String toString() {
      return getEdge().toString();
    }
  }
}
