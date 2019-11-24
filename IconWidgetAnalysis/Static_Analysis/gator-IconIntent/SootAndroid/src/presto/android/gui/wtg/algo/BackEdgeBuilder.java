/*
 * BackEdgeBuilder.java - part of the GATOR project
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
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGHelper;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;
import presto.android.gui.wtg.flowgraph.NLauncherNode;
import soot.SootMethod;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public class BackEdgeBuilder implements Algorithm {
  // util
  private WTGHelper helper = WTGHelper.v();

  @SuppressWarnings("unused")
  private GUIAnalysisOutput guiOutput;

  // successor cache
  private Map<WTGNode, Set<WTGNode>> succCache;

  public BackEdgeBuilder(GUIAnalysisOutput guiOutput, FlowgraphRebuilder flowgraphRebuilder) {
    this.guiOutput = guiOutput;
    succCache = Maps.newHashMap();
  }

  public Multimap<WTGEdgeSig, WTGEdge> buildEdges(
      WTG wtg, Multimap<WTGEdgeSig, WTGEdge> existEdges, Multimap<WTGNode, NActivityNode> ownership) {
    createEdgeToInterimNode(wtg, existEdges);

    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    Set<AlgorithmInput> inputSet = Sets.newHashSet();
    Multimap<WTGNode, WTGEdge> inEdges = buildInEdges(existEdges);

    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      AlgorithmInput input = new AlgorithmInput(wtg, existEdge, this, inEdges, ownership);
      inputSet.add(input);
    }
    Map<AlgorithmInput, AlgorithmOutput> outputs = new BuildScheduler()
        .schedule(inputSet);
    for (AlgorithmInput input : outputs.keySet()) {
      AlgorithmOutput output = outputs.get(input);
      newEdges.putAll(output.newEdges);
    }
    return newEdges;
  }

  @Override
  public AlgorithmOutput execute(AlgorithmInput input) {
    AlgorithmOutput output =  buildEdge(input.wtg, input.inEdges, input.edge, input.ownership);
    return output;
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

  private AlgorithmOutput buildEdge(
      WTG wtg, Multimap<WTGNode, WTGEdge> inEdges, WTGEdge edge,
      Multimap<WTGNode, NActivityNode> ownership) {
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    AlgorithmOutput output = new AlgorithmOutput();
    output.newEdges = newEdges;

    RootTag root = edge.getRootTag();
    NObjectNode finalPushTarget = edge.getFinalTarget();
    List<NObjectNode> popWindows = edge.getPopWindows();
    if (!helper.isHardwareEdge(edge) && finalPushTarget != null) {
      // push some target windows
      forkAndAddEdge(newEdges, edge, wtg);
    } else if (!helper.isHardwareEdge(edge) && root == RootTag.cyclic_edge && !popWindows.isEmpty()) {
      // replace these edges with correct ones
      handleCyclicEdge(newEdges, wtg, edge, ownership, inEdges);
    } else if (helper.isBackEdge(edge)) {
      handleBackEdge(newEdges, wtg, edge, ownership, inEdges);
    } else {
      forkAndAddEdge(newEdges, edge, wtg);
    }
    return output;
  }

  private void handleBackEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTG wtg,
      WTGEdge existEdge,
      Multimap<WTGNode, NActivityNode> ownership,
      Multimap<WTGNode, WTGEdge> inEdges) {
    WTGNode target = existEdge.getTargetNode();
    NObjectNode targetWindow = target.getWindow();
    if (targetWindow instanceof NDialogNode
        && !((NDialogNode)targetWindow).cancelable) {
      // if the dialog is not cancelable, then pressing back button is not useful
      return;
    }
    Set<WTGNode> successors = getSuccNode(wtg, inEdges, target);
    for (WTGNode successor : successors) {
      NObjectNode succWindow = successor.getWindow();
      // create back edges
      if (targetWindow instanceof NActivityNode) {
        if (succWindow instanceof NActivityNode) {
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              targetWindow,
              MethodNames.onActivityPauseSubSig,
              succWindow,
              MethodNames.onActivityRestartSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              targetWindow,
              MethodNames.onActivityStopSubSig,
              MethodNames.onActivityDestroySubSig
              );
          forkAndAddEdge(newEdges, existEdge, wtg, target, successor, targetWindow,
              EventType.implicit_back_event, Sets.<SootMethod>newHashSet(), RootTag.implicit_back,
              targetWindow, null, callbacks);
        } else if (succWindow instanceof NLauncherNode) {
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              targetWindow,
              MethodNames.onActivityPauseSubSig,
              MethodNames.onActivityStopSubSig,
              MethodNames.onActivityDestroySubSig
              );
          forkAndAddEdge(newEdges, existEdge, wtg, target, successor, targetWindow,
              EventType.implicit_back_event, Sets.<SootMethod>newHashSet(),
              RootTag.implicit_back, targetWindow, null, callbacks);
        } else {
          for (NObjectNode owner : ownership.get(successor)) {
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                targetWindow,
                MethodNames.onActivityPauseSubSig,
                owner,
                MethodNames.onActivityRestartSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                targetWindow,
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityDestroySubSig
                );
            forkAndAddEdge(newEdges, existEdge, wtg, target, successor, targetWindow,
                EventType.implicit_back_event, Sets.<SootMethod>newHashSet(), 
                RootTag.implicit_back, targetWindow, null, callbacks);
          }
        }
      } else if (targetWindow instanceof NDialogNode) {
        // owner can not be close in this case
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            targetWindow,
            MethodNames.onDialogStopSubSig
            );
        forkAndAddEdge(newEdges, existEdge, wtg, target, successor, targetWindow,
            EventType.implicit_back_event, Sets.<SootMethod>newHashSet(), RootTag.implicit_back,
            targetWindow, null, callbacks);
      } else if (targetWindow instanceof NMenuNode) {
        // owner can not be close in this case
        for (NActivityNode owner : ownership.get(target)) {
          List<EventHandler> callbacks = null;
          if (targetWindow instanceof NOptionsMenuNode) {
            callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onCloseOptionsMenuSubsig
                );
          } else {
            callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onCloseContextMenuSubsig
                );
          }
          forkAndAddEdge(newEdges, existEdge, wtg, target, successor, targetWindow,
              EventType.implicit_back_event, Sets.<SootMethod>newHashSet(), RootTag.implicit_back,
              targetWindow, null, callbacks);
        }
      } else {
        Logger.err(getClass().getSimpleName(), "unexpected case");
      }
    }
  }

  private void handleCyclicEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTG wtg,
      WTGEdge existEdge,
      Multimap<WTGNode, NActivityNode> ownership,
      Multimap<WTGNode, WTGEdge> inEdges) {
    WTGNode source = existEdge.getSourceNode();
    NObjectNode widget = existEdge.getGUIWidget();
    EventType event = existEdge.getEventType();
    Set<SootMethod> eventHandlers = existEdge.getEventHandlers();
    if (source.getWindow() instanceof NActivityNode) {
      // pop owner shouldn't happen
      NObjectNode popSelf = existEdge.getPopSelf();
      if (popSelf != null) {
        Set<WTGNode> successors = getSuccNode(wtg, inEdges, source);
        for (WTGNode successor : successors) {
          NObjectNode succWindow = successor.getWindow();
          if (succWindow instanceof NActivityNode) {
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                source.getWindow(),
                MethodNames.onActivityPauseSubSig,
                succWindow,
                MethodNames.onActivityRestartSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                source.getWindow(),
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityDestroySubSig
                );
            // callbacks.addAll(0, existEdge.getWTGHandlers());
            forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                eventHandlers, RootTag.finish_activity, source.getWindow(), null, callbacks);
          } else if (succWindow instanceof NLauncherNode) {
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                source.getWindow(),
                MethodNames.onActivityPauseSubSig,
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityDestroySubSig
                );
            // callbacks.addAll(0, existEdge.getWTGHandlers());
            forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                eventHandlers, RootTag.finish_activity, source.getWindow(), null, callbacks);
          } else {
            for (NObjectNode owner : ownership.get(successor)) {
              List<EventHandler> callbacks = helper.getCallbacks(
                  wtg,
                  source.getWindow(),
                  MethodNames.onActivityPauseSubSig,
                  owner,
                  MethodNames.onActivityRestartSubSig,
                  MethodNames.onActivityStartSubSig,
                  MethodNames.onActivityResumeSubSig,
                  source.getWindow(),
                  MethodNames.onActivityStopSubSig,
                  MethodNames.onActivityDestroySubSig
                  );
              // callbacks.addAll(0, existEdge.getWTGHandlers());
              forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                  eventHandlers, RootTag.finish_activity, source.getWindow(), null, callbacks);
            }
          }
        }
      }
    } else if (source.getWindow() instanceof NDialogNode
        || source.getWindow() instanceof NMenuNode) {
      NObjectNode popSelf = existEdge.getPopSelf();
      NObjectNode popOwner = existEdge.getPopOwner();
      if (popOwner != null) {
        RootTag root = RootTag.finish_activity;
        List<EventHandler> additionalCallbacks = null;
        if (source.getWindow() instanceof NDialogNode) {
          additionalCallbacks = helper.getCallbacks(
              wtg,
              source.getWindow(),
              MethodNames.onDialogStopSubSig
              );
        } else if (source.getWindow() instanceof NContextMenuNode) {
          additionalCallbacks = helper.getCallbacks(
              wtg,
              popOwner,
              MethodNames.onCloseContextMenuSubsig
              );
        } else {
          additionalCallbacks = helper.getCallbacks(
              wtg,
              popOwner,
              MethodNames.onCloseOptionsMenuSubsig
              );
        }
        // additionalCallbacks.addAll(0, existEdge.getWTGHandlers());
        WTGNode popOwnerNode = wtg.getNode(popOwner);
        Set<WTGNode> successors = getSuccNode(wtg, inEdges, popOwnerNode);
        for (WTGNode successor : successors) {
          NObjectNode succWindow = successor.getWindow();
          if (succWindow instanceof NActivityNode) {
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                popOwner,
                MethodNames.onActivityPauseSubSig,
                succWindow,
                MethodNames.onActivityRestartSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                popOwner,
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityDestroySubSig
                );
            callbacks.addAll(0, additionalCallbacks);
            forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                eventHandlers, root, source.getWindow(), popOwner, callbacks);
          } else if (succWindow instanceof NLauncherNode) {
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                popOwner,
                MethodNames.onActivityPauseSubSig,
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityDestroySubSig
                );
            callbacks.addAll(0, additionalCallbacks);
            forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                eventHandlers, root, source.getWindow(), popOwner, callbacks);
          } else {
            for (NObjectNode owner : ownership.get(successor)) {
              List<EventHandler> callbacks = helper.getCallbacks(
                  wtg,
                  popOwner,
                  MethodNames.onActivityPauseSubSig,
                  owner,
                  MethodNames.onActivityRestartSubSig,
                  MethodNames.onActivityStartSubSig,
                  MethodNames.onActivityResumeSubSig,
                  popOwner,
                  MethodNames.onActivityStopSubSig,
                  MethodNames.onActivityDestroySubSig
                  );
              callbacks.addAll(0, additionalCallbacks);
              forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                  eventHandlers, root, source.getWindow(), popOwner, callbacks);
            }
          }
        }
      } else if (popSelf != null) {
        // only pop self, owner is not closed
        Set<WTGNode> successors = getSuccNode(wtg, inEdges, source);
        for (WTGNode successor : successors) {
          if (source.getWindow() instanceof NDialogNode) {
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                source.getWindow(),
                MethodNames.onDialogStopSubSig
                );
            // callbacks.addAll(0, existEdge.getWTGHandlers());
            forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                eventHandlers, RootTag.dismiss_dialog, source.getWindow(), null, callbacks);
          } else {
            for (NActivityNode owner : ownership.get(source)) {
              if (source.getWindow() instanceof NContextMenuNode) {
                List<EventHandler> callbacks = helper.getCallbacks(
                    wtg,
                    owner,
                    MethodNames.onCloseContextMenuSubsig
                    );
                // callbacks.addAll(0, existEdge.getWTGHandlers());
                forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                    eventHandlers, RootTag.close_menu, source.getWindow(), null, callbacks);
              } else {
                List<EventHandler> callbacks = helper.getCallbacks(
                    wtg,
                    owner,
                    MethodNames.onCloseOptionsMenuSubsig
                    );
                // callbacks.addAll(0, existEdge.getWTGHandlers());
                forkAndAddEdge(newEdges, existEdge, wtg, source, successor, widget, event,
                    eventHandlers, RootTag.close_menu, source.getWindow(), null, callbacks);
              }
            }
          }
        }
      }
    }
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
      NObjectNode popSelf,
      NObjectNode popOwner,
      List<EventHandler> callbacks) {
    WTGEdge forkEdge = forkEdge(wtg, source, target, widget, event, eventHandlers,
        root, popSelf, popOwner, callbacks);
    newEdges.put(forkEdge.getSig(), existEdge);
  }

  private void forkAndAddEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTGEdge existEdge,
      WTG wtg) {
    WTGEdge forkEdge = helper.createEdge(wtg, existEdge.getSourceNode(), existEdge.getTargetNode(),
        existEdge.getGUIWidget(), existEdge.getEventType(), existEdge.getEventHandlers(),
        existEdge.getRootTag(), existEdge.getStackOps(), existEdge.getCallbacks());
    newEdges.put(forkEdge.getSig(), existEdge);
  }

  private WTGEdge forkEdge(
      WTG wtg,
      WTGNode source,
      WTGNode target,
      NObjectNode widget,
      EventType event,
      Set<SootMethod> eventHandlers,
      RootTag root,
      NObjectNode popSelf,
      NObjectNode popOwner,
      List<EventHandler> callbacks) {
    List<StackOperation> stackOps = Lists.newArrayList();
    if (popSelf != null) {
      stackOps.add(new StackOperation(OpType.pop, popSelf));
    }
    if (popOwner != null) {
      stackOps.add(new StackOperation(OpType.pop, popOwner));
    }
    WTGEdge forkEdge = helper.createEdge(wtg, source, target, widget, event, eventHandlers,
        root, stackOps, callbacks);
    return forkEdge;
  }

  private WTGEdge forkEdge(
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
    return forkEdge;
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

  private void createEdgeToInterimNode(WTG wtg, Multimap<WTGEdgeSig, WTGEdge> existEdges) {
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      Map<NObjectNode, Integer> interimNodes = existEdge.getInterimTarget();
      for (NObjectNode window : interimNodes.keySet()) {
        int index = interimNodes.get(window);
        List<StackOperation> stackOps = existEdge.getStackOps();
        List<StackOperation> subStackOps = stackOps.subList(0,  index + 1);
        WTGEdge forkEdge = forkEdge(wtg, existEdge.getSourceNode(), wtg.getNode(window),
            existEdge.getGUIWidget(), existEdge.getEventType(),
            existEdge.getEventHandlers(), RootTag.fake_interim_edge,
            subStackOps, Lists.<EventHandler> newArrayList());
        newEdges.put(forkEdge.getSig(), forkEdge);
      }
    }
    existEdges.putAll(newEdges);
  }
}
