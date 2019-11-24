/*
 * CloseWindowEdgeBuilder.java - part of the GATOR project
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import presto.android.Configs;
import presto.android.Logger;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NMenuNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.EventHandler;
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
import presto.android.gui.wtg.parallel.CFGScheduler;
import presto.android.gui.wtg.util.Filter;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

public class CloseWindowEdgeBuilder implements Algorithm {
  private WTGHelper helper = WTGHelper.v();

  private GUIAnalysisOutput guiOutput;
  private FlowgraphRebuilder flowgraphRebuilder;

  public CloseWindowEdgeBuilder(GUIAnalysisOutput guiOutput, FlowgraphRebuilder flowgraphRebuilder) {
    this.guiOutput = guiOutput;
    this.flowgraphRebuilder = flowgraphRebuilder;
  }

  public Multimap<WTGEdgeSig, WTGEdge> buildEdges(WTG wtg, Multimap<WTGEdgeSig, WTGEdge> existEdges,
      Multimap<WTGNode, NActivityNode> ownership) {
    // map from new edges back to original edges 
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    // identify may/must-self-close edges
    Set<WTGEdgeSig> mustSelfCloseEdges = Sets.newHashSet();
    Set<WTGEdgeSig> maySelfCloseEdges = Sets.newHashSet(); 
    createMustOrMaySelfCloseEdges(existEdges, mustSelfCloseEdges, maySelfCloseEdges);

    // identify may/must-owner-close edges
    Set<WTGEdgeSig> mustOwnerCloseEdges = Sets.newHashSet();
    Set<WTGEdgeSig> mayOwnerCloseEdges = Sets.newHashSet(); 
    Set<WTGEdgeSig> notCloseOwnerEdges = Sets.newHashSet();
    createMustOrMayOwnerCloseEdges(wtg, existEdges, ownership, mustOwnerCloseEdges, mayOwnerCloseEdges,
        notCloseOwnerEdges);

    Set<AlgorithmInput> inputSet = Sets.newHashSet();
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      AlgorithmInput input = new AlgorithmInput(wtg, existEdge, this, ownership,
          mustOwnerCloseEdges, mayOwnerCloseEdges, notCloseOwnerEdges, mustSelfCloseEdges, maySelfCloseEdges);
      inputSet.add(input);
    }
    Map<AlgorithmInput, AlgorithmOutput> outputs = new BuildScheduler().schedule(inputSet);
    for (AlgorithmInput input : outputs.keySet()) {
      AlgorithmOutput output = outputs.get(input);
      newEdges.putAll(output.newEdges);
    }
    return newEdges;
  }

  @Override
  public AlgorithmOutput execute(AlgorithmInput input) {
    return buildEdge(input.wtg, input.edge, input.ownership, input.mustOwnerCloseEdges,
        input.mayOwnerCloseEdges, input.notCloseOwnerEdges, input.mustSelfCloseEdges, input.maySelfCloseEdges);
  }

  private AlgorithmOutput buildEdge(
      WTG wtg,
      WTGEdge edge,
      Multimap<WTGNode, NActivityNode> ownership,
      Set<WTGEdgeSig> mustOwnerCloseEdges,
      Set<WTGEdgeSig> mayOwnerCloseEdges,
      Set<WTGEdgeSig> notCloseOwnerEdges,
      Set<WTGEdgeSig> mustSelfCloseEdges,
      Set<WTGEdgeSig> maySelfCloseEdges) {
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    if (helper.isForwardEdge(edge)) {
      handleForwardEdge(wtg, edge, newEdges, ownership, mustOwnerCloseEdges,
          mayOwnerCloseEdges, notCloseOwnerEdges, mustSelfCloseEdges, maySelfCloseEdges);
    } else if (helper.isCyclicEdge(edge)) {
      handleCyclicEdge(wtg, edge, newEdges, ownership, mustOwnerCloseEdges,
          mayOwnerCloseEdges, notCloseOwnerEdges, mustSelfCloseEdges, maySelfCloseEdges);
    } else if (helper.isHardwareEdge(edge)) {
      handleHardwareEdge(wtg, edge, newEdges, ownership);
    } else if (helper.isBackEdge(edge)) {
      // purely copy the edge and add it
      forkAndAddEdge(newEdges, edge, wtg, null, null);
    } else {
      Logger.err(getClass().getSimpleName(), "unexpected edge: " + edge);
    }
    AlgorithmOutput output = new AlgorithmOutput();
    output.newEdges = newEdges;
    return output;
  }

  private void handleForwardEdge(
      WTG wtg,
      WTGEdge existEdge,
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      Multimap<WTGNode, NActivityNode> ownership,
      Set<WTGEdgeSig> mustOwnerCloseEdges,
      Set<WTGEdgeSig> mayOwnerCloseEdges,
      Set<WTGEdgeSig> notCloseOwnerEdges,
      Set<WTGEdgeSig> mustSelfCloseEdges,
      Set<WTGEdgeSig> maySelfCloseEdges) {
    WTGEdgeSig sig = existEdge.getSig();
    WTGNode source = existEdge.getSourceNode();
    WTGNode target = existEdge.getTargetNode();
    if (source.getWindow() instanceof NActivityNode) {
      // activity doesn't have owner
      if (mustSelfCloseEdges.contains(sig) || maySelfCloseEdges.contains(sig)) {
        // close self
        forkAndAddEdge(newEdges, existEdge, wtg, source, null, target);
      }
      if (!mustSelfCloseEdges.contains(sig)) {
        // not close self
        forkAndAddEdge(newEdges, existEdge, wtg, null, null, target);
      }
    } else if (source.getWindow() instanceof NMenuNode) {
      // must close self always happens in this case
      if (mustOwnerCloseEdges.contains(sig) || mayOwnerCloseEdges.contains(sig)) {
        // close owner
        for (NActivityNode owner : ownership.get(source)) {
          forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(owner),
              target);
        }
      }
      if (!mustOwnerCloseEdges.contains(sig)) {
        // not close owner
        forkAndAddEdge(newEdges, existEdge, wtg, source, null, target);
      }
    } else if (source.getWindow() instanceof NDialogNode) {
      if (mustOwnerCloseEdges.contains(sig) || mayOwnerCloseEdges.contains(sig)) {
        // close owner
        if (mustSelfCloseEdges.contains(sig) || maySelfCloseEdges.contains(sig)) {
          // close self
          for (NActivityNode owner : ownership.get(source)) {
            forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(owner), target);
          }
        }
        if (!mustSelfCloseEdges.contains(sig)) {
          // not close self
          for (NActivityNode owner : ownership.get(source)) {
            forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(owner), target);
          }
        }
      }
      if (!mustOwnerCloseEdges.contains(sig)) {
        // not close owner
        if (mustSelfCloseEdges.contains(sig) || maySelfCloseEdges.contains(sig)) {
          // close self
          forkAndAddEdge(newEdges, existEdge, wtg, source, null, target);
        }
        if (!mustSelfCloseEdges.contains(sig) && notCloseOwnerEdges.contains(sig)) {
          // not close self
          forkAndAddEdge(newEdges, existEdge, wtg, null, null, target);
        }
      }
    } else if (source == wtg.getLauncherNode()) {
      forkAndAddEdge(newEdges, existEdge, wtg, null, null, target);
    } else {
      Logger.err(getClass().getSimpleName(), "impossible case: " + source);
    }
  }

  private void handleCyclicEdge(
      WTG wtg,
      WTGEdge existEdge,
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      Multimap<WTGNode, NActivityNode> ownership,
      Set<WTGEdgeSig> mustOwnerCloseEdges,
      Set<WTGEdgeSig> mayOwnerCloseEdges,
      Set<WTGEdgeSig> notCloseOwnerEdges,
      Set<WTGEdgeSig> mustSelfCloseEdges,
      Set<WTGEdgeSig> maySelfCloseEdges) {
    WTGEdgeSig sig = existEdge.getSig();
    WTGNode source = existEdge.getSourceNode();
    if (source.getWindow() instanceof NActivityNode) {
      // activity doesn't have owner
      if (mustSelfCloseEdges.contains(sig) || maySelfCloseEdges.contains(sig)) {
        // close self
        forkAndAddEdge(newEdges, existEdge, wtg, source, null);
      }
      if (!mustSelfCloseEdges.contains(sig)) {
        // not close self
        forkAndAddEdge(newEdges, existEdge, wtg, null, null);
      }
    } else if (source.getWindow() instanceof NMenuNode) {
      // must close self always happens in this case
      if (mustOwnerCloseEdges.contains(sig) || mayOwnerCloseEdges.contains(sig)) {
        // close owner
        for (NActivityNode owner : ownership.get(source)) {
          forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(owner));
        }
      }
      if (!mustOwnerCloseEdges.contains(sig)) {
        // not close owner
        forkAndAddEdge(newEdges, existEdge, wtg, source, null);
      }
    } else if (source.getWindow() instanceof NDialogNode) {
      if (mustOwnerCloseEdges.contains(sig) || mayOwnerCloseEdges.contains(sig)) {
        // close owner
        if (mustSelfCloseEdges.contains(sig) || maySelfCloseEdges.contains(sig)) {
          // close self
          for (NActivityNode owner : ownership.get(source)) {
            forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(owner));
          }
        }
        if (!mustSelfCloseEdges.contains(sig)) {
          // not close self
          for (NActivityNode owner : ownership.get(source)) {
            forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(owner));
          }
        }
      }
      if (!mustOwnerCloseEdges.contains(sig)) {
        // not close owner
        if (mustSelfCloseEdges.contains(sig) || maySelfCloseEdges.contains(sig)) {
          // close self
          forkAndAddEdge(newEdges, existEdge, wtg, source, null);
        }
        if (!mustSelfCloseEdges.contains(sig) && notCloseOwnerEdges.contains(sig)) {
          // not close self
          forkAndAddEdge(newEdges, existEdge, wtg, null, null);
        }
      }
    }
  }

  private void handleHardwareEdge(
      WTG wtg,
      WTGEdge existEdge,
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      Multimap<WTGNode, NActivityNode> ownership) {
    WTGNode source = existEdge.getSourceNode();
    WTGNode target = existEdge.getTargetNode();
    EventType event = existEdge.getEventType();
    if (event == EventType.implicit_rotate_event) {
      if (source.getWindow() instanceof NActivityNode) {
        forkAndAddEdge(newEdges, existEdge, wtg, source, null, source);
      } else if (source.getWindow() instanceof NOptionsMenuNode) {
        for (NObjectNode owner : ownership.get(source)) {
          forkAndAddEdge(newEdges, existEdge, wtg, source, wtg.getNode(owner), wtg.getNode(owner), source);
        }
      } else if (source.getWindow() instanceof NDialogNode) {
        // target is owner
        forkAndAddEdge(newEdges, existEdge, wtg, source, target, target);
      } else if (source.getWindow() instanceof NContextMenuNode) {
        // target is owner
        forkAndAddEdge(newEdges, existEdge, wtg, source, target, target);
      } else {
        Logger.err(getClass().getSimpleName(), "impossible case");
      }
    } else {
      // event is power and home
      if (source.getWindow() instanceof NActivityNode || source.getWindow() instanceof NDialogNode) {
        forkAndAddEdge(newEdges, existEdge, wtg, null, null);
      } else {
        if (Configs.getAndroidAPILevel() < 11 && source.getWindow() instanceof NContextMenuNode) {
          forkAndAddEdge(newEdges, existEdge, wtg, null, null);
        } else {
          forkAndAddEdge(newEdges, existEdge, wtg, source, null);
        }
      }
    }
  }

  private void forkAndAddEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTGEdge existEdge,
      WTG wtg,
      WTGNode popSelf,
      WTGNode popOwner,
      WTGNode... pushTargets) {
    List<StackOperation> stackOps = Lists.newArrayList();
    List<EventHandler> callbacks = Lists.newArrayList();
    if (popSelf != null) {
      stackOps.add(new StackOperation(OpType.pop, popSelf.getWindow()));
    }
    if (popOwner != null) {
      stackOps.add(new StackOperation(OpType.pop, popOwner.getWindow()));
    }
    stackOps.addAll(existEdge.getStackOps());
    if (pushTargets != null) {
      for (int i = 0; i < pushTargets.length; i++) {
        stackOps.add(new StackOperation(OpType.push, pushTargets[i].getWindow()));
      }
    }
    WTGEdge forkEdge = helper.createEdge(wtg, existEdge.getSourceNode(), existEdge.getTargetNode(),
        existEdge.getGUIWidget(), existEdge.getEventType(), existEdge.getEventHandlers(),
        existEdge.getRootTag(), stackOps, callbacks);
    newEdges.put(forkEdge.getSig(), existEdge);
  }

  private void createMustOrMaySelfCloseEdges(
      final Multimap<WTGEdgeSig, WTGEdge> existEdges,
      final Set<WTGEdgeSig> mustSelfCloseEdges,
      final Set<WTGEdgeSig> maySelfCloseEdges) {
    Set<CFGAnalyzerInput> inputSet = Sets.newHashSet();
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      WTGNode source = existEdge.getSourceNode();
      NObjectNode widget = existEdge.getGUIWidget();
      Set<SootMethod> eventHandlers = existEdge.getEventHandlers();
      if (eventHandlers.isEmpty()) {
        continue;
      }
      for (SootMethod eventHandler : eventHandlers) {
        if (source.getWindow() instanceof NActivityNode) {
          CFGAnalyzerInput input = new CFGAnalyzerInput(widget, eventHandler,
              Filter.closeActivityStmtFilter);
          inputSet.add(input);
        } else if (source.getWindow() instanceof NDialogNode) {
          CFGAnalyzerInput input = new CFGAnalyzerInput(widget, eventHandler,
              Filter.closeDialogStmtFilter);
          inputSet.add(input);
        } else {
          // ignore the other cases
        }
      }
    }
    CFGScheduler scheduler = new CFGScheduler(guiOutput, flowgraphRebuilder);
    Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput = scheduler.schedule(inputSet);
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      WTGNode source = existEdge.getSourceNode();
      NObjectNode widget = existEdge.getGUIWidget();
      Set<SootMethod> eventHandlers = existEdge.getEventHandlers();
      if (eventHandlers.isEmpty()) {
        continue;
      }
      for (SootMethod eventHandler : eventHandlers) {
        CFGAnalyzerOutput terminateAndAvoid = null;
        if (source.getWindow() instanceof NActivityNode) {
          CFGAnalyzerInput input = new CFGAnalyzerInput(widget, eventHandler,
              Filter.closeActivityStmtFilter);
          terminateAndAvoid = analyzeOutput.get(input);
          Assert.assertNotNull("[Error]: cfg analyze input is not processed yet: "
              + input, terminateAndAvoid);
        } else if (source.getWindow() instanceof NDialogNode) {
          CFGAnalyzerInput input = new CFGAnalyzerInput(widget, eventHandler,
              Filter.closeDialogStmtFilter);
          terminateAndAvoid = analyzeOutput.get(input);
          Assert.assertNotNull("[Error]: cfg analyze input is not processed yet: "
              + input, terminateAndAvoid);
        } else {
          terminateAndAvoid = new CFGAnalyzerOutput();
          terminateAndAvoid.targets = HashMultimap.create();
          terminateAndAvoid.targets.put(source.getWindow(), null);
          terminateAndAvoid.avoid = false;
        }
        Multimap<NObjectNode, Pair<Stmt, SootMethod>> terminatedWindows = terminateAndAvoid.targets;
        if (terminatedWindows.containsKey(source.getWindow())) {
          if (terminateAndAvoid.avoid) {
            maySelfCloseEdges.add(sig);
          } else {
            mustSelfCloseEdges.add(sig);
          }
        }
      }
    }
  }

  private void createMustOrMayOwnerCloseEdges(
      final WTG wtg,
      final Multimap<WTGEdgeSig, WTGEdge> existEdges,
      final Multimap<WTGNode, NActivityNode> ownership,
      final Set<WTGEdgeSig> mustOwnerCloseEdges,
      final Set<WTGEdgeSig> mayOwnerCloseEdges,
      final Set<WTGEdgeSig> notCloseOwnerEdges) {
    Set<CFGAnalyzerInput> inputSet = Sets.newHashSet();
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      NObjectNode widget = existEdge.getGUIWidget();
      Set<SootMethod> eventHandlers = existEdge.getEventHandlers();
      if (eventHandlers.isEmpty()) {
        continue;
      }
      for (SootMethod eventHandler : eventHandlers) {
        inputSet.add(new CFGAnalyzerInput(widget, eventHandler,
            Filter.closeActivitySystemStmtFilter));
      }
    }
    CFGScheduler scheduler = new CFGScheduler(guiOutput, flowgraphRebuilder);
    Map<CFGAnalyzerInput, CFGAnalyzerOutput> analyzeOutput = scheduler.schedule(inputSet);

    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      WTGNode source = existEdge.getSourceNode();
      NObjectNode widget = existEdge.getGUIWidget();
      Collection<NActivityNode> owners = ownership.get(source);
      Set<SootMethod> eventHandlers = existEdge.getEventHandlers();
      if (eventHandlers.isEmpty()) {
        continue;
      }
      boolean closeOwner = false;
      for (SootMethod eventHandler : eventHandlers) {
        CFGAnalyzerInput input = new CFGAnalyzerInput(widget, eventHandler,
            Filter.closeActivitySystemStmtFilter);
        CFGAnalyzerOutput targetsAndAvoid = analyzeOutput.get(input);
        Assert.assertNotNull("[Error]: cfg analyze input is not processed yet: " + input,
            targetsAndAvoid);
        if (targetsAndAvoid.exitSystem) {
          closeOwner = true;
        } else if (!targetsAndAvoid.targets.isEmpty()) {
          closeOwner = true;
        }
        for (NActivityNode owner : owners) {
          Multimap<NObjectNode, Pair<Stmt, SootMethod>> closeActivityTargets = HashMultimap.create();
          closeActivityTargets.putAll(targetsAndAvoid.targets);
          if (targetsAndAvoid.exitSystem) {
            closeActivityTargets.put(owner, null);
          }
          Set<NObjectNode> terminatedWindows = closeActivityTargets.keySet();
          if (terminatedWindows.contains(owner)) {
            if (targetsAndAvoid.avoid) {
              mayOwnerCloseEdges.add(sig);
            } else {
              mustOwnerCloseEdges.add(sig);
            }
          }
        }
      }
      if (!closeOwner) {
        notCloseOwnerEdges.add(sig);
      }
    }
  }
}
