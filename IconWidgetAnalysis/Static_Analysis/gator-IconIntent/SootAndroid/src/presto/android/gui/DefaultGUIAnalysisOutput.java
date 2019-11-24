/*
 * DefaultGUIAnalysisOutput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import presto.android.Hierarchy;
import presto.android.MethodNames;
import presto.android.MultiMapUtil;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.graph.NSetImageResourceOpNode;
import presto.android.gui.graph.NSetListenerOpNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.graph.NWindowNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.listener.ListenerSpecification;
import presto.android.xml.XMLParser;
import soot.Local;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class DefaultGUIAnalysisOutput implements GUIAnalysisOutput {
	// Objects that may store analysis results or help with retrieval of result.
	GUIAnalysis analysis;
	Flowgraph flowgraph;
	FixpointSolver solver;
	GraphUtil graphUtil;
	JimpleUtil jimpleUtil;
	Hierarchy hier;
	ListenerSpecification listenerSpec;

	// Some simple filters to help with easy implementation of variants of query
	// API methods.
	class EqualityEventTypeFilter implements Predicate<EventType> {
		EventType input;

		EqualityEventTypeFilter(EventType input) {
			this.input = input;
		}

		@Override
		public boolean apply(EventType type) {
			return type.equals(input);
		}
	}

	Predicate<EventType> alwaysTrueEventTypeFilter = Predicates.alwaysTrue();

	Predicate<EventType> isImplicitEventTypeFilter = new Predicate<EventType>() {
		@Override
		public boolean apply(EventType type) {
			return type.isImplicit();
		}
	};

	Predicate<EventType> isExplicitEventTypeFilter = Predicates.not(isImplicitEventTypeFilter);

	public DefaultGUIAnalysisOutput(GUIAnalysis analysis) {
		this.analysis = analysis;
		this.flowgraph = analysis.flowgraph;
		this.solver = analysis.fixpointSolver;
		this.graphUtil = GraphUtil.v();
		this.jimpleUtil = JimpleUtil.v();
		this.hier = Hierarchy.v();
		this.listenerSpec = ListenerSpecification.v();
	}

	@Override
	public Set<NDialogNode> getDialogs() {
		Set<NDialogNode> dialogs = Sets.newHashSet();
		for (NWindowNode window : NWindowNode.windowNodes) {
			if (window instanceof NDialogNode) {
				dialogs.add((NDialogNode) window);
			}
		}
		return dialogs;
	}

	@Override
	public Set<NNode> getDialogRoots(NDialogNode dialog) {
		Set<NNode> roots = solver.dialogRoots.get(dialog);
		if (roots == null) {
			roots = Collections.emptySet();
		}
		return roots;
	}

	/*
	 * Given a dialog, what are the triggering statements. For example,
	 * dialog.show(), builder.show(), activity.showDialog(...).
	 */
	@Override
	public Set<Stmt> getDialogShows(NDialogNode dialog) {
		Set<Stmt> shows = flowgraph.allDialogAndShows.get(dialog);
		if (shows == null) {
			shows = Collections.emptySet();
		}
		return shows;
	}

	/*
	 * Given a statement, tell if it can trigger dialogs.
	 */
	@Override
	public boolean isDialogShow(Stmt s) {
		for (Set<Stmt> shows : flowgraph.allDialogAndShows.values()) {
			if (shows.contains(s)) {
				return true;
			}
		}
		return false;
	}

	/*
	 * Given a statement, returns the set of dialogs it can trigger. If none, an
	 * empty set is returned.
	 */
	@Override
	public Set<NDialogNode> dialogsShownBy(Stmt s) {
		Set<NDialogNode> dialogs = Sets.newHashSet();
		for (Map.Entry<NDialogNode, Set<Stmt>> entry : flowgraph.allDialogAndShows.entrySet()) {
			NDialogNode dialog = entry.getKey();
			Set<Stmt> shows = entry.getValue();
			if (shows.contains(s)) {
				dialogs.add(dialog);
			}
		}
		return dialogs;
	}

	/*
	 * dialog.dimiss(), dialog.cancel(), activity.dismissDialog(),
	 * activity.removeDialog().
	 */
	@Override
	public Set<Stmt> getDialogDimisses(NDialogNode dialog) {
		Set<Stmt> dismisses = flowgraph.allDialogAndDismisses.get(dialog);
		if (dismisses == null) {
			dismisses = Collections.emptySet();
		}
		return dismisses;
	}

	@Override
	public boolean isDialogDismiss(Stmt s) {
		for (Set<Stmt> dismisses : flowgraph.allDialogAndDismisses.values()) {
			if (dismisses.contains(s)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Set<NDialogNode> dialogsDismissedBy(Stmt s) {
		Set<NDialogNode> dialogs = Sets.newHashSet();
		for (Map.Entry<NDialogNode, Set<Stmt>> entry : flowgraph.allDialogAndDismisses.entrySet()) {
			NDialogNode dialog = entry.getKey();
			Set<Stmt> dismisses = entry.getValue();
			if (dismisses.contains(s)) {
				dialogs.add(dialog);
			}
		}
		return dialogs;
	}

	@Override
	public Set<SootMethod> getDialogLifecycleHandlers(NDialogNode dialog) {
		Set<SootMethod> methods = flowgraph.allDialogLifecycleMethods.get(dialog);
		if (methods == null) {
			methods = Collections.emptySet();
		}
		return methods;
	}

	@Override
	public Set<SootMethod> getOtherEventHandlersForDialog(NDialogNode dialog) {
		Set<SootMethod> methods = flowgraph.allDialogNonLifecycleMethods.get(dialog);
		if (methods == null) {
			methods = Collections.emptySet();
		}
		return methods;
	}

	// Main activity
	@Override
	public SootClass getMainActivity() {
		return XMLParser.Factory.getXMLParser().getMainActivity();
	}

	@Override
	public Set<SootClass> getActivities() {
		return flowgraph.allNActivityNodes.keySet();
	}

	// Root views for a specified activity. Once the root view is obtained, you
	// can traverse the structure by following the parents and children
	// pointers,
	// or use GraphUtil class to find all descendant nodes of the root.
	@Override
	public Set<NNode> getActivityRoots(SootClass activity) {
		NActivityNode activityNode = flowgraph.allNActivityNodes.get(activity);
		return MultiMapUtil.getNonNullHashSetByKey(solver.activityRoots, activityNode);
	}

	// Next, APIs for retrieving framework-registered event handlers for a
	// specified activity

	List<String> menuHandlerSubsigs = Lists.newArrayList(MethodNames.onContextItemSelectedSubSig,
			MethodNames.onOptionsItemSelectedSubSig, MethodNames.onMenuItemSelectedSubSig);

	@Override
	public Set<SootMethod> getMenuHandlers(SootClass activity) {
		return getActivityHandlers(activity, menuHandlerSubsigs);
	}

	List<String> menuCreationHandlerSubSigs = Lists.newArrayList(MethodNames.onCreateOptionsMenuSubsig,
			MethodNames.onPrepareOptionsMenuSubsig);

	@Override
	public Set<SootMethod> getMenuCreationHandlers(SootClass activity) {
		return getActivityHandlers(activity, menuCreationHandlerSubSigs);
	}

	List<String> activityLifecycleHandlerSubsigs = Lists.newArrayList(MethodNames.onActivityCreateSubSig,
			MethodNames.onActivityDestroySubSig, MethodNames.onActivityPauseSubSig, MethodNames.onActivityRestartSubSig,
			MethodNames.onActivityResumeSubSig, MethodNames.onActivityStartSubSig, MethodNames.onActivityStopSubSig,
			MethodNames.activityOnNewIntentSubSig);

	List<String> optionsMenuLifecycleHandlerSubsigs = Lists.newArrayList(MethodNames.onCreateOptionsMenuSubsig,
			MethodNames.onPrepareOptionsMenuSubsig, MethodNames.onCloseOptionsMenuSubsig);

	List<String> contextMenuLifecycleHandlerSubsigs = Lists.newArrayList(MethodNames.onCreateContextMenuSubSig,
			MethodNames.onCloseContextMenuSubsig);

	List<String> dialogLifecycleMethodSubSigs = Lists.newArrayList(MethodNames.onDialogCreateSubSig,
			MethodNames.onDialogStartSubSig, MethodNames.onDialogStopSubSig);

	@Override
	public Set<SootMethod> getLifecycleHandlers(SootClass activity) {
		return getActivityHandlers(activity, activityLifecycleHandlerSubsigs);
	}

	List<String> dialogCreationHandlerSubsigs = Lists.newArrayList(MethodNames.activityOnCreateDialogSubSig,
			MethodNames.activityOnCreateDialogBundleSubSig, MethodNames.activityOnPrepareDialogSubSig,
			MethodNames.activityOnPrepareDialogBundleSubSig);

	@Override
	public Set<SootMethod> getDialogCreationHandlers(SootClass activity) {
		return getActivityHandlers(activity, dialogCreationHandlerSubsigs);
	}

	@Override
	public Set<SootMethod> getActivityHandlers(SootClass activity, List<String> subsigs) {
		Set<SootMethod> result = Sets.newHashSet();
		for (String subsig : subsigs) {
			SootClass onClass = hier.matchForVirtualDispatch(subsig, activity);
			if (onClass != null && onClass.isApplicationClass()) {
				result.add(onClass.getMethod(subsig));
			}
		}

		return result;
	}

	// For a specified event handler, return the local variable corresponding to
	// the target GUI object.
	@Override
	public Local getViewLocal(SootMethod handler) {
		String handlerSubsig = handler.getSubSignature();
		int viewPosition = listenerSpec.getViewPositionInHandler(handlerSubsig);
		if (viewPosition == -1) {
			return null;
		} else {
			// viewPosition is position in parameter list. For index of local,
			// we
			// need to add 1 onto it.
			return jimpleUtil.localForNthParameter(handler, viewPosition + 1);
		}
	}

	// For a specified event handler, return the local variable corresponding to
	// the listener object.
	@Override
	public Local getListenerLocal(SootMethod handler) {
		return jimpleUtil.thisLocal(handler);
	}

	// For a specified GUI object, return all its supported events and
	// corresponding event handlers.
	@Override
	public Map<EventType, Set<SootMethod>> getAllEventsAndTheirHandlers(NObjectNode guiObject) {
		return getSupportedEventsAndTheirHandlers(guiObject, alwaysTrueEventTypeFilter);
	}

	// For a specified GUI object, return all its explicit events and
	// corresponding event handlers.
	@Override
	public Map<EventType, Set<SootMethod>> getExplicitEventsAndTheirHandlers(NObjectNode guiObject) {
		return getSupportedEventsAndTheirHandlers(guiObject, isExplicitEventTypeFilter);
	}

	// For a specified GUI object, return all its implicit events and
	// corresponding event handlers.
	public Map<EventType, Set<SootMethod>> getImplicitEventsAndTheirHandlers(NObjectNode guiObject) {
		return getSupportedEventsAndTheirHandlers(guiObject, isImplicitEventTypeFilter);
	}

	// For a specified GUI object, based on a specified condition, return the
	// satisfying events that can be triggered on it and the corresponding event
	// handlers.
	public Map<EventType, Set<SootMethod>> getSupportedEventsAndTheirHandlers(NObjectNode guiObject,
			Predicate<EventType> condition) {
		Map<EventType, Set<SootMethod>> result = Maps.newHashMap();
		Set<Stmt> regs = getCallbackRegistrations(guiObject, condition);
		for (Stmt s : regs) {
			EventType eventType = listenerSpec.lookupEventType(s);
			Set<SootMethod> handlers = result.get(eventType);
			if (handlers == null) {
				handlers = Sets.newHashSet();
				result.put(eventType, handlers);
			}
			// Set<SootMethod> handlersToAdd =
			// flowgraph.regToEventHandlers.get(s);
			Collection<SootMethod> handlersToAdd = flowgraph.regToEventHandlers.get(s);
			if (handlersToAdd == null || handlersToAdd.isEmpty()) {
				continue;
			}
			handlers.addAll(handlersToAdd);
		}
		return result;
	}

	@Override
	public Set<NIdNode> getImageResourceId(NObjectNode guiObject) {
		Set<NIdNode> result = Sets.newHashSet();
		for (NOpNode opNode : NOpNode.getNodes(NSetImageResourceOpNode.class)) {
			NSetImageResourceOpNode setImageResourceOp = (NSetImageResourceOpNode) opNode;
			Set<NNode> receiverSet = solver.solutionReceivers.get(setImageResourceOp);
			if (receiverSet != null && receiverSet.contains(guiObject)) {

				if (setImageResourceOp.getParameter() instanceof NIdNode) {
					result.add((NIdNode) setImageResourceOp.getParameter());
				} else {
//					System.out.println();
//					cannot handle string variables
				}
			}
		}

		return result;
	}

	// For a specified GUI object, return the set of all supported events.
	@Override
	public Set<EventType> getAllSupportedEvents(NObjectNode guiObject) {
		return getSupportedEvents(guiObject, alwaysTrueEventTypeFilter);
	}

	// For a specified GUI object, return the set of all implicit events.
	public Set<EventType> getSupportedImplicitEvents(NObjectNode guiObject) {
		return getSupportedEvents(guiObject, isImplicitEventTypeFilter);
	}

	// For a specified GUI object, return the set of all explicit events.
	public Set<EventType> getSupportedExplicitEvents(NObjectNode guiObject) {
		return getSupportedEvents(guiObject, isExplicitEventTypeFilter);
	}

	// For a specified GUI object, return the set of supported events that
	// satisfy a specified condition.
	public Set<EventType> getSupportedEvents(NObjectNode guiObject, Predicate<EventType> condition) {
		Set<Stmt> regs = getCallbackRegistrations(guiObject, condition);
		Set<EventType> result = Sets.newHashSet();
		for (Stmt s : regs) {
			result.add(listenerSpec.lookupEventType(s));
		}
		return result;
	}

	// For a specified GUI object, return the set of all its corresponding
	// callback registration statements satisfying a specified condition.
	public Set<Stmt> getCallbackRegistrations(NObjectNode guiObject, Predicate<EventType> condition) {
		Set<Stmt> result = Sets.newHashSet();
		for (NOpNode opNode : NOpNode.getNodes(NSetListenerOpNode.class)) {
			NSetListenerOpNode setListener = (NSetListenerOpNode) opNode;
			Set<NNode> receiverSet = solver.solutionReceivers.get(setListener);
			if (receiverSet != null && receiverSet.contains(guiObject)) {
				Stmt regStmt = setListener.callSite.getO1();
				EventType eventType = listenerSpec.lookupEventType(regStmt);
				if (condition.apply(eventType)) {
					result.add(regStmt);
				}
			}
		}

		return result;
	}

	// For a specified GUI object, return the set of all its corresponding
	// callback registration statements.
	@Override
	public Set<Stmt> getCallbackRegistrations(NObjectNode guiObject) {
		return getCallbackRegistrations(guiObject, alwaysTrueEventTypeFilter);
	}

	// For a specified GUI object, return the set of all its corresponding
	// callback registration statements relevant to a specified event type.
	@Override
	public Set<Stmt> getCallbackRegistrations(NObjectNode guiObject, EventType eventType) {
		return getCallbackRegistrations(guiObject, new EqualityEventTypeFilter(eventType));
	}

	// For a specified GUI object, return the set of all its corresponding
	// event handler methods relevant to a specified event type.
	@Override
	public Set<SootMethod> getEventHandlers(NObjectNode guiObject, EventType eventType) {
		return getEventHandlers(guiObject, new EqualityEventTypeFilter(eventType));
	}

	// For a specified GUI object, return the set of all its corresponding
	// event handler methods satisfying a specified condition.
	public Set<SootMethod> getEventHandlers(NObjectNode guiObject, Predicate<EventType> condition) {
		Set<Stmt> registrations = getCallbackRegistrations(guiObject);
		Set<SootMethod> eventHandlers = Sets.newHashSet();
		for (Stmt reg : registrations) {
			EventType eventType = listenerSpec.lookupEventType(reg);
			if (condition.apply(eventType)) {
				eventHandlers.addAll(flowgraph.regToEventHandlers.get(reg));
			}
		}
		return eventHandlers;
	}

	// Return a VariableValueQueryInterface object which provides the ability to
	// query for possible values of variables of some type (e.g., activities,
	// views, IDs).
	@Override
	public VariableValueQueryInterface getVariableValueQueryInterface() {
		return analysis.variableValueQueryInterface;
	}

	// Return the instance of Flowgraph associated with this run of the analysis
	// algorithm. The returned instance is mutable.
	//
	// WARNING: changes to the state of the Flowgraph object may invalidate the
	// result of GUI analysis. Use with caution.
	@Override
	public Flowgraph getFlowgraph() {
		return flowgraph;
	}

	@Override
	public FixpointSolver getSolver() {
		return solver;
	}

	// Return the NOptionsMenuNode associated with the specified activity
	@Override
	public NOptionsMenuNode getOptionsMenu(SootClass activity) {
		return flowgraph.activityClassToOptionsMenu.get(activity);
	}

	// For a specified GUI object, return the set of associated NContextMenuNode
	// nodes.
	@Override
	public void getContextMenus(NObjectNode view, Set<NContextMenuNode> result) {
		VariableValueQueryInterface variableValues = getVariableValueQueryInterface();
		for (NContextMenuNode contextMenu : flowgraph.menuVarNodeToContextMenus.values()) {
			for (NVarNode viewVarNode : contextMenu.varNodesForRegisteredViews) {
				if (variableValues.guiVariableValues(viewVarNode.l).contains(view)) {
					result.add(contextMenu);
				}
			}
		}
	}

	@Override
	public Set<NContextMenuNode> getContextMenus(NObjectNode view) {
		Set<NContextMenuNode> result = Sets.newHashSet();
		getContextMenus(view, result);
		return result;
	}

	// Given a context menu node, returns the onCreateContextMenu method that
	// "allocates" this menu object.
	@Override
	public SootMethod getOnCreateContextMenuMethod(NContextMenuNode contextMenu) {
		return flowgraph.contextMenuToOnCreateContextMenus.get(contextMenu);
	}

	/*
	 * Given a statement, return a set of menus that can be triggered by this
	 * statement. Typically examples: view.showContextMenu(),
	 * activity.openContextMenu(view), activity.openOptionsMenu().
	 */
	@Override
	public Set<NContextMenuNode> explicitlyTriggeredContextMenus(Stmt s) {
		Local view = flowgraph.explicitShowContextMenuCallAndViewLocals.get(s);
		if (view == null) {
			Collections.emptySet();
		}
		VariableValueQueryInterface variableValues = getVariableValueQueryInterface();
		Set<NObjectNode> objects = variableValues.guiVariableValues(view);
		Set<NContextMenuNode> contextMenus = Sets.newHashSet();
		for (NObjectNode viewObject : objects) {
			getContextMenus(viewObject, contextMenus);
		}
		if (contextMenus.isEmpty()) {
			System.out.println("[WARNING] Cannot find context menu for " + s + " @ " + jimpleUtil.lookup(s));
		}
		return contextMenus;
	}

	@Override
	public Set<NOptionsMenuNode> explicitlyTriggeredOptionsMenus(Stmt s) {
		Local activity = flowgraph.explicitShowOptionsMenuCallAndActivityLocals.get(s);
		if (activity == null) {
			return Collections.emptySet();
		}
		VariableValueQueryInterface variableValues = getVariableValueQueryInterface();
		Set<NOptionsMenuNode> result = Sets.newHashSet();
		for (NObjectNode activityObject : variableValues.activityVariableValues(activity)) {
			result.add(getOptionsMenu(activityObject.getClassType()));
		}
		if (result.isEmpty()) {
			System.out.println("[WARNING] Cannot find options menu for " + s + " @ " + jimpleUtil.lookup(s));
		}
		return result;
	}

	@Override
	public boolean isExplicitShowContextMenuCall(Stmt s) {
		return flowgraph.explicitShowContextMenuCallAndViewLocals.containsKey(s);
	}

	@Override
	public boolean isExplicitShowOptionsMenuCall(Stmt s) {
		return flowgraph.explicitShowOptionsMenuCallAndActivityLocals.containsKey(s);
	}

	// For a specified statement, return the corresponding NOpNode if it does
	// represent such a node. If not, null will be returned.
	public NOpNode operationNode(Stmt s) {
		return NOpNode.lookupByStmt(s);
	}

	// For a specified Jimple statement, determine whether it represents a
	// callback registration.
	@Override
	public boolean isCallbackRegistration(Stmt s) {
		NOpNode node = NOpNode.lookupByStmt(s);
		return node instanceof NSetListenerOpNode;
	}

	// Return a set of operation nodes in the specified type. For example, a
	// call
	// "operationNodes(NFindView1OpNode.class)" returns all the FindView1
	// operation nodes.
	@Override
	public Set<NOpNode> operationNodes(Class<? extends NOpNode> klass) {
		return NOpNode.getNodes(klass);
	}

	// Return the set of all operation nodes.
	public Set<NOpNode> operationNodes() {
		Set<NOpNode> result = Sets.newHashSet();
		for (NNode n : flowgraph.allNNodes) {
			if (n instanceof NOpNode) {
				result.add((NOpNode) n);
			}
		}
		return result;
	}

	// Given an operation node, return the set of possible values for its
	// receiver variable.
	public Set<NNode> receiverSolutionSet(NOpNode opNode) {
		Set<NNode> result = solver.solutionReceivers.get(opNode);
		if (result == null) {
			result = Collections.emptySet();
		}
		return result;
	}

	@Override
	public Map<NOpNode, Set<NNode>> operationNodeAndReceivers() {
		return solver.solutionReceivers;
	}

	public Set<NNode> parameterSolutionSet(NOpNode opNode) {
		Set<NNode> result = solver.solutionParameters.get(opNode);
		if (result == null) {
			result = Collections.emptySet();
		}
		return result;
	}

	@Override
	public Map<NOpNode, Set<NNode>> operationNodeAndParameters() {
		return solver.solutionParameters;
	}

	public Set<NNode> resultSolutionSet(NOpNode opNode) {
		Set<NNode> result = solver.solutionResults.get(opNode);
		if (result == null) {
			result = Collections.emptySet();
		}
		return result;
	}

	// Map from operation nodes that have lhs to values the lhs can contain
	@Override
	public Map<NOpNode, Set<NNode>> operationNodeAndResults() {
		return solver.solutionResults;
	}

	public Set<NNode> listenerSolutionSet(NOpNode opNode) {
		Set<NNode> result = solver.solutionListeners.get(opNode);
		if (result == null) {
			result = Collections.emptySet();
		}
		return result;
	}

	@Override
	public Map<NOpNode, Set<NNode>> operationNodeAndListeners() {
		return solver.solutionListeners;
	}

	/*
	 * Given an artificial handler, returns the real handler.
	 */
	@Override
	public SootMethod getRealHandler(SootMethod fakeHandler) {
		return flowgraph.fakeHandlerToRealHandler.get(fakeHandler);
	}

	// GUI analysis running time in nano seconds
	private long runningTimeInNanoSeconds;

	@Override
	public long getRunningTimeInNanoSeconds() {
		return runningTimeInNanoSeconds;
	}

	@Override
	public void setRunningTimeInNanoSeconds(long runningTimeInNanoSeconds) {
		this.runningTimeInNanoSeconds = runningTimeInNanoSeconds;
	}

	@Override
	public String getAppPackageName() {
		return flowgraph.xmlUtil.getAppPackageName();
	}

	@Override
	public boolean isLifecycleHandler(SootMethod handler) {
		String thisSubsig = handler.getSubSignature();
		for (String subsig : this.activityLifecycleHandlerSubsigs) {
			if (thisSubsig.equals(subsig)) {
				return true;
			}
		}
		for (String subsig : this.dialogLifecycleMethodSubSigs) {
			if (thisSubsig.equals(subsig)) {
				return true;
			}
		}
		for (String subsig : this.optionsMenuLifecycleHandlerSubsigs) {
			if (thisSubsig.equals(subsig)) {
				return true;
			}
		}
		for (String subsig : this.contextMenuLifecycleHandlerSubsigs) {
			if (thisSubsig.equals(subsig)) {
				return true;
			}
		}
		return false;
	}

}
