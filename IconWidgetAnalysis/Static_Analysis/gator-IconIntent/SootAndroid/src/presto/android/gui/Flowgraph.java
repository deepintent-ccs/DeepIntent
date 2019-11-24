/*
 * Flowgraph.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import presto.android.*;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NAddView1OpNode;
import presto.android.gui.graph.NAddView2OpNode;
import presto.android.gui.graph.NAllocNode;
import presto.android.gui.graph.NAnonymousIdNode;
import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NDrawableIdNode;
import presto.android.gui.graph.NFieldNode;
import presto.android.gui.graph.NFindView1OpNode;
import presto.android.gui.graph.NFindView1OpNode.FindView1Type;
import presto.android.gui.graph.NFindView2OpNode;
import presto.android.gui.graph.NFindView3OpNode;
import presto.android.gui.graph.NFindView3OpNode.FindView3Type;
import presto.android.gui.graph.NInflNode;
import presto.android.gui.graph.NInflate1OpNode;
import presto.android.gui.graph.NInflate2OpNode;
import presto.android.gui.graph.NLayoutIdNode;
import presto.android.gui.graph.NListenerAllocNode;
import presto.android.gui.graph.NMenuIdNode;
import presto.android.gui.graph.NMenuInflateOpNode;
import presto.android.gui.graph.NMenuItemInflNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.graph.NSetIdOpNode;
import presto.android.gui.graph.NSetImageResourceOpNode;
import presto.android.gui.graph.NSetListenerOpNode;
import presto.android.gui.graph.NSetTextOpNode;
import presto.android.gui.graph.NStringConstantNode;
import presto.android.gui.graph.NStringIdNode;
import presto.android.gui.graph.NTabSpecNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.graph.NViewAllocNode;
import presto.android.gui.graph.NWidgetIdNode;
import presto.android.gui.graph.NWindowNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.listener.ListenerRegistration;
import presto.android.gui.listener.ListenerSpecification;
import presto.android.xml.XMLParser;
import soot.ArrayType;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.LongType;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.BreakpointStmt;
import soot.jimple.CastExpr;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.DefinitionStmt;
import soot.jimple.Expr;
import soot.jimple.FieldRef;
import soot.jimple.GotoStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.MonitorStmt;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.NopStmt;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.RetStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.TableSwitchStmt;
import soot.jimple.ThisRef;
import soot.jimple.ThrowStmt;
import soot.toolkits.scalar.Pair;
import soot.util.Chain;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

// This is the constraint graph described in our paper.
public class Flowgraph implements MethodNames {
	static final String FAKE_NAME_PREFIX = "FakeName_";
	private long fakeNameIndex = 0;

	String nextFakeName() {
		return FAKE_NAME_PREFIX + fakeNameIndex++;
	}

	public Map<SootMethod, SootMethod> fakeHandlerToRealHandler = Maps.newHashMap();

	public Set<NNode> allNNodes = Sets.newHashSet();
	public Map<Local, NVarNode> allNVarNodes = Maps.newHashMap();
	public Map<SootField, NFieldNode> allNFieldNodes = Maps.newHashMap();
	public Map<Expr, NAllocNode> allNAllocNodes = Maps.newHashMap();
	public Map<SootClass, NActivityNode> allNActivityNodes = Maps.newHashMap();
	public Map<Integer, NLayoutIdNode> allNLayoutIdNodes = Maps.newHashMap();
	public Map<Integer, NMenuIdNode> allNMenuIdNodes = Maps.newHashMap();
	public Map<Integer, NWidgetIdNode> allNWidgetIdNodes = Maps.newHashMap();
	public Map<Integer, NStringIdNode> allNStringIdNodes = Maps.newHashMap();
	public Map<Integer, NDrawableIdNode> allNDrawableIdNode = Maps.newHashMap();
	public Map<Stmt, NDialogNode> allNDialogNodes = Maps.newHashMap();
	// Right now, we don't distinguish string constants "allocated" at different
	// locations but contain the same value.
	public Map<String, NStringConstantNode> allNStringConstantNodes = Maps.newHashMap();

	public Hierarchy hier;
	public Set<Integer> allLayoutIds;
	public Set<Integer> allMenuIds;
	public Set<Integer> allWidgetIds;
	public Set<Integer> allStringIds;

	public Set<NInflNode> allMenuItems = Sets.newHashSet();

	ListenerSpecification listenerSpecs;

	public Multimap<Stmt, SootMethod> regToEventHandlers = HashMultimap.create();
	// public Map<Stmt, Set<SootMethod>> regToEventHandlers = Maps.newHashMap();

	// Utils
	JimpleUtil jimpleUtil;
	GraphUtil graphUtil;
	XMLParser xmlUtil;
	private Set<Integer> allDrawableIds;

	public Flowgraph(Hierarchy hier, Set<Integer> allLayoutIds, Set<Integer> allMenuIds, Set<Integer> allWidgetIds,
			Set<Integer> allStringIds, Set<Integer> allDrawableIds) {
		this.hier = hier;
		this.allLayoutIds = allLayoutIds;
		this.allMenuIds = allMenuIds;
		this.allWidgetIds = allWidgetIds;
		this.allStringIds = allStringIds;
		this.allDrawableIds = allDrawableIds;

		this.listenerSpecs = ListenerSpecification.v();

		this.jimpleUtil = JimpleUtil.v(hier);
		this.graphUtil = GraphUtil.v();
		this.xmlUtil = XMLParser.Factory.getXMLParser();
	}

	public void buildIdNodes() {
		// Nodes for all layout ids, menu ids, widget ids, and string ids
		for (Integer i : allLayoutIds) {
			layoutIdNode(i);
		}
		for (Integer i : allMenuIds) {
			menuIdNode(i);
		}
		for (Integer i : allWidgetIds) {
			widgetIdNode(i);
		}
		for (Integer i : allStringIds) {
			stringIdNode(i);
		}

		for (Integer i : allDrawableIds) {
			drawableIdNode(i);
		}
	}

	void processFrameworkManagedCallbacks() {
		// 1) nodes and "this"-parameter edges for activities
		// 2) nodes and "menu"-parameter for Menu
		// (TODO: other framework created/managed classes)
		for (SootClass c : hier.frameworkManaged.keySet()) {
			if (hier.applicationActivityClasses.contains(c)) {
				processActivityCallbacks(c);
			} else {
				System.out.println("[TODO] Unhandled framework-managed class " + c);
			}
		}
	}

	void processActivityCallbacks(SootClass c) {
		if (c.isAbstract()) {
			return;
		}
		// Flow from "Dialog onCreateDialog" to "onPrepareDialog(,Dialog)"
		modelFlowFromOnCreateDialogToOnPrepareDialog(c);
		// Model onCreateOptionsMenu and onPrepareOptionsMenu; then, model flow
		// from them to onOptionsItemSelected and onMenuItemSelected.
		modelOnCreateOrPrepareOptionsMenuAndItsFlowToItemSelected(c);
		modelFlowFromCreateContextMenuToItemSelected(c);
		// Connect activity node to <this> of callback methods
		Set<SootMethod> callbacks = hier.frameworkManaged.get(c);
		for (SootMethod callbackPrototype : callbacks) {
			String subsig = callbackPrototype.getSubSignature();
			SootClass matched = hier.matchForVirtualDispatch(subsig, c);
			if (matched == null) {
				System.out.println("[WARNING] " + subsig + " does not exist for " + c);
				continue;
			}
			if (!matched.isApplicationClass()) {
				continue;
			}
			SootMethod callback = matched.getMethod(subsig);
			Local thisLocal = jimpleUtil.thisLocal(callback);
			NActivityNode actNode = activityNode(c);
			actNode.addEdgeTo(varNode(thisLocal), null);
		}
	}

	Map<SootClass, Map<SootMethod, Set<SootMethod>>> activityToCreateOrPrepareMenuAndItemSelected = Maps.newHashMap();

	Map<SootMethod, NVarNode> itemSelectedAndFakeVarNodes = Maps.newHashMap();

	void modelOnCreateOrPrepareOptionsMenuAndItsFlowToItemSelected(SootClass activityClass) {
		Set<SootMethod> menuCallbacks = Sets.newHashSet();
		SootClass matched = hier.matchForVirtualDispatch(onCreateOptionsMenuSubsig, activityClass);
		if (matched != null && matched.isApplicationClass()) {
			menuCallbacks.add(matched.getMethod(onCreateOptionsMenuSubsig));
		}
		matched = hier.matchForVirtualDispatch(onPrepareOptionsMenuSubsig, activityClass);
		if (matched != null && matched.isApplicationClass()) {
			menuCallbacks.add(matched.getMethod(onPrepareOptionsMenuSubsig));
		}
		if (menuCallbacks.isEmpty()) {
			return;
		}
		NOptionsMenuNode optionsMenuNode = findOrCreateOptionsMenuNode(activityClass);
		SootClass menuClass = Scene.v().getSootClass("android.view.Menu");
		Set<String> itemSelectedSubsigs = Sets.newHashSet(onOptionsItemSelectedSubSig, onMenuItemSelectedSubSig);
		for (SootMethod cb : menuCallbacks) {
			Local menuLocal = jimpleUtil.localForNthParameter(cb, 1);
			optionsMenuNode.addEdgeTo(varNode(menuLocal), null);
			for (String itemSelectedSubsig : itemSelectedSubsigs) {
				modelFlowFromCreateOrPrepareMenuToItemSelected(optionsMenuNode, menuClass, activityClass, cb,
						itemSelectedSubsig, 1);
			}
		}
	}

	void modelFlowFromCreateContextMenuToItemSelected(SootClass activityClass) {
		SootClass matched = hier.matchForVirtualDispatch(onCreateContextMenuSubSig, activityClass);
		if (matched == null || !matched.isApplicationClass()) {
			return;
		}
		SootMethod onCreateContextMenuMethod = matched.getMethod(onCreateContextMenuSubSig);
		SootClass contextMenuClass = Scene.v().getSootClass("android.view.ContextMenu");

		modelFlowFromCreateOrPrepareMenuToItemSelected(null, contextMenuClass, activityClass, onCreateContextMenuMethod,
				onContextItemSelectedSubSig, 1);

		modelFlowFromCreateOrPrepareMenuToItemSelected(null, contextMenuClass, activityClass, onCreateContextMenuMethod,
				onMenuItemSelectedSubSig, 2);
	}

	void modelFlowFromOnCreateDialogToOnPrepareDialog(SootClass activityClass) {
		Set<Local> sources = getDialogLocalsForOnCreateDialog(activityClass);
		if (sources.isEmpty()) {
			return;
		}
		Set<Local> targets = Sets.newHashSet();
		getAndSaveDialogLocalsForActivityCallback(activityClass, activityOnPrepareDialogSubSig,
				getLocalForArgOneFunction, targets);
		getAndSaveDialogLocalsForActivityCallback(activityClass, activityOnPrepareDialogBundleSubSig,
				getLocalForArgOneFunction, targets);

		for (Local src : sources) {
			for (Local tgt : targets) {
				varNode(src).addEdgeTo(varNode(tgt), null);
			}
		}
	}

	Set<Local> getDialogLocalsForOnCreateDialog(SootClass activityClass) {
		Set<Local> sources = Sets.newHashSet();
		getAndSaveDialogLocalsForActivityCallback(activityClass, activityOnCreateDialogSubSig,
				getReturnVariablesFunction, sources);
		getAndSaveDialogLocalsForActivityCallback(activityClass, activityOnCreateDialogBundleSubSig,
				getReturnVariablesFunction, sources);
		return sources;
	}

	void getAndSaveDialogLocalsForActivityCallback(SootClass activityClass, String callbackSubsig,
			Function<SootMethod, Set<Local>> getDialogLocals, Set<Local> localSet) {
		SootClass matched = hier.matchForVirtualDispatch(callbackSubsig, activityClass);
		if (matched != null && matched.isApplicationClass()) {
			SootMethod dialogCallback = matched.getMethod(callbackSubsig);
			localSet.addAll(getDialogLocals.apply(dialogCallback));
		}
	}

	Stmt currentStmt;
	SootMethod currentMethod;

	void processApplicationClasses() {
		// Now process each "ordinary" statements
		for (SootClass c : hier.appClasses) {
			// for (Iterator<SootMethod> iter = c.getMethods().iterator();
			// iter.hasNext();) {
			for (Iterator<SootMethod> iter = Lists.newArrayList(c.getMethods()).iterator(); iter.hasNext();) {
				currentMethod = iter.next();
				if (!currentMethod.isConcrete()) {
					continue;
				}
				Body b = currentMethod.retrieveActiveBody();
				Iterator<Unit> stmts = b.getUnits().iterator();
				while (stmts.hasNext()) {
					currentStmt = (Stmt) stmts.next();
					if (currentStmt instanceof ReturnVoidStmt) {
						continue;
					}
					if (currentStmt instanceof ThrowStmt) {
						continue;
					}
					if (currentStmt instanceof GotoStmt) {
						continue;
					}
					if (currentStmt instanceof BreakpointStmt) {
						continue;
					}
					if (currentStmt instanceof NopStmt) {
						continue;
					}
					if (currentStmt instanceof RetStmt) {
						continue;
					}
					if (currentStmt instanceof IfStmt) {
						continue;
					}
					if (currentStmt instanceof TableSwitchStmt) {
						continue;
					}
					if (currentStmt instanceof LookupSwitchStmt) {
						continue;
					}
					if (currentStmt instanceof MonitorStmt) {
						continue;
					}
					jimpleUtil.record(currentStmt, currentMethod); // remember
																	// the
																	// method

					// Some "special" handling of calls
					if (currentStmt.containsInvokeExpr()) {
						InvokeExpr ie = currentStmt.getInvokeExpr();
						SootMethod stm = null;
						try {
							stm = ie.getMethod(); // static target
						} catch (Exception e) {
							if (Configs.verbose) {
								Logger.verb("WARNING",
										"Bug in Soot. In Statement " + stm + " have incorrect invoke statement");
								e.printStackTrace();
							}
							continue;
						}

						// Model Android framework calls
						NOpNode opNode = null;
						try {
							opNode = createOpNode(currentStmt);
						} catch (Exception e) {
							System.out.println("Stmt: " + currentStmt.toString());
							e.printStackTrace();
							throw e;
						}
						if (opNode != null && opNode != NOpNode.NullNode) {
							allNNodes.add(opNode);
							continue;
						}
						// It is an operation node, but with missing parameters.
						// So, there
						// is no point continue matching other cases.
						if (opNode == NOpNode.NullNode) {
							continue;
						}
						// Other interesting calls
						recordInterestingCalls(currentStmt);

						// flow graph edges at non-virtual calls
						if (ie instanceof StaticInvokeExpr || ie instanceof SpecialInvokeExpr) {
							if (stm.getDeclaringClass().isApplicationClass()) {
								processFlowAtCall(currentStmt, stm);
							}
							continue;
						}

						// flow graph edges at virtual calls
						Local rcv_var = jimpleUtil.receiver(ie);
						Type rcv_t = rcv_var.getType();
						// could be ArrayType, for clone() calls
						if (!(rcv_t instanceof RefType)) {
							continue;
						}
						SootClass stc = ((RefType) rcv_t).getSootClass();
						for (Iterator<SootClass> trg_i = hier.getConcreteSubtypes(stc).iterator(); trg_i.hasNext();) {
							SootClass sub = trg_i.next();
							SootMethod trg = hier.virtualDispatch(stm, sub);
							if (trg != null && trg.getDeclaringClass().isApplicationClass()) {
								processFlowAtCall(currentStmt, trg);
							}
						}
						continue;
					} // the statement was a call

					// assignment (but not with a call; calls are already
					// handled)
					if (!(currentStmt instanceof DefinitionStmt)) {
						continue;
					}
					DefinitionStmt ds = (DefinitionStmt) currentStmt;
					Value lhs = ds.getLeftOp();
					// filter based on types
					if (!jimpleUtil.interesting(lhs.getType())) {
						continue;
					}
					Value rhs = ds.getRightOp();
					if (rhs instanceof CaughtExceptionRef) {
						continue;
					}
					// parameter passing taken care of by processFlowAtCall
					if (rhs instanceof ThisRef || rhs instanceof ParameterRef) {
						continue;
					}
					// remember array refs for later resolution
					if (lhs instanceof ArrayRef) {
						Value x = ((ArrayRef) lhs).getBase();
						if (x instanceof Local) {
							recordVarAtArrayRefWrite((Local) x, currentStmt);
						}
						continue;
					}
					if (rhs instanceof ArrayRef) {
						Value x = ((ArrayRef) rhs).getBase();
						if (x instanceof Local) {
							recordVarAtArrayRefRead((Local) x, currentStmt);
						}
						continue;
					}
					NNode nn_lhs = simpleNode(lhs), nn_rhs = simpleNode(rhs);
					// record for debugging purpose
					if (nn_rhs instanceof NAllocNode) {
						jimpleUtil.record(((NAllocNode) nn_rhs).e, currentStmt);
					}
					// create the flow edge
					if (nn_lhs != null && nn_rhs != null) {
						nn_rhs.addEdgeTo(nn_lhs, currentStmt);
						if (nn_rhs instanceof NAllocNode) {
							NAllocNode an = (NAllocNode) nn_rhs;
							// special treatment for "run" methods
							if (an.e instanceof NewExpr) {
								SootClass cl = ((NewExpr) an.e).getBaseType().getSootClass();
								if (cl.declaresMethod("void run()")) {
									SootMethod rn = cl.getMethod("void run()");
									try {
										// for some reason, soot may complain
										// the run() method does not have a
										// valid body.
										Local thisLocal = jimpleUtil.thisLocal(rn);
										an.addEdgeTo(varNode(thisLocal), currentStmt);
									} catch (RuntimeException e) {
										Logger.verb("WARNING", "Cannot resolve method: " + rn.getName());
									}
								}
							}
						}
					}
				} // all statements in the method body
			} // all methods in an application class
		} // all application classes
	}

	public void build() {
		buildIdNodes();
		processFrameworkManagedCallbacks();
		processApplicationClasses();

		// Additional manipulation (a.k.a, post-processing)

		// Resolve one-level array-refs. We may want to refine this if later we
		// find it necessary
		resolveArrayRefs();

		// Deal with recorded dialog and its builder calls
		// WARNING: the order of the following two calls cannot be changed!!!
		processAllRecordedDialogCalls();

		checkAndPatchRootlessActivities();
		checkAndPatchRootlessDialogs();

		// For each ListActivity, model its onListItemClick
		patchListActivity();

		// Deal with list views and list adapters
		processRecordedListViewCalls();

		// TabHost, TabSpec...
		processTabHostRelatedCalls();

		processFlowFromSetListenerToEventHandlers();
	}

	public boolean processFlowFromSetListenerToEventHandlers() {
		if (tasks.isEmpty()) {
			return false;
		}
		// SetListener to event handler flow
		for (FlowFromSetListenerToEventHandlers t : tasks) {
			processFlowFromSetListenerToEventHandlers(t.listenerClass, t.listenerParameterType, t.listenerNode,
					t.viewNode, t.setListener, t.s, t.caller, t.registration, t.isContextMenuSetListener);
		}
		tasks.clear();
		return true;
	}

	public Map<SootClass, NOptionsMenuNode> activityClassToOptionsMenu = Maps.newHashMap();

	/**
	 * Creates a NOptionsMenuNode node to be associated with the specified
	 * activity class. Returns the node immediately if it already exists.
	 *
	 * @param activityClass
	 *            specifies the owning activity
	 * @return a NOptionsMenuNode owned by the specified activity
	 */
	NOptionsMenuNode findOrCreateOptionsMenuNode(SootClass activityClass) {
		NOptionsMenuNode optionsMenuNode = activityClassToOptionsMenu.get(activityClass);
		if (optionsMenuNode == null) {
			optionsMenuNode = new NOptionsMenuNode();
			optionsMenuNode.ownerActivity = activityClass;
			activityClassToOptionsMenu.put(activityClass, optionsMenuNode);
			allNNodes.add(optionsMenuNode);
		}
		return optionsMenuNode;
	}

	// Arrays.
	Map<Local, Set<Stmt>> varsAtArrayRefRead = Maps.newHashMap();
	Map<Local, Set<Stmt>> varsAtArrayRefWrite = Maps.newHashMap();

	void resolveArrayRefs() {
		for (Expr e : allNAllocNodes.keySet()) {
			// Array allocations
			if (!(e instanceof NewArrayExpr || e instanceof NewMultiArrayExpr)) {
				continue;
			}
			if (e instanceof NewArrayExpr) {
				Type baseType = ((NewArrayExpr) e).getBaseType();
				if (baseType instanceof ArrayType) {
					continue;
				}
			}
			Set<Stmt> sources = Sets.newHashSet();
			Set<Stmt> targets = Sets.newHashSet();

			Set<NNode> reachables = graphUtil.reachableNodes(allNAllocNodes.get(e));
			for (NNode r : reachables) {
				if (!(r instanceof NVarNode)) {
					continue;
				}
				// Now, r is a base variable of some array ref
				NVarNode v = (NVarNode) r;
				if (varsAtArrayRefRead.containsKey(v.l)) {
					targets.addAll(varsAtArrayRefRead.get(v.l));
				}
				if (varsAtArrayRefWrite.containsKey(v.l)) {
					sources.addAll(varsAtArrayRefWrite.get(v.l));
				}
			}
			// Base variable one-level aliasing
			for (Stmt src : sources) {
				for (Stmt tgt : targets) {
					NNode sn = simpleNode(((AssignStmt) src).getRightOp());
					NNode tn = varNode(jimpleUtil.lhsLocal(tgt));
					if (sn != null && tn != null) {
						sn.addEdgeTo(tn, null);
					}
				}
			}
		}
	}

	void recordVarAtArrayRefRead(Local x, Stmt s) {
		Set<Stmt> z = varsAtArrayRefRead.get(x);
		if (z == null) {
			z = Sets.newHashSet();
			varsAtArrayRefRead.put(x, z);
		}
		z.add(s);
	}

	void recordVarAtArrayRefWrite(Local x, Stmt s) {
		Set<Stmt> z = varsAtArrayRefWrite.get(x);
		if (z == null) {
			z = Sets.newHashSet();
			varsAtArrayRefWrite.put(x, z);
		}
		z.add(s);
	}

	// Calls
	public void processFlowAtCall(Stmt caller, SootMethod callee) {
		// Check & filter
		InvokeExpr ie = caller.getInvokeExpr();
		if (!callee.getDeclaringClass().isApplicationClass()) {
			throw new RuntimeException();
		}
		if (!callee.isConcrete()) {
			return; // could happen for native methods
		}
		// Parameter binding
		Body b = callee.retrieveActiveBody();
		Iterator<Unit> stmts = b.getUnits().iterator();
		int num_param = callee.getParameterCount();
		if (!callee.isStatic()) {
			num_param++;
		}
		Local receiverLocal = null;
		for (int i = 0; i < num_param; i++) {
			// we have seen strange cases, in which method have empty bodies.
			if (!stmts.hasNext()) {
				return;
			}
			Stmt s = (Stmt) stmts.next();
			Value actual;
			if (ie instanceof InstanceInvokeExpr) {
				if (i == 0) {
					receiverLocal = jimpleUtil.receiver(ie);
					actual = receiverLocal;
				} else {
					actual = ie.getArg(i - 1);
				}
			} else {
				actual = ie.getArg(i);
			}

			// Here is an example where the method body does not read the formal
			// Param.
			// From <com.amazon.inapp.purchasing.PurchasingObserver: void
			// onContentDownloadResponse(com.amazon.inapp.purchasing.ContentDownloadResponse)>
			// in WSJ
			// $r0 = new java.lang.Error;
			// specialinvoke $r0.<java.lang.Error: void
			// <init>(java.lang.String)>("Unresolved compilation error: Method
			// <com.amazon.inapp.purchasing.PurchasingObserver: void
			// onContentDownloadResponse(com.amazon.inapp.purchasing.ContentDownloadResponse)>
			// does not exist!");
			// throw $r0;
			if (!(s instanceof DefinitionStmt)) {
				return;
			}

			Local formal = jimpleUtil.lhsLocal(s);
			if (!jimpleUtil.interesting(formal.getType())) {
				continue;
			}
			NVarNode lhsNode = varNode(formal);
			NNode rhsNode = simpleNode(actual);
			if (rhsNode != null) {
				rhsNode.addEdgeTo(lhsNode, caller);
			}
		}

		// Now, do something for the return
		if (caller instanceof InvokeStmt) {
			return; // no ret val
		}
		Local lhs_at_call = jimpleUtil.lhsLocal(caller);
		if (!jimpleUtil.interesting(lhs_at_call.getType())) {
			return;
		}
		NNode lhsNode = varNode(lhs_at_call);
		while (stmts.hasNext()) {
			Stmt d = (Stmt) stmts.next();
			if (!(d instanceof ReturnStmt)) {
				continue;
			}
			Value retval = ((ReturnStmt) d).getOp();
			NNode returnValueNode = simpleNode(retval);
			if (returnValueNode != null) {
				returnValueNode.addEdgeTo(lhsNode, caller);
			}
		}
	}

	// Op Nodes
	public NOpNode createOpNode(Stmt s) {
		// Inflate1: view = inflater.inflate(id)
		{
			NOpNode inflate1 = createInflate1OpNode(s);
			if (inflate1 != null) {
				return inflate1;
			}
		}

		// Inflate2: act.setContentView(id)
		{
			NOpNode inflate2 = createInflate2OpNode(s);
			if (inflate2 != null) {
				return inflate2;
			}
		}

		// FindView1: view.findViewById(id)
		{
			NOpNode findView1 = createFindView1OpNode(s);
			if (findView1 != null) {
				return findView1;
			}
		}
		// FindView2: act.findViewById(id)
		{
			NOpNode findView2 = createFindView2OpNode(s);
			if (findView2 != null) {
				return findView2;
			}
		}

		// FindView3: lhs = view.m()
		{
			NOpNode findView3 = createFindView3OpNode(s);
			if (findView3 != null) {
				return findView3;
			}
		}

		// AddView1: act.setContentView(view)
		{
			NOpNode addView1 = createAddView1OpNode(s);
			if (addView1 != null) {
				return addView1;
			}
		}

		// AddView2: parent.addView(child)
		{
			NOpNode addView2 = createAddView2OpNode(s);
			if (addView2 != null) {
				return addView2;
			}
		}

		// SetId: view.setId(id)
		{
			NOpNode setId = createSetIdOpNode(s);
			if (setId != null) {
				return setId;
			}
		}

		// SetImageResource: view.setImageResource(id)
		{
			NOpNode setImageResource = createSetImageResourceOpNode(s);
			if (setImageResource != null) {
				return setImageResource;
			}
		}

		// SetListener: view.setXYZListener(listener)
		{
			NOpNode setListener = createSetListenerOpNode(s);
			if (setListener != null) {
				return setListener;
			}
		}

		// AddMenuItem: menuItem = menu.add(...)
		{
			NOpNode addMenuItem = createAddMenuItemOpNode(s);
			if (addMenuItem != null) {
				return addMenuItem;
			}
		}

		// MenuItemSetTitle: menuItem.setTitle(titleSpecifier)
		{
			NOpNode menuItemSetTitle = createMenuItemSetTitleOpNode(s);
			if (menuItemSetTitle != null) {
				return menuItemSetTitle;
			}
		}

		// MenuInflate: menuInflater.inflate(menuId, menu)
		{
			NOpNode menuInflate = createMenuInflateOpNode(s);
			if (menuInflate != null) {
				return menuInflate;
			}
		}

		// GetTabHost: TabActivity.getTabHost()
		{
			NOpNode getTabHost = createGetTabHostOpNode(s);
			if (getTabHost != null) {
				return getTabHost;
			}
		}

		// GetListView: lhs = act.getListView()
		{
			NOpNode getListView = createListActivityGetListViewOpNode(s);
			if (getListView != null) {
				return getListView;
			}
		}

		// GetSensorObject: lhs = SensorManager.getDefaultSensor
		{
			NOpNode getSensor = createSensorObjectOpNode(s);
			if (getSensor != null) {
				return getSensor;
			}
		}

		// SensorManager.registerListener(...)
		{
			NOpNode registerListener = null;
			if (registerListener != null) {
				return registerListener;
			}
		}

		return null;
	}

	private NOpNode createSetImageResourceOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();

		if (!subsig.equals(setImageSubSig)) {
			return null;
		}
		Local rcv = jimpleUtil.receiver(ie);
		SootClass rcvClass = ((RefType) rcv.getType()).getSootClass();
		if (!hier.viewClasses.contains(rcvClass)) {
			return null;
		}
		NVarNode receiverNode = varNode(rcv);

		Value viewId = ie.getArg(0);
		NNode idNode = simpleNode(viewId);
		if (idNode == null) {
			if (Configs.verbose) {
				System.out.println(
						"[WARNING] Unknown view id node for SetId: '" + s + " @ " + jimpleUtil.lookup(s) + "'");
			}
			return null;
		}

		NOpNode setImageResource = new NSetImageResourceOpNode(idNode, receiverNode,
				new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)), false);

		return setImageResource;
	}

	// Inflate1: view = inflater.inflate(id)
	// Inflate1.pred[0]: layout id node
	// Inflate1.succ[0]: lhs node
	public NOpNode createInflate1OpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String sig = callee.getSignature();
		Value layoutIdVal = null;
		if (sig.equals(layoutInflaterInflate) || sig.equals(layoutInflaterInflateBool)) {
			layoutIdVal = ie.getArg(0);
		} else if (sig.equals(viewCtxInflate)) {
			layoutIdVal = ie.getArg(1);
		}
		if (layoutIdVal == null) {
			return null;
		}
		if (ignoreLayoutIdCall(layoutIdVal)) {
			return null;
		}

		// Retrieve "outside root"
		Value outsideRootVal = null;
		if (sig.equals(layoutInflaterInflate)) {
			outsideRootVal = ie.getArg(1);
		} else if (sig.equals(layoutInflaterInflateBool)) {
			Value last = ie.getArg(2);
			if (!(last instanceof IntConstant)) {
				// throw new RuntimeException();
				return null;
			}
			if (((IntConstant) last).value != 0) {
				outsideRootVal = ie.getArg(1);
			}
		} else if (sig.equals(viewCtxInflate)) {
			outsideRootVal = ie.getArg(2);
		}
		Local outsideRoot = null;
		if (outsideRootVal instanceof Local) {
			outsideRoot = (Local) outsideRootVal;
		}

		// Prep is done, now create nodes and edges
		SootMethod caller = jimpleUtil.lookup(s);
		Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
		NOpNode inflate1 = null;
		NNode layoutIdNode = simpleNode(layoutIdVal);
		if (layoutIdNode == null) {
			System.out.println("[WARNING] Null layout id for " + s + " @ " + caller);
			System.out.println("  layoutIdVal: " + layoutIdVal);
			return null;
		}

		// TODO(tony): do we care about outside root when the return value is
		// not
		// assigned to lhs? Right now, assume it's ok to ignore.
		NVarNode lhsNode = (s instanceof DefinitionStmt ? varNode(jimpleUtil.lhsLocal(s)) : null);
		if (outsideRoot == null) {
			if (lhsNode != null) {
				inflate1 = new NInflate1OpNode(layoutIdNode, lhsNode, callSite, false);
			}
		} else {
			// lhs = inflater.inflate(id, outside) ==>
			// fakeLocal = inflater.inflate(id); outside.addView(fakeLocal); lhs
			// = fakeLocal
			String fakeLocalName = nextFakeName();
			Local fakeLocal = Jimple.v().newLocal(fakeLocalName, Scene.v().getSootClass("android.view.View").getType());
			NVarNode fakeLocalNode = varNode(fakeLocal); // child
			NVarNode outsideRootNode = varNode(outsideRoot); // parent
			inflate1 = new NInflate1OpNode(layoutIdNode, fakeLocalNode, callSite, false);
			NOpNode addView2 = new NAddView2OpNode(outsideRootNode, fakeLocalNode,
					new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)), false);
			allNNodes.add(addView2);
			if (Configs.debugCodes.contains(Debug.OP_NODE_DEBUG)) {
				System.out.println(
						"[AddView2] parent: " + outsideRootNode + ", child: " + fakeLocalNode + " @ " + addView2);
				System.out.println("[Inflate1] lhs: " + inflate1.getLhs());
			}
			if (lhsNode != null) {
				fakeLocalNode.addEdgeTo(lhsNode, s);
			}
		}

		if (Configs.debugCodes.contains(Debug.OP_NODE_DEBUG)) {
			System.out.printf("[Inflate1] id: %s, lhs: %s, outside: %s @ %s\n", layoutIdNode.toString(),
					(lhsNode == null ? "<NULL>" : lhsNode.toString()),
					(outsideRoot == null ? "<NULL>" : varNode(outsideRoot).toString()), inflate1.shortDescription());
			System.out.println("  stmt: " + s + " @ " + jimpleUtil.lookup(s));
		}

		return inflate1;
	}

	// Inflate2: act/dialog.setContentView(id)
	public NInflate2OpNode createInflate2OpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		SootClass c = callee.getDeclaringClass();
		String subsig = callee.getSubSignature();
		if (!(subsig.equals(setContentViewSubSig))) {
			return null;
		}
		boolean isActivity = hier.libActivityClasses.contains(c) || hier.applicationActivityClasses.contains(c);
		boolean isDialog = hier.libraryDialogClasses.contains(c) || hier.applicationDialogClasses.contains(c);
		if (!isActivity && !isDialog) {
			return null;
		}

		Value layoutIdVal = ie.getArg(0);
		if (ignoreLayoutIdCall(layoutIdVal)) {
			return null;
		}
		NNode layoutIdNode = simpleNode(layoutIdVal);
		NVarNode receiverNode = varNode(jimpleUtil.receiver(ie));
		try {
			NInflate2OpNode inflate2 = new NInflate2OpNode(layoutIdNode, receiverNode,
					new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)), false);
			return inflate2;
		} catch (Exception ex) {
			Logger.verb("ERROR", "layoutIdNode : " + layoutIdVal.toString() + " not found");
			ex.printStackTrace();

			return null;
		}
	}

	// FindView1: lhs = view.findViewById(id)
	public NOpNode createFindView1OpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();

		boolean viewFindView = subsig.equals(findViewByIdSubSig);
		boolean getTabWidget = subsig.equals("android.widget.TabWidget getTabWidget()");
		boolean getTabContentView = subsig.equals("android.widget.FrameLayout getTabContentView()");
		boolean menuFindItem = subsig.equals(menuFindItemSubSig);
		if (!viewFindView && !getTabWidget && !getTabContentView && !menuFindItem) {
			return null;
		}
		// FIXME(tony): this is a temp hack to prevent exceptions
		if (getTabWidget || getTabContentView) {
			return null;
		}
		Local rcv = jimpleUtil.receiver(ie);
		SootClass receiverClass = ((RefType) rcv.getType()).getSootClass();
		if (viewFindView && !hier.viewClasses.contains(receiverClass)) {
			return null;
		}
		if (menuFindItem && !hier.menuClasses.contains(receiverClass)) {
			return null;
		}
		Value widgetIdVal = null;
		if (viewFindView) {
			widgetIdVal = ie.getArg(0);
		} else if (menuFindItem) {
			widgetIdVal = ie.getArg(0);
		} else if (getTabWidget) {
			Integer tabWidgetId = xmlUtil.getSystemRIdValue("tabs");
			if (tabWidgetId == null) {
				throw new RuntimeException("[Error]: we can not identify the id for \"tabs\"");
			}
			widgetIdVal = IntConstant.v(tabWidgetId.intValue());
		} else if (getTabContentView) {
			Integer tabContentId = xmlUtil.getSystemRIdValue("tabcontent");
			if (tabContentId == null) {
				throw new RuntimeException("[Error]: we can not identify the id for \"tabcontent\"");
			}
			widgetIdVal = IntConstant.v(tabContentId.intValue());
		}
		NNode widgetIdNode = simpleNode(widgetIdVal);
		if (widgetIdNode == null && menuFindItem && widgetIdVal instanceof IntConstant) {
			widgetIdNode = anonymousWidgetIdNode(((IntConstant) widgetIdVal).value);
		}

		if (widgetIdNode == null) {
			if (Configs.verbose) {
				System.out.println(
						"[WARNING] Unknown widget id for FindView1: '" + s + " @ " + jimpleUtil.lookup(s) + "'");
			}
			return null;
		}
		NVarNode receiverNode = varNode(rcv);

		// Here is the example from walmart app;
		// In public void
		// onSuccessSameThread(com.walmart.android.data.StoreItem)
		// The body is
		// {
		// com.walmart.android.app.qr.QRProductPagePresenter$7 r0;
		// com.walmart.android.data.StoreItem r1;
		// com.walmart.android.app.qr.QRProductPagePresenter $r2, $r5;
		// android.view.View $r3, $r6;
		// java.lang.String $r4;

		// r0 := @this: com.walmart.android.app.qr.QRProductPagePresenter$7;
		// r1 := @parameter0: com.walmart.android.data.StoreItem;
		// $r2 = r0.<com.walmart.android.app.qr.QRProductPagePresenter$7:
		// com.walmart.android.app.qr.QRProductPagePresenter this$0>;
		// $r3 = staticinvoke
		// <com.walmart.android.app.qr.QRProductPagePresenter: android.view.View
		// access$1000(com.walmart.android.app.qr.QRProductPagePresenter)>($r2);
		// $r4 = virtualinvoke r1.<com.walmart.android.data.StoreItem:
		// java.lang.String getName()>();
		// staticinvoke <com.walmart.android.utils.ViewUtil: void
		// setText(int,android.view.View,java.lang.CharSequence)>(2131231309,
		// $r3, $r4);
		// $r5 = r0.<com.walmart.android.app.qr.QRProductPagePresenter$7:
		// com.walmart.android.app.qr.QRProductPagePresenter this$0>;
		// $r6 = staticinvoke
		// <com.walmart.android.app.qr.QRProductPagePresenter: android.view.View
		// access$1000(com.walmart.android.app.qr.QRProductPagePresenter)>($r5);
		// virtualinvoke $r6.<android.view.View: android.view.View
		// findViewById(int)>(2131231309);
		// return;
		// }
		if (!(s instanceof DefinitionStmt)) {
			return null;
		}

		NVarNode lhsNode = varNode(jimpleUtil.lhsLocal(s));
		NOpNode findView1 = new NFindView1OpNode(widgetIdNode, receiverNode, lhsNode,
				new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)),
				menuFindItem ? FindView1Type.MenuFindItem : FindView1Type.Ordinary, false);

		if (Configs.debugCodes.contains(Debug.OP_NODE_DEBUG)) {
			System.out.println("[FindView1] view: " + receiverNode + ", id: " + widgetIdNode + ", lhs: " + lhsNode
					+ " @ " + findView1.shortDescription());
			System.out.println("  stmt: " + s + " @ " + jimpleUtil.lookup(s));
		}

		return findView1;
	}

	// FindView2: lhs = act.findViewById(id)
	public NOpNode createFindView2OpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();

		if (!subsig.equals(findViewByIdSubSig)) {
			return null;
		}
		Local receiver = jimpleUtil.receiver(ie);
		SootClass receiverClass = ((RefType) receiver.getType()).getSootClass();

		boolean isActivity = hier.libActivityClasses.contains(receiverClass)
				|| hier.applicationActivityClasses.contains(receiverClass);
		boolean isDialog = hier.libraryDialogClasses.contains(receiverClass)
				|| hier.applicationDialogClasses.contains(receiverClass);
		if (!isActivity && !isDialog) {
			return null;
		}
		// It is possible that the return value of FindViewById is not saved
		// In this case return null
		if (!(s instanceof DefinitionStmt)) {
			return null;
		}
		SootMethod caller = jimpleUtil.lookup(s);
		NVarNode receiverNode = varNode(receiver);
		NVarNode lhsNode = varNode(jimpleUtil.lhsLocal(s));
		Value layoutIdVal = ie.getArg(0);
		NNode layoutIdNode = simpleNode(layoutIdVal);
		if (layoutIdNode == null) {
			System.out.println("[WARNING] Null layout id for " + s + " @ " + caller);
			return NOpNode.NullNode;
		}
		NOpNode findView2 = new NFindView2OpNode(layoutIdNode, receiverNode, lhsNode,
				new Pair<Stmt, SootMethod>(s, caller), false);

		if (Configs.debugCodes.contains(Debug.OP_NODE_DEBUG)) {
			System.out.println("[FindView2] id: " + layoutIdNode + ", act: " + receiverNode + ", lhs: " + lhsNode
					+ " @ " + findView2.shortDescription());
			System.out.println("  stmt: " + s + " @ " + jimpleUtil.lookup(s));
		}

		return findView2;
	}

	// FindView3: lhs = view.findSomething()
	public NOpNode createFindView3OpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		SootClass c = callee.getDeclaringClass();
		String subsig = callee.getSubSignature();

		boolean viewType = hier.viewClasses.contains(c);
		boolean menuType = hier.menuClasses.contains(c);
		if (!viewType && !menuType) {
			return null;
		}
		// TODO(tony): refactor the following hard-coded method subsigs.
		// Type 1: returns all descendants, including root
		boolean match1 = subsig.equals("android.view.View findFocus()")
				|| subsig.equals("android.view.View findViewWithTag()")
				|| subsig.equals("android.view.View focusSearch(int)") ||
				// NOTE: View has a methods getFocusables which returns an
				// ArrayList of views. Do not handle this right now.
				subsig.equals("android.view.View focusSearch(int)")
				|| subsig.equals("android.view.View focusSearch(android.view.View,int)");

		// Type 2: returns all (immediate) children
		boolean match2 = subsig.equals("android.view.View getChildAt(int)")
				|| subsig.equals("android.view.View getFocusedChild(int)")
				|| subsig.equals("android.view.View getCurrentView()")
				|| subsig.equals("android.view.View getSelectedView()") || subsig.equals(menuGetItemSubSig);

		// Type 3: returns all descendants, excluding root.
		// For Menu.findItem(), non MenuItem type nodes should not
		// be returned, which will be handled in the solver.
		boolean match3 = subsig.equals(menuFindItemSubSig);

		NFindView3OpNode.FindView3Type type;
		if (match1) {
			type = NFindView3OpNode.FindView3Type.FindDescendantsAndSelf;
		} else if (match2) {
			type = NFindView3OpNode.FindView3Type.FindChildren;
		} else if (match3) {
			Value itemId = ie.getArg(0);
			if (itemId instanceof IntConstant) {
				return NOpNode.NullNode;
			}
			type = NFindView3OpNode.FindView3Type.FindDescendantsNoSelf;
		} else {
			return null;
		}
		// It is possible that this statement is not a definition statement, as
		// seen in com.gelakinetic.mtgfam_41.apk
		// If it is not, return null
		if (!(s instanceof DefinitionStmt)) {
			return null;
		}
		NVarNode receiverNode = varNode(jimpleUtil.receiver(ie));
		NNode lhsNode = varNode(jimpleUtil.lhsLocal(s));
		NOpNode findView3 = new NFindView3OpNode(receiverNode, lhsNode,
				new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)), type, false);

		if (Configs.debugCodes.contains(Debug.OP_NODE_DEBUG)) {
			System.out.println(
					"[FindView3] view: " + receiverNode + ", lhs: " + lhsNode + " @ " + findView3.shortDescription());
			System.out.println("  stmt: " + s + " @ " + jimpleUtil.lookup(s));
		}

		return findView3;
	}

	// AddView1: act/dialog.setContentView(view)
	public NAddView1OpNode createAddView1OpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();

		if (!subsig.equals(setContentViewViewSubSig) && !subsig.equals(setContentViewViewParaSubSig)) {
			return null;
		}

		Local receiver = jimpleUtil.receiver(ie);
		SootClass receiverClass = ((RefType) receiver.getType()).getSootClass();

		boolean isActivity = hier.libActivityClasses.contains(receiverClass)
				|| hier.applicationActivityClasses.contains(receiverClass);
		boolean isDialog = hier.libraryDialogClasses.contains(receiverClass)
				|| hier.applicationDialogClasses.contains(receiverClass);
		if (!isActivity && !isDialog) {
			return null;
		}

		NVarNode receiverNode = varNode(receiver);
		NVarNode parameterNode = varNode((Local) ie.getArg(0));
		NAddView1OpNode addView1 = new NAddView1OpNode(parameterNode, receiverNode,
				new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)), false);

		return addView1;
	}

	// AddView2: parent.addView(child)
	public NAddView2OpNode createAddView2OpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		SootClass c = callee.getDeclaringClass();
		String name = callee.getName();
		boolean match = name.equals(addViewName) && hier.viewClasses.contains(c);
		if (!match) {
			return null;
		}

		SootMethod caller = jimpleUtil.lookup(s);
		NVarNode parentNode = varNode(jimpleUtil.receiver(ie));
		NVarNode childNode = varNode((Local) ie.getArg(0));
		return new NAddView2OpNode(parentNode, childNode, new Pair<Stmt, SootMethod>(s, caller), false);
	}

	// SetId: view.setId(id)
	public NOpNode createSetIdOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();

		if (!subsig.equals(setIdSubSig)) {
			return null;
		}
		Local rcv = jimpleUtil.receiver(ie);
		SootClass rcvClass = ((RefType) rcv.getType()).getSootClass();
		if (!hier.viewClasses.contains(rcvClass)) {
			return null;
		}
		NVarNode receiverNode = varNode(rcv);

		Value viewId = ie.getArg(0);
		NNode idNode = simpleNode(viewId);
		if (idNode == null) {
			if (Configs.verbose) {
				System.out.println(
						"[WARNING] Unknown view id node for SetId: '" + s + " @ " + jimpleUtil.lookup(s) + "'");
			}
			return null;
		}
		NOpNode setId = new NSetIdOpNode(idNode, receiverNode, new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)),
				false);

		return setId;
	}

	// SetListener: view.setXYZListener(listenerObject)
	public NOpNode createSetListenerOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();

		String subsig = callee.getSubSignature();
		SootMethod caller = jimpleUtil.lookup(s);

		boolean isRegisterForContextMenu = subsig.equals(registerForContextMenuSubSig);
		ListenerRegistration registration = listenerSpecs.getListenerRegistration(s);
		if (registration == null && !isRegisterForContextMenu) {
			return null;
		}

		int listenerPosition = ListenerSpecification.UNKNOWN_POSITION;
		Value parameterValue;
		if (isRegisterForContextMenu) {
			parameterValue = ie.getArg(0);
		} else {
			listenerPosition = registration.position;
			if (listenerPosition == ListenerSpecification.UNKNOWN_POSITION) {
				System.out.println("[WARNING] Something is wrong for listenerPosition at " + s + " @ " + caller);
				return null;
			}

			parameterValue = ie.getArg(listenerPosition);
			if (parameterValue instanceof NullConstant) {
				return null;
			}
			if (!(parameterValue instanceof Local)) {
				throw new RuntimeException();
			}
		}

		String name = callee.getName();
		boolean isContextMenuSetListener = name.equals(setOnCreateContextMenuListenerName) || isRegisterForContextMenu;
		SootClass listenerParameterType;
		if (isContextMenuSetListener) {
			listenerParameterType = Scene.v().getSootClass("android.view.View$OnCreateContextMenuListener");
		} else {
			listenerParameterType = ((RefType) callee.getParameterType(listenerPosition)).getSootClass();
		}
		// Only framework-defined listener types are considered
		if (listenerParameterType.isApplicationClass()) {
			return null;
		}

		// filters done, let's get to business
		Local viewLocal;
		Local listenerLocal;
		if (isRegisterForContextMenu) {
			viewLocal = (Local) parameterValue;
			listenerLocal = jimpleUtil.receiver(ie);
		} else {
			viewLocal = jimpleUtil.receiver(ie);
			listenerLocal = (Local) parameterValue;
		}

		SootClass viewType = ((RefType) viewLocal.getType()).getSootClass();
		// NOTE(tony): right now, we record MenuItem classes separately from
		// other
		// View classes. We may merge them in future revisions.
		if (!hier.viewClasses.contains(viewType) && !hier.menuItemClasses.contains(viewType)) {
			return null;
		}

		Type listenerType = listenerLocal.getType();
		if (!(listenerType instanceof RefType)) {
			return null;
		}
		SootClass listenerClass = ((RefType) listenerType).getSootClass();
		// preliminary filtering
		if (!listenerSpecs.isListenerType(listenerClass)) {
			return null;
		}

		// We can process onCreateContextMenu at this time.
		// TODO(tony): this actually is not perfect. Rethink this at some point.
		boolean shouldProcessFlow = isContextMenuSetListener;

		return createSetListenerAndProcessFlow(viewLocal, listenerLocal, s, caller, isContextMenuSetListener,
				registration, listenerClass, listenerParameterType, shouldProcessFlow, null, false);
	}

	Set<NSetListenerOpNode> alreadyProcessedSetListeners = Sets.newHashSet();
	Map<NSetListenerOpNode, Set<Integer>> processedSetListenerAndHashes = Maps.newHashMap();

	boolean processSetListenerOpNode(NSetListenerOpNode opNode, NObjectNode viewObject, NObjectNode listenerObject) {
		if (alreadyProcessedSetListeners.contains(opNode)) {
			return false;
		}
		// Check if already processed. If so, abort and return. Otherwise,
		// continue.
		int hash = Objects.hashCode(viewObject, listenerObject);
		Set<Integer> setListenerHashes = processedSetListenerAndHashes.get(opNode);
		if (setListenerHashes == null) {
			setListenerHashes = Sets.newHashSet();
			processedSetListenerAndHashes.put(opNode, setListenerHashes);
		}
		if (setListenerHashes.contains(hash)) {
			return false;
		}
		setListenerHashes.add(hash);

		// Ignore context menu because it has been processed.
		if (viewObject instanceof NContextMenuNode) {
			return false;
		}

		// Find the ListenerRegistration from the SetListener statement
		SootClass listenerClass = listenerObject.getClassType();
		Pair<Stmt, SootMethod> callSite = opNode.callSite;
		Stmt s = callSite.getO1();
		SootMethod caller = callSite.getO2();

		ListenerRegistration registration = listenerSpecs.getListenerRegistration(s);
		if (registration == null) {
			throw new RuntimeException("Unexpected SetListener stmt: " + s + " @ " + caller);
		}

		// Resolve event handlers: first get prototype, and then do virtual
		// dispatch
		Set<SootMethod> handlerPrototypes = registration.getHandlerPrototypes();
		Set<SootMethod> handlers = Sets.newHashSet();
		computeConcreteHandlers(handlers, handlerPrototypes, Collections.singleton(listenerClass));
		// regToEventHandlers.put(s, handlers);
		regToEventHandlers.putAll(s, handlers);
		// For each dispatched handler, connect the flow.
		for (SootMethod h : handlers) {
			// this := listenerObject
			listenerObject.addEdgeTo(varNode(jimpleUtil.thisLocal(h)), s);

			// Find the view parameter in the event handler, and then do:
			// viewPara := viewObject
			String handlerSubsig = h.getSubSignature();
			int parameterPositionForView = listenerSpecs.getViewPositionInHandler(handlerSubsig);
			int localVariableIndexForViewParameter = parameterPositionForView + 1;

			if (parameterPositionForView == ListenerSpecification.UNKNOWN_POSITION) {
				continue;
			}

			Local viewPara = jimpleUtil.localForNthParameter(h, localVariableIndexForViewParameter);
			Type viewParaType = viewPara.getType();
			boolean valid = (viewParaType instanceof RefType);
			if (valid) {
				SootClass viewParaClass = ((RefType) viewParaType).getSootClass();
				valid = hier.isViewClass(viewParaClass)
						|| hier.isSubclassOf(viewParaClass, Scene.v().getSootClass("android.view.MenuItem"));
			}
			if (!valid) {
				System.out.println("[WARNING] Cannot find View parameter for " + h.getSignature() + ", listenerObject: "
						+ listenerObject + ", viewObject: " + viewObject + ", reason: " + s + " in " + caller);
				continue;
			}
			viewObject.addEdgeTo(varNode(viewPara), s);
		}

		return true;
	}

	NSetListenerOpNode createSetListenerAndProcessFlow(Local viewLocal, Local listenerLocal, Stmt s, SootMethod caller,
			boolean isContextMenuSetListener, ListenerRegistration registration, SootClass listenerClass,
			SootClass listenerParameterType, boolean shouldProcessFlow, EventType eventType, /* optional */
			boolean artificial) {
		// Create SetListener op node
		NVarNode viewNode = varNode(viewLocal);
		NVarNode listenerNode = varNode(listenerLocal);
		Pair<Stmt, SootMethod> callSite = (s == null ? null : new Pair<Stmt, SootMethod>(s, caller));
		NSetListenerOpNode setListener = new NSetListenerOpNode(viewNode, listenerNode, callSite,
				isContextMenuSetListener, artificial);

		// Save event type
		if (isContextMenuSetListener) {
			listenerSpecs.saveRegAndEvents(s, EventType.implicit_create_context_menu);
		} else {
			if (registration == null) {
				if (eventType == null) {
					throw new RuntimeException("Event type unknown for " + s + " @ " + caller);
				} else {
					listenerSpecs.saveRegAndEvents(s, eventType);
				}
			} else {
				listenerSpecs.saveRegAndEvents(s, registration.eventType);
			}
		}

		// From SetListener to callback
		if (shouldProcessFlow) {
			alreadyProcessedSetListeners.add(setListener);
			processFlowFromSetListenerToEventHandlersInFuture(listenerClass, listenerParameterType, listenerNode,
					viewNode, setListener, s, caller, registration, isContextMenuSetListener);
		}

		if (Configs.debugCodes.contains(Debug.OP_NODE_DEBUG) || Configs.debugCodes.contains(Debug.LISTENER_DEBUG)) {
			System.out.printf("[SetListener] view: %s, listener: %s @ %s\n  stmt: %s @ %s\n", viewNode, listenerNode,
					setListener.shortDescription(), callSite.getO1(), callSite.getO2());
		}
		return setListener;
	}

	Set<FlowFromSetListenerToEventHandlers> tasks = Sets.newHashSet();

	class FlowFromSetListenerToEventHandlers {
		SootClass listenerClass;
		SootClass listenerParameterType;
		NVarNode listenerNode;
		NVarNode viewNode;
		NSetListenerOpNode setListener;
		Stmt s;
		SootMethod caller;
		ListenerRegistration registration;
		boolean isContextMenuSetListener;

		FlowFromSetListenerToEventHandlers(SootClass listenerClass, SootClass listenerParameterType,
				NVarNode listenerNode, NVarNode viewNode, NSetListenerOpNode setListener, Stmt s, SootMethod caller,
				ListenerRegistration registration, boolean isContextMenuSetListener) {
			this.listenerClass = listenerClass;
			this.listenerParameterType = listenerParameterType;
			this.listenerNode = listenerNode;
			this.viewNode = viewNode;
			this.setListener = setListener;
			this.s = s;
			this.caller = caller;
			this.registration = registration;
			this.isContextMenuSetListener = isContextMenuSetListener;
		}
	}

	void processFlowFromSetListenerToEventHandlersInFuture(SootClass listenerClass, SootClass listenerParameterType,
			NVarNode listenerNode, NVarNode viewNode, NSetListenerOpNode setListener, Stmt s, SootMethod caller,
			ListenerRegistration registration, boolean isContextMenuSetListener) {
		FlowFromSetListenerToEventHandlers t = new FlowFromSetListenerToEventHandlers(listenerClass,
				listenerParameterType, listenerNode, viewNode, setListener, s, caller, registration,
				isContextMenuSetListener);
		tasks.add(t);
	}

	// Process flow from SetListener to corresponding callback methods via
	// some unknown framework code.
	void processFlowFromSetListenerToEventHandlers(SootClass listenerClass, SootClass listenerParameterType,
			NVarNode listenerNode, NVarNode viewNode, NSetListenerOpNode setListener, Stmt s, SootMethod caller,
			ListenerRegistration registration, boolean isContextMenuSetListener) {
		// First, find the method prototypes in listener interface. Listener
		// interface is the parameter type of the SetListener call.
		Set<SootMethod> handlerPrototypes;
		if (isContextMenuSetListener || registration == null) {
			handlerPrototypes = jimpleUtil.getMethodsInInterface(listenerParameterType);
		} else {
			handlerPrototypes = registration.getHandlerPrototypes();
		}

		// Some listeners may be "inflated" because they are views as well. So,
		// we
		// cannot use graph reachability to find concrete type of listener.
		// Instead,
		// we should use class hierarchy.
		// Then, for each concrete actual type, do virtual dispatch for the
		// method
		// prototypes found in first step.
		Set<SootMethod> handlers = Sets.newHashSet();
		computeConcreteHandlers(handlers, handlerPrototypes, listenerNode);
		// regToEventHandlers.put(s, handlers);
		regToEventHandlers.putAll(s, handlers);
		// Finally, create flow edges to represent the link between SetListener
		// and the dispatched callback methods.
		for (SootMethod h : handlers) {
			if (Configs.debugCodes.contains(Debug.LISTENER_DEBUG)) {
				System.out.println("{SL->CB} " + setListener + " ===> " + h);
			}
			listenerNode.addEdgeTo(varNode(jimpleUtil.thisLocal(h)), s);

			String handlerSubsig = h.getSubSignature();
			Local viewPara;
			if (isContextMenuSetListener) {
				// For view binding
				viewPara = jimpleUtil.localForNthParameter(h, 2);

				// Get corresponding context menu node
				NContextMenuNode contextMenuNode = findOrCreateContextMenuNode(h, viewNode);

				// Now, from onCreateContextMenu() to onContextItemSelected and
				// onMenuItemSelected.
				connectToFakeContextMenuVarNode(contextMenuNode, h);
			} else { // non-special
				int parameterPositionForView = listenerSpecs.getViewPositionInHandler(handlerSubsig);
				int localVariableIndexForViewParameter = parameterPositionForView + 1;

				if (parameterPositionForView == ListenerSpecification.UNKNOWN_POSITION) {
					continue;
				}

				viewPara = jimpleUtil.localForNthParameter(h, localVariableIndexForViewParameter);
			}
			Type viewParaType = viewPara.getType();
			boolean valid = (viewParaType instanceof RefType);
			if (valid) {
				SootClass viewParaClass = ((RefType) viewParaType).getSootClass();
				valid = hier.isViewClass(viewParaClass)
						|| hier.isSubclassOf(viewParaClass, Scene.v().getSootClass("android.view.MenuItem"));
			}
			if (!valid) {
				System.out.println("[WARNING] Cannot find View parameter for " + h.getSignature() + ", listenerClass: "
						+ listenerClass + ", listenerParameter: " + listenerParameterType + ", reason: " + s + " in "
						+ caller);
				System.out.println("  viewParaType: " + viewParaType);
				continue;
			}
			viewNode.addEdgeTo(varNode(viewPara), s);
		}
	}

	void computeConcreteHandlers(Set<SootMethod> handlers, Set<SootMethod> handlerPrototypes, NVarNode listenerNode) {
		Set<SootClass> listenerTypes = computePossibleListenerTypesCHA(listenerNode);
		computeConcreteHandlers(handlers, handlerPrototypes, listenerTypes);
	}

	void computeConcreteHandlers(Set<SootMethod> handlers, Set<SootMethod> handlerPrototypes,
			Set<SootClass> listenerTypes) {
		for (SootClass possibleListenerType : listenerTypes) {
			for (SootMethod prototype : handlerPrototypes) {
				String prototypeSubsig = prototype.getSubSignature();
				SootClass matchedClass = hier.matchForVirtualDispatch(prototypeSubsig, possibleListenerType);
				if (matchedClass != null && matchedClass.isApplicationClass()
						&& listenerSpecs.isListenerType(matchedClass)) {
					SootMethod h = matchedClass.getMethod(prototypeSubsig);
					if (h.isConcrete()) {
						handlers.add(h);
					}
				}
			}
		}
	}

	Set<SootClass> computePossibleListenerTypesCHA(NVarNode listenerNode) {
		SootClass declaredListenerType = ((RefType) listenerNode.l.getType()).getSootClass();
		return Collections.unmodifiableSet(hier.getSubtypes(declaredListenerType));
	}

	Set<SootClass> computePossibleListenerTypesPTA(NVarNode listenerNode) {
		Set<SootClass> types = Sets.newHashSet();
		Set<NNode> listenerObjects = graphUtil.backwardReachableNodes(listenerNode);
		for (NNode n : listenerObjects) {
			if (!(n instanceof NObjectNode)) {
				continue;
			}
			NObjectNode obj = (NObjectNode) n;
			types.add(obj.getClassType());
		}
		return types;
	}

	// AddMenuItem: menuItem = menu.add([...,]titleSpecifier)
	// "instrument" ->
	// menuItem = InflNode(MenuItem)
	// AddView2: menu.addView(menuItem)
	// SetText: menuItem.setTitle(titleSpecifier)
	//
	// titleSepcifier is the last parameter in the original call. it could be
	// either a string id or a string constant.
	public NOpNode createAddMenuItemOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();
		boolean isMenuAdd = menuAddCharSeqSubSig.equals(subsig) || menuAddIntSubSig.equals(subsig)
				|| menuAdd4IntSubSig.equals(subsig) || menuAdd3IntCharSeqSubSig.equals(subsig);
		if (!isMenuAdd) {
			return null;
		}

		// menuItem = InflNode(MenuItem)
		NInflNode inflMenuItem = inflNode(Scene.v().getSootClass("android.view.MenuItem"));
		allMenuItems.add(inflMenuItem);
		Local lhsLocal = null;
		if (s instanceof DefinitionStmt) {
			lhsLocal = jimpleUtil.lhsLocal(s);
		} else {
			String menuItemName = nextFakeName();
			lhsLocal = Jimple.v().newLocal(menuItemName, Scene.v().getSootClass("android.view.MenuItem").getType());
		}
		NVarNode menuItem = varNode(lhsLocal);
		inflMenuItem.addEdgeTo(menuItem, s);
		if (menuAdd4IntSubSig.equals(subsig) || menuAdd3IntCharSeqSubSig.equals(subsig)) {
			// System.out.println("{SetMenuItemId} " + s + " @ " +
			// currentMethod);
			Value itemId = ie.getArg(1);
			menuItemSetItemId(inflMenuItem, itemId);
		}

		// AddView2: menu.addView(menuItem)
		Local receiverLocal = jimpleUtil.receiver(ie);
		NVarNode menu = varNode(receiverLocal);
		Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s));
		NOpNode addMenuItem = new NAddView2OpNode(menu, menuItem, callSite, false);

		// SetText: menuItem.setTitle(titleSpecifier)
		Value lastArgument = ie.getArg(ie.getArgCount() - 1);
		NNode titleSpecifier = simpleNode(lastArgument);
		if (titleSpecifier != null) {
			NOpNode setText = new NSetTextOpNode(titleSpecifier, menuItem, callSite);
			allNNodes.add(setText);
			// System.out.println("{Menu.add} menuItem: " + menuItem + ", title:
			// "
			// + titleSpecifier);
		}

		if (Configs.debugCodes.contains(Debug.MENU_DEBUG)) {
			System.out.println(
					"[AddMenuItem] menu: " + menu + ", menuItem: " + menuItem + " @ " + addMenuItem.shortDescription());
			System.out.println("  stmt: " + s + " @ " + jimpleUtil.lookup(s));
		}

		return addMenuItem;
	}

	Map<NInflNode, Set<NVarNode>> pendingMenuItems = Maps.newHashMap();

	void menuItemSetItemId(NInflNode menuItem, Value itemId) {
		if (itemId instanceof IntConstant) {
			menuItem.idNode = anonymousWidgetIdNode(((IntConstant) itemId).value);
		} else if (itemId instanceof Local) {
			MultiMapUtil.addKeyAndHashSetElement(pendingMenuItems, menuItem, varNode((Local) itemId));
		}
	}

	// TODO: def/use analysis to find out the id values for recorded menu items
	// and the corresponding id var nodes.
	void processPendingMenuItems() {
	}

	// MenuItemSetTitle: menuItem.setTitle(titleSpecifier)
	// model as a NSetTextOpNode
	public NOpNode createMenuItemSetTitleOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();
		boolean isMenuItemSetTitle = menuItemSetTitleCharSeqSubSig.equals(subsig)
				|| menuItemSetTitleIntSubSig.equals(subsig);
		if (!isMenuItemSetTitle) {
			return null;
		}
		NNode titleNode = simpleNode(ie.getArg(0));
		if (titleNode == null) {
			return NOpNode.NullNode;
		}
		NVarNode receiverNode = varNode(jimpleUtil.receiver(ie));
		SootMethod caller = jimpleUtil.lookup(s);
		Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
		NOpNode setText = new NSetTextOpNode(titleNode, receiverNode, callSite);

		if (s instanceof DefinitionStmt) {
			NVarNode lhsNode = varNode(jimpleUtil.lhsLocal(s));
			receiverNode.addEdgeTo(lhsNode, s);
		}
		return setText;
	}

	// MenuInflate: menuInflater.inflate(menuId, menu)
	// "instrument" ->
	// Inflate1: menu = inflater.inflate(menuId)
	public NOpNode createMenuInflateOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String sig = callee.getSignature();
		if (!sig.equals(menuInflaterSig)) {
			return null;
		}
		Value menuIdVal = ie.getArg(0);
		NNode menuIdNode = simpleNode(menuIdVal);
		NNode menuNode = simpleNode(ie.getArg(1));
		Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s));
		NOpNode menuInflate = new NMenuInflateOpNode(menuIdNode, menuNode, callSite, false);

		if (Configs.debugCodes.contains(Debug.MENU_DEBUG)) {
			System.out.println("--- [CREATED] " + menuInflate);
		}
		return menuInflate;
	}

	// GetTabHost: lhs = TabActivity.getTabHost()
	// TabActivity.setContentView(id:tab_content)
	// lhs = TabActivity.findViewById(id:tabhost)
	public NOpNode createGetTabHostOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String sig = callee.getSubSignature();
		if (!sig.equals(getTabHostSubSig)) {
			return null;
		}
		Local rcv = jimpleUtil.receiver(ie);
		SootClass receiverClass = ((RefType) rcv.getType()).getSootClass();
		if (!hier.applicationActivityClasses.contains(receiverClass)) {
			return null;
		}
		// TabActivity.setContentView(id:tab_content)
		NVarNode lhsNode = varNode(jimpleUtil.lhsLocal(s));
		NVarNode receiverNode = varNode(rcv);

		Integer layoutId = xmlUtil.getSystemRLayoutValue("tab_content");
		if (layoutId == null) {
			throw new RuntimeException("[Error]: we can not identify the id for layout \"tab_content\"");
		}

		NNode layoutIdNode = simpleNode(IntConstant.v(layoutId.intValue()));

		if (!(layoutIdNode instanceof NLayoutIdNode)) {
			throw new RuntimeException("[Error]: we can not create a LayoutIdNode from id: " + layoutId.intValue());
		}

		NOpNode inflate2 = new NInflate2OpNode(layoutIdNode, receiverNode,
				new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)), true);

		// remeber the inflation, since our analysis doesn't consider control
		// flow
		// this will introduce inaccuracy by creating multiple TabHost
		allNNodes.add(inflate2);

		// lhs = TabActivity.findViewById(id:tabhost)
		Integer hostId = xmlUtil.getSystemRIdValue("tabhost");
		if (hostId == null) {
			throw new RuntimeException("[Error]: we can not identify the id for host \"tabhost\"");
		}
		Value hostVal = IntConstant.v(hostId.intValue());
		NNode hostValNode = simpleNode(hostVal);
		NOpNode findView2 = new NFindView2OpNode(hostValNode, receiverNode, lhsNode,
				new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s)), false);

		return findView2;
	}

	public NOpNode createSensorObjectOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String sig = callee.getSignature();
		if ((!sig.equals(getDefaultSensorSig)) && (!sig.equals(getDefaultSensor2Sig))) {
			return null;
		}

		if (!(s instanceof DefinitionStmt)) {
			return null;
		}

		// The receiver is always an instance of SensorManager.
		// Don't need to worry about it.
		Value lhs = ((DefinitionStmt) s).getLeftOp();

		return null;
	}

	/**
	 * Models "lhs=act.getListView()". Interesting code that may get executed
	 * when act.getListView() is called includes the following:
	 * act.setContentView( com.android.internal.R.layout.list_content_simple);
	 * mList = (ListView)findViewById(com.android.internal.R.id.list); return
	 * mList;
	 *
	 * It can literally be modeled with: Inflate2: act.setContentView(
	 * com.android.internal.R.layout.list_content_simple) FindView2: lhs =
	 * act.findViewById(com.android.internal.R.id.list)
	 *
	 * However, if the app inflates its own layout with ListView:list in it, the
	 * framework-defined layout will not get inflated. So, we will treat this
	 * call as a simple findViewById(list), and later patch in the default
	 * layout if the app does not call setContentView().
	 *
	 * Note that AlertDialog has a getListView() method too, but it has a
	 * different meaning.
	 *
	 * @param s
	 * @return
	 */
	public NOpNode createListActivityGetListViewOpNode(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();
		if (!subsig.equals(getListViewSubSig)) {
			return null;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Type receiverType = receiver.getType();
		SootClass receiverClass = ((RefType) receiverType).getSootClass();
		if (!hier.isSubclassOf(receiverClass, Scene.v().getSootClass("android.app.ListActivity"))) {
			return null;
		}

		// Now, we are sure it is ListActivity.getListView()

		// Don't bother if return value is not read
		if (!(s instanceof DefinitionStmt)) {
			System.out.println("[WARNING] getListView() called but return value ignored.");
			return NOpNode.NullNode;
		}
		// FindView2
		SootMethod caller = jimpleUtil.lookup(s);
		Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
		NNode idNode = widgetIdNode(xmlUtil.getSystemRIdValue("list"));
		NVarNode receiverNode = varNode(receiver);
		NVarNode lhsNode = varNode(jimpleUtil.lhsLocal(s));
		NOpNode findView2 = new NFindView2OpNode(idNode, receiverNode, lhsNode, callSite, false);

		// System.out.println("--- op nodes created for " + s + " @ " + caller);
		// System.out.println(" [Inflate2] layoutId: " + layoutIdNode
		// + ", receiver: " + receiverNode);
		// System.out.println(" [FindView2] idNode: " + idNode + ", lhs: " +
		// lhsNode);
		return findView2;
	}

	void checkAndPatchRootlessActivities() {
		Set<NOpNode> activitySetContentView = NOpNode.getNodes(NInflate2OpNode.class);
		activitySetContentView.addAll(NOpNode.getNodes(NAddView1OpNode.class));
		Set<NActivityNode> suspects = Sets.newHashSet(allNActivityNodes.values());
		for (NOpNode op : activitySetContentView) {
			try {
				NVarNode activity = op.getReceiver();
				for (NNode n : graphUtil.backwardReachableNodes(activity)) {
					if (n instanceof NActivityNode) {
						suspects.remove(n);
					}
				}
			} catch (NullPointerException ne) {
				// work around for applications that use layout ids that are not
				// in res/layout
				Logger.verb("WARNING", "OpNode " + op.toString() + " unknown");
				continue;
			}
		}
		for (NActivityNode activity : suspects) {
			SootClass activityClass = activity.c;
			if (hier.isSubclassOf(activityClass, Scene.v().getSootClass("android.app.ListActivity"))) {
				if (hier.isSubclassOf(activityClass, Scene.v().getSootClass("android.preference.PreferenceActivity"))) {
					// TODO(tony): handle Preference in future, but ignore for
					// now
					continue;
				}
				patchRootlessListActivity(activity);
			} else {
				// TODO(tony): remove warnings for activities that call finish()
				// in its
				// onCreate(). This information is also useful for model
				// construction.
				if (Configs.verbose) {
					System.out.println("[WARNING] setContentView() not called on " + activityClass);
				}
			}
		}
	}

	void checkAndPatchRootlessDialogs() {
		// TODO
	}

	void patchListActivity() {
		SootClass listActivityClass = Scene.v().getSootClass("android.app.ListActivity");
		if (listActivityClass.isPhantom()) {
			return;
		}
		SootClass listViewClass = Scene.v().getSootClass("android.widget.ListView");
		for (NActivityNode activityNode : allNActivityNodes.values()) {
			SootClass activityClass = activityNode.c;
			if (!hier.isSubclassOf(activityClass, listActivityClass)) {
				continue;
			}
			SootClass matched = hier.matchForVirtualDispatch(onListItemClickSubSig, activityClass);
			if (matched != null && matched.isApplicationClass()) {
				/*
				 * onListItemClick (ListView l, View v, int position, long id)
				 *
				 * ListView listView = activity.findViewById(list) View listItem
				 * = listView.findChild(...)
				 * listView.setOnItemClickListener(activity)
				 */
				SootMethod onListItemClick = matched.getMethod(onListItemClickSubSig);
				Local receiver = Jimple.v().newLocal(nextFakeName(), listActivityClass.getType());
				NNode idNode = widgetIdNode(xmlUtil.getSystemRIdValue("list"));
				NVarNode receiverNode = varNode(receiver);
				activityNode.addEdgeTo(receiverNode);
				Local listView = Jimple.v().newLocal(nextFakeName(), listViewClass.getType());
				NVarNode listViewNode = varNode(listView);
				NOpNode findView2 = new NFindView2OpNode(idNode, receiverNode, listViewNode, null, true);
				allNNodes.add(findView2);

				Local listItem = Jimple.v().newLocal(nextFakeName(), RefType.v("android.view.View"));
				NVarNode listItemNode = varNode(listItem);
				NFindView3OpNode findView3 = new NFindView3OpNode(listViewNode, listItemNode, null,
						FindView3Type.FindChildren, true);
				allNNodes.add(findView3);

				// A fake listener that calls onListItemClick
				SootClass fakeListenerClass = createFakeOnItemClickListenerClass(activityNode, listViewNode,
						listItemNode, onListItemClick);
				RefType fakeListenerType = fakeListenerClass.getType();
				Expr newListener = Jimple.v().newNewExpr(fakeListenerType);
				NAllocNode listenerObject = allocNode(newListener);
				String fakeListenerLocalName = nextFakeName();
				Local fakeListenerLocal = Jimple.v().newLocal(fakeListenerLocalName, fakeListenerType);
				NVarNode listenerNode = varNode(fakeListenerLocal);
				listenerObject.addEdgeTo(listenerNode);
				Stmt fakeListenerReg = Jimple.v().newNopStmt();
				SootMethod constructor = listActivityClass.getMethod("void <init>()");
				jimpleUtil.record(fakeListenerReg, constructor);
				NSetListenerOpNode setListener = createSetListenerAndProcessFlow(listView, fakeListenerLocal,
						fakeListenerReg, constructor, false, null, fakeListenerClass,
						Scene.v().getSootClass("android.widget.AdapterView$OnItemClickListener"), true,
						EventType.item_click, true);
				allNNodes.add(setListener);
			}
		}
	}

	SootClass createFakeOnItemClickListenerClass(NActivityNode listActivityNode, NVarNode listViewNode,
			NVarNode listItemNode, SootMethod handlerMethod) {
		String fakeListenerClassName = nextFakeName();
		Debug.v().printf("[Flowgraph] Create %s for activity %s | %s | %s | %s\n", fakeListenerClassName,
				listActivityNode.toString(), listViewNode.toString(), listItemNode.toString(),
				handlerMethod.getSignature());
		SootClass fakeListenerClass = new SootClass(fakeListenerClassName);
		SootClass superClass = Scene.v().getSootClass("android.widget.AdapterView$OnItemClickListener");
		fakeListenerClass.addInterface(superClass);

		// patch Hierarchy
		hier.appClasses.add(fakeListenerClass);
		hier.addFakeListenerClass(fakeListenerClass, superClass);
		// patch scene
		Scene.v().addClass(fakeListenerClass);
		fakeListenerClass.setApplicationClass();
		RefType fakeListenerClassType = fakeListenerClass.getType();
		RefType adapterViewType = RefType.v("android.widget.AdapterView");
		RefType viewType = RefType.v("android.view.View");

		SootMethod fakeOnItemClickMethod = new SootMethod("onItemClick",
				Lists.<Type>newArrayList(adapterViewType, viewType, IntType.v(), LongType.v()), VoidType.v());

		Jimple jimple = Jimple.v();
		JimpleBody body = jimple.newBody(fakeOnItemClickMethod);
		fakeOnItemClickMethod.setActiveBody(body);
		fakeListenerClass.addMethod(fakeOnItemClickMethod);

		// Add locals
		Chain<Local> locals = body.getLocals();
		Local thisLocal = jimple.newLocal("r0", fakeListenerClassType);
		locals.add(thisLocal);

		Local parentLocal = jimple.newLocal("r1", adapterViewType);
		locals.add(parentLocal);
		listViewNode.addEdgeTo(varNode(parentLocal));

		Local viewLocal = jimple.newLocal("r2", viewType);
		locals.add(viewLocal);
		listItemNode.addEdgeTo(varNode(viewLocal));

		Local positionLocal = jimple.newLocal("r3", IntType.v());
		locals.add(positionLocal);

		Local idLocal = jimple.newLocal("r4", LongType.v());
		locals.add(idLocal);

		Type listActivityType = listActivityNode.c.getType();
		Local listActivityLocal = jimple.newLocal("r5", listActivityType);
		locals.add(listActivityLocal);

		// Add statements
		PatchingChain<Unit> units = body.getUnits();
		// r0 := <this>
		Stmt defineThis = jimple.newIdentityStmt(thisLocal, jimple.newThisRef(fakeListenerClassType));
		units.add(defineThis);

		// r1 := parent
		Stmt defineParent = jimple.newIdentityStmt(parentLocal, jimple.newParameterRef(adapterViewType, 0));
		units.add(defineParent);

		// r2 := view
		Stmt defineView = jimple.newIdentityStmt(viewLocal, jimple.newParameterRef(viewType, 1));
		units.add(defineView);

		// r3 := position
		Stmt definePosition = jimple.newIdentityStmt(positionLocal, jimple.newParameterRef(IntType.v(), 2));
		units.add(definePosition);

		// r4 := id
		Stmt defineId = jimple.newIdentityStmt(idLocal, jimple.newParameterRef(LongType.v(), 3));
		units.add(defineId);

		// listActivity.onListItemClick(parent, view, position, id)
		Expr callOnItemClickExpr = jimple.newVirtualInvokeExpr(listActivityLocal, handlerMethod.makeRef(),
				Lists.newArrayList(parentLocal, viewLocal, positionLocal, idLocal));
		units.add(jimple.newInvokeStmt(callOnItemClickExpr));

		// parameter passing
		listActivityNode.addEdgeTo(varNode(listActivityLocal));
		Local dstParentLocal = jimpleUtil.localForNthParameter(handlerMethod, 1);
		varNode(parentLocal).addEdgeTo(varNode(dstParentLocal));
		Local dstViewLocal = jimpleUtil.localForNthParameter(handlerMethod, 2);
		varNode(viewLocal).addEdgeTo(varNode(dstViewLocal));

		units.add(jimple.newReturnVoidStmt());

		// record
		fakeHandlerToRealHandler.put(fakeOnItemClickMethod, handlerMethod);

		return fakeListenerClass;
	}

	void patchRootlessListActivity(NActivityNode activity) {
		// System.out.println("[Flowgraph] Patching " + activity);
		// Inflate2
		// Starting from 4.0.1_r1 onward, it's list_content_simple. Before that,
		// it's list_content.
		int layoutId;
		if (Configs.numericApiLevel >= 14) {
			layoutId = xmlUtil.getSystemRLayoutValue("list_content_simple");
		} else {
			layoutId = xmlUtil.getSystemRLayoutValue("list_content");
		}
		NNode layoutIdNode = layoutIdNode(layoutId);
		String fakeLocalName = nextFakeName();

		Local fakeLocal = Jimple.v().newLocal(fakeLocalName, RefType.v("android.app.ListActivity"));
		NVarNode receiverNode = varNode(fakeLocal);
		activity.addEdgeTo(receiverNode);
		NOpNode inflate2 = new NInflate2OpNode(layoutIdNode, receiverNode, null, true);
		allNNodes.add(inflate2);
	}

	public void recordInterestingCalls(Stmt s) {
		if (recordListViewRelatedCalls(s)) {
			return;
		}
		if (recordDialogRelatedCalls(s)) {
			return;
		}
		if (recordAlertDialogBuilderCalls(s)) {
			return;
		}
		if (recordExplicitShowMenuCalls(s)) {
			return;
		}
		if (recordTabHostRelatedCalls(s)) {
			return;
		}
	}

	// --- List views and adapters

	// TODO(tony): figure if it is important to handle AdapterView that are not
	// ListView.
	final SootClass adapterViewClass = Scene.v().getSootClass(" android.widget.AdapterView");

	final SootClass listViewClass = Scene.v().getSootClass("android.widget.ListView");

	final SootClass gridViewClass = Scene.v().getSootClass("android.widget.GridView");

	final SootClass arrayAdapterClass = Scene.v().getSootClass("android.widget.ArrayAdapter");

	final SootClass baseAdapterClass = Scene.v().getSootClass("android.widget.BaseAdapter");

	final SootClass listAdapterClass = Scene.v().getSootClass("android.widget.ListAdapter");

	Set<Stmt> listViewSetAdapterCalls = Sets.newHashSet();
	Set<Stmt> listAdapterConstructorCalls = Sets.newHashSet();
	Set<Stmt> listAdapterGetViewCalls = Sets.newHashSet();

	boolean recordSetAdapterCalls(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();
		if (!subsig.equals(setAdapterSubSig)) {
			return false;
		}

		Local receiver = jimpleUtil.receiver(ie);
		if (receiver == null) {
			// A static invoke
			return false;
		}

		Type receiverType = receiver.getType();
		if (!(receiverType instanceof RefType)) {
			return false;
		}
		SootClass receiverClass = ((RefType) receiverType).getSootClass();
		if (!hier.isSubclassOf(receiverClass, listViewClass) && !hier.isSubclassOf(receiverClass, gridViewClass)) {
			return false;
		}

		listViewSetAdapterCalls.add(s);
		return true;
	}

	public boolean recordListAdapterConstructor(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		if (!callee.isConstructor()) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		SootClass receiverClass = ((RefType) receiver.getType()).getSootClass();
		if (!hier.isSubclassOf(receiverClass, listAdapterClass)) {
			return false;
		}
		listAdapterConstructorCalls.add(s);
		return true;
	}

	boolean recordListAdapterGetView(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String subsig = callee.getSubSignature();
		if (!subsig.equals(getViewSubSig)) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		SootClass receiverClass = ((RefType) receiver.getType()).getSootClass();
		if (!hier.isSubclassOf(receiverClass, listAdapterClass)) {
			return false;
		}

		listAdapterGetViewCalls.add(s);
		return true;
	}

	boolean recordListViewRelatedCalls(Stmt s) {
		// SetAdapter: listView.setAdapter(listAdapter)
		if (recordSetAdapterCalls(s)) {
			return true;
		}
		// GetView: listAdapter.getView(...)
		if (recordListAdapterGetView(s)) {
			return true;
		}
		// AdapterConstructor
		if (recordListAdapterConstructor(s)) {
			return true;
		}
		return false;
	}

	HashMap<NAllocNode, Set<NNode>> adapterAndResourceIds = Maps.newHashMap();

	/**
	 * Adapter has a getView() method which defines the list item views.
	 *
	 * There are two cases. First, getView() serves as a callback method invoked
	 * by the framework. In this case, we need to connect the return value and
	 * pass the list view object into getView(). Second, getView() may be a
	 * "normal" method invoked by the application code. In this case, if is
	 * calling the library getView(), we need to model it as an inflate.
	 * Otherwise, we have already handle it. Also, in this case, we don't
	 * propagate the list view object because it is going into library code.
	 *
	 * The default getView() uses the layout id passed into constructor of
	 * adapter, so we need to resolve that as well.
	 */
	void processRecordedListViewCalls() {
		if (Configs.verbose) {
			dumpRecordedListViewCalls();
		}

		// Step 1: resolve the resource id associated with adapter objects
		resolveResourceIdForAdapters();

		// Step 2: connect the list view with corresponding getView().
		connectListViewWithGetView();

		// Step 3: model calls to platform-defined getView().
		processGetViewCalls();
	}

	void resolveResourceIdForAdapters() {
		for (Stmt s : listAdapterConstructorCalls) {
			SootClass constructorClass = s.getInvokeExpr().getMethod().getDeclaringClass();
			// ArrayAdapter only
			if (!hier.isSubclassOf(constructorClass, arrayAdapterClass)) {
				continue;
			}
			String stringForStmt = s + " @ " + jimpleUtil.lookup(s);

			Value v = extractLayoutIdFromAdapterConstructor(s);
			if (v == null) {
				System.out.println("[WARNING] Cannot find resource id for " + stringForStmt);
				continue;
			}
			NNode idNode = simpleNode(v);
			if (idNode == null) {
				// Typically, this is when the value is 0. So, we are fine.
				if (Configs.verbose) {
					System.out.println("[WARNING] Cannot create node for id " + v);
					System.out.println("  * " + stringForStmt);
				}
				continue;
			}
			NVarNode adapterVar = varNode(jimpleUtil.receiver(s));
			int found = 0;
			for (NNode n : graphUtil.backwardReachableNodes(adapterVar)) {
				// System.out.println(" - [reach] " + n);
				if (n instanceof NAllocNode) {
					found++;
					NAllocNode adapter = (NAllocNode) n;
					Set<NNode> ids = adapterAndResourceIds.get(adapter);
					if (ids == null) {
						ids = Sets.newHashSet();
						adapterAndResourceIds.put(adapter, ids);
					}
					ids.add(idNode);
				}
			}
			if (found == 0) {
				System.out.println("[WARNING] Cannot find value for adapter variable at " + stringForStmt);
			}
		}
	}

	void connectListViewWithGetView() {
		for (Stmt s : listViewSetAdapterCalls) {
			connectListViewWithGetView(s);
		}
	}

	void dumpRecordedListViewCalls() {
		System.out.println("[TRan2mat] --- setAdapter");
		for (Stmt s : listViewSetAdapterCalls) {
			System.out.println("[TRan2mat]   * " + s);
		}
		System.out.println("[TRan2mat] --- adapter.<init>");
		for (Stmt s : listAdapterConstructorCalls) {
			System.out.println("[TRan2mat]   * " + s);
		}
		System.out.println("[TRan2mat] --- getView");
		for (Stmt s : listAdapterGetViewCalls) {
			System.out.println("[TRan2mat]   * " + s);
		}
	}

	public void connectListViewWithGetView(Stmt setAdapterCall) {
		InvokeExpr ie = setAdapterCall.getInvokeExpr();
		Local receiver = jimpleUtil.receiver(ie);

		Value arg0 = ie.getArg(0);
		if (arg0 instanceof NullConstant) {
			return;
		}

		NVarNode listViewNode = varNode(receiver);
		Local adapter = (Local) arg0;

		// Assume adapter flow is only intra-procedural.
		SootMethod caller = jimpleUtil.lookup(setAdapterCall);
		Set<NNode> sources = graphUtil.backwardReachableNodes(varNode(adapter));
		Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(setAdapterCall, caller);
		if (Configs.debugCodes.contains(Debug.LIST_ADAPTER_DEBUG)) {
			System.out.println("--- SetAdapter: " + setAdapterCall + " in " + caller);
		}
		for (NNode src : sources) {
			if (!(src instanceof NObjectNode)) {
				continue;
			}
			SootClass concreteType = hier.matchForVirtualDispatch(getViewSubSig, ((NObjectNode) src).getClassType());
			if (concreteType == null)
				continue;
			SootMethod getView = concreteType.getMethod(getViewSubSig);
			if (Configs.debugCodes.contains(Debug.LIST_ADAPTER_DEBUG)) {
				System.out.println("  * " + getView);
			}
			// We handle ArrayAdapter only for now.
			// if (!hier.isSubclassOf(concreteType, arrayAdapterClass)) {
			if (!hier.isSubclassOf(concreteType, baseAdapterClass)) {
				return;
			}
			if (concreteType.isApplicationClass()) {
				// Application overrides getView()
				// 1) Connect return variable
				Set<Value> returnValuesForGetView = jimpleUtil.getReturnValues(getView);
				for (Value returnValue : returnValuesForGetView) {
					if (returnValue instanceof Local) {
						NVarNode listItemNode = varNode((Local) returnValue);
						NOpNode addView2 = new NAddView2OpNode(listViewNode, listItemNode, callSite, true);
						allNNodes.add(addView2);
						if (Configs.debugCodes.contains(Debug.LIST_ADAPTER_DEBUG)) {
							System.out.println("  [AV2] parent: " + listViewNode + ", child: " + listItemNode);
						}
					} else if (!(returnValue instanceof NullConstant)) {
						System.out.println("[WARNING] Unexpected return value for " + getView);
					}
				}
				// 2) Connect list view parameter
				NVarNode viewGroupNode = varNode(jimpleUtil.localForNthParameter(getView, 3));
				listViewNode.addEdgeTo(viewGroupNode, setAdapterCall);
				if (Configs.debugCodes.contains(Debug.LIST_ADAPTER_DEBUG)) {
					System.out.println("  [Edge] " + listViewNode + " ---> " + viewGroupNode);
				}
			} else {
				// Library-defined adapter type
				if (!(src instanceof NAllocNode)) {
					System.out.println("Unexpected adapter object " + src);
					continue;
				}
				NAllocNode adapterObject = (NAllocNode) src;
				Set<NNode> idNodes = adapterAndResourceIds.get(adapterObject);

				if (idNodes == null || idNodes.isEmpty()) {
					System.out
							.println("[WARNING] Missing layout id for adapter at " + setAdapterCall + " in " + caller);
				} else {
					for (NNode layoutNode : idNodes) {
						connectListViewWithGetView(layoutNode, listViewNode, callSite);
					}
				}
			}
		}
	}

	public NVarNode connectListViewWithGetView(NNode layoutNode, NVarNode listViewNode,
			Pair<Stmt, SootMethod> callSite) {
		if (layoutNode == null) {
			return null;
		}
		String fakeLocalName = nextFakeName();
		Local fakeLocal = Jimple.v().newLocal(fakeLocalName, Scene.v().getSootClass("android.view.View").getType());
		NVarNode listItemNode = varNode(fakeLocal);
		NOpNode inflate1 = new NInflate1OpNode(layoutNode, listItemNode, callSite, true);
		allNNodes.add(inflate1);
		NOpNode addView2 = new NAddView2OpNode(listViewNode, listItemNode, callSite, true);
		allNNodes.add(addView2);
		return listItemNode;
	}

	/**
	 * Calls to ArrayAdapter.getView() by default would inflate the saved layout
	 * ID. When we call this, the layout IDs should have been resolved. So, what
	 * we need is to create infate nodes.
	 */
	void processGetViewCalls() {
		for (Stmt s : listAdapterGetViewCalls) {
			processGetViewCall(s);
		}
	}

	// GetView: adapter.getView()
	public void processGetViewCall(Stmt adapterGetViewCall) {
		InvokeExpr ie = adapterGetViewCall.getInvokeExpr();
		SootMethod caller = jimpleUtil.lookup(adapterGetViewCall);

		Local receiver = jimpleUtil.receiver(ie);
		Set<NNode> adapters = graphUtil.backwardReachableNodes(varNode(receiver));
		for (NNode adapter : adapters) {
			if (!(adapter instanceof NAllocNode)) {
				continue;
			}
			NAllocNode adapterObject = (NAllocNode) adapter;
			SootClass actualAdapterClass = adapterObject.getClassType();

			// Handle ArrayAdapter only for now
			if (!hier.isSubclassOf(actualAdapterClass, arrayAdapterClass)) {
				continue;
			}
			SootClass matchedClass = hier.matchForVirtualDispatch(getViewSubSig, actualAdapterClass);

			if (matchedClass.isApplicationClass()) {
				// It's calling application code, we are fine
				continue;
			} else {
				// It's calling library code, let's model it

				// First, if return value not used, don't bother
				if (!(adapterGetViewCall instanceof DefinitionStmt)) {
					continue;
				}

				// Now, get the layouts and create inflate node
				Set<NNode> layouts = adapterAndResourceIds.get(adapterObject);
				if (layouts == null || layouts.isEmpty()) {
					System.out.println("Cannot find layout for " + adapterGetViewCall + " @ " + caller);
				} else {
					Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(adapterGetViewCall, caller);
					NNode lhsNode = varNode(jimpleUtil.lhsLocal(adapterGetViewCall));
					for (NNode layoutNode : layouts) {
						NOpNode inflate1 = new NInflate1OpNode(layoutNode, lhsNode, callSite, true);
						allNNodes.add(inflate1);
					}
				}
			}
		}
	}

	boolean extractIdDebug = false;

	/**
	 * Extract the layout id for a given adapter constructor statement. Right
	 * now, we only handle ArrayAdapter. If it is ArrayAdapter, we know the
	 * value at position 1 is the layout id. Otherwise, we look into the body of
	 * the constructor method.
	 *
	 * @param stmt
	 * @return
	 */
	public Value extractLayoutIdFromAdapterConstructor(Stmt stmt) {
		if (extractIdDebug) {
			System.out.println("--- extracting from " + stmt + " @ " + jimpleUtil.lookup(stmt));
		}
		InstanceInvokeExpr ie = (InstanceInvokeExpr) stmt.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		SootClass calleeClass = callee.getDeclaringClass();
		if (calleeClass.equals(Scene.v().getSootClass("android.widget.ArrayAdapter"))) {
			return ie.getArg(1);
		}
		return extractLayoutIdFromAdapterConstructor(callee);
	}

	/**
	 * Extract the layout id from an application adapter constructor method. We
	 * look for calls to super adapter constructors. If it is ArrayAdapter, we
	 * know what is going on. Otherwise, we need to look at additional
	 * constructor methods.
	 *
	 * @param method
	 * @return
	 */
	public Value extractLayoutIdFromAdapterConstructor(SootMethod method) {
		Body body = method.retrieveActiveBody();
		if (extractIdDebug) {
			System.out.println("--- extracting from " + method);
			System.out.println(body);
		}
		SootClass thisClass = method.getDeclaringClass();
		Iterator<Unit> stmts = body.getUnits().iterator();
		while (stmts.hasNext()) {
			Stmt s = (Stmt) stmts.next();
			if (extractIdDebug) {
				System.out.println("  * s: " + s);
			}
			if (!s.containsInvokeExpr()) {
				continue;
			}
			InvokeExpr invokeExpr = s.getInvokeExpr();
			SootMethod call = invokeExpr.getMethod();
			// r0.<init> - either this(...) or super(...)
			if (call.isConstructor()) {
				SootClass callClass = call.getDeclaringClass();
				boolean callingThisConstructor = callClass.equals(thisClass);
				boolean callingSuperConstructor = thisClass.hasSuperclass()
						&& callClass.equals(thisClass.getSuperclass());
				if (!callingThisConstructor && !callingSuperConstructor) {
					continue;
				}
				if (callClass.equals(arrayAdapterClass)) {
					return invokeExpr.getArg(1);
				} else if (hier.isSubclassOf(callClass, arrayAdapterClass)) {
					return extractLayoutIdFromAdapterConstructor(s);
				}
				if (Configs.verbose) {
					System.out
							.println("[WARNING] Failed to extract layout id from " + s + " in " + jimpleUtil.lookup(s));
				}
				return null;
			}
		}
		return null;
	}

	/*
	 * Records view.showContextMenu(), activity.openContextMenu(view). We assume
	 * that OnCreateContextMenuListener has been registered with the view and
	 * the flow to onCreateContextMenu() has been modeled.
	 */
	Map<Stmt, Local> explicitShowContextMenuCallAndViewLocals = Maps.newHashMap();
	Map<Stmt, Local> explicitShowOptionsMenuCallAndActivityLocals = Maps.newHashMap();

	boolean recordExplicitShowMenuCalls(Stmt s) {
		if (recordExplicitShowContextMenuCalls(s)) {
			return true;
		}
		if (recordExplicitShowOptionsMenuCalls(s)) {
			return true;
		}
		return false;
	}

	boolean recordExplicitShowContextMenuCalls(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		if (!(ie instanceof InstanceInvokeExpr)) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Type receiverType = receiver.getType();
		if (!(receiverType instanceof RefType)) {
			return false;
		}
		SootClass receiverClass = ((RefType) receiverType).getSootClass();
		SootMethod callee = ie.getMethod();
		String calleeSubsig = callee.getSubSignature();
		if (calleeSubsig.equals(viewShowContextMenuSubsig) && hier.viewClasses.contains(receiverClass)) {
			explicitShowContextMenuCallAndViewLocals.put(s, receiver);
			return true;
		}

		if (calleeSubsig.equals(activityOpenContextMenuSubsig)
				&& (hier.applicationActivityClasses.contains(receiverClass)
						|| hier.libActivityClasses.contains(receiverClass))) {
			Value viewArg = ie.getArg(0);
			if (viewArg instanceof Local) {
				explicitShowContextMenuCallAndViewLocals.put(s, (Local) viewArg);
			}
			return true;
		}
		return false;
	}

	boolean recordExplicitShowOptionsMenuCalls(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		if (!(ie instanceof InstanceInvokeExpr)) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Type receiverType = receiver.getType();
		if (!(receiverType instanceof RefType)) {
			return false;
		}
		SootClass receiverClass = ((RefType) receiverType).getSootClass();
		SootMethod callee = ie.getMethod();
		String calleeSubsig = callee.getSubSignature();
		if (calleeSubsig.equals(activityOpenOptionsMenuSubsig)
				&& (hier.applicationActivityClasses.contains(receiverClass)
						|| hier.libActivityClasses.contains(receiverClass))) {
			explicitShowOptionsMenuCallAndActivityLocals.put(s, receiver);
			return true;
		}
		return false;
	}

	// --- AlertDialog and its builders
	/*
	 * There are two challenges here. First, the use of builder requires
	 * modeling. Second, both AlertDialog and its builder delegate to an
	 * underlying AlertController class for their core functionalities. The idea
	 * is to (1) record all calls to AlertDialog and its builder; (2) convert
	 * calls to builder into calls to AlertDialog itself; (3) look at these
	 * calls on AlertDialog and model the effects of AlertController.
	 *
	 * The call sequence for an AlertDialog is like the following: (1) Create an
	 * instance of AlertDialog. An owener Context is needed for this. Typically,
	 * it is the owner activity. We need to track this info too so that we can
	 * report it in the output. First record the associating variables, and
	 * later resolve using graph reachability.
	 *
	 * (2) Various calls to the setter method on the AlertDialog instance. This
	 * actually remembers a bunch of things to be used later. At this point, no
	 * View has been created yet.
	 *
	 * (3) A call to show() to bring on the dialog. The method show() will call
	 * the appropriate onCreate() and onStart() method on the dialog instance.
	 * Inside the default onCreate(), a system-defined layout will be inflated.
	 * Which layout file to use depends on the things remembered by setter
	 * calls. Note that subclass of AlertDialog may override this onCreate()
	 * method to set its own view using setContentView(). However, we have not
	 * found such use in the 20 apps, and also it would be very confusing to do
	 * business in this way.
	 *
	 * (4) Next, when dismissDialog() is called on the instance to make the
	 * dialog go away, its onStop() method will be called.
	 *
	 * In short, the lifecycle of a dialog goes like this allocation -> onCreate
	 * -> onStart -> onStop
	 *
	 * In cases when a Builder is used, things are still rememebered using
	 * setter calls. Then, a call to create() is equivalent to a new expression.
	 * So, nothing new really.
	 *
	 * Another aspect of dialogs in general is the use of APIs like showDialog,
	 * and the corresponding callback onPrepareDialog/onCreateDialog, etc. We
	 * will worry about this later.
	 */
	public final String alertDialogClassName = "android.app.AlertDialog";
	public final String alertDialogBuilderClassName = "android.app.AlertDialog$Builder";

	// Subclasses of AlertDialog
	public final String timePickerDialogClassName = "android.app.TimePickerDialog";
	public final String datePickerDialogClassName = "android.app.DatePickerDialog";

	// dialog.setXYZ(...)
	Map<Local, Set<Stmt>> dialogAsReceiverSetterCalls = Maps.newHashMap();
	Map<NDialogNode, Set<Stmt>> dialogObjectAndSetters = Maps.newHashMap();

	// Resolved alertDialogBuilder.setXYZ()
	Map<NDialogNode, Set<Stmt>> alertDialogObjectAndBuilderSetters = Maps.newHashMap();

	// For AlertDialog itself, there's no create(). It is simply "a = new
	// AlertDialog".

	// dialog.show()
	Map<Local, Set<Stmt>> dialogShowCalls = Maps.newHashMap();
	Map<NDialogNode, Set<Stmt>> dialogObjectAndShows = Maps.newHashMap();

	// dialog.dismiss(), dialog.cancel()
	Map<Local, Set<Stmt>> dialogDismissCalls = Maps.newHashMap();
	Map<NDialogNode, Set<Stmt>> dialogObjectAndDismisses = Maps.newHashMap();

	// alertDialogBuilder.setXYZ(...)
	Map<Local, Set<Stmt>> alertDialogBuilderAsReceiverSetterCalls = Maps.newHashMap();
	Map<NAllocNode, Set<Stmt>> alertDialogBuilderObjectAndSetters = Maps.newHashMap();

	// alertDialogBuilder.create()
	Map<Local, Set<Stmt>> alertDialogBuilderCreateCalls = Maps.newHashMap();
	Map<NAllocNode, Set<Stmt>> alertDialogBuilderObjectAndCreates = Maps.newHashMap();

	// alertDialogBuilder.show() - recorded in create() and the show() part of
	// effect is accounted by remembering it in alertDialogObjectAndShows.

	// activity.showDialog
	Map<Local, Set<Stmt>> activityShowDialogCalls = Maps.newHashMap();
	Map<NDialogNode, Set<Stmt>> dialogObjectAndShowDialogs = Maps.newHashMap();

	// activity.dismissDialog, activity.removeDialog
	Map<Local, Set<Stmt>> activityDismissDialogCalls = Maps.newHashMap();
	Map<NDialogNode, Set<Stmt>> dialogObjectAndDismissDialogs = Maps.newHashMap();

	Set<SootClass> subclassesOfAlertDialog = Sets.newHashSet();
	Set<SootClass> subclassesOfAlertDialogBuilder = Sets.newHashSet();

	void processAllRecordedDialogCalls() {
		processRecordedAlertDialogBuilderCalls();
		processRecordedDialogCalls();
		processRecordedActivityDialogCalls();
		buildOutputForDialogs();
	}

	// Look at recorded builder calls, and convert them into dialog calls
	void processRecordedAlertDialogBuilderCalls() {
		// Debug print
		// printRecordedDialogBuilderCalls();

		// Simple reachability of Builder objects
		alertDialogBuilderBackwardReachability();

		// Ready to do it
		SootClass alertDialog = Scene.v().getSootClass("android.app.AlertDialog");
		for (Map.Entry<NAllocNode, Set<Stmt>> builderAndCreates : alertDialogBuilderObjectAndCreates.entrySet()) {
			NAllocNode builder = builderAndCreates.getKey();
			Set<Stmt> creates = builderAndCreates.getValue();
			Set<Stmt> setters = alertDialogBuilderObjectAndSetters.get(builder);
			for (Stmt s : creates) {
				NDialogNode dialog = dialogNode(alertDialog, s, jimpleUtil.lookup(s));
				Local lhs;
				if (s instanceof DefinitionStmt) {
					lhs = jimpleUtil.lhsLocal(s);
					NVarNode dialogNode = varNode(lhs);
					dialog.addEdgeTo(dialogNode, s);
				}
				Set<Stmt> setterStmts = alertDialogObjectAndBuilderSetters.get(dialog);
				if (setters != null && !setters.isEmpty()) {
					if (setterStmts == null) {
						setterStmts = Sets.newHashSet();
						alertDialogObjectAndBuilderSetters.put(dialog, setterStmts);
					}
					setterStmts.addAll(setters);
				}

				// Additional step when it is alertDialogBuilder.show()
				boolean isShow = "show".equals(s.getInvokeExpr().getMethod().getName());
				if (isShow) {
					Set<Stmt> shows = dialogObjectAndShows.get(dialog);
					if (shows == null) {
						shows = Sets.newHashSet();
						dialogObjectAndShows.put(dialog, shows);
					}
					shows.add(s);
				}
			}
		}

		// Some warnings
		for (SootClass c : subclassesOfAlertDialog) {
			System.out.println("  \033[1;31m[TODO]\033[0m Customized AlertDialog: " + c);
		}
		for (SootClass c : subclassesOfAlertDialogBuilder) {
			System.out.println("  \033[1;31m[TDOO]\033[0m Customized AlertDialog.Builder: " + c);
		}
	}

	void processRecordedDialogCalls() {
		// Resolve the receiver objects
		dialogBackwardReachability();

		// alertDialog.show()
		SootClass alertDialogClass = Scene.v().getSootClass(alertDialogClassName);
		for (Map.Entry<NDialogNode, Set<Stmt>> entry : dialogObjectAndShows.entrySet()) {
			NDialogNode alertDialog = entry.getKey();
			if (!hier.isSubclassOf(alertDialog.c, alertDialogClass)) {
				continue;
			}

			Set<Stmt> shows = entry.getValue();
			for (Stmt s : shows) {
				// Treat it as a simple inflate call. Later, we examine all the
				// setter
				// calls to model the effects of remaining code in
				// installContent().
				modelAlertControllerInstallContent(alertDialog, s);
			}
		}
		// dialog.setXYZ()
		for (NWindowNode window : NWindowNode.windowNodes) {
			if (!(window instanceof NDialogNode)) {
				continue;
			}
			NDialogNode dialog = (NDialogNode) window;

			// Look at the setters
			processRecordedDialogSetters(dialog);
			if (hier.isSubclassOf(dialog.c, alertDialogClass)) {
				processConvertedAlertDialogSetters(dialog);
			}
		}
	}

	Function<SootMethod, Set<Local>> getReturnVariablesFunction = new Function<SootMethod, Set<Local>>() {
		@Override
		public Set<Local> apply(SootMethod method) {
			Set<Local> locals = Sets.newHashSet();
			for (Value v : jimpleUtil.getReturnValues(method)) {
				if (v instanceof Local) {
					locals.add((Local) v);
				}
			}
			return locals;
		}
	};

	// this.f(argZero, argOne, ...)
	Function<SootMethod, Set<Local>> getLocalForArgOneFunction = new Function<SootMethod, Set<Local>>() {
		@Override
		public Set<Local> apply(SootMethod method) {
			return Collections.singleton(jimpleUtil.localForNthParameter(method, 2));
		}
	};

	void processRecordedActivityDialogCalls(Map<Local, Set<Stmt>> activityLocalAndCalls,
			Map<NDialogNode, Set<Stmt>> dialogObjectAndCalls, boolean isShow) {
		for (Map.Entry<Local, Set<Stmt>> entry : activityLocalAndCalls.entrySet()) {
			Local activityLocal = entry.getKey();
			Set<Stmt> dialogCalls = entry.getValue();
			boolean activityResolved = false;
			boolean dialogResolved = false;
			for (NNode n : graphUtil.backwardReachableNodes(varNode(activityLocal))) {
				if (!(n instanceof NActivityNode)) {
					continue;
				}
				activityResolved = true;
				SootClass activityClass = ((NActivityNode) n).getClassType();

				for (NDialogNode dialog : getDialogObjectsForOnCreateDialog(activityClass)) {
					dialogResolved = true;
					Set<Stmt> calls = dialogObjectAndCalls.get(dialog);
					if (calls == null) {
						calls = Sets.newHashSet();
						dialogObjectAndCalls.put(dialog, calls);
					}
					calls.addAll(dialogCalls);

					// Now, let's "call" dialog.show() if it is an AlertDialog
					if (isShow
							&& hier.isSubclassOf(dialog.getClassType(), Scene.v().getSootClass(alertDialogClassName))) {
						for (Stmt showStmt : dialogCalls) {
							modelAlertControllerInstallContent(dialog, showStmt);
						}
					}
				} // find dialog node
			} // find activity node

			Stmt oneStmt = dialogCalls.iterator().next();
			if (!activityResolved) {
				System.out.println(
						"[WARNING] Cannot find activity node for " + oneStmt + " @ " + jimpleUtil.lookup(oneStmt));
			} else if (!dialogResolved) {
				System.out.println(
						"[WARNING] Cannot find dialog node for " + oneStmt + " @ " + jimpleUtil.lookup(oneStmt));
			}
		} // look at input
	}

	/*
	 * For each activity.showDialog(), find the concrete activity object rep'ed
	 * by activityClass. Then, find return vars for corresponding onCreateDialog
	 * invoked on this activityClass. Do backward reachability to find the
	 * dialog nodes referenced by these return vars. Now, for each dialog, it is
	 * as if there is a dialog.show().
	 */
	void processRecordedActivityDialogCalls() {
		processRecordedActivityDialogCalls(activityShowDialogCalls, dialogObjectAndShowDialogs, true);
		processRecordedActivityDialogCalls(activityDismissDialogCalls, dialogObjectAndDismissDialogs, false);
	}

	Set<NDialogNode> getDialogObjectsForOnCreateDialog(SootClass activityClass) {
		Set<NDialogNode> dialogs = Sets.newHashSet();
		Set<Local> locals = getDialogLocalsForOnCreateDialog(activityClass);
		for (Local l : locals) {
			for (NNode n : graphUtil.backwardReachableNodes(varNode(l))) {
				if (n instanceof NDialogNode) {
					dialogs.add((NDialogNode) n);
				}
			}
		}
		return dialogs;
	}

	// Output interface for dialogs
	public Map<NDialogNode, Set<Stmt>> allDialogAndShows = Maps.newHashMap();
	public Map<NDialogNode, Set<Stmt>> allDialogAndDismisses = Maps.newHashMap();

	public Map<NDialogNode, Set<SootMethod>> allDialogLifecycleMethods = Maps.newHashMap();
	public Map<NDialogNode, Set<SootMethod>> allDialogNonLifecycleMethods = Maps.newHashMap();

	void buildOutputForDialogs() {
		// show
		addAllObjectStmtPairs(allDialogAndShows, dialogObjectAndShows);
		addAllObjectStmtPairs(allDialogAndShows, dialogObjectAndShowDialogs);

		// dismiss
		addAllObjectStmtPairs(allDialogAndDismisses, dialogObjectAndDismisses);
		addAllObjectStmtPairs(allDialogAndDismisses, dialogObjectAndDismissDialogs);
	}

	void addAllObjectStmtPairs(Map<NDialogNode, Set<Stmt>> output, Map<NDialogNode, Set<Stmt>> input) {
		for (Map.Entry<NDialogNode, Set<Stmt>> entry : input.entrySet()) {
			NDialogNode key = entry.getKey();
			Set<Stmt> s = output.get(key);
			if (s == null) {
				s = Sets.newHashSet();
				output.put(key, s);
			}
			s.addAll(entry.getValue());
		}
	}

	void flowToDialogLifecycleMethods(NDialogNode dialog) {
		for (String subsig : dialogLifecycleMethodSubSigs) {
			SootClass matched = hier.matchForVirtualDispatch(subsig, dialog.c);
			if (matched.isApplicationClass()) {
				SootMethod matchedMethod = matched.getMethod(subsig);
				dialog.addEdgeTo(varNode(jimpleUtil.thisLocal(matchedMethod)), null);
				saveDialogLifecycleMethod(dialog, matchedMethod);
			}
		}
	}

	void saveDialogLifecycleMethod(NDialogNode dialog, SootMethod method) {
		Set<SootMethod> methods = allDialogLifecycleMethods.get(dialog);
		if (methods == null) {
			methods = Sets.newHashSet();
			allDialogLifecycleMethods.put(dialog, methods);
		}
		methods.add(method);
	}

	void saveDialogNonLifecycleMethod(NDialogNode dialog, SootMethod method) {
		Set<SootMethod> methods = allDialogNonLifecycleMethods.get(dialog);
		if (methods == null) {
			methods = Sets.newHashSet();
			allDialogNonLifecycleMethods.put(dialog, methods);
		}
		methods.add(method);
	}

	// Look at dialogObjectAndSetters
	void processRecordedDialogSetters(NDialogNode dialog) {
		Set<Stmt> setters = dialogObjectAndSetters.get(dialog);
		if (setters == null || setters.isEmpty()) {
			return;
		}
		for (Stmt s : setters) {
			processRecordedDialogSetters(dialog, s);
		}
	}

	public static final int alertDialogPositiveButton = -1;
	public static final int alertDialogNegativeButton = -2;
	public static final int alertDialogNeutralButton = -3;

	public static final String alertDialogPositiveButtonId = "button1";
	public static final String alertDialogNegativeButtonId = "button2";
	public static final String alertDialogNeutralButtonId = "button3";

	void processRecordedDialogSetters(NDialogNode dialog, Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String calleeName = callee.getName();
		String calleeSubsig = callee.getSubSignature();
		SootMethod caller = jimpleUtil.lookup(s);

		// Inherited from dialog
		if (matchAndHandleDialogNonLifecycleMethods(ie, dialog)) {
			return;
		}

		// Not gonna handle
		if (calleeName.equals("setCancelMessage")
				// || calleeName.equals("setCancelable")
				|| calleeName.equals("setCanceledOnTouchOutside") || calleeName.equals("setFeatureDrawable")
				|| calleeName.equals("setFeatureDrawableAlpha") || calleeName.equals("setFeatureDrawableResource")
				|| calleeName.equals("setFeatureDrawableUri") || calleeName.equals("setVolumeControlStream")) {
			return;
		}

		// May handle
		if (calleeName.equals("setDismissMessage") || calleeName.equals("setOwnerActivity")) {
			return;
		}

		// AlertDialog specific
		if (calleeName.startsWith("setButton")) {
			if (calleeSubsig.contains("android.os.Message")) {
				// This is for posting messages. We can't understand it yet.
				// Print out
				// a warning message and return to ignore.
				System.out.println("[WARNING] Posting message: " + s + " @ " + caller);
				return;
			}
			// Now, we are sure that a listener is set.
			int argCount = ie.getArgCount();
			int which = alertDialogPositiveButton;
			if (calleeName.equals("setButton2")) {
				which = alertDialogNegativeButton;
			} else if (calleeName.equals("setButton3")) {
				which = alertDialogNeutralButton;
			}

			if (argCount == 3) {
				Value whichArg = ie.getArg(0);
				if (whichArg instanceof IntConstant) {
					which = ((IntConstant) whichArg).value;
					if (which > alertDialogPositiveButton || which < alertDialogNeutralButton) {
						System.out.println("[WARNING] Button specifier invalid for " + s + " @ " + caller);
						return;
					}
				} else {
					System.out.println("[WARNING] Non constant button specifier for " + s + " @ " + caller);
					return;
				}
			}

			Value listenerArg = ie.getArg(argCount - 1);
			if (!(listenerArg instanceof Local)) {
				System.out.println("[WARNING] Unexpected listener arg for " + s + " @ " + caller);
				return;
			}
			String buttonId = "button" + (-which);
			Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
			this.modelListenerForAlertDialogButtons((Local) listenerArg, dialog, buttonId, which, callSite);
			return;
		}

		if (calleeName.equals("setView")) {
			handleAlertDialogSetView(ie, dialog, s, caller);
			return;
		}

		// TODO(tony): handle in future
		if (calleeName.equals("setCustomTitle") || calleeName.equals("setMessage") || calleeName.equals("setTitle")) {
			return;
		}

		// Probably not going to handle
		if (calleeName.equals("setIcon") || calleeName.equals("setIconAttribute")
				|| calleeName.equals("setInverseBackgroundForced")) {
			return;
		}

		if (calleeName.equals("setProgressStyle") || calleeName.equals("setProgressDrawable")
				|| calleeName.equals("setMax")) {
			return;
		}

		if (calleeName.equals("setCancelable")) {
			Value cancelable = ie.getArg(0);
			if (cancelable instanceof IntConstant && ((IntConstant) cancelable).value == 0) {
				dialog.cancelable = false;
			}
			return;
		}

		System.out.println("[WARNING] Unknown setter: " + s + " @ " + caller);
		// throw new RuntimeException("Unknown setter: " + s + " @ " + caller);
	}

	// Look at alertDialogObjectAndBuilderSetters
	void processConvertedAlertDialogSetters(NDialogNode alertDialog) {
		Set<Stmt> setters = alertDialogObjectAndBuilderSetters.get(alertDialog);
		if (setters == null || setters.isEmpty()) {
			return;
		}
		for (Stmt s : setters) {
			processConvertedAlertDialogSetters(alertDialog, s);
		}
	}

	void processConvertedAlertDialogSetters(NDialogNode alertDialog, Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String name = callee.getName();
		SootMethod caller = jimpleUtil.lookup(s);

		// Calls we don't care
		// if (name.equals("setCancelable") || name.equals("setIcon")
		if (name.equals("setIcon") || name.equals("setIconAttribute") || name.equals("setInverseBackgroundForced")) {
			return;
		}

		// Related to titles, strings. Ignore for now.
		// TODO(tony): handle if necessary
		if (name.equals("setCustomTitle") || name.equals("setMessage") || name.equals("setTitle")) {
			return;
		}

		// Related to list adapters. Ignore for now.
		// TODO(tony): handle this
		if (name.equals("setAdapter") || name.equals("setCursor") || name.equals("setItems")
				|| name.equals("setMultiChoiceItems") || name.equals("setOnItemSelectedListener")
				|| name.equals("setSingleChoiceItems")) {
			return;
		}

		// Adds to the R.id.custom Framelayout
		if (name.equals("setView")) {
			handleAlertDialogSetView(ie, alertDialog, s, caller);
			return;
		}

		if (matchAndHandleDialogNonLifecycleMethods(ie, alertDialog)) {
			return;
		}

		if (name.equals("setPositiveButton")) {
			Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
			this.modelListenerForAlertDialogButtons(ie, alertDialog, alertDialogPositiveButtonId,
					alertDialogPositiveButton, callSite);
			return;
		}
		if (name.equals("setNegativeButton")) {
			Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
			this.modelListenerForAlertDialogButtons(ie, alertDialog, alertDialogNegativeButtonId,
					alertDialogPositiveButton, callSite);
			return;
		}

		if (name.equals("setNeutralButton")) {
			Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
			this.modelListenerForAlertDialogButtons(ie, alertDialog, alertDialogNeutralButtonId,
					alertDialogNeutralButton, callSite);
			return;
		}

		if (name.equals("setCancelable")) {
			Value cancelable = ie.getArg(0);
			if (cancelable instanceof IntConstant && ((IntConstant) cancelable).value == 0) {
				alertDialog.cancelable = false;
			}
			return;
		}

		throw new RuntimeException("Unknown setter: " + s + " @ " + caller);

	}

	void handleAlertDialogSetView(InvokeExpr ie, NDialogNode alertDialog, Stmt s, SootMethod caller) {
		Value viewArg = ie.getArg(0);
		if (viewArg instanceof NullConstant) {
			return;
		}

		if (viewArg instanceof Local) {
			Local viewLocal = (Local) viewArg;
			Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
			handleAlertDialogSetView(viewLocal, alertDialog, callSite);
		} else if (viewArg instanceof IntConstant) {
			NNode viewNode = simpleNode(viewArg);
			Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
			// NVarNode receiverNode = varNode(jimpleUtil.receiver(ie));
			handleAlertDialogSetView(viewNode, alertDialog, callSite);
		} else {
			throw new RuntimeException("Unexpected argument for " + s + " @ " + caller);
		}
	}

	void handleAlertDialogSetView(Local viewLocal, NDialogNode alertDialog, Pair<Stmt, SootMethod> callSite) {
		// custom = dialog.findViewById(internal.R.id.custom)
		NVarNode custom = internalDialogFindViewById(alertDialog, "custom", "android.widget.FrameLayout", callSite);
		// custom.addView(mView)
		NVarNode mView = varNode(viewLocal);
		NAddView2OpNode customAddView = new NAddView2OpNode(custom, mView, callSite, true);
		allNNodes.add(customAddView);
	}

	void handleAlertDialogSetView(NNode viewNode, NDialogNode alertDialog, Pair<Stmt, SootMethod> callSite) {
		// custom = dialog.findViewById(internal.R.id.custom)
		NVarNode custom = internalDialogFindViewById(alertDialog, "custom", "android.widget.FrameLayout", callSite);
		// custom.addView(mView)

		NInflate2OpNode customAddView = new NInflate2OpNode(viewNode, custom, callSite, true);
		allNNodes.add(customAddView);
	}

	boolean matchAndHandleDialogNonLifecycleMethods(InvokeExpr ie, NDialogNode dialog) {
		String name = ie.getMethod().getName();
		// This is a handler for an event on dialog. So, connect the flows, but
		// do not create the usual SetListener
		if (name.equals("setOnCancelListener")) {
			flowForDialogEvents(ie, dialog, dialogOnCancelSubSig);
			return true;
		}
		if (name.equals("setOnDismissListener")) {
			flowForDialogEvents(ie, dialog, dialogOnDismissSubSig);
			return true;
		}
		if (name.equals("setOnKeyListener")) {
			flowForDialogEvents(ie, dialog, dialogOnKeySubSig);
			return true;
		}
		if (name.equals("setOnShowListener")) {
			flowForDialogEvents(ie, dialog, dialogOnShowSubSig);
			return true;
		}
		return false;
	}

	void modelListenerForAlertDialogButtons(InvokeExpr ie, NDialogNode alertDialog, String buttonId, int which,
			Pair<Stmt, SootMethod> callSite) {
		Value listenerArg = ie.getArg(1);
		if (!(listenerArg instanceof Local)) {
			return;
		}
		modelListenerForAlertDialogButtons((Local) listenerArg, alertDialog, buttonId, which, callSite);
	}

	// TODO(tony): capture the button name.
	// TODO(tony): add a fake listener that calls dismiss() even if the listener
	// arg is null.
	void modelListenerForAlertDialogButtons(Local listenerArg, NDialogNode alertDialog, String buttonId, int which,
			Pair<Stmt, SootMethod> callSite) {
		Stmt s = callSite.getO1();
		SootMethod caller = callSite.getO2();

		String handlerSubsig = dialogOnClickSubSig;
		for (NNode n : graphUtil.backwardReachableNodes(varNode(listenerArg))) {
			if (!(n instanceof NObjectNode)) {
				continue;
			}
			SootClass c = ((NObjectNode) n).getClassType();
			SootClass matched = hier.matchForVirtualDispatch(handlerSubsig, c);
			if (matched != null) {
				SootMethod handlerMethod = matched.getMethod(handlerSubsig);
				// 1. button = alertDialog.findViewById(button)
				NVarNode button = internalDialogFindViewById(alertDialog, buttonId, "android.widget.Button", callSite);
				// 2. listener = new View$OnClickListener() {
				// onClick() { handlerMethod(alertDialog, int(buttonId)); }
				// };

				// Create a fake listener class
				NVarNode dialogInterfaceOnClickListener = varNode(listenerArg);
				SootClass fakeOnClickListenerClass = createFakeOnClickListenerClass(alertDialog,
						dialogInterfaceOnClickListener, handlerMethod, which);
				// "listener = new FakeListener"
				RefType fakeListenerType = fakeOnClickListenerClass.getType();
				Expr newListener = Jimple.v().newNewExpr(fakeListenerType);
				NAllocNode listenerObject = allocNode(newListener);
				String fakeListenerLocalName = nextFakeName();
				Local fakeListenerLocal = Jimple.v().newLocal(fakeListenerLocalName, fakeListenerType);
				NVarNode listenerLocal = varNode(fakeListenerLocal);
				listenerObject.addEdgeTo(listenerLocal, s);

				// 3. button.setOnClickListener(listener)
				NSetListenerOpNode setListener = createSetListenerAndProcessFlow(button.l, fakeListenerLocal, s, caller,
						false, null, fakeOnClickListenerClass,
						Scene.v().getSootClass("android.view.View$OnClickListener"), true, EventType.click, true);
				allNNodes.add(setListener);
			}
		}
	}

	SootClass createFakeOnClickListenerClass(NDialogNode alertDialog, NVarNode dialogInterfaceOnClickListener,
			SootMethod handlerMethod, int which) {
		String fakeListenerClassName = nextFakeName();
		Debug.v().printf("[Flowgraph] Create %s for dialog %s | %s @ %s | %s\n", fakeListenerClassName, alertDialog,
				"" + alertDialog.allocStmt, "" + alertDialog.allocMethod, handlerMethod.getSignature());
		SootClass fakeListenerClass = new SootClass(fakeListenerClassName);
		SootClass superClass = Scene.v().getSootClass("android.view.View$OnClickListener");
		fakeListenerClass.addInterface(superClass);

		// patch Hierarchy
		hier.appClasses.add(fakeListenerClass);
		hier.addFakeListenerClass(fakeListenerClass, superClass);
		// patch scene
		Scene.v().addClass(fakeListenerClass);
		fakeListenerClass.setApplicationClass();
		RefType fakeListenerClassType = fakeListenerClass.getType();

		RefType viewType = RefType.v("android.view.View");
		SootMethod fakeOnClickMethod = new SootMethod("onClick", Collections.<Type>singletonList(viewType),
				VoidType.v());
		Jimple jimple = Jimple.v();
		JimpleBody body = jimple.newBody(fakeOnClickMethod);
		fakeOnClickMethod.setActiveBody(body);
		fakeListenerClass.addMethod(fakeOnClickMethod);

		Chain<Local> locals = body.getLocals();
		Local thisLocal = jimple.newLocal("r0", fakeListenerClassType);
		locals.add(thisLocal);

		Local viewLocal = jimple.newLocal("r1", viewType);
		locals.add(viewLocal);

		Type dialogListenerType = dialogInterfaceOnClickListener.l.getType();
		Local dialogListenerLocal = jimple.newLocal("r2", dialogListenerType);
		locals.add(dialogListenerLocal);
		dialogInterfaceOnClickListener.addEdgeTo(varNode(dialogListenerLocal), null);

		Type alertDialogType = alertDialog.c.getType();
		Local alertDialogLocal = jimple.newLocal("r3", alertDialogType);
		locals.add(alertDialogLocal);
		alertDialog.addEdgeTo(varNode(alertDialogLocal), null);

		PatchingChain<Unit> units = body.getUnits();
		// r0 := <this>
		Stmt defineThis = jimple.newIdentityStmt(thisLocal, jimple.newThisRef(fakeListenerClassType));
		units.add(defineThis);
		// r1 := view
		Stmt defineView = jimple.newIdentityStmt(viewLocal, jimple.newParameterRef(viewType, 0));
		units.add(defineView);
		// r2 := null
		Stmt defineDialogListener = jimple.newAssignStmt(dialogListenerLocal, NullConstant.v());
		units.add(defineDialogListener);
		// r3 := null
		Stmt defineAlertDialog = jimple.newAssignStmt(alertDialogLocal, NullConstant.v());
		units.add(defineAlertDialog);

		// r2:dialogInterfaceListener.onClick(r3:alertDialog, IntConstant:which)
		Expr callOnClickExpr = jimple.newVirtualInvokeExpr(dialogListenerLocal, handlerMethod.makeRef(),
				alertDialogLocal, IntConstant.v(which));
		units.add(jimple.newInvokeStmt(callOnClickExpr));
		// parameter passing
		Local dialogInterfaceOnClickThis = jimpleUtil.thisLocal(handlerMethod);
		Local alertDialogParameterLocal = jimpleUtil.localForNthParameter(handlerMethod, 1);
		varNode(dialogListenerLocal).addEdgeTo(varNode(dialogInterfaceOnClickThis), null);
		varNode(alertDialogLocal).addEdgeTo(varNode(alertDialogParameterLocal), null);

		// r3:alertDialog.dismiss()
		SootMethod dismissMethod = Scene.v().getSootClass("android.app.Dialog").getMethodByName("dismiss");
		Expr callDismissExpr = jimple.newVirtualInvokeExpr(alertDialogLocal, dismissMethod.makeRef());
		Stmt dismissStmt = jimple.newInvokeStmt(callDismissExpr);
		units.add(dismissStmt);
		// record in the dismiss maps
		{
			Set<Stmt> dismisses = dialogDismissCalls.get(alertDialogLocal);
			if (dismisses == null) {
				dismisses = Sets.newHashSet();
				dialogDismissCalls.put(alertDialogLocal, dismisses);
			}
			dismisses.add(dismissStmt);
		}
		{
			Set<Stmt> dismisses = dialogObjectAndDismisses.get(alertDialog);
			if (dismisses == null) {
				dismisses = Sets.newHashSet();
				dialogObjectAndDismisses.put(alertDialog, dismisses);
			}
			dismisses.add(dismissStmt);
		}
		// System.out.println(
		// "[dismiss] " + dismissStmt + "[" + dismissStmt.hashCode() + "]"
		// + " | " + fakeOnClickMethod);

		units.add(jimple.newReturnVoidStmt());
		// System.out.println("--- " + fakeOnClickMethod);
		// System.out.println(fakeOnClickMethod.retrieveActiveBody() + "\n");

		// record
		fakeHandlerToRealHandler.put(fakeOnClickMethod, handlerMethod);

		return fakeListenerClass;
	}

	void flowForDialogEvents(InvokeExpr ie, NDialogNode dialog, String handlerSubsig) {
		Value listenerArg = ie.getArg(0);
		if (!(listenerArg instanceof Local)) {
			return;
		}

		for (NNode n : graphUtil.backwardReachableNodes(varNode((Local) listenerArg))) {
			if (!(n instanceof NAllocNode)) {
				continue;
			}
			SootClass c = ((NAllocNode) n).getClassType();
			SootClass matched = hier.matchForVirtualDispatch(handlerSubsig, c);
			if (matched != null) {
				SootMethod handlerMethod = matched.getMethod(handlerSubsig);
				Local listenerLocal = jimpleUtil.thisLocal(handlerMethod);
				n.addEdgeTo(varNode(listenerLocal), null);
				Local dialogLocal = jimpleUtil.localForNthParameter(handlerMethod, 1);
				dialog.addEdgeTo(varNode(dialogLocal), null);

				saveDialogNonLifecycleMethod(dialog, handlerMethod);
			}
		}
	}

	Set<NDialogNode> alreadyModeled = Sets.newHashSet();

	// See com.android.internal.app.AlertController#installContent()
	void modelAlertControllerInstallContent(NDialogNode alertDialog, Stmt s) {
		if (alreadyModeled.contains(alertDialog)) {
			return;
		}
		alreadyModeled.add(alertDialog);
		// Inflate2:
		// dialog.setContentView(com.android.internal.R.layout.alert_dialog)
		String dialogLocalName = nextFakeName();
		Local fakeLocal = Jimple.v().newLocal(dialogLocalName, RefType.v(alertDialogClassName));
		NVarNode receiverNode = varNode(fakeLocal);
		alertDialog.addEdgeTo(receiverNode);
		NLayoutIdNode layoutIdNode = layoutIdNode(xmlUtil.getSystemRLayoutValue("alert_dialog"));
		SootMethod caller = jimpleUtil.lookup(s);
		Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, caller);
		NInflate2OpNode inflate2 = new NInflate2OpNode(layoutIdNode, receiverNode, callSite, true);
		allNNodes.add(inflate2);

		// Additional setups for subclasses
		String thisDialogClassName = alertDialog.getClassType().getName();
		if (thisDialogClassName.equals(timePickerDialogClassName)) {
			additionalSetupForTimePickerDialog(alertDialog, s, callSite);
			return;
		}
		if (thisDialogClassName.equals(datePickerDialogClassName)) {
			additionalSetupForDatePickerDialog(alertDialog, s, callSite);
			return;
		}
	}

	void additionalSetupForTimePickerDialog(NDialogNode alertDialog, Stmt s, Pair<Stmt, SootMethod> callSite) {
		// setIcon(0);
		// ignore

		// setTitle(R.string.time_picker_dialog_title);
		// TODO(tony): handle later

		// setButton(BUTTON_POSITIVE,
		// themeContext.getText(R.string.date_time_done), this);
		Local listenerArg = Jimple.v().newLocal(nextFakeName(), alertDialog.getClassType().getType());
		alertDialog.addEdgeTo(varNode(listenerArg));
		int which = alertDialogPositiveButton;
		String buttonId = "button" + (-which);
		modelListenerForAlertDialogButtons(listenerArg, alertDialog, buttonId, which, callSite);

		// View view = inflater.inflate(R.layout.time_picker_dialog, null);
		NLayoutIdNode layoutIdNode = layoutIdNode(xmlUtil.getSystemRLayoutValue("time_picker_dialog"));
		Local view = Jimple.v().newLocal(nextFakeName(), RefType.v("android.view.View"));
		NInflate1OpNode inflate1 = new NInflate1OpNode(layoutIdNode, varNode(view), callSite, true);
		allNNodes.add(inflate1);

		// setView(view);
		handleAlertDialogSetView(view, alertDialog, callSite);
	}

	void additionalSetupForDatePickerDialog(NDialogNode alertDialog, Stmt s, Pair<Stmt, SootMethod> callSite) {
		// setButton(BUTTON_POSITIVE,
		// themeContext.getText(R.string.date_time_done), this);
		Local listenerArg = Jimple.v().newLocal(nextFakeName(), alertDialog.getClassType().getType());
		alertDialog.addEdgeTo(varNode(listenerArg));
		int which = alertDialogPositiveButton;
		String buttonId = "button" + (-which);
		modelListenerForAlertDialogButtons(listenerArg, alertDialog, buttonId, which, callSite);

		// setIcon(0);
		// ignore

		// View view = inflater.inflate(R.layout.date_picker_dialog, null);
		NLayoutIdNode layoutIdNode = layoutIdNode(xmlUtil.getSystemRLayoutValue("date_picker_dialog"));
		Local view = Jimple.v().newLocal(nextFakeName(), RefType.v("android.view.View"));
		NInflate1OpNode inflate1 = new NInflate1OpNode(layoutIdNode, varNode(view), callSite, true);
		allNNodes.add(inflate1);

		// setView(view);
		handleAlertDialogSetView(view, alertDialog, callSite);
	}

	/*
	 * Models "lhs = dialog.findViewById(com.android.internal.R.id.viewId)", and
	 * returns varNode(lhs).
	 */
	NVarNode internalDialogFindViewById(NObjectNode alertDialog, String viewId, String viewClassName,
			Pair<Stmt, SootMethod> callSite) {
		NWidgetIdNode idNode = widgetIdNode(xmlUtil.getSystemRIdValue(viewId));
		String fakeReceiverName = nextFakeName();
		Local fakeReceiverLocal = Jimple.v().newLocal(fakeReceiverName, RefType.v(alertDialogClassName));
		NVarNode receiverNode = varNode(fakeReceiverLocal);
		alertDialog.addEdgeTo(receiverNode);

		String fakeLhsName = nextFakeName();
		Local fakeLhsLocal = Jimple.v().newLocal(fakeLhsName, RefType.v(viewClassName));
		NVarNode lhsNode = varNode(fakeLhsLocal);

		NFindView2OpNode findView2 = new NFindView2OpNode(idNode, receiverNode, lhsNode, callSite, true);
		allNNodes.add(findView2);

		return lhsNode;
	}

	// Resolve the alert dialog builder objects a receiver variable can
	// reference
	void alertDialogBuilderBackwardReachability() {
		receiverBackwardReachability(alertDialogBuilderAsReceiverSetterCalls, alertDialogBuilderObjectAndSetters,
				NAllocNode.class);
		receiverBackwardReachability(alertDialogBuilderCreateCalls, alertDialogBuilderObjectAndCreates,
				NAllocNode.class);
	}

	// Resolve Dialog objects that receiver variables can reference.
	void dialogBackwardReachability() {
		receiverBackwardReachability(dialogAsReceiverSetterCalls, dialogObjectAndSetters, NDialogNode.class);
		receiverBackwardReachability(dialogShowCalls, dialogObjectAndShows, NDialogNode.class);
		receiverBackwardReachability(dialogDismissCalls, dialogObjectAndDismisses, NDialogNode.class);
	}

	<E> void receiverBackwardReachability(Map<Local, Set<Stmt>> receiverAndCalls, Map<E, Set<Stmt>> objectAndCalls,
			Class<E> receiverNodeType) {
		for (Map.Entry<Local, Set<Stmt>> entry : receiverAndCalls.entrySet()) {
			Local receiver = entry.getKey();
			NVarNode receiverNode = varNode(receiver);
			Set<Stmt> calls = entry.getValue();
			Set<NNode> receiverObjects = graphUtil.backwardReachableNodes(receiverNode);

			boolean resolved = false;
			for (NNode n : receiverObjects) {
				if (n instanceof NObjectNode) {
					if (!(receiverNodeType.isInstance(n))) {
						continue;
						// GraphUtil.verbose = true;
						// graphUtil.backwardReachableNodes(receiverNode);
						// for (NNode obj : receiverObjects) {
						// if (obj instanceof NVarNode) {
						// System.out.println(
						// " * " + obj + " | " + ((NVarNode)obj).l.getType());
						// }
						// }
						//
						// for (Stmt edge : receiverNode.predSites) {
						// if (edge != null) {
						// System.out.println(" * edge: " + edge);
						// }
						// }
						//
						// throw new RuntimeException("Expected type: " +
						// receiverNodeType
						// + ", but object is: " + n.toString());
					}
					resolved = true;
					E object = receiverNodeType.cast(n);
					Set<Stmt> callsForObject = objectAndCalls.get(object);
					if (callsForObject == null) {
						callsForObject = Sets.newHashSet();
						objectAndCalls.put(object, callsForObject);
					}
					callsForObject.addAll(calls);
				}
			} // for each receiver
			if (!resolved) {
				Stmt oneStmt = calls.iterator().next();
				System.out.println(
						"[WARNING] Receiver object unknown for " + oneStmt + " @ " + jimpleUtil.lookup(oneStmt));
			}
		}
	}

	public static final String dialogClassName = "android.app.Dialog";

	// Record calls related to Dialog, AlertDialog.Builder for later processing
	boolean recordDialogRelatedCalls(Stmt s) {
		if (recordDialogCalls(s)) {
			return true;
		}
		if (recordActivityDialogCalls(s)) {
			return true;
		}
		return false;
	}

	// dialog.xyz(...)
	boolean recordDialogCalls(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		if (!(ie instanceof InstanceInvokeExpr)) {
			return false;
		}
		SootMethod callee = ie.getMethod();
		if (callee.getDeclaringClass().isApplicationClass()) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Type receiverType = receiver.getType();
		if (!(receiverType instanceof RefType)) {
			return false;
		}
		SootClass receiverClass = ((RefType) receiverType).getSootClass();
		SootClass dialogClass = Scene.v().getSootClass(dialogClassName);

		if (!hier.isSubclassOf(receiverClass, dialogClass)) {
			return false;
		}
		// If subclass of AlertDialog, can't handle yet. TODO
		SootClass alertDialogClass = Scene.v().getSootClass(alertDialogClassName);
		String receiverClassName = receiverClass.getName();
		if (hier.isSubclassOf(receiverClass, alertDialogClass) && !receiverClassName.equals(alertDialogClassName)
				&& !receiverClassName.equals(timePickerDialogClassName) && !receiverClass.isApplicationClass()) {
			subclassesOfAlertDialog.add(receiverClass);
			return true;
		}

		if (recordDialogSetters(s)) {
			return true;
		}

		if (recordDialogShow(s)) {
			return true;
		}

		if (recordDialogDismisses(s)) {
			return true;
		}

		return true;
	}

	// alertDialog.setXYZ(...)
	boolean recordDialogSetters(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String name = callee.getName();
		boolean nameMatch = name.startsWith("set") && name.length() > 3;
		if (!nameMatch) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Set<Stmt> setters = dialogAsReceiverSetterCalls.get(receiver);
		if (setters == null) {
			setters = Sets.newHashSet();
			dialogAsReceiverSetterCalls.put(receiver, setters);
		}
		setters.add(s);
		return true;
	}

	// alertDialog.show()
	boolean recordDialogShow(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String name = callee.getName();
		if (!name.equals("show")) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Set<Stmt> shows = dialogShowCalls.get(receiver);
		if (shows == null) {
			shows = Sets.newHashSet();
			dialogShowCalls.put(receiver, shows);
		}
		shows.add(s);
		return true;
	}

	boolean recordDialogDismisses(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String name = callee.getName();
		boolean match = name.equals("dismiss") || name.equals("cancel");
		if (!match) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Set<Stmt> dismisses = dialogDismissCalls.get(receiver);
		if (dismisses == null) {
			dismisses = Sets.newHashSet();
			dialogDismissCalls.put(receiver, dismisses);
		}
		dismisses.add(s);
		return true;
	}

	boolean recordActivityDialogCalls(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		if (!(ie instanceof InstanceInvokeExpr)) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Type receiverType = receiver.getType();
		if (!(receiverType instanceof RefType)) {
			return false;
		}
		SootClass receiverClass = ((RefType) receiverType).getSootClass();
		if (!hier.applicationActivityClasses.contains(receiverClass)) {
			return false;
		}
		SootMethod callee = ie.getMethod();
		String calleeSubsig = callee.getSubSignature();

		// show
		if (calleeSubsig.equals(activityShowDialogSubSig) || calleeSubsig.equals(activityShowDialogBundleSubSig)) {
			Set<Stmt> showDialogs = activityShowDialogCalls.get(receiver);
			if (showDialogs == null) {
				showDialogs = Sets.newHashSet();
				activityShowDialogCalls.put(receiver, showDialogs);
			}
			showDialogs.add(s);
			return true;
		}

		// dismiss
		if (calleeSubsig.equals(activityDismissDialogSubSig) || calleeSubsig.equals(activityRemoveDialogSubSig)) {
			Set<Stmt> dismissDialogs = activityDismissDialogCalls.get(receiver);
			if (dismissDialogs == null) {
				dismissDialogs = Sets.newHashSet();
				activityDismissDialogCalls.put(receiver, dismissDialogs);
			}
			dismissDialogs.add(s);
			return true;
		}

		return false;
	}

	// alertDialogBuilder.xyz(...)
	boolean recordAlertDialogBuilderCalls(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		if (!(ie instanceof InstanceInvokeExpr)) {
			return false;
		}
		SootClass alertDialogBuilderClass = Scene.v().getSootClass(alertDialogBuilderClassName);
		Local receiver = jimpleUtil.receiver(ie);
		Type receiverType = receiver.getType();
		if (!(receiverType instanceof RefType)) {
			return false;
		}
		SootClass receiverClass = ((RefType) receiverType).getSootClass();
		if (!hier.isSubclassOf(receiverClass, alertDialogBuilderClass)) {
			return false;
		}
		String receiverClassName = receiverClass.getName();
		if (!alertDialogBuilderClassName.equals(receiverClassName)) {
			subclassesOfAlertDialogBuilder.add(receiverClass);
			return true;
		}

		if (recordAlertDialogBuilderSetters(s)) {
			return true;
		}
		if (recordAlertDialogBuilderCreateOrShow(s)) {
			return true;
		}
		return true;
	}

	// s - [lhs =] alertDialogBuilder.setXYZ(...)
	boolean recordAlertDialogBuilderSetters(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String name = callee.getName();
		boolean nameMatch = name.startsWith("set") && name.length() > 3;
		if (!nameMatch) {
			return false;
		}

		// Now, we know it is "s: [lhs = ]builder.setXYZ(...)"
		Local receiver = jimpleUtil.receiver(ie);
		Set<Stmt> setters = alertDialogBuilderAsReceiverSetterCalls.get(receiver);
		if (setters == null) {
			setters = Sets.newHashSet();
			alertDialogBuilderAsReceiverSetterCalls.put(receiver, setters);
		}
		setters.add(s);
		// If there is return value
		if (s instanceof DefinitionStmt) {
			Local lhs = jimpleUtil.lhsLocal(s);
			varNode(receiver).addEdgeTo(varNode(lhs));
		}
		return true;
	}

	// alertDialogBuilder.create() or alertDialogBuilder.show()
	boolean recordAlertDialogBuilderCreateOrShow(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();
		String name = callee.getName();

		// builder.show() is equiv "d = builder.create(); d.show()".
		boolean isCreate = name.equals("create");
		boolean isShow = name.equals("show");
		if (!isCreate && !isShow) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		Set<Stmt> createStmts = alertDialogBuilderCreateCalls.get(receiver);
		if (createStmts == null) {
			createStmts = Sets.newHashSet();
			alertDialogBuilderCreateCalls.put(receiver, createStmts);
		}
		createStmts.add(s);

		return true;
	}

	// Handles the "crazy" TabHost, TabSpec, TabWidget stuff

	// TabSpec (the parameter) to TabHost.addTab(TabSpec) calls
	Map<Local, Set<Stmt>> tabHostAddTabCalls = Maps.newHashMap();
	Map<NTabSpecNode, Set<Stmt>> resolvedTabHostAddTabCalls = Maps.newHashMap();

	// lhs TabSpec to TabHost.newTabSpec(String) calls
	// Map<Local, Set<Stmt>> tabHostNewTabSpecCalls = Maps.newHashMap();

	// TabSpec local to indicator strategy type recorded due to setIndiactor
	Map<Local, Set<Stmt>> tabSpecLabelIndicators = Maps.newHashMap();
	Map<Local, Set<Stmt>> tabSpecViewIndicators = Maps.newHashMap();

	Map<NTabSpecNode, Set<Stmt>> resolvedTabSpecLabelIndicators = Maps.newHashMap();
	Map<NTabSpecNode, Set<Stmt>> resolvedTabSpecViewIndicators = Maps.newHashMap();

	// Contents
	Map<Local, Set<Stmt>> tabSpecLayoutContents = Maps.newHashMap();
	Map<Local, Set<Stmt>> tabSpecFactoryContents = Maps.newHashMap();

	Map<NTabSpecNode, Set<Stmt>> resolvedTabSpecLayoutContents = Maps.newHashMap();
	Map<NTabSpecNode, Set<Stmt>> resolvedTabSpecFactoryContents = Maps.newHashMap();

	boolean recordTabHostRelatedCalls(Stmt s) {
		if (recordTabHostAddTab(s)) {
			return true;
		}
		if (recordTabHostNewTabSpec(s)) {
			return true;
		}
		if (recordTabSpecSetIndicator(s)) {
			return true;
		}
		if (recordTabSpecSetContent(s)) {
			return true;
		}
		return false;
	}

	boolean recordTabHostAddTab(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();

		String subsig = callee.getSubSignature();
		if (!subsig.equals(tabHostAddTabSugSig)) {
			return false;
		}

		SootClass receiverClass = ((RefType) jimpleUtil.receiver(ie).getType()).getSootClass();
		if (!receiverClass.getName().equals("android.widget.TabHost")) {
			System.out.println(
					"[WARNING] Unexpected receiver type " + receiverClass + " for " + s + " @ " + jimpleUtil.lookup(s));
			return true;
		}

		Value arg0 = ie.getArg(0);
		if (arg0 instanceof Local) {
			Local tabSpecLocal = (Local) arg0;
			MultiMapUtil.addKeyAndHashSetElement(tabHostAddTabCalls, tabSpecLocal, s);
		} else if (arg0 instanceof NullConstant) {
			; // ignore for now
		} else {
			throw new RuntimeException("Unexpected TabSpec for " + s + " @ " + jimpleUtil.lookup(s));
		}

		return true;
	}

	// Creates "inflated" TabSpec node, so not really recording anything.
	boolean recordTabHostNewTabSpec(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();

		String subsig = callee.getSubSignature();
		if (!subsig.equals(tabHostNewTabSpecSubSig)) {
			return false;
		}

		SootClass receiverClass = ((RefType) jimpleUtil.receiver(ie).getType()).getSootClass();
		if (!receiverClass.getName().equals("android.widget.TabHost")) {
			System.out.println(
					"[WARNING] Unexpected receiver type " + receiverClass + " for " + s + " @ " + jimpleUtil.lookup(s));
			return true;
		}

		if (!(s instanceof AssignStmt)) {
			return true;
		}

		Local lhs = jimpleUtil.lhsLocal(s);
		NTabSpecNode tabSpecNode = tabSpecNode(Scene.v().getSootClass("android.widget.TabHost$TabSpec"), s,
				jimpleUtil.lookup(s));
		tabSpecNode.addEdgeTo(varNode(lhs));
		// MultiMapUtil.addKeyAndHashSetElement(tabHostNewTabSpecCalls, lhs, s);

		return true;
	}

	boolean recordTabSpecSetIndicator(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();

		String subsig = callee.getSubSignature();
		boolean labelMatch = subsig.equals(tabSpecSetIndicatorCharSeqSubSig)
				|| subsig.equals(tabSpecSetIndicatorCharSeqDrawableSubSig);
		boolean viewMatch = subsig.equals(tabSpecSetIndicatorViewSubSig);
		if (!labelMatch && !viewMatch) {
			return false;
		}
		Local receiver = jimpleUtil.receiver(ie);
		if (labelMatch) {
			MultiMapUtil.addKeyAndHashSetElement(tabSpecLabelIndicators, receiver, s);
		} else {
			MultiMapUtil.addKeyAndHashSetElement(tabSpecViewIndicators, receiver, s);
		}

		if (s instanceof AssignStmt) {
			varNode(receiver).addEdgeTo(varNode(jimpleUtil.lhsLocal(s)));
		}

		return true;
	}

	boolean recordTabSpecSetContent(Stmt s) {
		InvokeExpr ie = s.getInvokeExpr();
		SootMethod callee = ie.getMethod();

		String subsig = callee.getSubSignature();
		boolean layoutMatch = subsig.equals(tabSpecSetContentIntSubSig);
		boolean factoryMatch = subsig.equals(tabSpecSetContentFactorySubSig);
		boolean intentMatch = subsig.equals(tabSpecSetContentIntentSubSig);
		if (!layoutMatch && !factoryMatch && !intentMatch) {
			return false;
		}

		Local receiver = jimpleUtil.receiver(ie);
		if (layoutMatch) {
			MultiMapUtil.addKeyAndHashSetElement(tabSpecLayoutContents, receiver, s);
		} else if (factoryMatch) {
			MultiMapUtil.addKeyAndHashSetElement(tabSpecFactoryContents, receiver, s);
		} else {
			System.out.println("[TODO] TabSpect.setContent(Intent): " + s + " @ " + jimpleUtil.lookup(s));
		}

		if (s instanceof AssignStmt) {
			varNode(receiver).addEdgeTo(varNode(jimpleUtil.lhsLocal(s)));
		}

		return true;
	}

	// TODO(tony): do this after menu item id stuff is added
	void processTabHostRelatedCalls() {
		receiverBackwardReachability(tabHostAddTabCalls, resolvedTabHostAddTabCalls, NTabSpecNode.class);

		// Two types indicators
		receiverBackwardReachability(tabSpecLabelIndicators, resolvedTabSpecLabelIndicators, NTabSpecNode.class);
		receiverBackwardReachability(tabSpecViewIndicators, resolvedTabSpecViewIndicators, NTabSpecNode.class);

		receiverBackwardReachability(tabSpecLayoutContents, resolvedTabSpecLayoutContents, NTabSpecNode.class);
		receiverBackwardReachability(tabSpecFactoryContents, resolvedTabSpecFactoryContents, NTabSpecNode.class);

		for (Map.Entry<NTabSpecNode, Set<Stmt>> addTabEntry : resolvedTabHostAddTabCalls.entrySet()) {
			NTabSpecNode tabSpecNode = addTabEntry.getKey();
			Set<Stmt> addTabCalls = addTabEntry.getValue();
			for (Stmt addTab : addTabCalls) {
				processAddTabCall(tabSpecNode, addTab);
			}
		}
	}

	Map<NTabSpecNode, Set<NVarNode>> tabSpecIndicatorNodes = Maps.newHashMap();
	Map<NTabSpecNode, Set<NVarNode>> tabSpecContentNodes = Maps.newHashMap();

	void processTabSpecNode(NTabSpecNode tabSpecNode) {
		Integer tabIndicatorLayout = xmlUtil.getSystemRLayoutValue("tab_indicator");
		if (tabIndicatorLayout == null) {
			tabIndicatorLayout = xmlUtil.getSystemRLayoutValue("tab_indicator_holo");
			if (tabIndicatorLayout == null) {
				throw new RuntimeException("tab_indicator layout file unknown!");
			}
		}
		NLayoutIdNode layoutIdNode = layoutIdNode(tabIndicatorLayout);
		for (Stmt setLabelIndicator : MultiMapUtil.getNonNullHashSetByKey(resolvedTabSpecLabelIndicators,
				tabSpecNode)) {
			String fakeName = nextFakeName();
			Local fakeLocal = Jimple.v().newLocal(fakeName, RefType.v("android.view.View"));
			NVarNode viewNode = varNode(fakeLocal);
			Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(setLabelIndicator,
					jimpleUtil.lookup(setLabelIndicator));
			NInflate1OpNode infalte1 = new NInflate1OpNode(layoutIdNode, viewNode, callSite, true);
			allNNodes.add(infalte1);
			MultiMapUtil.addKeyAndHashSetElement(tabSpecIndicatorNodes, tabSpecNode, viewNode);
		}
		for (Stmt setViewIndicator : MultiMapUtil.getNonNullHashSetByKey(resolvedTabSpecViewIndicators, tabSpecNode)) {
			Value arg0 = setViewIndicator.getInvokeExpr().getArg(0);
			if (arg0 instanceof Local) {
				NVarNode viewNode = varNode((Local) arg0);
				MultiMapUtil.addKeyAndHashSetElement(tabSpecIndicatorNodes, tabSpecNode, viewNode);
			}
		}
		for (Stmt setLayoutContent : MultiMapUtil.getNonNullHashSetByKey(resolvedTabSpecLayoutContents, tabSpecNode)) {
			Value arg0 = setLayoutContent.getInvokeExpr().getArg(0);
			NNode contentLayoutIdNode = simpleNode(arg0);
			if (contentLayoutIdNode == null) {
				continue;
			}
			String fakeName = nextFakeName();
			Local fakeLocal = Jimple.v().newLocal(fakeName, RefType.v("android.view.View"));
			NVarNode viewNode = varNode(fakeLocal);
			Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(setLayoutContent,
					jimpleUtil.lookup(setLayoutContent));
			NInflate1OpNode infalte1 = new NInflate1OpNode(layoutIdNode, viewNode, callSite, true);
			allNNodes.add(infalte1);
			MultiMapUtil.addKeyAndHashSetElement(tabSpecContentNodes, tabSpecNode, viewNode);
		}
		for (Stmt setFactoryContent : MultiMapUtil.getNonNullHashSetByKey(resolvedTabSpecFactoryContents,
				tabSpecNode)) {
			Value arg0 = setFactoryContent.getInvokeExpr().getArg(0);
			if (arg0 instanceof NullConstant) {
				continue;
			}
			Local factoryLocal = (Local) arg0;
			for (NNode node : graphUtil.backwardReachableNodes(varNode(factoryLocal))) {
				if (!(node instanceof NObjectNode)) {
					continue;
				}
				SootClass factoryClass = ((NObjectNode) node).getClassType();
				SootClass match = hier.matchForVirtualDispatch(tabContentFactoryCreateSubSig, factoryClass);
				if (match != null && match.isApplicationClass()) {
					SootMethod createTabContent = match.getMethod(tabContentFactoryCreateSubSig);
					for (Value ret : jimpleUtil.getReturnValues(createTabContent)) {
						if (ret instanceof NullConstant) {
							continue;
						}
						NVarNode viewNode = varNode((Local) ret);
						MultiMapUtil.addKeyAndHashSetElement(tabSpecContentNodes, tabSpecNode, viewNode);
					}
				}
			}
		}
	}

	void processAddTabCall(NTabSpecNode tabSpecNode, Stmt addTab) {
		processTabSpecNode(tabSpecNode);

		Local tabHost = jimpleUtil.receiver(addTab);
		NVarNode tabHostNode = varNode(tabHost);

		Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(addTab, jimpleUtil.lookup(addTab));

		// mTabWidget = (TabWidget)
		// findViewById(com.android.internal.R.id.tabs);
		Local mTabWidget = Jimple.v().newLocal(nextFakeName(), RefType.v("android.widget.TabWidget"));
		NVarNode mTabWidgetNode = varNode(mTabWidget);
		NNode tabsIdNode = widgetIdNode(xmlUtil.getSystemRIdValue("tabs"));
		NFindView1OpNode findTabWidget = new NFindView1OpNode(tabsIdNode, tabHostNode, mTabWidgetNode, callSite,
				FindView1Type.Ordinary, true);
		allNNodes.add(findTabWidget);

		// mTabWidget.addView(tabIndicator);
		for (NVarNode tabIndicatorNode : MultiMapUtil.getNonNullHashSetByKey(tabSpecIndicatorNodes, tabSpecNode)) {
			NAddView2OpNode addView2 = new NAddView2OpNode(mTabWidgetNode, tabIndicatorNode, callSite, true);
			allNNodes.add(addView2);
		}

		// mTabContent = (FrameLayout)
		// findViewById(com.android.internal.R.id.tabcontent);
		Local mTabContent = Jimple.v().newLocal(nextFakeName(), RefType.v("android.widget.FrameLayout"));
		NVarNode mTabContentNode = varNode(mTabContent);
		NNode tabContentIdNode = widgetIdNode(xmlUtil.getSystemRIdValue("tabcontent"));
		NFindView1OpNode findTabContent = new NFindView1OpNode(tabContentIdNode, tabHostNode, mTabContentNode, callSite,
				FindView1Type.Ordinary, true);
		allNNodes.add(findTabContent);

		// mCurrentView = spec.mContentStrategy.getContentView();
		// mTabContent.addView(mCurrentView)
		for (NVarNode mCurrentViewNode : MultiMapUtil.getNonNullHashSetByKey(tabSpecContentNodes, tabSpecNode)) {
			NAddView2OpNode addView2 = new NAddView2OpNode(mTabContentNode, mCurrentViewNode, callSite, true);
			allNNodes.add(addView2);
		}
	}

	// --- Helper method for inflate operation nodes
	public boolean ignoreLayoutIdCall(Value layoutIdVal) {
		// Type check
		if (!isIntValue(layoutIdVal)) {
			return true;
		}

		// If not a constant int, cannot ignore
		if (!(layoutIdVal instanceof IntConstant)) {
			return false;
		}

		// Ignore if a "null" is used.
		return 0 == ((IntConstant) layoutIdVal).value;
	}

	public boolean isIntValue(Value v) {
		Type type = v.getType();
		return type instanceof IntType;
	}

	// --- Menu
	// The var node for the menu:ContextMenu in onCreateContextMenu to the
	// context menu node. The next map goes "hand-in-hand" with this one - it
	// maps the context menu node to the onCreateContextMenu method.
	public HashMap<NVarNode, NContextMenuNode> menuVarNodeToContextMenus = Maps.newHashMap();

	HashMap<NContextMenuNode, SootMethod> contextMenuToOnCreateContextMenus = Maps.newHashMap();

	// Build nodes

	/*
	 * WARNING: if this method is called from FixpointSolver, remember to check
	 * if we need to patch solutionReceivers!!!
	 *
	 * This is *only* for OnCreateContextMenuListener.onCreateContextMenu
	 * ContextMenu actualMenu = ... View actualView = ...
	 *
	 * onCreateContextMenu(ContextMenu formalMenu, View formalView) { formalMenu
	 * = actualMenu formalView = actualView }
	 *
	 * Binding of views is handled externally.
	 */
	NContextMenuNode findOrCreateContextMenuNode(SootMethod onCreateContextMenuMethod, NVarNode actualViewNode) {
		// Sanity check
		String subsig = onCreateContextMenuMethod.getSubSignature();
		if (!subsig.equals(onCreateContextMenuSubSig)) {
			throw new RuntimeException("Unexpected context menu creation method " + onCreateContextMenuMethod);
		}
		Local formalMenu = jimpleUtil.localForNthParameter(onCreateContextMenuMethod, 1);
		return findOrCreateContextMenuNode(onCreateContextMenuMethod, varNode(formalMenu), actualViewNode);
	}

	NContextMenuNode findOrCreateContextMenuNode(SootMethod onCreateContextMenuMethod, NVarNode formalMenuNode,
			NVarNode actualViewNode) {
		NContextMenuNode actualMenuNode = menuVarNodeToContextMenus.get(formalMenuNode);
		if (actualMenuNode == null) {
			actualMenuNode = new NContextMenuNode();
			actualMenuNode.menuParameterNode = formalMenuNode;
			actualMenuNode.addEdgeTo(formalMenuNode);
			menuVarNodeToContextMenus.put(formalMenuNode, actualMenuNode);
			contextMenuToOnCreateContextMenus.put(actualMenuNode, onCreateContextMenuMethod);
			allNNodes.add(actualMenuNode);
		}
		actualMenuNode.varNodesForRegisteredViews.add(actualViewNode);

		return actualMenuNode;
	}

	// onContextItemSelectedSubSig, onMenuItemSelectedSubsig
	void connectToFakeContextMenuVarNode(NContextMenuNode contextMenu, SootMethod onCreateContextMenuMethod) {
		// NOTE(tony): ignore the receiver class, and merge things together. In
		// future, we could make it more precise, which may not improve anything
		// at all
		boolean found = false;
		for (Map<SootMethod, Set<SootMethod>> createAndItemMethods : activityToCreateOrPrepareMenuAndItemSelected
				.values()) {
			Set<SootMethod> setOfItemMethods = createAndItemMethods.get(onCreateContextMenuMethod);
			if (setOfItemMethods == null || setOfItemMethods.isEmpty()) {
				continue;
			}
			found = true;
			for (SootMethod itemMethod : setOfItemMethods) {
				NVarNode fakeMenuNode = itemSelectedAndFakeVarNodes.get(itemMethod);
				if (fakeMenuNode == null) {
					throw new RuntimeException();
				}
				contextMenu.addEdgeTo(fakeMenuNode);
			}
		}
		if (!found && Configs.verbose) {
			System.out.println("[WARNING] No itemSelected method for " + onCreateContextMenuMethod);
		}
	}

	/*
	 * on*ItemSelected(..., MenuItem item) ->
	 *
	 * item = <menu>.findChild()
	 *
	 * Returns the resolved on*ItemSelected() method
	 *
	 * NOTE(tony): we assume on*ItemSelected is always defined in the same class
	 * as corresponding onCreate*Menu and onPrepare*Menu.
	 */
	SootMethod modelFlowFromCreateOrPrepareMenuToItemSelected(NNode menuNode, SootClass menuClass,
			SootClass activityClass, SootMethod createOrPrepareMenuMethod, String itemSelectedSubsig,
			int menuItemPosition) {
		SootClass itemSelectedClass = hier.matchForVirtualDispatch(itemSelectedSubsig, activityClass);
		if (itemSelectedClass == null || !itemSelectedClass.isApplicationClass()) {
			return null;
		}
		SootMethod itemSelectedMethod = itemSelectedClass.getMethod(itemSelectedSubsig);
		if (menuNode == null) {
			menuNode = itemSelectedAndFakeVarNodes.get(itemSelectedMethod);
			if (menuNode == null) {
				String fakeMenuLocalName = nextFakeName();
				Local fakeMenu = Jimple.v().newLocal(fakeMenuLocalName, menuClass.getType());
				menuNode = varNode(fakeMenu);
				itemSelectedAndFakeVarNodes.put(itemSelectedMethod, (NVarNode) menuNode);
			}
		}
		modelFlowFromCreateOrPrepareMenuToItemSelected(menuNode, menuClass, activityClass, createOrPrepareMenuMethod,
				itemSelectedMethod, menuItemPosition);
		return itemSelectedMethod;
	}

	void modelFlowFromCreateOrPrepareMenuToItemSelected(NNode menuNode, SootClass menuClass, SootClass activityClass,
			SootMethod createOrPrepareMenuMethod, SootMethod itemSelectedMethod, int menuItemPosition) {
		Map<SootMethod, Set<SootMethod>> methods = activityToCreateOrPrepareMenuAndItemSelected.get(activityClass);
		if (methods == null) {
			methods = Maps.newHashMap();
			activityToCreateOrPrepareMenuAndItemSelected.put(activityClass, methods);
		}
		Set<SootMethod> setOfItemSelectedMethods = methods.get(createOrPrepareMenuMethod);
		if (setOfItemSelectedMethods == null) {
			setOfItemSelectedMethods = Sets.newHashSet();
			methods.put(createOrPrepareMenuMethod, setOfItemSelectedMethods);
		}
		setOfItemSelectedMethods.add(itemSelectedMethod);

		Local menuItemLocal = jimpleUtil.localForNthParameter(itemSelectedMethod, menuItemPosition);

		// r = <MENU>
		String findItemReceiverLocalName = nextFakeName();
		Local findItemReceiverLocal = Jimple.v().newLocal(findItemReceiverLocalName, menuClass.getType());
		NVarNode findItemReceiverVarNode = varNode(findItemReceiverLocal);
		menuNode.addEdgeTo(findItemReceiverVarNode);

		// lhs = r.findChild()
		String findItemResultLocalName = nextFakeName();
		Local findItemResultLocal = Jimple.v().newLocal(findItemResultLocalName,
				Scene.v().getSootClass("android.view.MenuItem").getType());
		NVarNode findItemResultVarNode = varNode(findItemResultLocal);
		// TODO(tony): right now, we consider the callsite to be null. We may
		// instead use the first statement of the method as the call site.
		NOpNode findView3 = new NFindView3OpNode(findItemReceiverVarNode, findItemResultVarNode, null,
				NFindView3OpNode.FindView3Type.FindChildren, true);
		allNNodes.add(findView3);
		// item = lhs
		findItemResultVarNode.addEdgeTo(varNode(menuItemLocal));
	}

	/**
	 * Find/create the NVarNode corresponding to the specified Local.
	 *
	 * @param l
	 *            the specified Local
	 * @return the corresponding NVarNode
	 */
	public NVarNode varNode(Local l) {
		NVarNode x = allNVarNodes.get(l);
		if (x != null) {
			return x;
		}
		x = new NVarNode();
		x.l = l;
		allNVarNodes.put(l, x);
		allNNodes.add(x);
		return x;
	}

	/**
	 * Lookup but not create the NVarNode corresponding to the specified Local.
	 * A null will be returned if there is no such node.
	 * 
	 * @param local
	 *            the specified Local
	 * @return the corresponding NVarNode; null if not exist
	 */
	public NVarNode lookupVarNode(Local local) {
		return allNVarNodes.get(local);
	}

	public NFieldNode fieldNode(SootField f) {
		NFieldNode x = allNFieldNodes.get(f);
		if (x != null) {
			return x;
		}
		x = new NFieldNode();
		x.f = f;
		allNFieldNodes.put(f, x);
		allNNodes.add(x);
		return x;
	}

	public NFieldNode lookupFieldNode(SootField field) {
		return allNFieldNodes.get(field);
	}

	public NObjectNode allocNodeOrSpecialObjectNode(Expr e) {
		if (e instanceof NewExpr) {
			SootClass type = ((RefType) ((NewExpr) e).getType()).getSootClass();
			if (hier.isSubclassOf(type, Scene.v().getSootClass("android.app.Dialog"))) {
				jimpleUtil.record(e, currentStmt);
				return dialogNode(type, currentStmt, currentMethod);
			}
			if (hier.isSubclassOf(type, Scene.v().getSootClass("android.widget.TabHost$TabSpec"))) {
				jimpleUtil.record(e, currentStmt);
				return tabSpecNode(type, currentStmt, currentMethod);
			}
		}
		return allocNode(e);
	}

	public NAllocNode allocNode(Expr e) {
		NAllocNode x = allNAllocNodes.get(e);
		if (x != null) {
			return x;
		}
		if (e instanceof NewExpr) {
			SootClass c = ((NewExpr) e).getBaseType().getSootClass();
			if (hier.viewClasses.contains(c)) {
				x = createViewAllocNode(c);
			} else if (listenerSpecs.isListenerType(c)) {
				x = new NListenerAllocNode(c);
			}
		}
		if (x == null) {
			x = new NAllocNode();
		}
		x.e = e;
		allNAllocNodes.put(e, x);
		allNNodes.add(x);
		return x;
	}

	NViewAllocNode createViewAllocNode(SootClass c) {
		// Original code (SHOULD KEEP)
		NViewAllocNode x = new NViewAllocNode();
		// in the future will fill up "parent" and "id"
		x.c = c;

		// Additional code to find onCreateContextMenu
		SootClass matched = hier.matchForVirtualDispatch(viewOnCreateContextMenuSubSig, c);
		if (matched == null) {
			throw new RuntimeException(viewOnCreateContextMenuSubSig + " cannot be dispatched for " + c);
		}
		if (matched.isApplicationClass()) {
			SootMethod m = matched.getMethod(viewOnCreateContextMenuSubSig);
			Local thisVar = jimpleUtil.thisLocal(m);
			NVarNode thisVarNode = varNode(thisVar);
			x.addEdgeTo(thisVarNode);
			NVarNode formalMenuNode = varNode(jimpleUtil.localForNthParameter(m, 1));
			findOrCreateContextMenuNode(m, formalMenuNode, thisVarNode);
		}
		return x;
	}

	public NInflNode inflNode(SootClass c) {
		NInflNode x = new NInflNode();
		x.c = c;
		allNNodes.add(x);
		return x;
	}

	/**
	 * Creates the NActivityNode node in the flowgraph for the specified
	 * activity class. If such a node already exists, return it directly.
	 * 
	 * @param activityClass
	 *            specifies the activity class the result node corresponds to
	 * @return the desired NActivityNode node
	 */
	public NActivityNode activityNode(SootClass activityClass) {
		Preconditions.checkArgument(activityClass != null && !activityClass.isAbstract());
		NActivityNode activityNode = allNActivityNodes.get(activityClass);
		if (activityNode != null) {
			return activityNode;
		}
		activityNode = new NActivityNode();
		activityNode.c = activityClass;
		allNActivityNodes.put(activityClass, activityNode);
		allNNodes.add(activityNode);
		return activityNode;
	}

	public NDialogNode dialogNode(SootClass dialogClass, Stmt allocStmt, SootMethod allocMethod) {
		NDialogNode dialogNode = new NDialogNode(dialogClass, allocStmt, allocMethod);
		allNNodes.add(dialogNode);
		allNDialogNodes.put(allocStmt, dialogNode);
		// Connect to application lifecycle methods
		flowToDialogLifecycleMethods(dialogNode);

		return dialogNode;
	}

	public NTabSpecNode tabSpecNode(SootClass tabSpecClass, Stmt allocStmt, SootMethod allocMethod) {
		NTabSpecNode tabSpecNode = new NTabSpecNode(tabSpecClass, allocStmt, allocMethod);
		allNNodes.add(tabSpecNode);

		return tabSpecNode;
	}

	public NLayoutIdNode layoutIdNode(Integer i) {
		NLayoutIdNode x = allNLayoutIdNodes.get(i);
		if (x != null) {
			return x;
		}
		x = new NLayoutIdNode(i);
		allNLayoutIdNodes.put(i, x);
		allNNodes.add(x);
		return x;
	}

	public NMenuIdNode menuIdNode(Integer i) {
		NMenuIdNode x = allNMenuIdNodes.get(i);
		if (x != null) {
			return x;
		}
		x = new NMenuIdNode(i);
		allNMenuIdNodes.put(i, x);
		allNNodes.add(x);
		return x;
	}

	public NWidgetIdNode widgetIdNode(Integer i) {
		Preconditions.checkNotNull(i);
		NWidgetIdNode x = allNWidgetIdNodes.get(i);
		if (x != null) {
			return x;
		}
		x = new NWidgetIdNode(i);
		allNWidgetIdNodes.put(i, x);
		allNNodes.add(x);
		return x;
	}

	Map<Integer, NAnonymousIdNode> anonymousIdNodes = Maps.newHashMap();

	public NAnonymousIdNode anonymousWidgetIdNode(Integer i) {
		Preconditions.checkNotNull(i);
		NAnonymousIdNode node = anonymousIdNodes.get(i);
		if (node == null) {
			node = new NAnonymousIdNode(i);
			anonymousIdNodes.put(i, node);
			allNNodes.add(node);
		}

		return node;
	}

	public NStringIdNode stringIdNode(Integer stringId) {
		Preconditions.checkNotNull(stringId);
		NStringIdNode stringIdNode = allNStringIdNodes.get(stringId);
		if (stringIdNode == null) {
			stringIdNode = new NStringIdNode(stringId);
			allNStringIdNodes.put(stringId, stringIdNode);
			allNNodes.add(stringIdNode);
			// System.out.println("{Flowgraph.stringIdNode} " + stringIdNode);
		}
		return stringIdNode;
	}

	public NDrawableIdNode drawableIdNode(Integer drawableId) {
		Preconditions.checkNotNull(drawableId);
		NDrawableIdNode drawableIdNode = allNDrawableIdNode.get(drawableId);
		if (drawableIdNode == null) {
			drawableIdNode = new NDrawableIdNode(drawableId);
			allNDrawableIdNode.put(drawableId, drawableIdNode);
			allNNodes.add(drawableIdNode);
			// System.out.println("{Flowgraph.stringIdNode} " + stringIdNode);
		}
		return drawableIdNode;
	}

	public NStringConstantNode stringConstantNode(String value) {
		Preconditions.checkNotNull(value);
		NStringConstantNode stringConstantNode = allNStringConstantNodes.get(value);
		if (stringConstantNode == null) {
			stringConstantNode = new NStringConstantNode();
			stringConstantNode.value = value;
			allNStringConstantNodes.put(value, stringConstantNode);
			allNNodes.add(stringConstantNode);
		}
		return stringConstantNode;
	}

	public NNode simpleNode(Value jimpleValue) {
		if (jimpleValue instanceof FieldRef) {
			return fieldNode(((FieldRef) jimpleValue).getField());
		}
		if (jimpleValue instanceof Local) {
			return varNode((Local) jimpleValue);
		}
		if (jimpleValue instanceof IntConstant) {
			Integer integerConstant = new Integer(((IntConstant) jimpleValue).value);
			if (allLayoutIds.contains(integerConstant)) {
				return layoutIdNode(integerConstant);
			}
			if (allMenuIds.contains(integerConstant)) {
				return menuIdNode(integerConstant);
			}
			if (allWidgetIds.contains(integerConstant)) {
				return widgetIdNode(integerConstant);
			}
			if (allStringIds.contains(integerConstant)) {
				return stringIdNode(integerConstant);
			}

			if (allDrawableIds.contains(integerConstant)) {
				return drawableIdNode(integerConstant);
			}
		}
		if (jimpleValue instanceof StringConstant) {
			String stringValue = ((StringConstant) jimpleValue).value;
			return stringConstantNode(stringValue);
		}
		if (jimpleValue instanceof NewExpr || jimpleValue instanceof NewArrayExpr
				|| jimpleValue instanceof NewMultiArrayExpr) {
			return allocNodeOrSpecialObjectNode((Expr) jimpleValue);
		}
		if (jimpleValue instanceof CastExpr) {
			return simpleNode(((CastExpr) jimpleValue).getOp());
		}
		return null;
	}

	// === END of nodes

	/*
	 * void preProcessAppClasses(){ Map<Integer, Pair<String, Boolean>>
	 * callbacks = XMLParser.Factory.getXMLParser().retrieveCallbacks();
	 * if(callbacks.isEmpty()){ return; } for(SootClass appClz :
	 * hier.appClasses){ for (Iterator<SootMethod> iter =
	 * appClz.getMethods().iterator(); iter.hasNext();) { currentMethod =
	 * iter.next(); if (!currentMethod.isConcrete()) { continue; } Body body =
	 * currentMethod.retrieveActiveBody(); PatchingChain<Unit> units =
	 * body.getUnits(); Iterator<Unit> stmtItr = units.snapshotIterator();
	 * while(stmtItr.hasNext()){ currentStmt = (Stmt) stmtItr.next(); if
	 * (currentStmt instanceof ReturnVoidStmt) { continue; } if (currentStmt
	 * instanceof ThrowStmt) { continue; } if (currentStmt instanceof GotoStmt)
	 * { continue; } if (currentStmt instanceof BreakpointStmt) { continue; } if
	 * (currentStmt instanceof NopStmt) { continue; } if (currentStmt instanceof
	 * RetStmt) { continue; } if (currentStmt instanceof IfStmt) { continue; }
	 * if (currentStmt instanceof TableSwitchStmt) { continue; } if (currentStmt
	 * instanceof LookupSwitchStmt) { continue; } if (currentStmt instanceof
	 * MonitorStmt) { continue; } if (currentStmt.containsInvokeExpr()){ NOpNode
	 * findView2 = createFindView2OpNode(currentStmt); if (findView2 != null &&
	 * (currentStmt instanceof DefinitionStmt)) { DefinitionStmt as1 =
	 * (DefinitionStmt) currentStmt; Value rhs1 = as1.getRightOp(); Value lhs2 =
	 * as2.getLeftOp(); if(!(rhs1 instanceof VirtualInvokeExpr)) continue;
	 * VirtualInvokeExpr expr = (VirtualInvokeExpr)rhs1; if(!(expr.getArg(0)
	 * instanceof IntConstant)) continue; Integer id =
	 * ((IntConstant)expr.getArg(0)).value; Boolean isCreated =
	 * callBacksInXML.get(id).getO2(); if(isCreated) continue; else isCreated =
	 * true; String callBackName = callBacksInXML.get(id).getO1();
	 * if(callBackName == null) continue; String fakeMethodName =
	 * "fake"+callBackName+id; SootClass createdClass =
	 * createFakeOnClickListenerClass(c.getName().toString(),callBackName,
	 * fakeMethodName); SootMethod fakeMethod =
	 * createFakeMethod(c,fakeMethodName,createdClass); Jimple jimple =
	 * Jimple.v(); Expr virtualInvokeExpr =
	 * jimple.newVirtualInvokeExpr(b.getThisLocal(), fakeMethod.makeRef(),
	 * Lists.newArrayList(lhs2)); Stmt virtualInvokeStmt =
	 * Jimple.v().newInvokeStmt(virtualInvokeExpr);
	 * units.insertAfter(virtualInvokeStmt, nextStmt); } } } } } }
	 */

	/**
	 * Inflate Menu Items.
	 * 
	 * @param c
	 * @param attrs
	 * @return
	 */
	public NMenuItemInflNode inflMenuItemNode(SootClass c, HashMap<String, String> attrs) {
		NMenuItemInflNode x = new NMenuItemInflNode();
		x.c = c;
		x.attrs = attrs;
		allNNodes.add(x);
		return x;
	}
}
