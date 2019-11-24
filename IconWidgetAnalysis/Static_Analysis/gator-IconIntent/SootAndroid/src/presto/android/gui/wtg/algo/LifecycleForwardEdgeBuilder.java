/*
 * LifecycleForwardEdgeBuilder.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.algo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import presto.android.Configs;
import presto.android.Logger;
import presto.android.MethodNames;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NMenuNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.RootTag;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.StackOperation.OpType;
import presto.android.gui.wtg.analyzer.CFGAnalyzerInput;
import presto.android.gui.wtg.analyzer.CFGAnalyzerOutput;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGHelper;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;
import presto.android.gui.wtg.flowgraph.NLauncherNode;
import presto.android.gui.wtg.parallel.CFGScheduler;
import presto.android.gui.wtg.util.Filter;
import soot.SootMethod;

public class LifecycleForwardEdgeBuilder implements Algorithm {
  private WTGHelper helper = WTGHelper.v();

  private GUIAnalysisOutput guiOutput;

  private FlowgraphRebuilder flowgraphRebuilder;

  public LifecycleForwardEdgeBuilder(GUIAnalysisOutput guiOutput, FlowgraphRebuilder flowgraphRebuilder) {
    this.guiOutput = guiOutput;
    this.flowgraphRebuilder = flowgraphRebuilder;
  }

  public Multimap<WTGEdgeSig, WTGEdge> buildEdges(
      WTG wtg, Multimap<WTGEdgeSig, WTGEdge> existEdges, Multimap<WTGNode, NActivityNode> ownership) {
    Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput = analyzeCallback(existEdges);
    /****************************************************************************/
    /****************************************************************************/
    /**************************** start adding edges ****************************/
    /****************************************************************************/
    /****************************************************************************/
    /****************************************************************************/
    Set<AlgorithmInput> inputSet = Sets.newHashSet();
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      AlgorithmInput input = new AlgorithmInput(wtg, existEdge, this, analyzeOutput);
      inputSet.add(input);
    }
    Map<AlgorithmInput, AlgorithmOutput> outputs = new BuildScheduler().schedule(inputSet);
    for (AlgorithmInput input : outputs.keySet()) {
      AlgorithmOutput output = outputs.get(input);
      newEdges.putAll(output.newEdges);
    }
    // create ownership
    ownership.putAll(createOwnership(newEdges));

    // build hardware edges at very end
    if (Configs.hardwareEvent) {
      buildHardwareEdges(newEdges, wtg, ownership);
    }
    return newEdges;
  }

  @Override
  public AlgorithmOutput execute(AlgorithmInput input) {
    return buildEdge(input.wtg, input.cfgOutput, input.edge);
  }

  private Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeCallback(Multimap<WTGEdgeSig, WTGEdge> existEdges) {
    /****************************************************************************/
    /****************************************************************************/
    /**************************** analyze callbacks *****************************/
    /****************************************************************************/
    /****************************************************************************/
    /****************************************************************************/
    Set<CFGAnalyzerInput> inputSet = Sets.newHashSet();    
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      WTGNode target = existEdge.getTargetNode();
      RootTag root = existEdge.getRootTag();
      if (root == RootTag.start_activity || root == RootTag.implicit_launch) {
        if (!(target.getWindow() instanceof NActivityNode)) {
          Logger.err(getClass().getSimpleName(), "target is not activity");
        }
        SootMethod onCreate = helper.getCallback(target.getWindow().getClassType(),
            MethodNames.onActivityCreateSubSig);
        if (onCreate != null) {
          inputSet.add(new CFGAnalyzerInput(target.getWindow(), onCreate,
              Filter.openWindowStmtFilter));
        }
      }
    }
    CFGScheduler scheduler = new CFGScheduler(guiOutput, flowgraphRebuilder);
    return scheduler.schedule(inputSet);
  }

  private AlgorithmOutput buildEdge(
      WTG wtg, Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput, WTGEdge edge) {
    AlgorithmOutput output = new AlgorithmOutput();
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    output.newEdges = newEdges;
    WTGNode target = edge.getTargetNode();
    RootTag root = edge.getRootTag();
    if (root == RootTag.start_activity || root == RootTag.implicit_launch) {
      if (!(target.getWindow() instanceof NActivityNode)) {
        Logger.err(getClass().getSimpleName(), "target is not activity");
      }
      SootMethod onCreate = helper.getCallback(target.getWindow().getClassType(),
          MethodNames.onActivityCreateSubSig);
      if (onCreate != null) {
        CFGAnalyzerInput input = new CFGAnalyzerInput(target.getWindow(), onCreate,
            Filter.openWindowStmtFilter);
        CFGAnalyzerOutput targetsAndAvoid = analyzeOutput.get(input);
        for (NObjectNode targetWindow : targetsAndAvoid.targets.keySet()) {
          handleActivityEdge(wtg, newEdges, edge, targetWindow);
        }
        if (!targetsAndAvoid.avoid) {
          // don't add
          return output;
        }
      }
    }
    forkAndAddEdge(newEdges, edge, wtg, null);
    return output;
  }

  private void handleActivityEdge(WTG wtg, Multimap<WTGEdgeSig, WTGEdge> newEdges, WTGEdge existEdge,
      NObjectNode targetWindow) {
    if (targetWindow instanceof NActivityNode
        || targetWindow instanceof NMenuNode
        || targetWindow instanceof NDialogNode) {
      forkAndAddEdge(newEdges, existEdge, wtg, targetWindow);
    } else {
      Logger.err(getClass().getSimpleName(), "unexpected case");
    }
  }


  private void buildHardwareEdges(
      final Multimap<WTGEdgeSig, WTGEdge> newEdges,
      final WTG wtg,
      final Multimap<WTGNode, NActivityNode> ownership) {
    List<StackOperation> stackOps = Lists.newArrayList();
    List<EventHandler> callbacks = Lists.newArrayList();
    for (WTGNode source : wtg.getNodes()) {
      NObjectNode window = source.getWindow();
      // rotate
      if (window instanceof NOptionsMenuNode || window instanceof NActivityNode) {
        WTGEdge newEdge = helper.createEdge(wtg, source, source, window,
            EventType.implicit_rotate_event, Sets.<SootMethod>newHashSet(),
            RootTag.implicit_rotate, stackOps, callbacks);
        forkAndAddEdge(newEdges, newEdge, wtg, null);
      } else if (window instanceof NDialogNode) {
        if (!((NDialogNode) window).cancelable || Configs.getAndroidAPILevel() == 10) {
          WTGEdge newEdge = helper.createEdge(wtg, source, source, window,
              EventType.implicit_rotate_event, Sets.<SootMethod>newHashSet(), RootTag.implicit_rotate,
              stackOps, callbacks);
          forkAndAddEdge(newEdges, newEdge, wtg, null);
        } else {
          for (NActivityNode owner : ownership.get(source)) {
            WTGEdge newEdge = helper.createEdge(wtg, source, wtg.getNode(owner),
                window, EventType.implicit_rotate_event, Sets.<SootMethod>newHashSet(),
                RootTag.implicit_rotate, stackOps, callbacks);
            forkAndAddEdge(newEdges, newEdge, wtg, null);
          }
        }
      } else if (window instanceof NContextMenuNode) {
        for (NActivityNode owner : ownership.get(source)) {
          WTGEdge newEdge = helper.createEdge(wtg, source, wtg.getNode(owner),
              window, EventType.implicit_rotate_event, Sets.<SootMethod>newHashSet(),
              RootTag.implicit_rotate, stackOps, callbacks);
          forkAndAddEdge(newEdges, newEdge, wtg, null);
        }
      } else if (window instanceof NLauncherNode) {
      } else {
        Logger.err(getClass().getSimpleName(), "impossible case: " + window);
      }
      // home
      if (window instanceof NDialogNode || window instanceof NActivityNode) {
        WTGEdge newEdge = helper.createEdge(wtg, source, source, window,
            EventType.implicit_home_event, Sets.<SootMethod>newHashSet(),
            RootTag.implicit_home, stackOps, callbacks);
        forkAndAddEdge(newEdges, newEdge, wtg, null);
      } else {
        for (NActivityNode owner : ownership.get(source)) {
          WTGEdge newEdge = helper.createEdge(wtg, source, wtg.getNode(owner),
              window, EventType.implicit_home_event, Sets.<SootMethod>newHashSet(),
              RootTag.implicit_home, stackOps, callbacks);
          forkAndAddEdge(newEdges, newEdge, wtg, null);
        }
      }
      // power
      if (window instanceof NDialogNode || window instanceof NActivityNode) {
        WTGEdge newEdge = helper.createEdge(wtg, source, source, window, EventType.implicit_power_event,
            Sets.<SootMethod>newHashSet(), RootTag.implicit_power, stackOps, callbacks);
        forkAndAddEdge(newEdges, newEdge, wtg, null);
      } else {
        if (Configs.getAndroidAPILevel() < 11 && window instanceof NContextMenuNode) {
          WTGEdge newEdge = helper.createEdge(wtg, source, source, window, EventType.implicit_power_event,
              Sets.<SootMethod>newHashSet(), RootTag.implicit_power, stackOps, callbacks);
          forkAndAddEdge(newEdges, newEdge, wtg, null);
        } else {
          for (NActivityNode owner : ownership.get(source)) {
            WTGEdge newEdge = helper.createEdge(wtg, source, wtg.getNode(owner), window,
                EventType.implicit_power_event, Sets.<SootMethod>newHashSet(), RootTag.implicit_power,
                stackOps, callbacks);
            forkAndAddEdge(newEdges, newEdge, wtg, null);
          }
        }
      }
    }
  }

  private Multimap<WTGNode, NActivityNode> createOwnership(Multimap<WTGEdgeSig, WTGEdge> newEdges) {
    Multimap<WTGNode, NActivityNode> ownership = HashMultimap.create();
    Multimap<WTGNode, WTGEdge> inEdges = HashMultimap.create();
    for (WTGEdgeSig sig : newEdges.keySet()) {
      WTGEdge newEdge = sig.getEdge();
      RootTag root = newEdge.getRootTag();
      if (root != RootTag.start_activity && root != RootTag.show_dialog
          && root != RootTag.open_context_menu && root != RootTag.open_options_menu) {
        // only focus on forward edges
        continue;
      }
      WTGNode target = newEdge.getTargetNode();
      inEdges.put(target, newEdge);
    }
    for (WTGNode start : inEdges.keySet()) {
      if (start.getWindow() instanceof NActivityNode
          || start.getWindow() instanceof NLauncherNode) {
        // activity and launcher node don't have owner
        continue;
      }
      Set<NActivityNode> owners = Sets.newHashSet();
      Set<WTGNode> visitNodes = Sets.newHashSet(start);
      List<WTGNode> worklist = Lists.newArrayList(start);
      while (!worklist.isEmpty()) {
        WTGNode n = worklist.remove(0);
        for (WTGEdge inEdge : inEdges.get(n)) {
          WTGNode source = inEdge.getSourceNode();
          if (!visitNodes.add(source)) {
            continue;
          }
          if (source.getWindow() instanceof NActivityNode) {
            owners.add((NActivityNode) source.getWindow());
          } else {
            worklist.add(source);
          }
        }
      }
      ownership.putAll(start, owners);
    }
    return ownership;
  }

  private void forkAndAddEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTGEdge existEdge,
      WTG wtg,
      NObjectNode pushTarget) {
    List<StackOperation> stackOps = Lists.newArrayList();
    List<EventHandler> callbacks = Lists.newArrayList();
    WTGNode target = existEdge.getTargetNode();
    if (pushTarget != null) {
      stackOps.add(new StackOperation(OpType.push, target.getWindow()));  
      target = wtg.getNode(pushTarget);
    }
    WTGEdge forkEdge = helper.createEdge(wtg, existEdge.getSourceNode(), target,
        existEdge.getGUIWidget(), existEdge.getEventType(), existEdge.getEventHandlers(),
        existEdge.getRootTag(), stackOps, callbacks);
    newEdges.put(forkEdge.getSig(), existEdge);
  }
}
