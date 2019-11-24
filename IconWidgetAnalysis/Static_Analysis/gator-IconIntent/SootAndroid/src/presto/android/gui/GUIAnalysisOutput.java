/*
 * GUIAnalysisOutput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.graph.NSetImageResourceOpNode;
import presto.android.gui.listener.EventType;
import soot.Local;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;

public interface GUIAnalysisOutput {
  // === Results
  public Flowgraph getFlowgraph();
  public FixpointSolver getSolver();

  public Map<NOpNode, Set<NNode>> operationNodeAndReceivers();
  public Map<NOpNode, Set<NNode>> operationNodeAndParameters();
  public Map<NOpNode, Set<NNode>> operationNodeAndResults();
  public Map<NOpNode, Set<NNode>> operationNodeAndListeners();
  public Set<NOpNode> operationNodes(Class<? extends NOpNode> klass);

  public VariableValueQueryInterface getVariableValueQueryInterface();

  // === Activities
  public Set<SootClass> getActivities();
  public SootClass getMainActivity();
  public Set<NNode> getActivityRoots(SootClass activity);
  public Set<SootMethod> getLifecycleHandlers(SootClass activity);
  public Set<SootMethod> getActivityHandlers(SootClass activity,
      List<String> subsigs);

  // === Menus
  public NOptionsMenuNode getOptionsMenu(SootClass activity);
  public boolean isExplicitShowOptionsMenuCall(Stmt s);
  public Set<NOptionsMenuNode> explicitlyTriggeredOptionsMenus(Stmt s);

  public void getContextMenus(NObjectNode view, Set<NContextMenuNode> result);
  public Set<NContextMenuNode> getContextMenus(NObjectNode view);
  public SootMethod getOnCreateContextMenuMethod(NContextMenuNode contextMenu);
  public boolean isExplicitShowContextMenuCall(Stmt s);
  public Set<NContextMenuNode> explicitlyTriggeredContextMenus(Stmt s);

  public Set<SootMethod> getMenuHandlers(SootClass activity);
  public Set<SootMethod> getMenuCreationHandlers(SootClass activity);

  // === Dialogs
  public Set<NDialogNode> getDialogs();
  public Set<NNode> getDialogRoots(NDialogNode dialog);

  public Set<SootMethod> getDialogCreationHandlers(SootClass activity);

  public boolean isDialogShow(Stmt s);
  public Set<NDialogNode> dialogsShownBy(Stmt s);
  public Set<Stmt> getDialogShows(NDialogNode dialog);

  public boolean isDialogDismiss(Stmt s);
  public Set<NDialogNode> dialogsDismissedBy(Stmt s);
  public Set<Stmt> getDialogDimisses(NDialogNode dialog);

  public Set<SootMethod> getDialogLifecycleHandlers(NDialogNode dialog);
  public Set<SootMethod> getOtherEventHandlersForDialog(NDialogNode dialog);

  // === Views & Handlers
  public Map<EventType, Set<SootMethod>> getAllEventsAndTheirHandlers(NObjectNode guiObject);
  public Map<EventType, Set<SootMethod>> getExplicitEventsAndTheirHandlers(NObjectNode guiObject);
  public Map<EventType, Set<SootMethod>> getImplicitEventsAndTheirHandlers(NObjectNode guiObject);

  public Set<EventType> getAllSupportedEvents(NObjectNode guiObject);
  public Set<Stmt> getCallbackRegistrations(NObjectNode guiObject);
  public Set<Stmt> getCallbackRegistrations(NObjectNode guiObject, EventType eventType);
  public Set<SootMethod> getEventHandlers(NObjectNode guiObject, EventType eventType);
  public boolean isCallbackRegistration(Stmt s);

  public Local getViewLocal(SootMethod handler);
  public Local getListenerLocal(SootMethod handler);

  public SootMethod getRealHandler(SootMethod fakeHandler);

  // === Measurements
  public long getRunningTimeInNanoSeconds();
  public void setRunningTimeInNanoSeconds(long runningTimeInNanoSeconds);
  public String getAppPackageName();

  public boolean isLifecycleHandler(SootMethod handler);
Set<NIdNode> getImageResourceId(NObjectNode guiObject);
}
