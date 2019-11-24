/*
 * CallbackSequenceBuilder.java - part of the GATOR project
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
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.RootTag;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGHelper;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;
import presto.android.gui.wtg.flowgraph.NLauncherNode;

/**
 * add callback sequences for forward, cyclic and hardware edges
 * leaving back edges as well as cyclic whose target are unknown
 * to later stages 
 * */
public class CallbackSequenceBuilder implements Algorithm  {
  private WTGHelper helper = WTGHelper.v();

  @SuppressWarnings("unused")
  private GUIAnalysisOutput guiOutput;
  
  @SuppressWarnings("unused")
  private FlowgraphRebuilder flowgraphRebuilder;

  public CallbackSequenceBuilder(GUIAnalysisOutput guiOutput,
      FlowgraphRebuilder flowgraphRebuilder) {
    this.guiOutput = guiOutput;
    this.flowgraphRebuilder = flowgraphRebuilder;
  }

  public Multimap<WTGEdgeSig, WTGEdge> buildEdges(
      WTG wtg, Multimap<WTGEdgeSig, WTGEdge> existEdges, Multimap<WTGNode, NActivityNode> ownership) {
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    Set<AlgorithmInput> inputSet = Sets.newHashSet();
    for (WTGEdgeSig sig : existEdges.keySet()) {
      WTGEdge existEdge = sig.getEdge();
      AlgorithmInput input = new AlgorithmInput(wtg, existEdge, this, ownership);
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
    return buildEdge(input.wtg, input.edge, input.ownership);
  }

  private AlgorithmOutput buildEdge(
      WTG wtg, WTGEdge existEdge, Multimap<WTGNode, NActivityNode> ownership) {
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    if (helper.isBackEdge(existEdge)) {
      // purely copy the edge and add it
      forkAndAddEdge(newEdges, existEdge, wtg, existEdge.getCallbacks());
    } else if (helper.isHardwareEdge(existEdge)) {
      // hardware edge
      handleHardwareEdge(newEdges, wtg, existEdge, ownership);
    } else if (!existEdge.getPushWindows().isEmpty()) {
      // push some window
      handlePushEdge(newEdges, wtg, existEdge, ownership);
    } else if (existEdge.getPushWindows().isEmpty()
        && existEdge.getPopWindows().isEmpty()) {
      // don't push or pop any window. truely cyclic edge
      forkAndAddEdge(newEdges, existEdge, wtg, Lists.<EventHandler>newArrayList(/*existEdge.getWTGHandlers()*/));
    } else {
      // other cases, just copy all the edges
      forkAndAddEdge(newEdges, existEdge, wtg, existEdge.getCallbacks());
    }
    AlgorithmOutput output = new AlgorithmOutput();
    output.newEdges = newEdges;
    return output;
  }

  private void handlePushEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTG wtg,
      WTGEdge existEdge,
      Multimap<WTGNode, NActivityNode> ownership) {
    // step 1: build callback sequence for forward edges
    Multimap<WTGEdgeSig, WTGEdge> afterStep1 = handlePushEdgeStep1(wtg, existEdge, ownership);

    // step2: consider pop self on the top of previous callback sequences
    Multimap<WTGEdgeSig, WTGEdge> afterStep2 = handlePushEdgeStep2(wtg, afterStep1, ownership);

    // step3: consider pop owner
    Multimap<WTGEdgeSig, WTGEdge> afterStep3 = handlePushEdgeStep3(wtg, afterStep2, ownership);
    newEdges.putAll(afterStep3);
  }

  private Multimap<WTGEdgeSig, WTGEdge> handlePushEdgeStep1(
      WTG wtg,
      WTGEdge edge,
      Multimap<WTGNode, NActivityNode> ownership) {
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create(); 
    WTGNode source = edge.getSourceNode();
    List<NObjectNode> pushWindows = edge.getPushWindows();
    WTGNode finalTarget = null, interimTarget = null;
    if (pushWindows.size() == 0 || pushWindows.size() > 2) {
      Logger.err(getClass().getSimpleName(), "should only push 1 or 2 windows");
    }
    interimTarget = wtg.getNode(pushWindows.get(0));
    if (pushWindows.size() == 2) {
      finalTarget = wtg.getNode(pushWindows.get(pushWindows.size()-1));
    }
    if (interimTarget.getWindow() instanceof NActivityNode) {
      if (source.getWindow() instanceof NActivityNode) {
        if (finalTarget == null) {
          // source is activity, interim target is activity final target is null
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              source.getWindow(),
              MethodNames.onActivityPauseSubSig,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              source.getWindow(),
              MethodNames.onActivityStopSubSig
              );
          // callbacks.addAll(0, edge.getWTGHandlers());
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else if (finalTarget.getWindow() instanceof NActivityNode) {
          // source is activity, interim target is activity final target is activity
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              source.getWindow(),
              MethodNames.onActivityPauseSubSig,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              MethodNames.onActivityPauseSubSig,
              finalTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              source.getWindow(),
              MethodNames.onActivityStopSubSig,
              interimTarget.getWindow(),
              MethodNames.onActivityStopSubSig
              );
          // callbacks.addAll(0, edge.getWTGHandlers());
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else if (finalTarget.getWindow() instanceof NContextMenuNode) {
          // source is activity, interim target is activity final target is context menu
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              source.getWindow(),
              MethodNames.onActivityPauseSubSig,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              interimTarget.getWindow(),
              MethodNames.onCreateContextMenuSubSig,
              source.getWindow(),
              MethodNames.onActivityStopSubSig
              );
          // callbacks.addAll(0, edge.getWTGHandlers());
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else if (finalTarget.getWindow() instanceof NOptionsMenuNode) {
          // source is activity, interim target is activity final target is options menu
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              source.getWindow(),
              MethodNames.onActivityPauseSubSig,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              interimTarget.getWindow(),
              MethodNames.onCreateOptionsMenuSubsig,
              source.getWindow(),
              MethodNames.onActivityStopSubSig
              );
          // callbacks.addAll(0, edge.getWTGHandlers());
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else if (finalTarget.getWindow() instanceof NDialogNode) {
          // source is activity, interim target is activity final target is dialog
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              source.getWindow(),
              MethodNames.onActivityPauseSubSig,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              finalTarget.getWindow(),
              MethodNames.onDialogCreateSubSig,
              source.getWindow(),
              MethodNames.onActivityStopSubSig
              );
          // callbacks.addAll(0, edge.getWTGHandlers());
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else {
          Logger.err(getClass().getSimpleName(), "impossible case");
        }
      } else if (source.getWindow() instanceof NLauncherNode) {
        if (finalTarget == null) {
          // source is launcher, interim target is activity final target is null
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig
              );
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else if (finalTarget.getWindow() instanceof NActivityNode) {
          // source is launcher, interim target is activity final target is activity
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              MethodNames.onActivityPauseSubSig,
              finalTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              interimTarget.getWindow(),
              MethodNames.onActivityStopSubSig
              );
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else if (finalTarget.getWindow() instanceof NContextMenuNode) {
          // source is launcher, interim target is activity final target is context menu
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              interimTarget.getWindow(),
              MethodNames.onCreateContextMenuSubSig
              );
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else if (finalTarget.getWindow() instanceof NOptionsMenuNode) {
          // source is launcher, interim target is activity final target is options menu
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              interimTarget.getWindow(),
              MethodNames.onCreateOptionsMenuSubsig
              );
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else if (finalTarget.getWindow() instanceof NDialogNode) {
          // source is launcher, interim target is activity final target is dialog
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              interimTarget.getWindow(),
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig,
              finalTarget.getWindow(),
              MethodNames.onDialogCreateSubSig
              );
          forkAndAddEdge(newEdges, edge, wtg, callbacks);
        } else {
          Logger.err(getClass().getSimpleName(), "impossible case");
        }
      } else {
        for (NActivityNode owner : ownership.get(source)) {
          if (finalTarget == null) {
            // source is menu/dialog, interim target is activity final target is null
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onActivityPauseSubSig,
                interimTarget.getWindow(),
                MethodNames.onActivityCreateSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                owner,
                MethodNames.onActivityStopSubSig
                );
            // callbacks.addAll(0, edge.getWTGHandlers());
            forkAndAddEdge(newEdges, edge, wtg, callbacks);
          } else if (finalTarget.getWindow() instanceof NActivityNode) {
            // source is menu/dialog, interim target is activity final target is activity
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onActivityPauseSubSig,
                interimTarget.getWindow(),
                MethodNames.onActivityCreateSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                MethodNames.onActivityPauseSubSig,
                finalTarget.getWindow(),
                MethodNames.onActivityCreateSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                owner,
                MethodNames.onActivityStopSubSig,
                interimTarget.getWindow(),
                MethodNames.onActivityStopSubSig
                );
            // callbacks.addAll(0, edge.getWTGHandlers());
            forkAndAddEdge(newEdges, edge, wtg, callbacks);
          } else if (finalTarget.getWindow() instanceof NOptionsMenuNode) {
            // source is menu/dialog, interim target is activity final target is options menu
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onActivityPauseSubSig,
                interimTarget.getWindow(),
                MethodNames.onActivityCreateSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                interimTarget.getWindow(),
                MethodNames.onCreateOptionsMenuSubsig,
                owner,
                MethodNames.onActivityStopSubSig
                );
            // callbacks.addAll(0, edge.getWTGHandlers());
            forkAndAddEdge(newEdges, edge, wtg, callbacks);
          } else if (finalTarget.getWindow() instanceof NContextMenuNode) {
            // source is menu/dialog, interim target is activity final target is context menu
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onActivityPauseSubSig,
                interimTarget.getWindow(),
                MethodNames.onActivityCreateSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                interimTarget.getWindow(),
                MethodNames.onCreateContextMenuSubSig,
                owner,
                MethodNames.onActivityStopSubSig
                );
            // callbacks.addAll(0, edge.getWTGHandlers());
            forkAndAddEdge(newEdges, edge, wtg, callbacks);
          } else if (finalTarget.getWindow() instanceof NDialogNode) {
            // source is menu/dialog, interim target is activity final target is dialog
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onActivityPauseSubSig,
                interimTarget.getWindow(),
                MethodNames.onActivityCreateSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig,
                finalTarget.getWindow(),
                MethodNames.onDialogCreateSubSig,
                owner,
                MethodNames.onActivityStopSubSig
                );
            // callbacks.addAll(0, edge.getWTGHandlers());
            forkAndAddEdge(newEdges, edge, wtg, callbacks);
          } else {
            Logger.err(getClass().getSimpleName(), "impossible case");
          }
        }
      }
    } else if (interimTarget.getWindow() instanceof NMenuNode) {
      // source can only be activity
      List<EventHandler> callbacks = null;
      if (interimTarget.getWindow() instanceof NOptionsMenuNode) {
        callbacks = helper.getCallbacks(
          wtg,
          source.getWindow(),
          MethodNames.onCreateOptionsMenuSubsig,
          MethodNames.onPrepareOptionsMenuSubsig
          );
      } else {
        callbacks = helper.getCallbacks(
          wtg,
          source.getWindow(),
          MethodNames.onCreateContextMenuSubSig
          );
      }
      // callbacks.addAll(0, edge.getWTGHandlers());
      forkAndAddEdge(newEdges, edge, wtg, callbacks);
    } else if (interimTarget.getWindow() instanceof NDialogNode) {
      List<EventHandler> callbacks = helper.getCallbacks(
          wtg,
          interimTarget.getWindow(),
          MethodNames.onDialogCreateSubSig
          );
      // callbacks.addAll(0, edge.getWTGHandlers());
      forkAndAddEdge(newEdges, edge, wtg, callbacks);
    } else {
      Logger.err(getClass().getSimpleName(), "unexpected case");
    }
    return newEdges;
  }

  private Multimap<WTGEdgeSig, WTGEdge> handlePushEdgeStep2(
      WTG wtg,
      Multimap<WTGEdgeSig, WTGEdge> afterStep1,
      Multimap<WTGNode, NActivityNode> ownership) {
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    for (WTGEdgeSig sig : afterStep1.keySet()) {
      WTGEdge edge = sig.getEdge();
      WTGNode source = edge.getSourceNode();
      WTGNode target = edge.getTargetNode();
      List<NObjectNode> closeWindows = edge.getPopWindows();
      if (closeWindows.contains(source.getWindow())) {
        // pop self
        if (source.getWindow() instanceof NActivityNode) {
          if (target.getWindow() instanceof NActivityNode) {
            List<EventHandler> additionalCallbacks = helper.getCallbacks(
                wtg,
                source.getWindow(),
                MethodNames.onActivityDestroySubSig);
            List<EventHandler> callbacks = edge.getCallbacks();
            callbacks.addAll(additionalCallbacks);
            forkAndAddEdge(newEdges, edge, wtg, callbacks);
          }
        } else if (source.getWindow() instanceof NMenuNode) {
          for (NActivityNode owner : ownership.get(source)) {
            List<EventHandler> additionalCallbacks = null;
            if (source.getWindow() instanceof NOptionsMenuNode) {
              additionalCallbacks = helper.getCallbacks(
                  wtg,
                  owner,
                  MethodNames.onCloseOptionsMenuSubsig
                  );
            } else {
              additionalCallbacks = helper.getCallbacks(
                  wtg,
                  owner,
                  MethodNames.onCloseContextMenuSubsig
                  );
            }
            if (target.getWindow() instanceof NActivityNode) {
              List<EventHandler> callbacks = edge.getCallbacks();
              callbacks.addAll(1, additionalCallbacks);
              forkAndAddEdge(newEdges, edge, wtg, callbacks);
            } else if (target.getWindow() instanceof NDialogNode) {
              List<EventHandler> callbacks = edge.getCallbacks();
              callbacks.addAll(additionalCallbacks);
              forkAndAddEdge(newEdges, edge, wtg, callbacks);
            }
          }
        } else if (source.getWindow() instanceof NDialogNode) {
          List<EventHandler> additionalCallbacks =
              helper.getCallbacks(
                  wtg,
                  source.getWindow(),
                  MethodNames.onDialogStopSubSig);
          if (target.getWindow() instanceof NActivityNode) {
            List<EventHandler> callbacks = edge.getCallbacks();
            callbacks.addAll(1, additionalCallbacks);
            forkAndAddEdge(newEdges, edge, wtg, callbacks);
          } else if (target.getWindow() instanceof NDialogNode) {
            List<EventHandler> callbacks = edge.getCallbacks();
            callbacks.addAll(additionalCallbacks);
            forkAndAddEdge(newEdges, edge, wtg, callbacks);
          }
        } else {
          Logger.err(getClass().getSimpleName(), "unexpected case");
        }
      } else {
        forkAndAddEdge(newEdges, edge, wtg, edge.getCallbacks());
      }
    }
    return newEdges;
  }

  private Multimap<WTGEdgeSig, WTGEdge> handlePushEdgeStep3(
      WTG wtg,
      Multimap<WTGEdgeSig, WTGEdge> afterStep2,
      Multimap<WTGNode, NActivityNode> ownership) {
    Multimap<WTGEdgeSig, WTGEdge> newEdges = HashMultimap.create();
    for (WTGEdgeSig sig : afterStep2.keySet()) {
      WTGEdge edge = sig.getEdge();
      WTGNode source = edge.getSourceNode();
      WTGNode target = edge.getTargetNode();
      Collection<NActivityNode> owners = ownership.get(source);
      List<NObjectNode> closeWindows = edge.getPopWindows();
      boolean closeOwner = false;
      for (NActivityNode owner : owners) {
        if (closeWindows.contains(owner)) {
          closeOwner = true;
          if (source.getWindow() instanceof NActivityNode
              || source.getWindow() instanceof NLauncherNode) {
            // activity should not have owner
          } else if (source.getWindow() instanceof NMenuNode) {
            if (target.getWindow() instanceof NActivityNode) {
              List<EventHandler> additionalCallbacks = helper.getCallbacks(
                  wtg,
                  owner,
                  MethodNames.onActivityDestroySubSig);
              List<EventHandler> callbacks = edge.getCallbacks();
              callbacks.addAll(additionalCallbacks);
              forkAndAddEdge(newEdges, edge, wtg, callbacks);
            }
          } else if (source.getWindow() instanceof NDialogNode) {
            if (target.getWindow() instanceof NActivityNode) {
              List<EventHandler> additionalCallbacks = helper.getCallbacks(
                  wtg,
                  owner,
                  MethodNames.onActivityDestroySubSig);
              List<EventHandler> callbacks = edge.getCallbacks();
              callbacks.addAll(additionalCallbacks);
              forkAndAddEdge(newEdges, edge, wtg, callbacks);
            }
          } else {
            Logger.err(getClass().getSimpleName(), "unexpected case");
          }
        }
      }
      if (!closeOwner) {
        forkAndAddEdge(newEdges, edge, wtg, edge.getCallbacks());
      }
    }
    return newEdges;
  }

  private void handleHardwareEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTG wtg,
      WTGEdge existEdge,
      Multimap<WTGNode, NActivityNode> ownership) {
    WTGNode source = existEdge.getSourceNode();
    WTGNode target = existEdge.getTargetNode();
    RootTag root = existEdge.getRootTag();
    if (source.getWindow() instanceof NActivityNode) {
      if (root == RootTag.implicit_rotate) {
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            source.getWindow(),
            MethodNames.onActivityPauseSubSig,
            MethodNames.onActivityStopSubSig,
            MethodNames.onActivityDestroySubSig,
            MethodNames.onActivityCreateSubSig,
            MethodNames.onActivityStartSubSig,
            MethodNames.onActivityResumeSubSig);
        forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
      } else if (root == RootTag.implicit_home) {
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            source.getWindow(),
            MethodNames.onActivityPauseSubSig,
            MethodNames.onActivityStopSubSig,
            MethodNames.onActivityRestartSubSig,
            MethodNames.onActivityStartSubSig,
            MethodNames.onActivityResumeSubSig);
        forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
      } else if (root == RootTag.implicit_power) {
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            source.getWindow(),
            MethodNames.onActivityPauseSubSig,
            MethodNames.onActivityStopSubSig,
            MethodNames.onActivityRestartSubSig,
            MethodNames.onActivityStartSubSig,
            MethodNames.onActivityResumeSubSig);
        forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
      } else {
        Logger.err(getClass().getSimpleName(), "unexpected case");
      }
    } else if (source.getWindow() instanceof NOptionsMenuNode) {
      if (root == RootTag.implicit_rotate) {
        NObjectNode owner = existEdge.getPopOwner();
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            owner,
            MethodNames.onActivityPauseSubSig,
            MethodNames.onCloseOptionsMenuSubsig,
            MethodNames.onActivityStopSubSig,
            MethodNames.onActivityDestroySubSig,
            MethodNames.onActivityCreateSubSig,
            MethodNames.onActivityStartSubSig,
            MethodNames.onActivityResumeSubSig,
            MethodNames.onCreateOptionsMenuSubsig,
            MethodNames.onPrepareOptionsMenuSubsig);
        forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
      } else if (root == RootTag.implicit_home) {
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            target.getWindow(),
            MethodNames.onActivityPauseSubSig,
            MethodNames.onCloseOptionsMenuSubsig,
            MethodNames.onActivityStopSubSig,
            MethodNames.onActivityRestartSubSig,
            MethodNames.onActivityStartSubSig,
            MethodNames.onActivityResumeSubSig);
        forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
      } else if (root == RootTag.implicit_power) {
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            target.getWindow(),
            MethodNames.onActivityPauseSubSig,
            MethodNames.onCloseOptionsMenuSubsig,
            MethodNames.onActivityStopSubSig,
            MethodNames.onActivityRestartSubSig,
            MethodNames.onActivityStartSubSig,
            MethodNames.onActivityResumeSubSig);
        forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
      } else {
        Logger.err(getClass().getSimpleName(), "unexpected case");
      }
    } else if (source.getWindow() instanceof NContextMenuNode) {
      if (root == RootTag.implicit_rotate) {
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            target.getWindow(),
            MethodNames.onActivityPauseSubSig,
            MethodNames.onCloseContextMenuSubsig,
            MethodNames.onActivityStopSubSig,
            MethodNames.onActivityDestroySubSig,
            MethodNames.onActivityCreateSubSig,
            MethodNames.onActivityStartSubSig,
            MethodNames.onActivityResumeSubSig);
        forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
      } else if (root == RootTag.implicit_home) {
        List<EventHandler> callbacks = helper.getCallbacks(
            wtg,
            target.getWindow(),
            MethodNames.onActivityPauseSubSig,
            MethodNames.onCloseContextMenuSubsig,
            MethodNames.onActivityStopSubSig,
            MethodNames.onActivityRestartSubSig,
            MethodNames.onActivityStartSubSig,
            MethodNames.onActivityResumeSubSig);
        forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
      } else if (root == RootTag.implicit_power) {
        if (Configs.getAndroidAPILevel() < 11) {
          for (NActivityNode owner : ownership.get(source)) {
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onActivityPauseSubSig,
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityRestartSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig);
            forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
          }
        } else {
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              target.getWindow(),
              MethodNames.onActivityPauseSubSig,
              MethodNames.onCloseContextMenuSubsig,
              MethodNames.onActivityStopSubSig,
              MethodNames.onActivityRestartSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig);
          forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
        }
      } else {
        Logger.err(getClass().getSimpleName(), "unexpected case");
      }
    } else if (source.getWindow() instanceof NDialogNode) {
      if (root == RootTag.implicit_rotate) {
        if (((NDialogNode) source.getWindow()).cancelable && Configs.getAndroidAPILevel() != 10) {
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              target.getWindow(),
              MethodNames.onActivityPauseSubSig,
              source.getWindow(),
              MethodNames.onDialogStopSubSig,
              target.getWindow(),
              MethodNames.onActivityStopSubSig,
              MethodNames.onActivityDestroySubSig,
              MethodNames.onActivityCreateSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig);
          forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
        } else {
          for (NActivityNode owner : ownership.get(source)) {
            List<EventHandler> callbacks = helper.getCallbacks(
                wtg,
                owner,
                MethodNames.onActivityPauseSubSig,
                MethodNames.onActivityStopSubSig,
                MethodNames.onActivityDestroySubSig,
                MethodNames.onActivityCreateSubSig,
                MethodNames.onActivityStartSubSig,
                MethodNames.onActivityResumeSubSig);
            forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
          }
        }
      } else if (root == RootTag.implicit_home) {
        for (NActivityNode owner : ownership.get(source)) {
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              owner,
              MethodNames.onActivityPauseSubSig,
              MethodNames.onActivityStopSubSig,
              MethodNames.onActivityRestartSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig);
          forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
        }
      } else if (root == RootTag.implicit_power) {
        for (NActivityNode owner : ownership.get(source)) {
          List<EventHandler> callbacks = helper.getCallbacks(
              wtg,
              owner,
              MethodNames.onActivityPauseSubSig,
              MethodNames.onActivityStopSubSig,
              MethodNames.onActivityRestartSubSig,
              MethodNames.onActivityStartSubSig,
              MethodNames.onActivityResumeSubSig);
          forkAndAddEdge(newEdges, existEdge, wtg, callbacks);
        }
      } else {
        Logger.err(getClass().getSimpleName(), "unexpected case");
      }
    } else {
      Logger.err(getClass().getSimpleName(), "unexpected case");
    }
  }

  private void forkAndAddEdge(
      Multimap<WTGEdgeSig, WTGEdge> newEdges,
      WTGEdge existEdge,
      WTG wtg,
      List<EventHandler> callbacks) {
    WTGEdge forkEdge = helper.createEdge(wtg, existEdge.getSourceNode(), existEdge.getTargetNode(),
        existEdge.getGUIWidget(), existEdge.getEventType(), existEdge.getEventHandlers(),
        existEdge.getRootTag(), existEdge.getStackOps(), callbacks);
    newEdges.put(forkEdge.getSig(), existEdge);
  }
}
