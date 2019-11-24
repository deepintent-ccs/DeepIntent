/*
 * ListenerRegistration.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.listener;

import java.util.Set;

import soot.SootClass;
import soot.SootMethod;

import com.google.common.collect.Sets;

public class ListenerRegistration {
  public EventType eventType;
  public String subsig;
  public int position;
  public SootClass listenerClass;

  public Set<EventHandler> handlers;

  public ListenerRegistration(EventType eventType, String subsig, int position,
      SootClass listenerClass) {
    this.eventType = eventType;
    this.subsig = subsig;
    this.position = position;
    this.listenerClass = listenerClass;
    this.handlers = Sets.newHashSet();
  }

  public void addHandler(EventHandler handler) {
    handlers.add(handler);
  }

  public Set<SootMethod> getHandlerPrototypes() {
    Set<SootMethod> result = Sets.newHashSetWithExpectedSize(handlers.size());
    for (EventHandler h : handlers) {
      String subsig = h.subsig;
      result.add(listenerClass.getMethod(subsig));
    }
    return result;
  }
}
