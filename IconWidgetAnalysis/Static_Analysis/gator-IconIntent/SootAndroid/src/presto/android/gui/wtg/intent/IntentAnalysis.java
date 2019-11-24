/*
 * IntentAnalysis.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.intent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import presto.android.Configs;
import presto.android.Logger;
import presto.android.gui.Flowgraph;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.GraphUtil;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NAllocNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NStringConstantNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;
import presto.android.gui.wtg.flowgraph.NAnyValueNode;
import presto.android.gui.wtg.flowgraph.NClassConstantNode;
import presto.android.gui.wtg.flowgraph.NGetClassOpNode;
import presto.android.gui.wtg.flowgraph.NGetIntentOpNode;
import presto.android.gui.wtg.flowgraph.NSetIntentContentOpNode;
import presto.android.gui.wtg.flowgraph.NStartActivityOpNode;
import presto.android.gui.wtg.util.WTGUtil;
import presto.android.gui.wtg.util.QueryHelper;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.Expr;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

public class IntentAnalysis {
  // util
  private WTGUtil wtgUtil = WTGUtil.v();
  private GraphUtil graphUtil = GraphUtil.v();
  private IntentFilterManager filterManager = IntentFilterManager.v();
  private QueryHelper queryHelper = QueryHelper.v();
  // flowgraph rebuilder
  private FlowgraphRebuilder rebuilder;
  // guiOutput
  private GUIAnalysisOutput guiOutput;
  // launcherIntent, only one instance for the app
  private NAllocNode launcherIntent;

  // Map 1: NAllocNode -> Set<NStartActivityOpNode>: this will represent, for an Intent alloc node, which start-activity ops it reaches
  private Map<NAllocNode, Set<NStartActivityOpNode>> intentFlowtoStartActivity;
  // Map 2: NAllocNode -> Set<NSetIntentContentOpNode>: this will represent, for an Intent alloc node, which set-intent-content ops it reaches
  private Map<NAllocNode, Set<NSetIntentContentOpNode>> intentFlowtoSetIntentContent;
  // Map 3: NAllocNode -> IntentAnalysisInfo: this will store, for each Alloc Node for an intent, the data for the content of this intent
  private Map<NAllocNode, IntentAnalysisInfo> intentContent;
  // Map 4: NStartActivityOpNode -> Set<String>: this will represent, given a NStartActivityOpNode, the target activities it may trigger
  private Multimap<NStartActivityOpNode, String> startActivitytoTarget;
  // Map 5: Handler -> GUIObject -> NAllocNode -> IntentAnalysisInfo
  private Map<SootMethod, Map<NObjectNode, Map<NAllocNode, IntentAnalysisInfo>>> preciseIntentContent;
  // Map 6: NStartActivityOpNode -> Set<String>: this will represent, given a NStartActivityOpNode, the target activities it may trigger
  private Map<SootMethod, Map<NObjectNode, Multimap<NStartActivityOpNode, String>>> preciseStartActivitytoTarget;
  // Map 7: NAllocNode -> IntentAnalysisInfo: this will store, for each Alloc Node for an intent, the data for the content of this intent
  private Map<NAllocNode, IntentAnalysisInfo> approximateIntentContent;
  // Map 8: NStartActivityOpNode -> Set<String>: this will represent, given a NStartActivityOpNode, the target activities it may trigger
  private Multimap<NStartActivityOpNode, String> approximateStartActivitytoTarget;

  // mother intent analysis used to copy approximate intent result
  private static IntentAnalysis motherIntentAnalysis;
  private static Lock globalLock = new ReentrantLock();
  private static synchronized void createMotherIntentAnalysis(
      GUIAnalysisOutput guiOutput, FlowgraphRebuilder rebuilder) {
    if (motherIntentAnalysis != null) {
      if (motherIntentAnalysis.guiOutput != guiOutput
          || motherIntentAnalysis.rebuilder != rebuilder) {
        Logger.err("IntentAnalysis", "mother intent analysis has different input");
      }
      return;
    }
    motherIntentAnalysis = new IntentAnalysis();
    motherIntentAnalysis.initialize(guiOutput, rebuilder);
    motherIntentAnalysis.run();
  }

  public IntentAnalysis(GUIAnalysisOutput guiOutput, FlowgraphRebuilder rebuilder) {
    initialize(guiOutput, rebuilder);
  }

  private IntentAnalysis() {

  }

  private void initialize(GUIAnalysisOutput guiOutput, FlowgraphRebuilder rebuilder) {
    this.guiOutput = guiOutput;
    this.rebuilder = rebuilder;
    intentFlowtoStartActivity = Maps.newHashMap();
    intentFlowtoSetIntentContent = Maps.newHashMap();
    intentContent = Maps.newHashMap();
    startActivitytoTarget = HashMultimap.create();
    preciseIntentContent = Maps.newHashMap();
    preciseStartActivitytoTarget = Maps.newHashMap();
    approximateIntentContent = Maps.newHashMap();
    approximateStartActivitytoTarget = HashMultimap.create();
    // create intent for launcher activity
    createIntentForLauncherActivity();
    // copy approximate intent result
    createMotherIntentAnalysis(guiOutput, rebuilder);
    this.copyApproxiateIntentResolution(motherIntentAnalysis);
  }

  /**
   * this method should be run only once among all intent analysis instances
   * */
  private void run() {
    // read intent filters from AndroidManifest.xml
    IntentFilterReader intentFilterReader = IntentFilterReader.v();
    intentFilterReader.read();
    // resolve intent approximately
    resolveApproximateIntent();
  }
  
  private void resolveApproximateIntent() {
    // this method does not rely on const propagation analysis
    resolveLauncherActivityIntent();
    // start intent analysis
    boolean goOn = true;
    while (goOn) {
      goOn = false;
      // step 1: prepare the data for intent analysis
      prepare();
      // step 2: perform the intent analysis
      goOn = doAnalysis();
    }
    postAnalysis();
    // record the resolution result
    recordApproximateIntentResolution();
    // count number of simple implicit intent, which is defined
    // as the implicit intent with no "data" field
    // countSimpleImplicitIntent(true);
    reset();
  }

  private void resolvePartialPreciseIntent(
      SootMethod handler,
      NObjectNode guiObj,
      Map<NAllocNode, Set<NSetIntentContentOpNode>> intentAllocToSetIntent,
      Map<NAllocNode, Set<NStartActivityOpNode>> intentAllocToStartActivity
      ) {
    // this run relies on const propagation analysis
    resolveLauncherActivityIntent();
    this.intentFlowtoSetIntentContent.putAll(intentAllocToSetIntent);
    this.intentFlowtoStartActivity.putAll(intentAllocToStartActivity);
    // start intent analysis
    boolean goOn = true;
    while (goOn) {
      goOn = false;
      // step 1: prepare the data for intent analysis
      // prepare();
      // step 2: perform the intent analysis
      goOn = doAnalysis();
    }
    postAnalysis();
    // record the resolution result
    recordPreciseIntentResolution(handler, guiObj);
    reset();
  }

  private void recordApproximateIntentResolution() {
    this.approximateIntentContent.putAll(this.intentContent);
    this.approximateStartActivitytoTarget.putAll(this.startActivitytoTarget);
  }

  private void reset() {
    intentFlowtoStartActivity.clear();
    intentFlowtoSetIntentContent.clear();
    intentContent.clear();
    startActivitytoTarget.clear();
  }

  private void copyApproxiateIntentResolution(IntentAnalysis another) {
    this.approximateIntentContent.putAll(another.approximateIntentContent);
    this.approximateStartActivitytoTarget.putAll(another.approximateStartActivitytoTarget);
  }

  private void recordPreciseIntentResolution(SootMethod handler,
      NObjectNode guiObj) {
    { // store the intent content
      Map<NObjectNode, Map<NAllocNode, IntentAnalysisInfo>> objToResolution = this.preciseIntentContent
          .get(handler);
      if (objToResolution == null) {
        objToResolution = Maps.newHashMap();
        this.preciseIntentContent.put(handler, objToResolution);
      }
      Map<NAllocNode, IntentAnalysisInfo> intentToContent = objToResolution
          .get(guiObj);
      if (intentToContent == null) {
        intentToContent = Maps.newHashMap();
        objToResolution.put(guiObj, intentToContent);
      }
      intentToContent.putAll(this.intentContent);
    }
    { // store other part
      Map<NObjectNode,Multimap<NStartActivityOpNode, String>> objToResolution =
          this.preciseStartActivitytoTarget.get(handler);
      if (objToResolution == null) {
        objToResolution = Maps.newHashMap();
        this.preciseStartActivitytoTarget.put(handler, objToResolution);
      }
      Multimap<NStartActivityOpNode, String> startActivityToTarget = objToResolution.get(guiObj);
      if (startActivityToTarget == null) {
        startActivityToTarget = HashMultimap.create();
        objToResolution.put(guiObj, startActivityToTarget);
      }
      startActivityToTarget.putAll(this.startActivitytoTarget);
    }
  }
  private void resolveLauncherActivityIntent() {
    Pair<String, IntentFilter> launcher = filterManager.getLauncherFilter();
    if (launcher == null) {
      return;
    }
    IntentAnalysisInfo intentInfo = new IntentAnalysisInfo();
    String tgtActivity = launcher.getO1();
    intentInfo.addData(IntentField.TgtActivity, tgtActivity);
    IntentFilter filter = launcher.getO2();
    for (String action : filter.getActions()) {
      intentInfo.addData(IntentField.Action, action);
    }
    for (String category : filter.getCategories()) {
      intentInfo.addData(IntentField.Category, category);
    }
    intentContent.put(launcherIntent, intentInfo);
  }

  public Collection<String> getApproximateTargetActivity(NStartActivityOpNode opnode) {
    return approximateStartActivitytoTarget.get(opnode);
  }

  public Set<String> getApproximateTargetActivity(NAllocNode intentAllocNode) {
    IntentAnalysisInfo content = approximateIntentContent.get(intentAllocNode);
    Set<String> targets = Sets.newHashSet();
    if (content == null) {
      return targets;
    }
    targets.addAll(content.getData(IntentField.ImplicitTgtActivity));
    targets.addAll(content.getData(IntentField.TgtActivity));
    return targets;
  }

  public Collection<String> getPreciseTargetActivity(
      SootMethod handler, NObjectNode guiObj, NStartActivityOpNode opnode) {
    Map<NObjectNode, Multimap<NStartActivityOpNode, String>> objToResolution =
        preciseStartActivitytoTarget.get(handler);
    if (objToResolution == null) {
      Collection<String> targetShortNames = this.getApproximateTargetActivity(opnode);
      if (targetShortNames == null) {
        return Sets.newHashSet();
      } else {
        return targetShortNames;
      }
    }
    Multimap<NStartActivityOpNode, String> startActivityToTarget = objToResolution.get(guiObj);
    // if we can not find the targets through precise intent analysis,
    // try the over-approximate approach.
    if (startActivityToTarget == null || startActivityToTarget.isEmpty()) {
      Collection<String> targetShortNames = this.getApproximateTargetActivity(opnode);
      if (targetShortNames == null) {
        return Sets.newHashSet();
      } else {
        return targetShortNames;
      }
    }
    return startActivityToTarget.get(opnode);
  }

  public Collection<String> getPreciseTargetActivity(SootMethod handler, NObjectNode guiObj, NAllocNode intentAllocNode) {
    Map<NObjectNode,Map<NAllocNode,IntentAnalysisInfo>> objToResolution =
        preciseIntentContent.get(handler);
    if (objToResolution == null) {
      Collection<String> targetShortNames = this.getApproximateTargetActivity(intentAllocNode);
      if (targetShortNames == null) {
        return Sets.newHashSet();
      } else {
        return targetShortNames;
      }
    }
    Map<NAllocNode,IntentAnalysisInfo> startActivityToTarget = objToResolution.get(guiObj);
    // if we can not find the targets through precise intent analysis,
    // try the over-approximate approach.
    if (startActivityToTarget == null || startActivityToTarget.isEmpty()) {
      Collection<String> targetShortNames = this.getApproximateTargetActivity(intentAllocNode);
      if (targetShortNames == null) {
        return Sets.newHashSet();
      } else {
        return targetShortNames;
      }
    }
    IntentAnalysisInfo content = startActivityToTarget.get(intentAllocNode);
    Set<String> targets = Sets.newHashSet();
    targets.addAll(content.getData(IntentField.ImplicitTgtActivity));
    targets.addAll(content.getData(IntentField.TgtActivity));
    return targets;
  }

  private void prepare() {
    // reset the maps
    reset();
    // build the maps
    for (Map.Entry<Expr, NAllocNode> entry : guiOutput.getFlowgraph().allNAllocNodes.entrySet()) {
      NAllocNode intentAllocNode = entry.getValue();
      if (!wtgUtil.isIntentAllocNode(intentAllocNode) && !wtgUtil.isCreateIntentAllocNode(intentAllocNode)) {
        continue;
      }
      Set<NNode> reachedNodes = graphUtil.reachableNodes(intentAllocNode);
      for (NNode reachedNode : reachedNodes) {
        if (reachedNode instanceof NStartActivityOpNode) {
          Set<NStartActivityOpNode> startActivitySet = intentFlowtoStartActivity
              .get(intentAllocNode);
          if (startActivitySet == null) {
            startActivitySet = Sets.newHashSet();
            intentFlowtoStartActivity.put(intentAllocNode, startActivitySet);
          }
          startActivitySet.add((NStartActivityOpNode) reachedNode);
        } else if (reachedNode instanceof NSetIntentContentOpNode) {
          NNode rcvNode = ((NSetIntentContentOpNode) reachedNode).getReceiver();
          if (reachedNodes.contains(rcvNode)) {
            Set<NSetIntentContentOpNode> setIntentContentSet = intentFlowtoSetIntentContent
                .get(intentAllocNode);
            if (setIntentContentSet == null) {
              setIntentContentSet = Sets.newHashSet();
              intentFlowtoSetIntentContent.put(intentAllocNode,
                  setIntentContentSet);
            }
            setIntentContentSet.add((NSetIntentContentOpNode) reachedNode);
          }
        }
      }
    }
  }

  private boolean doAnalysis() {
    // resolve start activity intent
    resolveStartActivityIntentContent();
    // resolve target activity
    resolveIntentTarget();
    // re-build the broken propagation
    boolean affected = rebuildPropagation();
    return affected;
  }

  private void postAnalysis() {
    // if the startActivity can backward reach ANY intent, then add ANY value to its target in map: startActivitytoTarget
    for (NOpNode startActivityOpNode : NOpNode.getNodes(NStartActivityOpNode.class)) {
      NNode intentNode = startActivityOpNode.getParameter();
      Set<NNode> backReachedNodes = queryHelper.allVariableValues(intentNode);
      for (NNode backReachedNode : backReachedNodes) {
        if (backReachedNode instanceof NAnyValueNode) {
          startActivitytoTarget.put((NStartActivityOpNode)startActivityOpNode, IntentAnalysisInfo.Any);
        }
      }
    }
  }
  private void createIntentForLauncherActivity() {
    // create fake NAllocNode intent for launcher activity
    launcherIntent = new NAllocNode();
    Expr newExpr = Jimple.v().newNewExpr(RefType.v(wtgUtil.intentClass));
    launcherIntent.e = newExpr;
    Flowgraph fg = guiOutput.getFlowgraph();
    fg.allNAllocNodes.put(newExpr, launcherIntent);
    fg.allNNodes.add(launcherIntent);
  }

  private void resolveStartActivityIntentContent() {
    // workinglist which is used to do fix point computation for "all" field
    List<Pair<NAllocNode, NSetIntentContentOpNode>> workingList = Lists.newArrayList();
    for (Map.Entry<NAllocNode, Set<NSetIntentContentOpNode>> allocToSetContents : intentFlowtoSetIntentContent.entrySet()) {
      NAllocNode intentAllocNode = allocToSetContents.getKey();
      IntentAnalysisInfo info = intentContent.get(intentAllocNode);
      if (info == null) {
        info = new IntentAnalysisInfo();
        intentContent.put(intentAllocNode, info);
      }
      Set<NSetIntentContentOpNode> flowtoSetIntentContents = allocToSetContents.getValue();
      for (NSetIntentContentOpNode setIntentContent : flowtoSetIntentContents) {
        NVarNode rcvNode = setIntentContent.getReceiver();
        // Set<NNode> rcvBackReachedNodes = queryHelper.backwardReachableNodes(rcvNode);
        Set<NNode> rcvBackReachedNodes = queryHelper.allVariableValues(rcvNode);
        if (!rcvBackReachedNodes.contains(intentAllocNode)) {
          Logger.verb(getClass().getSimpleName(), "can't find the corresponding Intent Alloc Node: " + intentAllocNode + " to receiver of NNode: " +
              setIntentContent + " whose back reachable nodes: " + rcvBackReachedNodes);
        } else {
          // make sure the intentAllocNode will be the receiver to invoke the SetIntentContentOp
          for (Map.Entry<NNode, Set<IntentField>> paraNodeToFields : setIntentContent.getContentMap().entrySet()) {
            NNode paraNode = paraNodeToFields.getKey();
            Set<IntentField> fields = paraNodeToFields.getValue();
            for (IntentField field : fields) {
              // record the NSetIntentContentOpNode which propagate content through "all" field
              if (field.equals(IntentField.All)) {
                Pair<NAllocNode, NSetIntentContentOpNode> flowThroughAll = new
                    Pair<NAllocNode, NSetIntentContentOpNode>(intentAllocNode, setIntentContent);
                workingList.add(flowThroughAll);
                continue;
              }
              Set<NNode> paraBackReachedNodes = queryHelper.allVariableValues(paraNode);
              for (NNode backReachedNode : paraBackReachedNodes) {
                if (backReachedNode instanceof NStringConstantNode) {
                  info.addData(field, ((NStringConstantNode) backReachedNode).value);
                } else if (backReachedNode instanceof NClassConstantNode) {
                  info.addData(field, ((NClassConstantNode) backReachedNode).myClass.getName());
                } else if (backReachedNode instanceof NGetClassOpNode) {
                  // if we back reach getClass opnode, add all possible activity class to the explicit targets
                  NVarNode objectNode = ((NGetClassOpNode) backReachedNode).getReceiver();
                  for (NNode backReachedActivityNode : queryHelper.allVariableValues(objectNode)) {
                    if (backReachedActivityNode instanceof NActivityNode) {
                      info.addData(field, ((NActivityNode)backReachedActivityNode).c.getName());
                    }
                  }
                }
                else if (backReachedNode instanceof NAnyValueNode) {
                  // check for untracked value
                  info.addData(field, IntentAnalysisInfo.Any);
                } else {
                  // ignore the rest of nodes
                }
              }
            }
          }
        }
      }
    }
    while (!workingList.isEmpty()) {
      Pair<NAllocNode, NSetIntentContentOpNode> flowThroughAll = workingList.remove(0);
      NAllocNode intentAllocNode = flowThroughAll.getO1();
      IntentAnalysisInfo rcvContentInfo = intentContent.get(intentAllocNode);
      if (rcvContentInfo == null) {
        Logger.verb("WARNING", "rcvContentInfo is null, which should not happen");
        String fullClassName = Thread.currentThread().getStackTrace()[2].getClassName();
        String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        int lineNumber = Thread.currentThread().getStackTrace()[2].getLineNumber();
        Logger.verb("WARNING", String.format("At %s at %s at line %d",
                fullClassName, methodName, lineNumber));
        continue;
      }
      NSetIntentContentOpNode setIntentContent = flowThroughAll.getO2();
      boolean checkAffect = false;
      for (Map.Entry<NNode, Set<IntentField>> paraNodeToFields : setIntentContent.getContentMap().entrySet()) {
        Set<IntentField> fields = paraNodeToFields.getValue();
        for (IntentField field : fields) {
          if (!field.equals(IntentField.All)) {
            continue;
          }
          NNode paraNode = paraNodeToFields.getKey();
          Set<NNode> paraBackReachedNodes = queryHelper.allVariableValues(paraNode);
          for (NNode backReachedNode : paraBackReachedNodes) {
            if (wtgUtil.isCreateIntentAllocNode(backReachedNode) || wtgUtil.isIntentAllocNode(backReachedNode)) {
              IntentAnalysisInfo paraContentInfo = intentContent.get(backReachedNode);
              if (paraContentInfo != null) {
                boolean success = rcvContentInfo.addAllData(paraContentInfo);
                checkAffect = checkAffect || success;
              }
            }
          }
        }
      }
      if (checkAffect) {
        Set<NNode> forwardReachedNodes = graphUtil.reachableNodes(intentAllocNode);
        for (NNode forwardReachedNode : forwardReachedNodes) {
          if (forwardReachedNode instanceof NSetIntentContentOpNode) {
            checkAffect = false;
            for (Map.Entry<NNode, Set<IntentField>> paraNodeToFields : ((NSetIntentContentOpNode)forwardReachedNode).getContentMap().entrySet()) {
              Set<IntentField> fields = paraNodeToFields.getValue();
              for (IntentField field : fields) {
                if (!field.equals(IntentField.All)) {
                  continue;
                }
                NNode paraNode = paraNodeToFields.getKey();
                if (!forwardReachedNodes.contains(paraNode)) {
                  continue;
                }
                checkAffect = true;
              }
            }
            if (checkAffect) {
              NVarNode rcvNode = ((NSetIntentContentOpNode) forwardReachedNode).getReceiver();
              for (NNode backReachedNode : queryHelper.allVariableValues(rcvNode)) {
                if (wtgUtil.isCreateIntentAllocNode(backReachedNode) || wtgUtil.isIntentAllocNode(backReachedNode)) {
                  Pair<NAllocNode, NSetIntentContentOpNode> affectedPair =
                      new Pair<NAllocNode, NSetIntentContentOpNode>((NAllocNode)backReachedNode, (NSetIntentContentOpNode) forwardReachedNode);
                  workingList.add(affectedPair);
                }
              }
            }
          }
        }
      }
    }
  }

  private boolean rebuildPropagation() {
    boolean affected = false;
    for (Map.Entry<NAllocNode, IntentAnalysisInfo> allocToContent : intentContent.entrySet()) {
      NAllocNode intent = allocToContent.getKey();
      IntentAnalysisInfo intentInfo = allocToContent.getValue();
      if (!intentFlowtoStartActivity.containsKey(intent) && !wtgUtil.isLauncherIntent(intentInfo)) {
        continue;
      }
      Set<String> tgtActivities = Sets.newHashSet();
      tgtActivities.addAll(intentInfo.getData(IntentField.ImplicitTgtActivity));
      tgtActivities.addAll(intentInfo.getData(IntentField.TgtActivity));
      for (String tgtName : tgtActivities) {
        if (tgtName.equals(IntentAnalysisInfo.Any)) {
          continue;
        }
        SootClass tgtClz = wtgUtil.getActivitySootClassByNameSig(tgtName);
        NActivityNode tgtActivityNode = guiOutput.getFlowgraph().allNActivityNodes.get(tgtClz);
        if (tgtActivityNode == null) {
          continue;
        }
        Set<NNode> forwardReachedNodes = graphUtil.reachableNodes(tgtActivityNode);
        for (NNode forwardReachedNode : forwardReachedNodes) {
          // re-build the broken propagation
          if (forwardReachedNode instanceof NGetIntentOpNode) {
            NVarNode lhsNode = ((NGetIntentOpNode) forwardReachedNode).getLhs();
            // undo the tracking in FlowgraphRebuilder.createGetIntentOpNode
            globalLock.lock();
            NAnyValueNode.ANY.removeEdgeTo(lhsNode);
            int oldSize = intent.getNumberOfSuccessors();
            intent.addEdgeTo(lhsNode);
            if (intent.getNumberOfSuccessors() > oldSize) {
              affected = true;
            }
            globalLock.unlock();
          }
        }
      }
    }
    return affected;
  }

  private void resolveIntentTarget() {
    for (Map.Entry<NAllocNode, IntentAnalysisInfo> intentToInfo : intentContent.entrySet()) {
      NAllocNode intent = intentToInfo.getKey();
      IntentAnalysisInfo intentInfo = intentToInfo.getValue();
      Set<NStartActivityOpNode> startActivities = intentFlowtoStartActivity.get(intent);
      // if intent never reaches any startActivityOpNode, ignore it
      if (startActivities == null || startActivities.isEmpty()) {
        continue;
      }
      Set<String> explicitTargets = intentInfo.getData(IntentField.TgtActivity);
      // if it is not explicit intent, try implicit resolution
      if (explicitTargets.isEmpty() && Configs.implicitIntent) {
        Map<String, Set<IntentFilter>> clsToFilters = filterManager.getAllFilters();
        for (Map.Entry<String, Set<IntentFilter>> clsToFiltersEntry : clsToFilters.entrySet()) {
          String actName = clsToFiltersEntry.getKey();
          Set<IntentFilter> filters = clsToFiltersEntry.getValue();
          for (IntentFilter filter : filters) {
            boolean match = intentInfo.match(filter);
            if (match) {
              // set the implicit target activity field instead of target
              // activity field
              intentInfo.addData(IntentField.ImplicitTgtActivity, actName);
              break;
            }
          }
        }
        Set<String> implicitTargets = intentInfo.getData(IntentField.ImplicitTgtActivity);
        // conservatively think it may be inter application transition
        implicitTargets.add(IntentAnalysisInfo.UnknownTargetActivity);
        // build the map: startActivitytoTarget reached by implicit intent
        for (NStartActivityOpNode startActivity : startActivities) {
          startActivitytoTarget.putAll(startActivity, implicitTargets);
        }
      } else if (!explicitTargets.isEmpty()) {
        // build the map: startActivitytoTarget reached by explicit intent
        for (NStartActivityOpNode startActivity : startActivities) {
          startActivitytoTarget.putAll(startActivity, explicitTargets);
        }
      }
    }
  }

  public void resolvePreciseStartActivityTarget(
      SootMethod handler, NObjectNode guiObj,
      Set<Stmt> newIntentStmts, Set<Stmt> setIntentStmts,
      Map<Stmt, SootMethod> startActivityStmts) {
    Map<NAllocNode, Set<NStartActivityOpNode>> intentAllocToStartActivity = Maps.newHashMap();
    Map<NAllocNode, Set<NSetIntentContentOpNode>> intentAllocToSetIntent = Maps.newHashMap();
    for (Stmt newIntent : newIntentStmts) {
      Value rop = ((AssignStmt) newIntent).getRightOp();
      NNode intentAllocNode = rebuilder.lookupNode(rop);
      if (intentAllocNode == null) {
        Logger.err(getClass().getSimpleName(), "can not find intentAllocNode for stmt: " + newIntent);
      }
      Set<NNode> reachedNodes = graphUtil.reachableNodes(intentAllocNode);
      for (NNode reachedNode : reachedNodes) {
        if (reachedNode instanceof NStartActivityOpNode) {
          Stmt s = ((NStartActivityOpNode) reachedNode).callSite.getO1();
          if (!startActivityStmts.containsKey(s)) {
            // eliminate unreachable startActivity
            continue;
          }
          Set<NStartActivityOpNode> startActivities = intentAllocToStartActivity.get(intentAllocNode);
          if (startActivities == null) {
            startActivities = Sets.newHashSet();
            intentAllocToStartActivity.put((NAllocNode) intentAllocNode,
                startActivities);
          }
          startActivities.add((NStartActivityOpNode) reachedNode);
        } else if (reachedNode instanceof NSetIntentContentOpNode) {
          Stmt s = ((NSetIntentContentOpNode) reachedNode).callSite.getO1();
          if (!setIntentStmts.contains(s)) {
            // eliminate unreachable startActivity
            continue;
          }
          Set<NSetIntentContentOpNode> setIntents = intentAllocToSetIntent
              .get(intentAllocNode);
          if (setIntents == null) {
            setIntents = Sets.newHashSet();
            intentAllocToSetIntent.put((NAllocNode) intentAllocNode,
                setIntents);
          }
          setIntents.add((NSetIntentContentOpNode) reachedNode);
        }
      }
    }
    resolvePartialPreciseIntent(handler, guiObj, intentAllocToSetIntent, intentAllocToStartActivity);
  }

  @SuppressWarnings("unused")
  private void countSimpleImplicitIntent(boolean isApproximateSolution) {
    int totalImplicitIntents = 0;
    int nonSimpleImplicitIntent = 0;
    for (NAllocNode intentAllocNode : intentContent.keySet()) {
      IntentAnalysisInfo intentInfo = intentContent.get(intentAllocNode);
      Set<String> explicitTargets = intentInfo.getData(IntentField.TgtActivity);
      if (explicitTargets != null && !explicitTargets.isEmpty()) {
        // simply imagine it is explicit intent
        continue;
      }
      totalImplicitIntents++;
      Map<IntentField, Set<String>> fieldsInfo = intentInfo.getAllData();
      for (IntentField field : fieldsInfo.keySet()) {
        if (!field.isDataField()) {
          continue;
        }
        Set<String> info = fieldsInfo.get(field);
        if (!info.isEmpty()) {
          nonSimpleImplicitIntent++;
          Logger.verb(getClass().getSimpleName(), "intent: " + intentAllocNode
              + ", has data field: " + field + ", with value: " + info);
          break;
        }
      }
    }
    Logger.verb(getClass().getSimpleName(), "complex implicit intents: " + nonSimpleImplicitIntent
        + ", implicit intents: " + totalImplicitIntents);
  }
}
