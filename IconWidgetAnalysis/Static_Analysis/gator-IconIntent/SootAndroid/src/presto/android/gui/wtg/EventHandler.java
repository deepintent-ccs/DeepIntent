/*
 * EventHandler.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg;

import presto.android.gui.graph.NObjectNode;
import presto.android.gui.listener.EventType;
import soot.SootMethod;

public class EventHandler {
  private NObjectNode window;
  // specify the gui widget event is performed on
  // for implicit lifecycle handlers, related widget information is set to be either activity, menu or dialog
  private NObjectNode widget;
  private SootMethod handler;
  private EventType event;
  private EventHandlerSig sig;

  public EventHandler(NObjectNode window, NObjectNode guiWidget, EventType evt, SootMethod handler) {
    this.window = window;
    this.event = evt;
    this.widget = guiWidget;
    this.handler = handler;
    this.sig = new EventHandlerSig();
  }

  public SootMethod getEventHandler() {
    return handler;
  }

  public NObjectNode getWidget() {
    // specify the gui widget event is performed on
    // for implicit lifecycle handlers, related widget
    // information is set to be either activity, menu or dialog
    return this.widget;
  }

  public EventType getEvent() {
    return this.event;
  }

  public EventHandlerSig getSig() {
    return this.sig;
  }

  public NObjectNode getWindow() {
    return this.window;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("<");
    sb.append(window);
    sb.append(",");
    sb.append(widget);
    sb.append(",");
    sb.append(event);
    sb.append(",");
    sb.append(handler);
    sb.append(">");
    return sb.toString();
  }

  public class EventHandlerSig {
    public EventHandlerSig() {

    }

    public NObjectNode getWindow() {
      return window;
    }

    public NObjectNode getGUIWidget() {
      return widget;
    }

    public SootMethod getEventHandler() {
      return handler;
    }

    public EventType getEventType() {
      return event;
    }

    @Override
    public int hashCode() {
      return window.hashCode() + widget.hashCode() + event.hashCode()
          + (handler == null ? 0 : handler.hashCode());
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof EventHandlerSig)) {
        return false;
      }
      EventHandlerSig another = (EventHandlerSig) o;
      return window == another.getWindow()
          && widget == another.getGUIWidget()
          && event == another.getEventType()
          && handler == another.getEventHandler();
    }

  }
}
