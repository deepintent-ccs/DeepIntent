/*
 * GUIHierarchy.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.rep;

import java.util.ArrayList;

import com.google.common.collect.Lists;

public class GUIHierarchy {
  // Data
  public String app;
  public ArrayList<Activity> activities = Lists.newArrayList();
  public ArrayList<Dialog> dialogs = Lists.newArrayList();

  public static abstract class ViewContainer {
    public ArrayList<View> views = Lists.newArrayList();

    public void addChild(View v) {
      views.add(v);
    }
  }

  public static class EventAndHandler {
    public String event;
    public String handler;
  }

  public static class View extends ViewContainer {
    public String type;
    public int id;
    public String idName;
    public String title;
    public ArrayList<EventAndHandler> eventAndHandlers = Lists.newArrayList();

    public void addEventAndHandlerPair(EventAndHandler eventAndHandler) {
      eventAndHandlers.add(eventAndHandler);
    }
  }

  public static class Window extends ViewContainer {
    public String name;
  }

  public static class Activity extends Window {

  }

  public static class Dialog extends Window {
    public int allocLineNumber;
    public String allocStmt;
    public String allocMethod;
  }
}
