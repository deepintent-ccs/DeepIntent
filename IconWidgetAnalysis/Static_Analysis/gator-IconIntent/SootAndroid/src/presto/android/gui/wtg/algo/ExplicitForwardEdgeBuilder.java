/*
 * ExplicitForwardEdgeBuilder.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.algo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;

import presto.android.Configs;
import presto.android.Hierarchy;
import presto.android.Logger;
import presto.android.MethodNames;
import presto.android.Configs.AsyncOpStrategy;
import presto.android.gui.Flowgraph;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.GraphUtil;
import presto.android.gui.JimpleUtil;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NAllocNode;
import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NMenuNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.graph.NWindowNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.RootTag;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.analyzer.CFGAnalyzerInput;
import presto.android.gui.wtg.analyzer.CFGAnalyzerOutput;
import presto.android.gui.wtg.ds.HandlerBean;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGHelper;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;
import presto.android.gui.wtg.flowgraph.NLauncherNode;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph.Edge;
import presto.android.gui.wtg.parallel.CFGScheduler;
import presto.android.gui.wtg.util.WTGUtil;
import presto.android.gui.wtg.util.Filter;
import presto.android.gui.wtg.util.QueryHelper;
import soot.Local;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public class ExplicitForwardEdgeBuilder implements Algorithm {
	private WTGHelper helper = WTGHelper.v();
	private GraphUtil graphUtil = GraphUtil.v();
	private WTGHelper wtgHelper = WTGHelper.v();
	private WTGUtil wtgUtil = WTGUtil.v();
	private Hierarchy hier = Hierarchy.v();
	private JimpleUtil jimpleUtil = JimpleUtil.v();
	private QueryHelper queryHelper = QueryHelper.v();

	private GUIAnalysisOutput guiOutput;

	private FlowgraphRebuilder flowgraphRebuilder;
	private Multimap<NObjectNode, NObjectNode> guiHierarchy;
	private Multimap<NObjectNode, HandlerBean> widgetToHandlers;
	private Multimap<NObjectNode, NIdNode> widgetToImagess;

	public ExplicitForwardEdgeBuilder(GUIAnalysisOutput guiOutput, FlowgraphRebuilder flowgraphRebuilder) {
		this.guiOutput = guiOutput;
		this.flowgraphRebuilder = flowgraphRebuilder;
	}

	public Multimap<NObjectNode, NObjectNode> getGUIHierarchy() {
		return this.guiHierarchy;
	}

	public Multimap<NObjectNode, HandlerBean> getWidgetToHandlers() {
		return this.widgetToHandlers;
	}
	
	public Multimap<NObjectNode, NIdNode> getWidgetToImages() {
		return this.widgetToImagess;
	}

	/**
	 * build gui hierarchy and necessary wtg nodes before creating edges in this
	 * stage, no edges are added to wtg
	 */
	public Multimap<WTGEdgeSig, WTGEdge> buildEdges(WTG wtg) {
		Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
		// for each menu, the list of activities/dialogs that own it
		// for the context menu, the owner is *NOT* the view it attaches to
		Multimap<NMenuNode, NWindowNode> menuToWindowMap = HashMultimap.create();

		// build wtg nodes
		// for each window node, the set of views under it
		Multimap<NObjectNode, NObjectNode> guiHierarchy = HashMultimap.create();

		// for each activity/dialog, the pair of (view, context menu)
		Multimap<NWindowNode, Pair<NObjectNode, NContextMenuNode>> windowToContextMenus = HashMultimap.create();

		// build nodes in wtg
		buildWTGNodes(wtg, guiHierarchy, menuToWindowMap, windowToContextMenus);
		// build edges
		buildWTGEdges(newEdges, wtg, guiHierarchy, menuToWindowMap, windowToContextMenus);
		this.guiHierarchy = guiHierarchy;
		return newEdges;
	}

	@Override
	public AlgorithmOutput execute(AlgorithmInput input) {
		Logger.err(getClass().getSimpleName(), "is not designed to be parallel algorithm");
		return null;
	}

	private void buildExplicitForwardEdges(final Multimap<WTGEdgeSig, WTGEdge> newEdges, final WTG wtg,
			final Multimap<NObjectNode, NObjectNode> guiHierarchy,
			final Multimap<NObjectNode, HandlerBean> viewToHandlers) {
		Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput = analyzeCallback(guiHierarchy, viewToHandlers);
		// this method will build the initial version of wtg edges
		// using the algorithm CREATEEDGES
		for (NObjectNode window : guiHierarchy.keySet()) {
			if (window instanceof NActivityNode) {
				/**********************
				 * if window is activity
				 ****************************/
				buildActivityForwardEdges(newEdges, wtg, window, analyzeOutput, guiHierarchy, viewToHandlers);
			} else if (window instanceof NDialogNode || window instanceof NMenuNode) {
				/**********************
				 * if window is dialog or menu
				 ****************************/
				buildDialogOrMenuForwardEdges(newEdges, wtg, window, analyzeOutput, guiHierarchy, viewToHandlers);
			} else {
				Logger.err(getClass().getSimpleName(), "not recognize window: " + window);
			}
			// create self edge to represent back event
			// we did this for comparison experiment
			WTGNode current = wtg.getNode(window);
			WTGEdge backEdge = helper.createEdge(wtg, current, current, window, EventType.implicit_back_event,
					Sets.<SootMethod>newHashSet(), RootTag.implicit_back, Lists.<StackOperation>newArrayList(),
					Lists.<EventHandler>newArrayList());
			addEdge(newEdges, backEdge);
		}
	}

	private Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeCallback(
			final Multimap<NObjectNode, NObjectNode> guiHierarchy,
			final Multimap<NObjectNode, HandlerBean> viewToHandlers) {
		/****************************************************************************/
		/****************************************************************************/
		/****************************
		 * analyze callbacks
		 *****************************/
		/****************************************************************************/
		/****************************************************************************/
		/****************************************************************************/
		Set<CFGAnalyzerInput> inputSet = Sets.newHashSet();
		for (NObjectNode window : guiHierarchy.keySet()) {
			if (!(window instanceof NActivityNode || window instanceof NDialogNode || window instanceof NMenuNode)) {
				Logger.err(getClass().getSimpleName(), "unexpected window type: " + window);
			}
			Collection<NObjectNode> underneathViews = guiHierarchy.get(window);
			Set<NObjectNode> allViews = Sets.newHashSet(underneathViews);
			// allViews.add(window);
			for (NObjectNode view : allViews) {
				for (HandlerBean bean : viewToHandlers.get(view)) {
					for (SootMethod handler : bean.getHandlers()) {
						// we should get the real gui widget here instead of
						// using
						// "view" because we treat "onCreateOptionsMenu"
						// specially
						NObjectNode guiWidget = bean.getGUIWidget();
						CFGAnalyzerInput analyzerInput = null;
						if (window instanceof NActivityNode) {
							analyzerInput = new CFGAnalyzerInput(guiWidget, handler, Filter.openWindowStmtFilter);
						} else {
							analyzerInput = new CFGAnalyzerInput(guiWidget, handler,
									Filter.openActivityDialogStmtFilter);
						}
						inputSet.add(analyzerInput);
					}
				}
			}
		}
		CFGScheduler scheduler = new CFGScheduler(guiOutput, flowgraphRebuilder);
		return scheduler.schedule(inputSet);
	}

	private void buildWTGEdges(final Multimap<WTGEdgeSig, WTGEdge> newEdges, final WTG wtg,
			final Multimap<NObjectNode, NObjectNode> guiHierarchy, final Multimap<NMenuNode, NWindowNode> menuToWindows,
			final Multimap<NWindowNode, Pair<NObjectNode, NContextMenuNode>> windowToContextMenus) {
		// set up launcher and link the launcher with main activity
		WTGNode launcher = wtg.addLauncherNode(NLauncherNode.LAUNCHER);
		SootClass mainClz = guiOutput.getMainActivity();
		if (mainClz == null) {
			return;
		}
		Flowgraph flowgraph = guiOutput.getFlowgraph();
		NActivityNode mainActNode = flowgraph.allNActivityNodes.get(mainClz);
		WTGNode mainNode = wtg.getNode(mainActNode);
		List<StackOperation> stackOps = Lists.newArrayList();
		List<EventHandler> callbacks = Lists.newArrayList();
		WTGEdge newEdge = wtgHelper.createEdge(wtg, launcher, mainNode, NLauncherNode.LAUNCHER,
				EventType.implicit_launch_event, Sets.<SootMethod>newHashSet(), RootTag.implicit_launch, stackOps,
				callbacks);
		addEdge(newEdges, newEdge);

			
		// build other forward edges
		Multimap<NObjectNode, HandlerBean> widgetToHandlers = buildWidgetHandlers(guiHierarchy, menuToWindows,
				windowToContextMenus);

		// build image resource associations
		Multimap<NObjectNode, NIdNode> widgetToImagess = buildWidgetImages(guiHierarchy, menuToWindows,
				windowToContextMenus);

		buildExplicitForwardEdges(newEdges, wtg, guiHierarchy, widgetToHandlers);
		this.widgetToHandlers = widgetToHandlers;
		this.widgetToImagess = widgetToImagess;
	}

	private Multimap<NObjectNode, NIdNode> buildWidgetImages(Multimap<NObjectNode, NObjectNode> guiHierarchy,
			Multimap<NMenuNode, NWindowNode> menuToWindows,
			Multimap<NWindowNode, Pair<NObjectNode, NContextMenuNode>> windowToContextMenus) {
		Multimap<NObjectNode, NIdNode> viewToImages = HashMultimap.create();
		for (NObjectNode window : guiHierarchy.keySet()) {
			Collection<NObjectNode> underneathViews = guiHierarchy.get(window);
			for (NObjectNode v : underneathViews) {
				Set<NIdNode> evtToCallbacks = guiOutput.getImageResourceId(v);
				for (NIdNode idnode : evtToCallbacks) {
					if (!viewToImages.containsKey(v)) {
						viewToImages.put(v, idnode);
					} else {
						viewToImages.get(v).add(idnode);
					}
				}
			}
		}
		return viewToImages;
	}

	private void buildWTGNodes(final WTG wtg, final Multimap<NObjectNode, NObjectNode> guiHierarchy,
			final Multimap<NMenuNode, NWindowNode> menuToWindows,
			final Multimap<NWindowNode, Pair<NObjectNode, NContextMenuNode>> windowToContextMenus) {
		// build GUI hierarchy
		initializeGUIHierarchy(wtg, guiHierarchy, menuToWindows, windowToContextMenus);
	}

	private Multimap<NObjectNode, HandlerBean> buildWidgetHandlers(Multimap<NObjectNode, NObjectNode> guiHierarchy,
			Multimap<NMenuNode, NWindowNode> menuToWindows,
			Multimap<NWindowNode, Pair<NObjectNode, NContextMenuNode>> windowToContextMenus) {
		Multimap<NObjectNode, HandlerBean> viewToHandlers = HashMultimap.create();
		for (NObjectNode window : guiHierarchy.keySet()) {
			Collection<NObjectNode> underneathViews = guiHierarchy.get(window);
			for (NObjectNode v : underneathViews) {
				Map<EventType, Set<SootMethod>> evtToCallbacks = guiOutput.getExplicitEventsAndTheirHandlers(v);
				for (EventType evt : evtToCallbacks.keySet()) {
					Set<SootMethod> callbacks = evtToCallbacks.get(evt);
					for (SootMethod callback : callbacks) {
						SootClass declaringClz = callback.getDeclaringClass();
						SootClass windowClz = window.getClassType();
						if (!hier.isSibling(declaringClz, windowClz, wtgUtil.activityClass)
								&& !hier.isSibling(declaringClz, windowClz, wtgUtil.dialogClass)
								&& !(hier.isSubclassOf(declaringClz, windowClz) && declaringClz != windowClz)) {
							addHandlerBean(viewToHandlers, window, v, evt, callback, true);
						}
					}
				}
			}

			// build open context menus
			if (window instanceof NWindowNode) {
				Collection<Pair<NObjectNode, NContextMenuNode>> viewToContextMenus = windowToContextMenus
						.get((NWindowNode) window);
				for (Pair<NObjectNode, NContextMenuNode> viewToContextMenu : viewToContextMenus) {
					NObjectNode v = viewToContextMenu.getO1();
					{
						SootMethod callback = wtgHelper.getCallback(window.getClassType(),
								MethodNames.onCreateContextMenuSubSig);
						if (callback != null) {
							addHandlerBean(viewToHandlers, window, v, EventType.long_click, callback, true);
						}
					}
					{
						SootMethod callback = wtgHelper.getCallback(v.getClassType(),
								MethodNames.viewOnCreateContextMenuSubSig);
						if (callback != null) {
							addHandlerBean(viewToHandlers, window, v, EventType.long_click, callback, true);
						}
					}
				}
			}

			// if an activity has an options menu, we can pretend that
			// there is an artificial handler for the MENU button
			if (window instanceof NActivityNode) {
				SootClass clz = ((NActivityNode) window).c;
				NOptionsMenuNode om = guiOutput.getOptionsMenu(clz);
				if (om != null) {
					SootMethod callback = wtgHelper.getCallback(window.getClassType(),
							MethodNames.onCreateOptionsMenuSubsig);
					if (callback != null) {
						addHandlerBean(viewToHandlers, window, om, EventType.click, callback, false);
					}
				}
				generateActivityImplicitHandlers((NActivityNode) window, viewToHandlers);
			} else if (window instanceof NMenuNode) {
				// special handling of menus
				// the views are MenuItems
				Collection<NWindowNode> owners = menuToWindows.get((NMenuNode) window);
				if (owners.isEmpty()) {
					Logger.err(getClass().getSimpleName(), "can not find the owner window for menu node: " + window);
				}
				List<String> handlers;
				if (window instanceof NContextMenuNode) {
					handlers = Lists.newArrayList(new String[] { MethodNames.onContextItemSelectedSubSig,
							MethodNames.onMenuItemSelectedSubSig });
				} else {
					handlers = Lists.newArrayList(new String[] { MethodNames.onOptionsItemSelectedSubSig,
							MethodNames.onMenuItemSelectedSubSig });
				}

				Set<SootMethod> allHandlers = Sets.newHashSet();
				for (String menuItemHandler : handlers) {
					for (NWindowNode owner : owners) {
						SootMethod handler = wtgHelper.getCallback(owner.getClassType(), menuItemHandler);
						if (handler != null) {
							allHandlers.add(handler);
						}
					}
				}
				for (NObjectNode v : underneathViews) {
					// don't create handler bean for window itself
					if (v == window) {
						continue;
					}
					for (SootMethod callback : allHandlers) {
						addHandlerBean(viewToHandlers, window, v, EventType.click, callback, true);
					}
				}
			} else if (window instanceof NDialogNode) {
				generateDialogImplicitLifeCycleHandlers((NDialogNode) window, viewToHandlers);
			}
		}
		// analyze asynchronous operations
		if (Configs.asyncStrategy == AsyncOpStrategy.Default_Special_Async
				|| Configs.asyncStrategy == AsyncOpStrategy.All_Special_Async) {
			// handle View.post/postDelayed and Activity.runOnUiThread
			handleViewAndActivityAsyncOps(guiHierarchy, viewToHandlers);
			// handle Handler.post/postDelayed/postAtTime
			handleHandlerAsyncOps(menuToWindows, viewToHandlers);
		}
		return viewToHandlers;
	}

	private void initializeGUIHierarchy(final WTG wtg, final Multimap<NObjectNode, NObjectNode> guiHierarchy,
			final Multimap<NMenuNode, NWindowNode> menuToWindows,
			final Multimap<NWindowNode, Pair<NObjectNode, NContextMenuNode>> windowToContextMenus) {
		// traverse GUI hierarchy, collect views and context menus
		// window are currently defined as: Activity, Dialog and Menu
		List<NObjectNode> windowNodeList = Lists.newArrayList();
		for (SootClass c : guiOutput.getActivities()) {
			NActivityNode actNode = guiOutput.getFlowgraph().allNActivityNodes.get(c);
			windowNodeList.add(actNode);
		}
		for (NDialogNode dialogNode : guiOutput.getDialogs()) {
			windowNodeList.add(dialogNode);
		}
		while (!windowNodeList.isEmpty()) {
			NObjectNode windowNode = windowNodeList.remove(0);
			// window is in the hierarchy of itself
			guiHierarchy.put(windowNode, windowNode);
			if (windowNode instanceof NActivityNode) {
				SootClass actClass = ((NActivityNode) windowNode).c;
				NOptionsMenuNode optionMenu = guiOutput.getOptionsMenu(actClass);
				if (optionMenu != null) {
					// add it to list to resolve hierarchy
					windowNodeList.add(optionMenu);
					initWindow(optionMenu, (NActivityNode) windowNode, menuToWindows);
				}
				Set<NNode> roots = guiOutput.getActivityRoots(actClass);
				for (NNode r : roots) {
					for (NNode desc : graphUtil.descendantNodes(r)) {
						NObjectNode v = (NObjectNode) desc;
						Set<NContextMenuNode> contextMenus = guiOutput.getContextMenus(v);
						guiHierarchy.put(windowNode, v);
						for (NContextMenuNode cm : contextMenus) {
							windowNodeList.add(cm);
							initWindow(cm, (NActivityNode) windowNode, v, menuToWindows, windowToContextMenus);
						} // each context menu
					} // each view under the activity
				}
			} else if (windowNode instanceof NDialogNode) {
				Set<NNode> all_roots = guiOutput.getDialogRoots((NDialogNode) windowNode);
				for (NNode r : all_roots) {
					for (NNode desc : graphUtil.descendantNodes(r)) {
						NObjectNode v = (NObjectNode) desc;
						Set<NContextMenuNode> contextMenus = guiOutput.getContextMenus(v);
						guiHierarchy.put(windowNode, v);
						if (contextMenus != null) {
							for (NContextMenuNode cm : contextMenus) {
								windowNodeList.add(cm);
								initWindow(cm, (NDialogNode) windowNode, v, menuToWindows, windowToContextMenus);
							} // each context menu
						}
					} // each view under the dialog
				}
			} else if (windowNode instanceof NMenuNode) {
				for (NNode desc : graphUtil.descendantNodes(windowNode)) {
					NObjectNode v = (NObjectNode) desc;
					guiHierarchy.put(windowNode, v);
				}
			} else {
				Logger.err(getClass().getSimpleName(), "the current definition of window is of type: activity,"
						+ " dialog and menu, but it is " + windowNode);
			}
			// create corresponding wtg node in wtg
			wtg.addNode(windowNode);
		}
	}

	private void initWindow(NContextMenuNode menu, NWindowNode owner, NObjectNode view,
			Multimap<NMenuNode, NWindowNode> menuToWindows,
			Multimap<NWindowNode, Pair<NObjectNode, NContextMenuNode>> windowToContextMenus) {
		initWindow(menu, owner, menuToWindows);
		windowToContextMenus.put(owner, new Pair<NObjectNode, NContextMenuNode>(view, menu));
	}

	private void generateActivityImplicitHandlers(NActivityNode activity,
			Multimap<NObjectNode, HandlerBean> viewToHandlers) {
		SootClass activityClass = activity.c;
		{
			// handle onNewIntent callback
			SootMethod handler = wtgHelper.getCallback(activityClass, MethodNames.activityOnNewIntentSubSig);
			if (handler != null) {
				// HandlerBean callback = new HandlerBean(activityNode,
				// activityNode, EventType.implicit_on_activity_newIntent,
				// handler);
				// viewToHandlers.put(activityNode, callback);
				addHandlerBean(viewToHandlers, activity, activity, EventType.implicit_on_activity_newIntent, handler,
						true);
			}
		}
		if (wtgHelper.isSubClassOf(activityClass, wtgUtil.preferenceActivityClass)) {
			// don't handle preference activity
			return;
		}
		{
			// handle onActivityResult callback
			SootMethod handler = wtgHelper.getCallback(activityClass, MethodNames.onActivityResultSubSig);
			if (handler != null) {
				// HandlerBean callback = new HandlerBean(activityNode,
				// activityNode, EventType.implicit_on_activity_result,
				// handler);
				// viewToHandlers.put(activityNode, callback);
				addHandlerBean(viewToHandlers, activity, activity, EventType.implicit_on_activity_result, handler,
						true);
			}
		}
		{
			// handle the rest of explicit handlers on activity node
			List<String> keyActions = Lists.newArrayList("boolean onKeyDown(int,android.view.KeyEvent)",
					"boolean onKeyUp(int,android.view.KeyEvent)");
			Set<SootMethod> keyActionHandlers = guiOutput.getActivityHandlers(activityClass, keyActions);
			if (keyActionHandlers == null) {
				return;
			}
			for (SootMethod handler : keyActionHandlers) {
				// HandlerBean callback = new HandlerBean(activityNode,
				// activityNode, EventType.press_key, handler);
				// viewToHandlers.put(activityNode, callback);
				addHandlerBean(viewToHandlers, activity, activity, EventType.press_key, handler, true);
			}
		}
	}

	private void generateDialogImplicitLifeCycleHandlers(NDialogNode dialogNode,
			Multimap<NObjectNode, HandlerBean> viewToHandlers) {
		for (SootMethod handler : guiOutput.getOtherEventHandlersForDialog(dialogNode)) {
			String subsig = handler.getSubSignature();
			if (subsig.equals(MethodNames.dialogOnCancelSubSig)) {
				// HandlerBean callback = new HandlerBean(dialogNode,
				// dialogNode, EventType.dialog_cancel, handler);
				// viewToHandlers.put(dialogNode, callback);
				addHandlerBean(viewToHandlers, dialogNode, dialogNode, EventType.dialog_cancel, handler, true);
			} else if (subsig.equals(MethodNames.dialogOnDismissSubSig)) {
				// HandlerBean callback = new HandlerBean(dialogNode,
				// dialogNode, EventType.dialog_dismiss, handler);
				// viewToHandlers.put(dialogNode, callback);
				addHandlerBean(viewToHandlers, dialogNode, dialogNode, EventType.dialog_dismiss, handler, true);
			} else if (subsig.equals(MethodNames.dialogOnKeySubSig)) {
				// HandlerBean callback = new HandlerBean(dialogNode,
				// dialogNode, EventType.dialog_press_key, handler);
				// viewToHandlers.put(dialogNode, callback);
				addHandlerBean(viewToHandlers, dialogNode, dialogNode, EventType.dialog_press_key, handler, true);
			} else if (subsig.equals(MethodNames.dialogOnShowSubSig)) {
				// HandlerBean callback = new HandlerBean(dialogNode,
				// dialogNode, EventType.implicit_lifecycle_event, handler);
				// viewToHandlers.put(dialogNode, callback);
				addHandlerBean(viewToHandlers, dialogNode, dialogNode, EventType.implicit_lifecycle_event, handler,
						true);
			} else {
				Logger.err(getClass().getSimpleName(), "can not find other handler for dialog: " + handler);
			}
		}
	}

	private void initWindow(NMenuNode menu, NWindowNode owner, Multimap<NMenuNode, NWindowNode> menuToWindows) {
		menuToWindows.put(menu, owner);
	}

	private void buildActivityForwardEdges(final Multimap<WTGEdgeSig, WTGEdge> newEdges, final WTG wtg,
			final NObjectNode window, final Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput,
			final Multimap<NObjectNode, NObjectNode> guiHierarchy,
			final Multimap<NObjectNode, HandlerBean> viewToHandlers) {
		WTGNode srcNode = wtg.getNode(window);
		Collection<NObjectNode> underneathViews = guiHierarchy.get(window);
		Set<NObjectNode> allViews = Sets.newHashSet(underneathViews);
		// specify the stmts we are interested: showDialog, startActivity and
		// openMenu
		for (NObjectNode view : allViews) {
			for (HandlerBean bean : viewToHandlers.get(view)) {
				NObjectNode guiWidget = bean.getGUIWidget();
				EventType event = bean.getEvent();
				boolean avoid = true;
				Set<SootMethod> handlers = bean.getHandlers();
				for (SootMethod handler : handlers) {
					// we should get the real gui widget here instead of using
					// "view" because we treat "onCreateOptionsMenu" specially
					CFGAnalyzerInput input = new CFGAnalyzerInput(guiWidget, handler, Filter.openWindowStmtFilter);
					CFGAnalyzerOutput targetsAndAvoid = analyzeOutput.get(input);
					Assert.assertNotNull("[Error]: cfg analyze input is not processed yet: " + input, targetsAndAvoid);
					Multimap<NObjectNode, Pair<Stmt, SootMethod>> targetWindows = targetsAndAvoid.targets;
					avoid = avoid && targetsAndAvoid.avoid;
					for (NObjectNode targetWindow : targetWindows.keySet()) {
						buildActivityForwardEdge(newEdges, wtg, window, targetWindow, event, guiWidget, handlers);
					}
				}
				if (avoid) {
					List<StackOperation> stackOps = Lists.newArrayList();
					List<EventHandler> callbacks = Lists.newArrayList();
					// all avoid will create cyclic edge
					WTGEdge newEdge = helper.createEdge(wtg, srcNode, srcNode, guiWidget, event, handlers,
							RootTag.cyclic_edge, stackOps, callbacks);
					addEdge(newEdges, newEdge);
				}
			}
		}
	}

	private void buildActivityForwardEdge(final Multimap<WTGEdgeSig, WTGEdge> newEdges, final WTG wtg,
			final NObjectNode sourceWindow, final NObjectNode targetWindow, final EventType event,
			final NObjectNode guiWidget, final Set<SootMethod> eventHandlers) {

		WTGNode source = null;
		WTGNode target = null;
		try {
			source = wtg.getNode(sourceWindow);
			target = wtg.getNode(targetWindow);
		} catch (RuntimeException re) {
			// Temp work around for windows that does not have correspond
			// WTGNode
			return;
		}
		List<StackOperation> stackOps = Lists.newArrayList();
		List<EventHandler> callbacks = Lists.newArrayList();
		if (targetWindow instanceof NActivityNode) {
			/* if the target window is activity */
			WTGEdge newEdge = helper.createEdge(wtg, source, target, guiWidget, event, eventHandlers,
					RootTag.start_activity, stackOps, callbacks);
			addEdge(newEdges, newEdge);
		} else if (targetWindow instanceof NDialogNode) {
			/* if the target window is dialog */
			WTGEdge newEdge = helper.createEdge(wtg, source, target, guiWidget, event, eventHandlers,
					RootTag.show_dialog, stackOps, callbacks);
			addEdge(newEdges, newEdge);
		} else if (targetWindow instanceof NContextMenuNode) {
			/* if the target window is context menu */
			/*
			 * WTGEdge newEdge = null; if
			 * (!subsig.equals(MethodNames.onCreateContextMenuSubSig)) { newEdge
			 * = helper.createEdge(wtg, source, target, guiWidget, event,
			 * eventHandler, RootTag.open_context_menu, stackOps, callbacks); }
			 * else { newEdge = helper.createEdge(wtg, source, target,
			 * guiWidget, event, null, RootTag.open_context_menu, stackOps,
			 * callbacks); }
			 */
			WTGEdge newEdge = helper.createEdge(wtg, source, target, guiWidget, event, eventHandlers,
					RootTag.open_context_menu, stackOps, callbacks);
			addEdge(newEdges, newEdge);
		} else if (targetWindow instanceof NOptionsMenuNode) {
			/* if the target window is options menu */
			// we thought handler (onCreateContextMenu/onCreateOptionsMenu)
			// is the lifecycle of menu instead of real handler
			/*
			 * WTGEdge newEdge = null; if
			 * (!subsig.equals(MethodNames.onCreateOptionsMenuSubsig)) { newEdge
			 * = helper.createEdge(wtg, source, target, guiWidget, event,
			 * eventHandler, RootTag.open_options_menu, stackOps, callbacks); }
			 * else { newEdge = helper.createEdge(wtg, source, target,
			 * guiWidget, event, null, RootTag.open_options_menu, stackOps,
			 * callbacks); }
			 */
			WTGEdge newEdge = helper.createEdge(wtg, source, target, guiWidget, event, eventHandlers,
					RootTag.open_options_menu, stackOps, callbacks);
			addEdge(newEdges, newEdge);
		} else {
			Logger.err(getClass().getSimpleName(), "target window can not be type of " + targetWindow);
		}
	}

	private void buildDialogOrMenuForwardEdges(final Multimap<WTGEdgeSig, WTGEdge> newEdges, final WTG wtg,
			final NObjectNode window, final Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput,
			final Multimap<NObjectNode, NObjectNode> guiHierarchy,
			final Multimap<NObjectNode, HandlerBean> viewToHandlers) {
		// pending edges: edges we will handle later
		WTGNode srcNode = wtg.getNode(window);
		Collection<NObjectNode> underneathViews = guiHierarchy.get(window);
		Set<NObjectNode> allViews = Sets.newHashSet(underneathViews);
		for (NObjectNode view : allViews) {
			for (HandlerBean bean : viewToHandlers.get(view)) {
				NObjectNode guiWidget = bean.getGUIWidget();
				Set<SootMethod> handlers = bean.getHandlers();
				EventType event = bean.getEvent();
				boolean avoid = true;
				for (SootMethod handler : handlers) {
					CFGAnalyzerInput input = new CFGAnalyzerInput(guiWidget, handler,
							Filter.openActivityDialogStmtFilter);
					CFGAnalyzerOutput targetsAndAvoid = analyzeOutput.get(input);
					Assert.assertNotNull("[Error]: cfg analyze input is not processed yet: " + input, targetsAndAvoid);
					Multimap<NObjectNode, Pair<Stmt, SootMethod>> targetWindows = targetsAndAvoid.targets;
					avoid = avoid && targetsAndAvoid.avoid;
					for (NObjectNode targetWindow : targetWindows.keySet()) {
						buildDialogOrMenuForwardEdge(newEdges, wtg, window, targetWindow, bean);
					}
				}
				if (avoid) {
					List<StackOperation> stackOps = Lists.newArrayList();
					List<EventHandler> callbacks = Lists.newArrayList();
					WTGEdge newEdge = helper.createEdge(wtg, srcNode, srcNode, guiWidget, event, handlers,
							RootTag.cyclic_edge, stackOps, callbacks);
					addEdge(newEdges, newEdge);
				}
			}
		}
	}

	private void buildDialogOrMenuForwardEdge(final Multimap<WTGEdgeSig, WTGEdge> newEdges, final WTG wtg,
			final NObjectNode sourceWindow, final NObjectNode targetWindow, final HandlerBean callback) {
		WTGNode source = wtg.getNode(sourceWindow);
		WTGNode target = wtg.getNode(targetWindow);
		NObjectNode guiWidget = callback.getGUIWidget();
		EventType event = callback.getEvent();
		Set<SootMethod> handlers = callback.getHandlers();
		List<StackOperation> stackOps = Lists.newArrayList();
		List<EventHandler> callbacks = Lists.newArrayList();

		if (targetWindow instanceof NActivityNode) {
			/* if the target window is activity */
			// forward edge
			WTGEdge newEdge = helper.createEdge(wtg, source, target, guiWidget, event, handlers, RootTag.start_activity,
					stackOps, callbacks);
			addEdge(newEdges, newEdge);
		} else if (targetWindow instanceof NDialogNode) {
			/* if the target window is dialog */
			WTGEdge newEdge = helper.createEdge(wtg, source, target, guiWidget, event, handlers, RootTag.show_dialog,
					stackOps, callbacks);
			addEdge(newEdges, newEdge);
		} else if (targetWindow instanceof NMenuNode) {
			/* if the target window is menu */
			// we choose to ignore the case where target window is menu
		} else {
			/* if other cases happen */
			Logger.err(getClass().getSimpleName(), "target window can not be type of " + targetWindow);
		}
	}

	private void addHandlerBean(Multimap<NObjectNode, HandlerBean> beans, NObjectNode window, NObjectNode widget,
			EventType evt, SootMethod callback, boolean isWidgetKey) {
		NObjectNode key = widget;
		if (!isWidgetKey) {
			key = window;
		}
		for (HandlerBean bean : beans.get(key)) {
			if (evt != EventType.implicit_async_event && bean.getEvent() != EventType.implicit_async_event
					&& bean.getWindow() == window && bean.getGUIWidget() == widget && bean.getEvent() == evt) {
				bean.addHandler(callback);
				return;
			} else if (evt == EventType.implicit_async_event && bean.getEvent() == EventType.implicit_async_event
					&& bean.getWindow() == window && bean.getGUIWidget() == widget && bean.getHandlers().size() == 1
					&& bean.getHandlers().contains(callback)) {
				return;
			}
		}
		HandlerBean bean = new HandlerBean(window, widget, evt, Sets.newHashSet(callback));
		beans.put(key, bean);
	}

	private void addEdge(Multimap<WTGEdgeSig, WTGEdge> allEdges, WTGEdge newEdge) {
		WTGEdgeSig sig = newEdge.getSig();
		if (allEdges.containsKey(sig)) {
			return;
		}
		allEdges.put(sig, null);
	}

	private void handleViewAndActivityAsyncOps(Multimap<NObjectNode, NObjectNode> guiHierarchy,
			Multimap<NObjectNode, HandlerBean> viewToHandlers) {
		Multimap<NObjectNode, NObjectNode> reverseGUIHierarchy = HashMultimap.create();
		for (NObjectNode window : guiHierarchy.keySet()) {
			for (NObjectNode widget : guiHierarchy.get(window)) {
				reverseGUIHierarchy.put(widget, window);
			}
		}
		for (Pair<Stmt, SootMethod> asyncStmtPair : flowgraphRebuilder.getAsyncStmts()) {
			Stmt asyncStmt = asyncStmtPair.getO1();
			Local rcvLocal = jimpleUtil.receiver(asyncStmt);
			RefType rcvType = (RefType) rcvLocal.getType();
			if (hier.isSubclassOf(rcvType.getSootClass(), wtgUtil.handlerClass)) {
				continue;
			}
			Integer runnableArgIdx = wtgUtil.getAsyncMethodCallRunnableArg(asyncStmt);
			Set<SootMethod> runnableCallbacks = Sets.newHashSet();
			Value argValue = asyncStmt.getInvokeExpr().getArg(runnableArgIdx);
			NNode argNode = flowgraphRebuilder.lookupNode(argValue);
			for (NNode runnable : queryHelper.allVariableValues(argNode)) {
				if (runnable instanceof NObjectNode) {
					SootClass runnableCls = ((NObjectNode) runnable).getClassType();
					SootMethod callee = hier.virtualDispatch(wtgUtil.runnableRunMethodSubSig, runnableCls);
					if (callee == null) {
						continue;
					}
					runnableCallbacks.add(callee);
				}
			}
			if (runnableCallbacks.isEmpty()) {
				Logger.verb(getClass().getSimpleName(), "cannot find runnable for async operation: " + asyncStmt);
				continue;
			}
			NNode rcvNode = flowgraphRebuilder.lookupNode(rcvLocal);
			Set<NObjectNode> widgets = Sets.newHashSet();
			for (NNode widget : queryHelper.allVariableValues(rcvNode)) {
				if (widget instanceof NObjectNode) {
					widgets.add((NObjectNode) widget);
				}
			}
			if (widgets.isEmpty()) {
				Logger.verb(getClass().getSimpleName(), "cannot find receiver for async operation: " + asyncStmt);
				continue;
			}
			for (NObjectNode widget : widgets) {
				Collection<NObjectNode> windows = reverseGUIHierarchy.get((NObjectNode) widget);
				if (windows.isEmpty()) {
					Logger.verb(getClass().getSimpleName(), "cannot find window for async operation: " + asyncStmt);
					continue;
				}
				for (NObjectNode window : windows) {
					for (SootMethod callback : runnableCallbacks) {
						addHandlerBean(viewToHandlers, window, (NObjectNode) widget, EventType.implicit_async_event,
								callback, true);
					}
				}
			}
		}
	}

	private void handleHandlerAsyncOps(Multimap<NMenuNode, NWindowNode> menuToWindows,
			Multimap<NObjectNode, HandlerBean> viewToHandlers) {
		// callback to window map
		Multimap<SootMethod, NObjectNode> handlerToWindows = HashMultimap.create();
		for (NObjectNode view : viewToHandlers.keySet()) {
			for (HandlerBean bean : viewToHandlers.get(view)) {
				for (SootMethod handler : bean.getHandlers()) {
					handlerToWindows.put(handler, bean.getWindow());
				}
			}
		}
		// lifecycle handlers
		for (NNode nnode : guiOutput.getFlowgraph().allNNodes) {
			if (nnode instanceof NActivityNode) {
				NActivityNode actNode = (NActivityNode) nnode;
				SootClass actCls = actNode.c;
				for (SootMethod callback : helper.getCallbacks(actCls, MethodNames.onActivityCreateSubSig,
						MethodNames.onActivityStartSubSig, MethodNames.onActivityRestartSubSig,
						MethodNames.onActivityResumeSubSig, MethodNames.onActivityPauseSubSig,
						MethodNames.onActivityStopSubSig, MethodNames.onActivityDestroySubSig,
						MethodNames.activityOnNewIntentSubSig)) {
					handlerToWindows.put(callback, actNode);
				}
			} else if (nnode instanceof NDialogNode) {
				NDialogNode diaNode = (NDialogNode) nnode;
				SootClass diaCls = diaNode.c;
				for (SootMethod callback : helper.getCallbacks(diaCls, MethodNames.onDialogCreateSubSig,
						MethodNames.onDialogStartSubSig, MethodNames.onDialogStopSubSig)) {
					handlerToWindows.put(callback, diaNode);
				}
			} else if (nnode instanceof NOptionsMenuNode) {
				NOptionsMenuNode omNode = (NOptionsMenuNode) nnode;
				for (NWindowNode owner : menuToWindows.get(omNode)) {
					SootClass ownerCls = owner.c;
					for (SootMethod callback : helper.getCallbacks(ownerCls, MethodNames.onCreateOptionsMenuSubsig,
							MethodNames.onPrepareOptionsMenuSubsig, MethodNames.onCloseOptionsMenuSubsig)) {
						handlerToWindows.put(callback, omNode);
					}
				}
			} else if (nnode instanceof NContextMenuNode) {
				NContextMenuNode ctxNode = (NContextMenuNode) nnode;
				for (NWindowNode owner : menuToWindows.get(ctxNode)) {
					SootClass ownerCls = owner.c;

					for (SootMethod callback : helper.getCallbacks(ownerCls, MethodNames.onCreateContextMenuSubSig,
							MethodNames.onCloseContextMenuSubsig)) {
						handlerToWindows.put(callback, ctxNode);
					}
				}
			}
		}
		// map from android.os.Handler to source windows
		Multimap<NObjectNode, NObjectNode> handlerToSourceWindows = HashMultimap.create();
		AndroidCallGraph cg = AndroidCallGraph.v();
		for (Pair<Stmt, SootMethod> asyncStmtPair : flowgraphRebuilder.getHandlerInitStmts()) {
			AssignStmt initStmt = (AssignStmt) asyncStmtPair.getO1();
			SootMethod context = asyncStmtPair.getO2();
			NObjectNode handlerObj = (NObjectNode) flowgraphRebuilder.lookupNode(initStmt.getRightOp());
			List<SootMethod> worklist = Lists.newArrayList(context);
			Set<SootMethod> visitMethods = Sets.newHashSet(context);
			while (!worklist.isEmpty()) {
				SootMethod callee = worklist.remove(0);
				Set<Edge> callers = cg.getIncomingEdges(callee);
				if (callers.isEmpty()) {
					handlerToSourceWindows.putAll(handlerObj, handlerToWindows.get(callee));
				} else {
					for (Edge caller : callers) {
						if (visitMethods.add(caller.source)) {
							worklist.add(caller.source);
						}
					}
				}
			}
		}
		for (Pair<Stmt, SootMethod> asyncStmtPair : flowgraphRebuilder.getAsyncStmts()) {
			Stmt asyncStmt = asyncStmtPair.getO1();
			Local rcvLocal = jimpleUtil.receiver(asyncStmt);
			RefType rcvType = (RefType) rcvLocal.getType();
			if (!hier.isSubclassOf(rcvType.getSootClass(), wtgUtil.handlerClass)) {
				continue;
			}
			NNode rcvNode = flowgraphRebuilder.lookupNode(rcvLocal);

			Integer runnableArgIdx = wtgUtil.getAsyncMethodCallRunnableArg(asyncStmt);
			Set<SootMethod> runnableCallbacks = Sets.newHashSet();
			Value argValue = asyncStmt.getInvokeExpr().getArg(runnableArgIdx);
			NNode argNode = flowgraphRebuilder.lookupNode(argValue);
			for (NNode runnable : queryHelper.allVariableValues(argNode)) {
				if (runnable instanceof NObjectNode) {
					SootClass runnableCls = ((NObjectNode) runnable).getClassType();
					SootMethod callee = hier.virtualDispatch(wtgUtil.runnableRunMethodSubSig, runnableCls);
					if (callee == null) {
						continue;
					}
					runnableCallbacks.add(callee);
				}
			}
			for (NNode nnode : queryHelper.allVariableValues(rcvNode)) {
				if (nnode instanceof NAllocNode) {
					for (NObjectNode sourceWindow : handlerToSourceWindows.get((NAllocNode) nnode)) {
						for (SootMethod callback : runnableCallbacks) {
							addHandlerBean(viewToHandlers, sourceWindow, sourceWindow, EventType.implicit_async_event,
									callback, true);
						}
					}
				}
			}
		}
	}
}
