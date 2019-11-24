/*
 * GatorIntentAnalysis.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import presto.android.Configs;
import presto.android.Debug;
import presto.android.Hierarchy;
import presto.android.MethodNames;
import presto.android.MultiMapUtil;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.GraphUtil;
import presto.android.gui.JimpleUtil;
import presto.android.gui.graph.NAllocNode;
import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.wtg.WTGAnalysisOutput;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph;
import presto.android.gui.wtg.flowgraph.NAnyValueNode;
import presto.android.gui.wtg.flowgraph.NStartActivityOpNode;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph.Edge;
import presto.android.gui.wtg.intent.IntentAnalysis;
import presto.android.xml.XMLParser;
import presto.android.xml.XMLParser.ActivityLaunchMode;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.OrExpr;
import soot.jimple.Stmt;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/*
 * This class depends heavily on the intent analysis implemented in the wtg
 * construction algorithm.
 */
public class GatorIntentAnalysis implements IntentAnalysisInterface {
  private static GatorIntentAnalysis theInstance;

  private final GUIAnalysisOutput output;
  private IntentAnalysis intentAnalysis;
  private final GraphUtil graphUtil = GraphUtil.v();
  private final JimpleUtil jimpleUtil = JimpleUtil.v();
  private final Hierarchy hier = Hierarchy.v();
  private final XMLParser manifest = XMLParser.Factory.getXMLParser();
  private final AndroidCallGraph callgraph = AndroidCallGraph.v();

  private final SootClass INTENT_CLASS =
      Scene.v().getSootClass("android.content.Intent");

  // startActivity call statement and the corresponding op node.
  Map<Stmt, NStartActivityOpNode> stmtAndStartActivityNodes = Maps.newHashMap();

  Map<Stmt, Set<NObjectNode>> startActivityAndIntentObjects = Maps.newHashMap();

  Map<Stmt, Set<NObjectNode>> menuItemSetIntentAndIntentObjects = Maps.newHashMap();

  // Merge startActivityAndIntentObjects and menuItemSetIntentAndIntentObjects
  Map<Stmt, Set<NObjectNode>> activityLaunchAndIntentObjects = Maps.newHashMap();

  // intent object and the corresponding IntentFlags
  Map<NObjectNode, IntentFlags> intentObjectAndIntentFlags = Maps.newHashMap();

  // An activity class and the startActivity calls that can be invoked when the
  // activity is top of stack.
  Map<String, Set<Stmt>> activityAndActivityLaunchCalls = Maps.newHashMap();

  private GatorIntentAnalysis(GUIAnalysisOutput output) {
    this.output = output;
    runOnce();
  }

  public static GatorIntentAnalysis v(GUIAnalysisOutput output) {
    if (theInstance == null) {
      theInstance = new GatorIntentAnalysis(output);
    }
    return theInstance;
  }

  void runOnce() {
    // Step 1: run the intent analysis
    intentAnalysis = new WTGAnalysisOutput(output, null).getIntentAnalysis();

    // Step 2: post-processing
    processStartActivityCalls();
    processMenuItemSetIntentCalls();
    activityLaunchAndIntentObjects.putAll(startActivityAndIntentObjects);
    activityLaunchAndIntentObjects.putAll(menuItemSetIntentAndIntentObjects);
    startActivityAndIntentObjects = null;
    menuItemSetIntentAndIntentObjects = null;

    processSetOrAddFlagsCalls();
    associateActivityWithActivityLaunchCalls();
  }

  void processStartActivityCalls() {
    for (NOpNode opNode : NOpNode.getNodes(NStartActivityOpNode.class)) {
      NStartActivityOpNode startActivity = (NStartActivityOpNode) opNode;
      Stmt stmt = startActivity.callSite.getO1();
      stmtAndStartActivityNodes.put(stmt, startActivity);

      NNode intentNode = startActivity.getParameter();
      for (NNode n : graphUtil.backwardReachableNodes(intentNode)) {
        if (n instanceof NObjectNode) {
          MultiMapUtil.addKeyAndHashSetElement(
              startActivityAndIntentObjects, stmt, (NObjectNode)n);
        }
      }
    }
  }

  void processMenuItemSetIntentCalls() {
    for (SootClass c : hier.appClasses) {
      for (SootMethod m : c.getMethods()) {
        if (!m.isConcrete()) {
          continue;
        }
        for (Iterator<Unit> stmts =
            m.retrieveActiveBody().getUnits().iterator(); stmts.hasNext();) {
          Stmt s = (Stmt) stmts.next();
          if (!s.containsInvokeExpr()) {
            continue;
          }
          InvokeExpr ie = s.getInvokeExpr();
          if (!(ie instanceof InstanceInvokeExpr)) {
            continue;
          }
          InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
          Local receiver = (Local) iie.getBase();
          Type receiverType = receiver.getType();
          if (!(receiverType instanceof RefType)) {
            continue;
          }
          SootClass receiverClass = ((RefType)receiverType).getSootClass();
          if (!hier.isSubclassOf(receiverClass,
              Scene.v().getSootClass("android.view.MenuItem"))) {
            continue;
          }
          SootMethod callee = ie.getMethod();
          String subsig = callee.getSubSignature();
          if (!subsig.equals(MethodNames.menuItemSetIntentSubSig)) {
            continue;
          }
          NVarNode intentNode =
              output.getFlowgraph().varNode((Local) ie.getArg(0));
          for (NNode n : graphUtil.backwardReachableNodes(intentNode)) {
            if (n instanceof NObjectNode) {
              NObjectNode intentObject = (NObjectNode) n;
              MultiMapUtil.addKeyAndHashSetElement(
                  menuItemSetIntentAndIntentObjects, s, intentObject);
            }
          }
        }
      } // methods
    } // app classes
  }

  void processSetOrAddFlagsCalls() {
    for (SootClass c : hier.appClasses) {
      for (SootMethod m : c.getMethods()) {
        if (!m.isConcrete()) {
          continue;
        }
        for (Iterator<Unit> stmts =
            m.retrieveActiveBody().getUnits().iterator(); stmts.hasNext();) {
          Stmt s = (Stmt) stmts.next();
          if (!s.containsInvokeExpr()) {
            continue;
          }
          InvokeExpr ie = s.getInvokeExpr();
          if (!(ie instanceof InstanceInvokeExpr)) {
            continue;
          }
          InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
          Local receiver = (Local) iie.getBase();
          Type receiverType = receiver.getType();
          if (!(receiverType instanceof RefType)) {
            continue;
          }
          SootClass receiverClass = ((RefType)receiverType).getSootClass();
          if (!hier.isSubclassOf(receiverClass, INTENT_CLASS)) {
            continue;
          }
          SootMethod callee = ie.getMethod();
          String subsig = callee.getSubSignature();
          if (!subsig.equals(MethodNames.intentAddFlagsSubSig)
              && !subsig.equals(MethodNames.intentSetFlagsSubSig)) {
            continue;
          }
          processSetOrAddFlagsCall(s);
        }
      }
    }
  }

  // Resolve intent flags and associate with intent object
  void processSetOrAddFlagsCall(Stmt s) {
    SootMethod caller = jimpleUtil.lookup(s);
    String str = s + " @ " + caller;
    InvokeExpr ie = s.getInvokeExpr();
    String calleeName = ie.getMethod().getName();

    Set<NObjectNode> intentObjects = Sets.newHashSet();
    NVarNode intentNode = output.getFlowgraph().varNode(jimpleUtil.receiver(s));
    for (NNode n : graphUtil.backwardReachableNodes(intentNode)) {
      if (n instanceof NObjectNode) {
        intentObjects.add((NObjectNode) n);
      }
    }
    if (intentObjects.isEmpty()) {
      return;
    }

    IntentFlags.Type type = null;
    if (calleeName.equals("addFlags")) {
      type = IntentFlags.Type.AddFlags;
    } else if (calleeName.equals("setFlags")) {
      type = IntentFlags.Type.SetFlags;
    } else {
      throw new RuntimeException(str);
    }

    Value flags = ie.getArg(0);
    IntentFlags intentFlags = null;

    if (flags instanceof IntConstant) {
      int val = ((IntConstant)flags).value;
      intentFlags = IntentFlags.v(val, type);
    } else {
      Set<Integer> values = naiveLocalSlicing((Local)flags, s, caller);
      if (values.size() > 1) {
        throw new RuntimeException();
      }
      if (!values.isEmpty()) {
        intentFlags = IntentFlags.v(values.iterator().next(), type);
      }
    }
    if (Configs.verbose) {
      if (intentFlags == null) {
        System.out.println("<non-const> | " + str);
      } else {
        System.out.println(intentFlags + " | " + str);
      }
    }

    if (intentFlags == null) {
      return;
    }
    for (NObjectNode intentObject : intentObjects) {
      IntentFlags currentFlags = intentObjectAndIntentFlags.get(intentObject);
      if (currentFlags == null) {
        intentObjectAndIntentFlags.put(intentObject, intentFlags);
      } else {
        currentFlags.merge(intentFlags);
      }
    }
  }

  // Naive intra-procedural "slicing" to find intent flag values. It handles
  // *only* patterns that exist in the 20 apps.
  Set<Integer> naiveLocalSlicing(Local targetFlag, Stmt s, SootMethod caller) {
    // Get the possible values
    Map<Local, Set<Value>> possibleValues = Maps.newHashMap();
    possibleValues.put(targetFlag, Sets.<Value>newHashSet());
    PatchingChain<Unit> units = caller.retrieveActiveBody().getUnits();
    Stmt first = (Stmt) units.getFirst();
    for (Stmt current = (Stmt) units.getPredOf(s);
        current != first; current = (Stmt) units.getPredOf(current)) {
      if (!(current instanceof AssignStmt)) {
        continue;
      }
      if (current == s) {
        break;
      }
      AssignStmt assign = (AssignStmt) current;
      Local lhs = (Local) assign.getLeftOp();
      Value rhs = assign.getRightOp();
      if (possibleValues.containsKey(lhs)) {
        MultiMapUtil.addKeyAndHashSetElement(possibleValues, lhs, rhs);
        if (rhs instanceof Local) {
          if (possibleValues.containsKey(rhs)) {
            throw new RuntimeException();
          }
          possibleValues.put((Local)rhs, Sets.<Value>newHashSet());
        } else if (rhs instanceof OrExpr) {
          OrExpr orExpr = (OrExpr) rhs;
          Value op1 = orExpr.getOp1();
          Value op2 = orExpr.getOp2();
          if (op1 instanceof Local) {
            if (possibleValues.containsKey(op1)) {
              throw new RuntimeException();
            }
            possibleValues.put((Local)op1, Sets.<Value>newHashSet());
          }
          if (op2 instanceof Local) {
            if (possibleValues.containsKey(op2)) {
              throw new RuntimeException();
            }
            possibleValues.put((Local)op2, Sets.<Value>newHashSet());
          }
        } else {
          if (Configs.verbose) {
            System.out.println("[DEBUG] " + current + " @ " + caller);
          }
        }
      }
    }

    // Get the contant values
    Set<Integer> values = Sets.newHashSet();
    for (Value flagValue : possibleValues.get(targetFlag)) {
      if (flagValue instanceof IntConstant) {
        values.add(((IntConstant)flagValue).value);
        continue;
      }
      if (flagValue instanceof OrExpr) {
        OrExpr or = (OrExpr) flagValue;
        Value op1 = or.getOp1();
        Value op2 = or.getOp2();
        boolean op1Const = op1 instanceof IntConstant;
        boolean op2Const = op2 instanceof IntConstant;
        if (!(op1Const ^ op2Const)) {
          throw new RuntimeException();
        }
        int val1 = op1Const ? ((IntConstant)op1).value : ((IntConstant)op2).value;
        Set<Value> val2Set = op1Const ? possibleValues.get(op2) : possibleValues.get(op1);
        for (Value v : val2Set) {
          if (!(v instanceof InvokeExpr)) {
            throw new RuntimeException();
          }
          InvokeExpr ie = (InvokeExpr) v;
          String subsig = ie.getMethod().getSubSignature();
          if (!subsig.equals(MethodNames.intentGetFlagsSubsig)) {
            throw new RuntimeException();
          }
        }
        values.add(val1);
      }
    }
    return values;
  }

  /*
   * A special case is the MenuItem.setIntent calls. We treat it as a type of
   * special startActivity call. The rationale is that if setIntent can be
   * called, then the menu item is probably visible, and users can select the
   * item.
   */
  void associateActivityWithActivityLaunchCalls() {
    for (SootClass activity : output.getActivities()) {
      Set<SootMethod> reachableMethods = Sets.newHashSet();
      // 1. Views
      Set<NNode> views = Sets.newHashSet();
      for (NNode n : output.getActivityRoots(activity)) {
        Set<NNode> descendants = graphUtil.descendantNodes(n);
        for (NNode d : descendants) {
          NObjectNode guiObject = (NObjectNode) d;
          views.add(guiObject);

          // Context menu
          for (NContextMenuNode contextMenu : output.getContextMenus(guiObject)) {
            // views of context menu
            views.addAll(graphUtil.descendantNodes(contextMenu));
            // onCreateContextMenu
            reachableMethods.addAll(reachableMethods(
                output.getOnCreateContextMenuMethod(contextMenu)));
          }
        }
      }
      NOptionsMenuNode optionsMenu = output.getOptionsMenu(activity);
      if (optionsMenu != null) {
        views.addAll(graphUtil.descendantNodes(optionsMenu));
      }
      for (NNode n : views) {
        NObjectNode guiObject = (NObjectNode) n;
        for (Set<SootMethod> set :
          output.getAllEventsAndTheirHandlers(guiObject).values()) {
          for (SootMethod m : set) {
            reachableMethods.addAll(reachableMethods(m));
          }
        }
      }
      // 2. Lifecycle methods
      for (SootMethod m : output.getLifecycleHandlers(activity)) {
        reachableMethods.addAll(reachableMethods(m));
      }
      for (SootMethod m : output.getMenuHandlers(activity)) {
        reachableMethods.addAll(reachableMethods(m));
      }
      for (SootMethod m : output.getMenuCreationHandlers(activity)) {
        reachableMethods.addAll(reachableMethods(m));
      }
      for (SootMethod m : output.getDialogCreationHandlers(activity)) {
        reachableMethods.addAll(reachableMethods(m));
      }
      // onBackPressed, onSearchRequested
      for (SootMethod m : output.getActivityHandlers(activity,
          Lists.newArrayList(
              MethodNames.activityOnBackPressedSubSig,
              MethodNames.activityOnSearchRequestedSubSig))) {
        reachableMethods.addAll(reachableMethods(m));
      }

      // 3. Dialogs
      Set<NDialogNode> dialogs = Sets.newHashSet();
      while (true) {
        int oldSize = dialogs.size();
        for (NDialogNode dialog : output.getDialogs()) {
          if (dialogs.contains(dialog)) {
            continue;
          }
          for (Stmt s : output.getDialogShows(dialog)) {
            SootMethod m = jimpleUtil.lookup(s);
            if (reachableMethods.contains(m)) {
              dialogs.add(dialog);
              // views in dialog
              for (NNode root : output.getDialogRoots(dialog)) {
                for (NNode n : graphUtil.descendantNodes(root)) {
                  for (Set<SootMethod> set :
                    output.getAllEventsAndTheirHandlers((NObjectNode)n).values()) {
                    for (SootMethod mm : set) {
                      reachableMethods.addAll(reachableMethods(mm));
                    }
                  }
                }
              }
              // dialog callbacks
              for (SootMethod mm : output.getDialogLifecycleHandlers(dialog)) {
                reachableMethods.addAll(reachableMethods(mm));
              }
              for (SootMethod mm : output.getOtherEventHandlersForDialog(dialog)) {
                reachableMethods.addAll(reachableMethods(mm));
              }
              break;
            }
          } // showDialogs
        } // dialogs
        int newSize = dialogs.size();
        if (newSize == oldSize) {
          break;
        }
      } // dialog fixed-point

      // Now, we have a set of reachable methods
      String activityClassName = activity.getName();
      for (Stmt s : getAllActivityLaunchCalls()) {
        if (reachableMethods.contains(jimpleUtil.lookup(s))) {
          MultiMapUtil.addKeyAndHashSetElement(
              activityAndActivityLaunchCalls, activityClassName, s);
        }
      }
    } // activity
  }

  Map<SootMethod, Set<SootMethod>> methodAndReachables = Maps.newHashMap();

  Set<SootMethod> reachableMethods(SootMethod m) {
    Set<SootMethod> reachables = methodAndReachables.get(m);
    if (reachables != null) {
      return reachables;
    }
    reachables = Sets.newHashSet(m);
    methodAndReachables.put(m, reachables);
    LinkedList<SootMethod> worklist = Lists.newLinkedList();
    worklist.add(m);
    while (!worklist.isEmpty()) {
      SootMethod source = worklist.remove();
      for (Edge e : callgraph.getOutgoingEdges(source)) {
        SootMethod target = e.target;
        if (reachables.contains(target)) {
          continue;
        }
        reachables.add(target);
        worklist.add(target);
      }
    }
    return reachables;
  }

  @Override
  public boolean isActivityLaunch(Stmt s) {
    return activityLaunchAndIntentObjects.containsKey(s);
  }

  private final Set<String> EMPTY_TARGET_SET = Collections.emptySet();

  @Override
  public Set<String> getTargetActivities(Stmt s) {
    Set<String> gatorTargets = getTargetActivitiesGATOR(s);
    /*
    Set<String> epiccTargets = getTargetActivitiesEPICC(s);
    for (String t : epiccTargets) {
      if (!gatorTargets.contains(t)) {
        Debug.v().printf(
            "EPICC-only target `%s' for %s @ %s\n", t, s, jimpleUtil.lookup(s));
      }
    }
    */
    return gatorTargets;
  }

  public Set<String> getTargetActivitiesGATOR(Stmt s) {
    Set<NObjectNode> intents = activityLaunchAndIntentObjects.get(s);
    if (intents == null || intents.isEmpty()) {
      return EMPTY_TARGET_SET;
    }
    Set<String> targetSet = Sets.newHashSet();
    for (NObjectNode intent : intents) {
      if (intent instanceof NAnyValueNode) {
        continue;
      }
      if (!(intent instanceof NAllocNode)) {
        System.out.println("[WARNING] Unexpected intent object: " + intent);
        continue;
      }
      targetSet.addAll(intentAnalysis.getApproximateTargetActivity((NAllocNode) intent));
    }
    targetSet.remove("ANY");
    return targetSet;
  }

  public Set<String> getTargetActivitiesEPICC(Stmt s) {
    if (Configs.benchmarkName.equals("TippyTipper")
        || Configs.benchmarkName.equals("OpenManager")) {
      return EMPTY_TARGET_SET;
    }
    EpiccBasedIntentAnalysis epicc = EpiccBasedIntentAnalysis.v();
    return epicc.getTargetActivities(s);
  }

  @Override
  public Set<LaunchConfiguration> getLaunchConfigurations(Stmt s, String targetActivity) {
    ActivityLaunchMode launchMode = manifest.getLaunchMode(targetActivity);
    if (launchMode == null || launchMode == ActivityLaunchMode.singleTask
        || launchMode == ActivityLaunchMode.singleInstance) {
      return LaunchConfiguration.INVALID_CONFIGURATION_SET;
    }

    Set<LaunchConfiguration> configurations = Sets.newHashSet();
    for (NObjectNode intentObject : activityLaunchAndIntentObjects.get(s)) {
      IntentFlags intentFlags = intentObjectAndIntentFlags.get(intentObject);

      if (intentFlags == null || intentFlags.empty) {
        configurations.add(new LaunchConfiguration(launchMode));
      } else if (intentFlags.invalid) {
        configurations.add(LaunchConfiguration.INVALID_CONFIGURATION);
        continue;
      } else {
        int flags = 0;
        if (launchMode == ActivityLaunchMode.singleTop
            || intentFlags.singleTop) {
          flags |= LaunchConfiguration.FLAG_SINGLE_TOP;
        } else {
          flags |= LaunchConfiguration.FLAG_STANDARD;
        }
        if (intentFlags.clearTop) {
          Debug.v().printf("{clearTop} %s | %s @ %s\n", targetActivity,
              s.toString(), jimpleUtil.lookup(s).toString());
          flags |= LaunchConfiguration.FLAG_CLEAR_TOP;
        }
        if (intentFlags.reorderToFront) {
          flags |= LaunchConfiguration.FLAG_REORDER_TO_FRONT;
        }

        if (flags == 0) {
          throw new RuntimeException();
        }
        configurations.add(new LaunchConfiguration(flags));
      }
    }
    return configurations;
  }

  @Override
  public Set<Stmt> getAllActivityLaunchCalls() {
    return activityLaunchAndIntentObjects.keySet();
  }

  @Override
  public Set<Stmt> getActivityLaunchCalls(String activityClassName) {
    return MultiMapUtil.getNonNullHashSetByKey(
        activityAndActivityLaunchCalls, activityClassName);
  }

}
