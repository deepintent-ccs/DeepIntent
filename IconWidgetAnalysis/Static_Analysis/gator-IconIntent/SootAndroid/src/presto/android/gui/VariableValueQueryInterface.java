/*
 * VariableValueQueryInterface.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.util.Set;

import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NObjectNode;
import soot.Local;

// For a given GUI-related variable (GUI object, ID, activity, etc), return the
// set of values this variable may reference.
public interface VariableValueQueryInterface {
  public Set<NIdNode> idVariableValues(Local local);

  public Set<NObjectNode> activityVariableValues(Local local);

  public Set<NObjectNode> guiVariableValues(Local local);

  public Set<NObjectNode> listenerVariableValues(Local local);
}
