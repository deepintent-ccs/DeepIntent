/*
 * HandlerBean.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.ds;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import presto.android.gui.graph.NObjectNode;
import presto.android.gui.listener.EventType;
import soot.SootMethod;

public class HandlerBean {
  public HandlerBean(NObjectNode window, NObjectNode widget, EventType evt,
      Set<SootMethod> handlers) {
    Preconditions.checkNotNull(handlers,
        "[Error]: actual handler for HandlerBean shouldn't be null");
    this.window = window;
    this.guiWidget = widget;
    this.event = evt;
    this.handlers = Sets.newHashSet(handlers);
  }

  /*
   * public NObjectNode getWindow() { return this.window; }
   */
  public NObjectNode getGUIWidget() {
    return this.guiWidget;
  }

  public EventType getEvent() {
    return this.event;
  }

  public Set<SootMethod> getHandlers() {
    return Sets.newHashSet(this.handlers);
  }

  public void addHandler(SootMethod handler) {
    this.handlers.add(handler);
  }

  public NObjectNode getWindow() {
    return this.window;
  }

  public int hashCode() {
    return window.hashCode() + guiWidget.hashCode() + event.hashCode();
  }

  public boolean equals(Object o) {
    if (!(o instanceof HandlerBean)) {
      return false;
    }
    HandlerBean another = (HandlerBean) o;
    
    return
        (another.event != EventType.implicit_async_event
          && this.event != EventType.implicit_async_event
          && this.window == another.window
          && this.guiWidget == another.guiWidget
          && this.event == another.event)
        ||
        (another.event == EventType.implicit_async_event
          && this.event == EventType.implicit_async_event
          && another.window == this.window
          && another.guiWidget == this.guiWidget
          && another.handlers.size() == 1
          && this.handlers.size() == 1
          && another.handlers.equals(this.handlers));
  }

  private NObjectNode window;
  private NObjectNode guiWidget;
  private EventType event;
  private Set<SootMethod> handlers;
@Override
public String toString() {
	return "HandlerBean [event=" + event + ", handlers=" + handlers + "]";
}
  
  
}
