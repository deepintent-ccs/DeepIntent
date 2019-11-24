/*
 * LifecycleCloseEdgeBuilder.java - part of the GATOR project
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

import presto.android.Logger;
import presto.android.MethodNames;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NObjectNode;
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

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public class LifecycleCloseEdgeBuilder implements Algorithm {
  // util
  private WTGHelper helper = WTGHelper.v();

  // successor cache
  private Map<WTGNode, Set<WTGNode>> succCache;

  private GUIAnalysisOutput guiOutput;

  private FlowgraphRebuilder flowgraphRebuilder;

  public LifecycleCloseEdgeBuilder(GUIAnalysisOutput guiOutput, FlowgraphRebuilder flowgraphRebuilder) {
    this.guiOutput = guiOutput;
    this.flowgraphRebuilder = flowgraphRebuilder;
    this.succCache = Maps.newHashMap();
  }

  public Multimap<WTGEdgeSig, WTGEdge> buildEdges(
      WTG wtg, Multimap<WTGEdgeSig, WTGEdge> existEdges, Multimap<WTGNode, NActivityNode> ownership) {
    Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput = analyzeCallbacks(existEdges);

    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    Multimap<WTGNode, WTGEdge> inEdges = buildInEdges(existEdges);
    Set<AlgorithmInput> inputSet = Sets.newHashSet();
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      AlgorithmInput input = new AlgorithmInput(wtg, existEdge, this, inEdges, ownership, analyzeOutput);
      inputSet.add(input);
    }
    Map<AlgorithmInput, AlgorithmOutput> outputs = new BuildScheduler().schedule(inputSet);
    for (AlgorithmInput input : outputs.keySet()) {
      AlgorithmOutput output = outputs.get(input);
      for (WTGEdgeSig sig : output.newEdges.keySet()) {
        if (sig.getRootTag() != RootTag.fake_interim_edge) {
          newEdges.putAll(sig, output.newEdges.get(sig));
        }
      }
    }
    return newEdges;
  }

  @Override
  public AlgorithmOutput execute(AlgorithmInput input) {
    return buildEdge(input.cfgOutput, input.wtg, input.inEdges, input.edge,
        input.ownership);
  }

  private Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeCallbacks(
      Multimap<WTGEdgeSig, WTGEdge> existEdges) {
    Set<CFGAnalyzerInput> inputSet = Sets.newHashSet();    
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      WTGNode target = existEdge.getTargetNode();
      RootTag root = existEdge.getRootTag();
      if (root == RootTag.start_activity || root == RootTag.implicit_launch) {
        if (target.getWindow() != existEdge.getFinalTarget()) {
          Logger.err(getClass().getSimpleName(), "unmatched ultimate push node and target of edge: " + existEdge);
        }
        List<EventHandler> callbacks = existEdge.getCallbacks();
        EventHandler onCreate = null;
        for (EventHandler callback : callbacks) {
          SootMethod eventHandler = callback.getEventHandler();
          if (MethodNames.onActivityCreateSubSig.equals(eventHandler.getSubSignature())
              && eventHandler.getDeclaringClass() == target.getWindow().getClassType()) {
            onCreate = callback;
            break;
          }
        }
        if (onCreate != null) {
          inputSet.add(new CFGAnalyzerInput(onCreate.getWindow(), onCreate.getEventHandler(),
              Filter.closeActivityStmtFilter));
        }
      }
    }
    CFGScheduler scheduler = new CFGScheduler(guiOutput, flowgraphRebuilder);
    return scheduler.schedule(inputSet);
  }

  private AlgorithmOutput buildEdge(
      Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput,
      WTG wtg,
      Multimap<WTGNode, WTGEdge> inEdges,
      WTGEdge edge,
      Multimap<WTGNode, NActivityNode> ownership) {
    AlgorithmOutput output = new AlgorithmOutput();
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    output.newEdges = newEdges;
    WTGNode target = edge.getTargetNode();
    RootTag root = edge.getRootTag();
    if (root == RootTag.start_activity || root == RootTag.implicit_launch) {
      if (target.getWindow() != edge.getFinalTarget()) {
        Logger.err(getClass().getSimpleName(), "unmatched ultimate push node and target of edge");
      }
      List<EventHandler> callbacks = edge.getCallbacks();
      EventHandler onCreate = null;
      for (EventHandler callback : callbacks) {
        SootMethod eventHandler = callback.getEventHandler();
        if (MethodNames.onActivityCreateSubSig.equals(eventHandler.getSubSignature())
            && eventHandler.getDeclaringClass() == target.getWindow().getClassType()) {
          onCreate = callback;
          break;
        }
      }
      if (onCreate != null) {
        CFGAnalyzerInput input = new CFGAnalyzerInput(onCreate.getWindow(),
            onCreate.getEventHandler(), Filter.closeActivityStmtFilter);
        CFGAnalyzerOutput targetsAndAvoid = analyzeOutput.get(input);
        if (targetsAndAvoid.targets.containsKey(target.getWindow())) {
          // target is close
          handleActivityCloseEdge(newEdges, wtg, edge, ownership, inEdges);
        }
        if (!targetsAndAvoid.avoid) {
          // not add it into newEdges
          return output;
        }
      }
    }
    forkAndAddEdge(newEdges, edge, wtg, edge.getSourceNode(), edge.getTargetNode(), edge.getGUIWidget(),
        edge.getEventType(), edge.getEventHandlers(), edge.getRootTag(), edge.getStackOps(),
        edge.getCallbacks());
    return output;
  }

  private void handleActivityCloseEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTG wtg,
      WTGEdge existEdge,
      Multimap<WTGNode, NActivityNode> ownership,
      Multimap<WTGNode, WTGEdge> inEdges) {
    WTGNode source = existEdge.getSourceNode();
    WTGNode target = existEdge.getTargetNode();
    NObjectNode widget = existEdge.getGUIWidget();
    EventType event = existEdge.getEventType();
    Set<SootMethod> eventHandlers = existEdge.getEventHandlers();
    NObjectNode popOwner = existEdge.getPopOwner();
    NObjectNode popSelf = existEdge.getPopSelf();

    List<EventHandler> rawCallbacks = existEdge.getCallbacks();
    // find target.onCreate
    int index = 0;
    for (; index < rawCallbacks.size(); index++) {
      EventHandler handler = rawCallbacks.get(index);
      if (handler.getWidget() == target.getWindow()
          && handler.getEventHandler().getSubSignature().equals(
              MethodNames.onActivityCreateSubSig)) {
        index++;
        break;
      }
    }
    // sublist of callbacks from beginning to target.onCreate
    rawCallbacks = rawCallbacks.subList(0, index);

    if (popSelf instanceof NActivityNode || popOwner instanceof NActivityNode) {
      // if base activity is closed
      Set<WTGNode> successors = null;
      if (popOwner instanceof NActivityNode) {
        WTGNode popOwnerNode = wtg.getNode(popOwner);
        successors = getSuccNode(wtg, inEdges, popOwnerNode);
      } else {
        WTGNode popSelfNode = wtg.getNode(popSelf);
        successors = getSuccNode(wtg, inEdges, popSelfNode);
      }

      for (WTGNode successor : successors) {
        for (NActivityNode owner : ownership.get(source)) {
          List<EventHandler> callbacks = Lists.newArrayList(rawCallbacks);

          if (successor.getWindow() instanceof NActivityNode) {
            List<StackOperation> stackOps = existEdge.getStackOps();
            stackOps.add(new StackOperation(OpType.pop, target.getWindow()));
            List<EventHandler> additionalCallbacks = helper.getCallbacks(
                wtg,
                successor.getWindow(),
                MethodNames.onActivityRestartSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                target.getWindow(),
                MethodNames.onActivityDestroySubSig,
                owner,
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityDestroySubSig
                );
            callbacks.addAll(additionalCallbacks);
            forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event, eventHandlers,
                RootTag.finish_activity, stackOps, callbacks);
          } else if (successor.getWindow() instanceof NLauncherNode) {
            List<StackOperation> stackOps = existEdge.getStackOps();
            stackOps.add(new StackOperation(OpType.pop, target.getWindow()));
            List<EventHandler> additionalCallbacks = helper.getCallbacks(
                wtg,
                target.getWindow(),
                MethodNames.onActivityDestroySubSig,
                owner,
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityDestroySubSig
                );
            callbacks.addAll(additionalCallbacks);
            forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event, eventHandlers,
                RootTag.finish_activity, stackOps, callbacks);
          } else {
            for (NActivityNode succOwner : ownership.get(successor)) {
              List<StackOperation> stackOps = existEdge.getStackOps();
              stackOps.add(new StackOperation(OpType.pop, target.getWindow()));
              List<EventHandler> additionalCallbacks = helper.getCallbacks(
                  wtg,
                  succOwner,
                  MethodNames.onActivityRestartSubSig,
                  MethodNames.onActivityStartSubSig,
                  MethodNames.onActivityResumeSubSig,
                  target.getWindow(),
                  MethodNames.onActivityDestroySubSig,
                  owner,
                  MethodNames.onActivityStopSubSig,
                  MethodNames.onActivityDestroySubSig
                  );
              callbacks.addAll(additionalCallbacks);
              forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event, eventHandlers,
                  RootTag.finish_activity, stackOps, callbacks);
            }
          }
        }
      }
    } else {
      // if base activity is not closed
      List<EventHandler> callbacks = Lists.newArrayList(rawCallbacks);

      if (source.getWindow() instanceof NActivityNode) {
        List<StackOperation> stackOps = existEdge.getStackOps();
        stackOps.add(new StackOperation(OpType.pop, target.getWindow()));
        List<EventHandler> additionalCallbacks = helper.getCallbacks(
            wtg,
            source.getWindow(),
            MethodNames.onActivityResumeSubSig,
            target.getWindow(),
            MethodNames.onActivityDestroySubSig
            );
        callbacks.addAll(additionalCallbacks);
        WTGEdge forkEdge = helper.createEdge(wtg, source, source, widget, event, eventHandlers,
            RootTag.finish_activity, stackOps, callbacks);
        NObjectNode targetWindow = forkEdge.getFinalTarget();
        if (targetWindow != null) {
          forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(targetWindow), widget,
              event, eventHandlers, RootTag.finish_activity, stackOps, callbacks);
        }
      } else if (source.getWindow() instanceof NLauncherNode) {
        List<StackOperation> stackOps = existEdge.getStackOps();
        stackOps.add(new StackOperation(OpType.pop, target.getWindow()));
        List<EventHandler> additionalCallbacks = helper.getCallbacks(
            wtg,
            target.getWindow(),
            MethodNames.onActivityDestroySubSig
            );
        callbacks.addAll(additionalCallbacks);
        WTGEdge forkEdge = helper.createEdge(wtg, source, source, widget, event, eventHandlers,
            RootTag.finish_activity, stackOps, callbacks);
        NObjectNode targetWindow = forkEdge.getFinalTarget();
        if (targetWindow != null) {
          forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(targetWindow), widget,
              event, eventHandlers, RootTag.finish_activity, stackOps, callbacks);
        }
      } else {
        for (NActivityNode owner : ownership.get(source)) {
          List<StackOperation> stackOps = existEdge.getStackOps();
          stackOps.add(new StackOperation(OpType.pop, target.getWindow()));
          List<EventHandler> additionalCallbacks = helper.getCallbacks(
              wtg,
              owner,
              MethodNames.onActivityResumeSubSig,
              target.getWindow(),
              MethodNames.onActivityDestroySubSig
              );
          callbacks.addAll(additionalCallbacks);
          WTGEdge forkEdge = helper.createEdge(wtg, source, source, widget, event, eventHandlers,
              RootTag.finish_activity, stackOps, callbacks);
          NObjectNode targetWindow = forkEdge.getFinalTarget();
          if (targetWindow != null) {
            forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(targetWindow), widget,
                event, eventHandlers, RootTag.finish_activity, stackOps, callbacks);
          }
        }
      }
    }
  }

  private Multimap<WTGNode, WTGEdge> buildInEdges(Multimap<WTGEdgeSig, WTGEdge> existEdges) {
    Multimap<WTGNode, WTGEdge> inEdges = HashMultimap.create();
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      WTGNode target = existEdge.getTargetNode();
      inEdges.put(target, existEdge);
    }
    return inEdges;
  }

  private void forkAndAddEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTGEdge existEdge,
      WTG wtg,
      WTGNode source,
      WTGNode target,
      NObjectNode widget,
      EventType event,
      Set<SootMethod> eventHandlers,
      RootTag root,
      List<StackOperation> stackOps,
      List<EventHandler> callbacks) {
    WTGEdge forkEdge = helper.createEdge(wtg, source, target, widget, event, eventHandlers,
        root, stackOps, callbacks);
    newEdges.put(forkEdge.getSig(), existEdge);
  }

  private Set<WTGNode> getSuccNode(WTG wtg, Multimap<WTGNode, WTGEdge> inEdges, WTGNode start) {
    Preconditions.checkNotNull(start);
    synchronized(start) {
      Set<WTGNode> successors = succCache.get(start);
      if (successors == null) {
        successors = helper.getSuccNode(wtg, inEdges, start);
        succCache.put(start, successors);
      }
      return successors;
    }
  }
}
