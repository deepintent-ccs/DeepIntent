/*
 * CFGAnalyzer.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.analyzer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import presto.android.Configs;
import presto.android.Logger;
import presto.android.MethodNames;
import presto.android.gui.Flowgraph;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.JimpleUtil;
import presto.android.gui.VariableValueQueryInterface;
import presto.android.gui.clients.energy.VarUtil;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NAllocNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NInflNode;
import presto.android.gui.graph.NMenuNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;
import presto.android.gui.wtg.flowgraph.NStartActivityOpNode;
import presto.android.gui.wtg.intent.IntentAnalysis;
import presto.android.gui.wtg.intent.IntentAnalysisInfo;
import presto.android.gui.wtg.util.WTGUtil;
import presto.android.gui.wtg.util.Filter;
import presto.android.gui.wtg.util.QueryHelper;
import soot.Local;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.Pair;

public class CFGAnalyzer {
  private FlowgraphRebuilder rebuilder;
  private GUIAnalysisOutput guiOutput;
  private IntentAnalysis intentAnalysis;
  private ConstantAnalysis constAnalysis;
  // NInflNode->Set<NActivityNode>, MenuItemNode to target activities
  private Multimap<NInflNode, NActivityNode> menuItemToActivity = HashMultimap.create();
  // WTG traversal
  private CFGTraversal cfgTraversal = CFGTraversal.v();
  // WTG util
  private WTGUtil wtgUtil = WTGUtil.v();
  // jimple util
  private JimpleUtil jimpleUtil = JimpleUtil.v();
  // query helper
  private QueryHelper queryHelper = QueryHelper.v();

  public CFGAnalyzer(GUIAnalysisOutput output, FlowgraphRebuilder rebuilder) {
    Preconditions.checkNotNull(rebuilder);
    Preconditions.checkNotNull(output);
    this.rebuilder = rebuilder;
    this.guiOutput = output;
    this.constAnalysis = new ConstantAnalysis(this.guiOutput, this.rebuilder);
    VarUtil.v().constantAnalysis = this.constAnalysis;
    this.intentAnalysis = new IntentAnalysis(this.guiOutput, this.rebuilder);
  }

  public CFGAnalyzerOutput analyzeCallbackMethod(
      final NObjectNode guiElement, final SootMethod handler,
      final Filter<Stmt, SootMethod> filter) {
    // perform constant propagation analysis first
    HashMultimap<Stmt, Stmt> infeasibleEdges = HashMultimap.create();
    HashMultimap<Stmt, SootMethod> infeasibleCalls = HashMultimap.create();
    if (Configs.resolveContext) {
      // if trying to provide context info while resolving infeasible
      // transitions
      constAnalysis.doAnalysis(guiElement, handler, infeasibleEdges,
          infeasibleCalls);
//      if (filter == Filter.openWindowStmtFilter || filter == Filter.openActivityDialogStmtFilter)
//        Logger.verb("CONSTAL0",
//              "GUI:" + guiElement + "\tinfeasibleEdges: "+ infeasibleEdges.size() + "\tinfeasibleCalls:" + infeasibleCalls.size());
    }
    return analyzeCallbackMethod(guiElement, handler, infeasibleEdges,
        infeasibleCalls, filter);
  }

  private CFGAnalyzerOutput analyzeCallbackMethod(
      final NObjectNode guiWidget,
      final SootMethod handler,
      final HashMultimap<Stmt, Stmt> infeasibleEdges,
      final HashMultimap<Stmt, SootMethod> infeasibleCalls,
      final Filter<Stmt, SootMethod> stmtFilter) {
    // stmtFilter is used to specify the types of stmts we interested
    final Set<Stmt> escapedStmts = Sets.newHashSet();
    final Map<Stmt, SootMethod> visitedStmts = Maps.newHashMap();
    final Map<SootMethod, UnitGraph> methodToCFG = Maps.newHashMap();
    final Map<Stmt, SootMethod> startActivityStmts = Maps.newHashMap();
    final Map<Stmt, SootMethod> showDialogStmts = Maps.newHashMap();
    final Set<Stmt> setIntentContentStmts = Sets.newHashSet();
    final Set<Stmt> newIntentStmts = Sets.newHashSet();
    final Multimap<NObjectNode, Pair<Stmt, SootMethod>> openMenus = HashMultimap.create();
    final Multimap<NObjectNode, Pair<Stmt, SootMethod>> terminatedActivities = HashMultimap.create();
    final Multimap<NObjectNode, Pair<Stmt, SootMethod>> terminatedDialogs = HashMultimap.create();
    final Multimap<NObjectNode, Pair<Stmt, SootMethod>> targetWindows = HashMultimap.create();
    final Multimap<NObjectNode, Pair<Stmt, SootMethod>> acquireResources = HashMultimap.create();
    final Multimap<NObjectNode, Pair<Stmt, SootMethod>> releaseResources = HashMultimap.create();
    final CFGAnalyzerOutput analyzeResult = new CFGAnalyzerOutput();
    final Filter<Stmt, SootMethod> filter = new Filter<Stmt, SootMethod>() {
      @Override
      public boolean match(Stmt unit, SootMethod context) {
        if (wtgUtil.isMenuItemSetIntentCall(unit)) {
          collectTargetMenuItemSetIntent(unit);
          return false;
        }

        if (stmtFilter.lookforAcquireResource()
            && wtgUtil.isAcquireResourceCall(unit)) {
          Set<NObjectNode> resourceSet = collectAcquireResource(unit);
          for (NObjectNode acquireRes : resourceSet) {
            acquireResources.put(acquireRes, new Pair<Stmt, SootMethod>(unit, context));
          }
          return false;
        }

        if (stmtFilter.lookforReleaseResource()
            && wtgUtil.isReleaseResourceCall(unit)) {
          Set<NObjectNode> resourceSet = collectReleaseResource(unit);
          for (NObjectNode releaseRes : resourceSet) {
            releaseResources.put(releaseRes, new Pair<Stmt, SootMethod>(unit, context));
          }
          return false;
        }

        if (stmtFilter.lookforExitSystem()
            && wtgUtil.isExecutionExitCall(unit)) {
          analyzeResult.exitSystem = true;
          if (stmtFilter.isDetectThenStop()) {
            return true;
          } else {
            return false;
          }
        }

        if (stmtFilter.lookforFinishActivity()
            && wtgUtil.isActivityFinishCall(unit)) {
          Set<NObjectNode> finishedActivities = detectFinishWindow(unit);
          boolean match = false;
          if (finishedActivities != null) {
            for (NObjectNode finishedActivity : finishedActivities) {
              terminatedActivities.put(finishedActivity,
                  new Pair<Stmt, SootMethod>(unit, context));
              match = true;
            }
          }
          if (match && stmtFilter.isDetectThenStop()) {
            return true;
          } else {
            return false;
          }
        }

        if (stmtFilter.lookforDismissDialog()
            && guiOutput.isDialogDismiss(unit)) {
          Set<NDialogNode> closedDialogs = guiOutput.dialogsDismissedBy(unit);
          boolean match = false;
          for (NDialogNode dialog : closedDialogs) {
            terminatedDialogs.put(dialog, new Pair<Stmt, SootMethod>(unit,
                context));
            match = true;
          }
          if (match && stmtFilter.isDetectThenStop()) {
            return true;
          } else {
            return false;
          }
        }

        if (stmtFilter.lookforOpenMenu()) {
          if (wtgUtil.isMenuItemAddCall(unit)
              || wtgUtil.isMenuInflateCall(unit)) {
            if (handler.getSubSignature().equals(MethodNames.onCreateOptionsMenuSubsig)
                && collectTargetMenu(unit).contains(guiWidget)) {
              openMenus.put(guiWidget, new Pair<Stmt, SootMethod>(unit, context));
            } else if (handler.getSubSignature().equals(MethodNames.onCreateContextMenuSubSig)
                || handler.getSubSignature().equals(MethodNames.viewOnCreateContextMenuSubSig)) {
              for (NMenuNode openMenu : collectTargetMenu(unit)) {
                openMenus.put(openMenu, new Pair<Stmt, SootMethod>(unit, context));
             }
            }
            if (stmtFilter.isDetectThenStop()) {
              return true;
            } else {
              return false;
            }
          } else if (guiOutput.isExplicitShowContextMenuCall(unit)) {
            for (NMenuNode openMenu : guiOutput.explicitlyTriggeredContextMenus(unit)) {
              openMenus.put(openMenu, new Pair<Stmt, SootMethod>(unit, context));
            }
            if (stmtFilter.isDetectThenStop()) {
              return true;
            } else {
              return false;
            }
          } else if (guiOutput.isExplicitShowOptionsMenuCall(unit)) {
            if (guiOutput.explicitlyTriggeredOptionsMenus(unit).contains(
                guiWidget)) {
              openMenus.put(guiWidget, new Pair<Stmt, SootMethod>(unit, context));
            }
            if (stmtFilter.isDetectThenStop()) {
              return true;
            } else {
              return false;
            }
          }
        }

        if (stmtFilter.lookforShowDialog() && guiOutput.isDialogShow(unit)) {
          showDialogStmts.put(unit, context);
          if (stmtFilter.isDetectThenStop()) {
            return true;
          } else {
            return false;
          }
        }

        if (stmtFilter.lookforStartActivity()) {
          if (wtgUtil.isStartActivityCall(unit)) {
            startActivityStmts.put(unit, context);
            if (stmtFilter.isDetectThenStop()) {
              return true;
            } else {
              return false;
            }
          } else if (wtgUtil.isSetIntentContentCall(unit)) {
            setIntentContentStmts.add(unit);
            return false;
          } else if (unit instanceof AssignStmt) {
            Value rop = ((AssignStmt) unit).getRightOp();
            if (wtgUtil.isCreateIntentCall(unit)) {
              // this is because CreateIntentOpNode also sets intent
              // content
              setIntentContentStmts.add(unit);
              newIntentStmts.add(unit);
            } else if (rop instanceof NewExpr) {
              SootClass c = ((NewExpr) rop).getBaseType().getSootClass();
              if (wtgUtil.isIntentType(c)) {
                newIntentStmts.add(unit);
              }
            }
            return false;
          }
        }

        return false;
      }
    };
    cfgTraversal.forwardTraversal(handler, visitedStmts, escapedStmts,
        methodToCFG, filter, infeasibleEdges, infeasibleCalls);
    if (stmtFilter.lookforStartActivity()) {
      targetWindows.putAll(addStartActivityTarget(handler, guiWidget,
          newIntentStmts, setIntentContentStmts, startActivityStmts));
    }
    if (stmtFilter.lookforShowDialog()) {
      targetWindows.putAll(addShowDialogTarget(handler, guiWidget,
          showDialogStmts));
    }
    if (stmtFilter.lookforOpenMenu()) {
      targetWindows.putAll(openMenus);
    }
    if (stmtFilter.lookforFinishActivity()) {
      targetWindows.putAll(terminatedActivities);
    }
    if (stmtFilter.lookforDismissDialog()) {
      targetWindows.putAll(terminatedDialogs);
    }
    if (stmtFilter.lookforAcquireResource()) {
      targetWindows.putAll(acquireResources);
    }
    if (stmtFilter.lookforReleaseResource()) {
      targetWindows.putAll(releaseResources);
    }
    UnitGraph handlerCFG = methodToCFG.get(handler);
    boolean reachToExit = false;
    for (Unit exitNode : handlerCFG.getTails()) {
      if (visitedStmts.containsKey(exitNode)) {
        reachToExit = true;
        break;
      }
    }
    analyzeResult.targets = targetWindows;
    if (!reachToExit) {
      analyzeResult.avoid = false;
    } else {
      analyzeResult.avoid = true;
    }
    return analyzeResult;
  }

  private void collectTargetMenuItemSetIntent(Stmt s) {
    Pair<NVarNode, NVarNode> menuItemAndIntent = rebuilder
        .getMenuItemAndTargetAt(s);
    Set<NInflNode> menuItems = Sets.newHashSet();
    {
      NVarNode varMenuItem = menuItemAndIntent.getO1();
      VariableValueQueryInterface query = guiOutput
          .getVariableValueQueryInterface();
      Set<NObjectNode> backReachedNodes = query
          .guiVariableValues(varMenuItem.l);
      for (NObjectNode backReachedNode : backReachedNodes) {
        if (backReachedNode instanceof NInflNode) {
          menuItems.add((NInflNode) backReachedNode);
        }
      }
    }
    // backReachedNodes = query.guiVariableValues(varIntent.l);
    Set<NAllocNode> intents = Sets.newHashSet();
    {
      NVarNode varIntent = menuItemAndIntent.getO2();
      Set<NNode> backReachedNodes = queryHelper.allVariableValues(varIntent);
      for (NNode backReachedNode : backReachedNodes) {
        if (backReachedNode instanceof NAllocNode) {
          intents.add((NAllocNode) backReachedNode);
        }
      }
    }
    Set<String> targetShortNames = Sets.newHashSet();
    for (NAllocNode intent : intents) {
      targetShortNames.addAll(intentAnalysis
          .getApproximateTargetActivity(intent));
    }
    for (String targetShortName : targetShortNames) {
      if (targetShortName.equals(IntentAnalysisInfo.Any)
          || targetShortName.equals(IntentAnalysisInfo.UnknownTargetActivity)) {
        continue;
      }
      for (NInflNode menuItem : menuItems) {
        SootClass targetActivity = wtgUtil
            .getActivitySootClassByNameSig(targetShortName);
        if (targetActivity == null) {
          continue;
        }
        NActivityNode activityNode = guiOutput.getFlowgraph().allNActivityNodes
            .get(targetActivity);
        if (activityNode == null) {
          Logger.err(getClass().getSimpleName(), "can not find node for activity name: "
                  + targetShortName + ", class: " + targetActivity);
        }
        menuItemToActivity.put(menuItem, activityNode);
      }
    }
  }

  private Set<NMenuNode> collectTargetMenu(Stmt s) {
    Set<NMenuNode> targetMenus = Sets.newHashSet();
    if (wtgUtil.isMenuItemAddCall(s)) {
      Local rcvLocal = jimpleUtil.receiver(s);
      VariableValueQueryInterface query = guiOutput
          .getVariableValueQueryInterface();
      Set<NObjectNode> backReachedNodes = query.guiVariableValues(rcvLocal);
      for (NObjectNode backReachedNode : backReachedNodes) {
        if (backReachedNode instanceof NMenuNode) {
          targetMenus.add((NMenuNode) backReachedNode);
        }
      }
    } else if (wtgUtil.isMenuInflateCall(s)) {
      Local argLocal = (Local) s.getInvokeExpr().getArg(1);
      VariableValueQueryInterface query = guiOutput
          .getVariableValueQueryInterface();
      Set<NObjectNode> backReachedNodes = query.guiVariableValues(argLocal);
      for (NObjectNode backReachedNode : backReachedNodes) {
        if (backReachedNode instanceof NMenuNode) {
          targetMenus.add((NMenuNode) backReachedNode);
        }
      }
    } else {
      Logger.err(getClass().getSimpleName(), "we didn't consider such stmt as way to register menu item on menu: "
              + s);
    }
    return targetMenus;
  }

  private Set<NObjectNode> collectAcquireResource(Stmt s) {
    Set<NObjectNode> acquireResource = Sets.newHashSet();
    Integer pos = wtgUtil.getAcquireResourceField(s);
    if (pos == null) {
      Logger.err(getClass().getSimpleName(), "can not find the resource local for stmt: " + s);
    }
    Local resLocal = null;
    if (pos == 0) {
      resLocal = jimpleUtil.receiver(s);
    } else if (pos == -1) {
      resLocal = jimpleUtil.lhsLocal(s);
    } else {
      Value argValue = s.getInvokeExpr().getArg(pos - 1);
      if (!(argValue instanceof Local)) {
        Logger.err(getClass().getSimpleName(), "the resource local is not type of local for stmt: " + s);
      }
      resLocal = (Local) argValue;
    }
    Set<NNode> backReachedNodes = queryHelper.allVariableValues(guiOutput
        .getFlowgraph().simpleNode(resLocal));
    for (NNode backReachedNode : backReachedNodes) {
      if (backReachedNode instanceof NObjectNode) {
        NObjectNode resource = (NObjectNode) backReachedNode;
        acquireResource.add(resource);
      }
    }
    return acquireResource;
  }

  private Set<NObjectNode> collectReleaseResource(Stmt s) {
    Set<NObjectNode> releaseResource = Sets.newHashSet();
    Integer pos = wtgUtil.getReleaseResourceField(s);
    if (pos == null) {
      Logger.err(getClass().getSimpleName(), "can not find the resource local for stmt: " + s);
    }
    Local resLocal = null;
    if (pos == 0) {
      resLocal = jimpleUtil.receiver(s);
    } else {
      Value argValue = s.getInvokeExpr().getArg(pos - 1);
      if (!(argValue instanceof Local)) {
        Logger.err(getClass().getSimpleName(), "the resource local is not type of local for stmt: " + s);
      }
      resLocal = (Local) argValue;
    }
    Set<NNode> backReachedNodes = queryHelper.allVariableValues(guiOutput
        .getFlowgraph().simpleNode(resLocal));
    for (NNode backReachedNode : backReachedNodes) {
      if (backReachedNode instanceof NObjectNode) {
        NObjectNode resource = (NObjectNode) backReachedNode;
        releaseResource.add(resource);
      }
    }
    return releaseResource;
  }

  private Multimap<NObjectNode, Pair<Stmt, SootMethod>> addStartActivityTarget(
      SootMethod handler, NObjectNode guiWidget, Set<Stmt> newIntentStmts,
      Set<Stmt> setIntentContentStmts, Map<Stmt, SootMethod> startActivityStmts) {
    Multimap<NObjectNode, Pair<Stmt, SootMethod>> targetWindows = HashMultimap.create();
    for (Stmt startActivityStmt : startActivityStmts.keySet()) {
      intentAnalysis.resolvePreciseStartActivityTarget(handler, guiWidget,
          newIntentStmts, setIntentContentStmts, startActivityStmts);
      Collection<String> targetShortNames = intentAnalysis.getPreciseTargetActivity(handler, guiWidget,
              (NStartActivityOpNode) NOpNode.lookupByStmt(startActivityStmt));
      if (targetShortNames == null || targetShortNames.isEmpty()) {
        continue;
      }
      for (String targetShortName : targetShortNames) {
        if (targetShortName.equals(IntentAnalysisInfo.Any)
            || targetShortName.equals(IntentAnalysisInfo.UnknownTargetActivity)) {
          // simply ignore NAnyValueNode in the wtg
          continue;
        }
        SootClass targetActivity = wtgUtil.getActivitySootClassByNameSig(targetShortName);
        if (targetActivity == null) {
          continue;
        }
        NActivityNode activityNode = guiOutput.getFlowgraph().allNActivityNodes
            .get(targetActivity);
        if (activityNode == null) {
          Logger.err(getClass().getSimpleName(), "can not find node for activity name: "
                  + targetShortName + ", class: " + targetActivity);
        }
        targetWindows.put(activityNode,
            new Pair<Stmt, SootMethod>(startActivityStmt, startActivityStmts.get(startActivityStmt)));
      }
    }
    return targetWindows;
  }

  private Multimap<NObjectNode, Pair<Stmt, SootMethod>> addShowDialogTarget(SootMethod handler,
      NObjectNode guiWidget, Map<Stmt, SootMethod> showDialogStmts) {
    Multimap<NObjectNode, Pair<Stmt, SootMethod>> targetWindows = HashMultimap.create();
    VariableValueQueryInterface queryHelper = guiOutput
        .getVariableValueQueryInterface();
    for (Stmt s : showDialogStmts.keySet()) {
      if (Configs.resolveContext && wtgUtil.isActivityShowDialogCall(s)) {
        InvokeExpr ie = s.getInvokeExpr();
        // hard code the position of id integer
        Value contextArg = ie.getArg(0);
        if (!(contextArg instanceof IntConstant)
            && !(contextArg instanceof Local)) {
          continue;
        }
        Integer context = null;
        if (contextArg instanceof IntConstant) {
          context = ((IntConstant) contextArg).value;
        } else {
          Object value = constAnalysis.getConstSolution((Local) contextArg);
          if (value instanceof Integer) {
            context = (Integer) value;
          }
        }
        if (context != null) {
          // we are lucky enough to get context info
          Local rcvLocal = jimpleUtil.receiver(s);
          for (NObjectNode node : queryHelper.activityVariableValues(rcvLocal)) {
            NActivityNode activityNode = (NActivityNode) node;
            List<String> callbackSubsigs = Lists.newArrayList(
                MethodNames.activityOnCreateDialogSubSig,
                MethodNames.activityOnCreateDialogBundleSubSig);
            Set<SootMethod> onCreateDialogCallbacks = guiOutput
                .getActivityHandlers(activityNode.c, callbackSubsigs);
            for (SootMethod onCreateDialogCallback : onCreateDialogCallbacks) {
              HashMultimap<Stmt, Stmt> infeasibleEdges = HashMultimap.create();
              HashMultimap<Stmt, SootMethod> infeasibleCalls = HashMultimap
                  .create();
              ConstantAnalysis copyOfConstAnalysis = new ConstantAnalysis(
                  constAnalysis);
              copyOfConstAnalysis.doAnalysis(context, onCreateDialogCallback,
                  infeasibleEdges, infeasibleCalls);
              Set<NDialogNode> targetDialogs = detectTargetDialog(
                  onCreateDialogCallback, infeasibleEdges, infeasibleCalls);
              for (NDialogNode targetDialog : targetDialogs) {
                targetWindows.put(targetDialog,
                    new Pair<Stmt, SootMethod>(s, showDialogStmts.get(s)));
              }
            }
          }
          continue;
        }
      }
      // if we can not resolve the context info
      for (NDialogNode targetDialog : guiOutput.dialogsShownBy(s)) {
        targetWindows.put(targetDialog, new Pair<Stmt, SootMethod>(s, showDialogStmts.get(s)));
      }
    }
    return targetWindows;
  }

  private Set<NDialogNode> detectTargetDialog(SootMethod handler,
      HashMultimap<Stmt, Stmt> infeasibleEdges,
      HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    final Flowgraph fg = guiOutput.getFlowgraph();
    Map<Stmt, SootMethod> visitedStmts = Maps.newHashMap();
    Set<Stmt> escapedStmts = Sets.newHashSet();
    Map<SootMethod, UnitGraph> methodToCFG = Maps.newHashMap();
    final Set<NDialogNode> targetDialogs = Sets.newHashSet();
    cfgTraversal.forwardTraversal(handler, visitedStmts, escapedStmts,
        methodToCFG, new Filter<Stmt, SootMethod>() {
          @Override
          public boolean match(Stmt unit, SootMethod context) {
            NDialogNode dialog = fg.allNDialogNodes.get(unit);
            if (dialog != null) {
              targetDialogs.add(dialog);
            }
            return false;
          }
        }, infeasibleEdges, infeasibleCalls);
    return targetDialogs;
  }

  private Set<NObjectNode> detectFinishWindow(Stmt unit) {
    VariableValueQueryInterface query = guiOutput.getVariableValueQueryInterface();
    Local rcvLocal = jimpleUtil.receiver(unit);
    Set<NObjectNode> activityNodes = query.activityVariableValues(rcvLocal);
    return activityNodes;
  }
}
