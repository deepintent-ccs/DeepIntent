/*
 * WTGAnalysisOutput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;
import presto.android.gui.wtg.flowgraph.NLauncherNode;
import presto.android.gui.wtg.intent.IntentAnalysis;

public class WTGAnalysisOutput {
  private WTG wtg;
  private GUIAnalysisOutput guiOutput;
  private IntentAnalysis intentAnalysis;

  public WTGAnalysisOutput(GUIAnalysisOutput guiOutput, WTGBuilder wtgBuilder) {
    this.guiOutput = guiOutput;
    if (wtgBuilder != null) {
      this.wtg = wtgBuilder.getWTG();
    }
  }

  public IntentAnalysis getIntentAnalysis() {
    if (intentAnalysis != null) {
      return intentAnalysis;
    }

    FlowgraphRebuilder rebuilder = FlowgraphRebuilder.v(guiOutput);
    intentAnalysis = new IntentAnalysis(guiOutput, rebuilder);
    return intentAnalysis;
  }

  public WTG getWTG() {
    return this.wtg;
  }

  public List<NObjectNode> getPopWindows(WTGEdge e) {
    return e.getPopWindows();
  }

  public List<NObjectNode> getPushWindows(WTGEdge e) {
    return e.getPushWindows();
  }

  public List<WTGEdge> expandFeasibleEdge(WindowStack stack) {
    return stack.expandFeasibleEdge();
  }

  public List<List<WTGEdge>> explorePaths(
      WTGNode n, int k, boolean feasibilityCheck, boolean allowLoop) {
    List<List<WTGEdge>> paths = Lists.newArrayList();
    if (feasibilityCheck) {
      for (WTGEdge outEdge : n.getOutEdges()) {
        List<WTGEdge> path = Lists.newArrayList(outEdge);
        WindowStack windowStack = new WindowStack(path);
        paths.addAll(exploreFeasiblePaths(windowStack, k, allowLoop));
      }
    } else {
      for (WTGEdge outEdge : n.getOutEdges()) {
        List<WTGEdge> path = Lists.newArrayList(outEdge);
        paths.addAll(exploreInfeasiblePaths(path, k, allowLoop));
      }
    }
    return paths;
  }
  
  private List<List<WTGEdge>> exploreFeasiblePaths(WindowStack path, int length, boolean allowLoop) {
    List<List<WTGEdge>> paths = Lists.newArrayList();
    if (isLauncherToLauncher(path.getPath().get(0))) {
      // don't allow the case where first edge is launcher to
      // launcher
      return paths;
    } else if (path.getPath().size() == length) {
      List<WTGEdge> copy = Lists.newArrayList(path.getPath());
      paths.add(copy);
      return paths;
    } else if (path.getPath().size() > length) {
      return paths;
    }
    for (WTGEdge outEdge : path.expandFeasibleEdge()) {
      if (isLauncherToLauncher(outEdge)) {
        // don't allow the case where the last edge is launcher to
        // launcher
        continue;
      } else if (!allowLoop && path.getPath().contains(outEdge)) {
        continue;
      } 
      WindowStack copy = path.copy();
      copy.addEdge(outEdge);
      paths.addAll(exploreFeasiblePaths(copy, length, allowLoop));
    }
    return paths;
  }
  
  private List<List<WTGEdge>> exploreInfeasiblePaths(List<WTGEdge> path, int length, boolean allowLoop) {
    List<List<WTGEdge>> paths = Lists.newArrayList();
    if (isLauncherToLauncher(path.get(0))) {
      // don't allow the case where first edge is launcher to
      // launcher
      return paths;
    }
    if (path.size() == length) {
      List<WTGEdge> copy = Lists.newArrayList(path);
      paths.add(copy);
      return paths;
    } else if (path.size() > length) {
      return paths;
    }
    WTGEdge lastEdge = path.get(path.size()-1);
    for (WTGEdge outEdge : lastEdge.getTargetNode().getOutEdges()) {
      if (isLauncherToLauncher(outEdge)) {
        // don't allow the case where the last edge is launcher to
        // launcher
        continue;
      } else if (!allowLoop && path.contains(outEdge)) {
        continue;
      } 
      path.add(outEdge);
      paths.addAll(exploreInfeasiblePaths(path, length, allowLoop));
      path.remove(path.size()-1);
    }
    return paths;
  }

  public List<List<WTGEdge>> getShortestFeasiblePath(
      WTGNode source, WTGNode dest, boolean feasibilityCheck) {
    Preconditions.checkNotNull(source);
    Preconditions.checkNotNull(dest);
    List<List<WTGEdge>> feasiblePaths = Lists.newArrayList();
    if (feasibilityCheck) {
      // add feasible paths only
      List<WindowStack> stacks = Lists.newArrayList();
      for (WTGEdge outEdge : source.getOutEdges()) {
        WindowStack initStack = new WindowStack(Lists.newArrayList(outEdge));
        stacks.add(initStack);
      }
      int length = -1;
      while (!stacks.isEmpty()) {
        WindowStack stack = stacks.remove(0);
        for (WTGEdge feasibleEdge : stack.expandFeasibleEdge()) {
          WindowStack newStack = stack.copy();
          newStack.addEdge(feasibleEdge);
          if (length != -1 && newStack.getPath().size() > length) {
            continue;
          }
          if (feasibleEdge.getTargetNode() == dest) {
            // find target
            List<WTGEdge> newPath = newStack.getPath();
            feasiblePaths.add(newPath);
            length = newPath.size();
          } else {
            stacks.add(newStack);
          }
        }
      }
    } else {
      // add (in)feasible paths
      List<List<WTGEdge>> paths = Lists.newArrayList();
      for (WTGEdge outEdge : source.getOutEdges()) {
        List<WTGEdge> initPath = Lists.newArrayList(outEdge);
        paths.add(initPath);
      }
      int length = -1;
      while (!paths.isEmpty()) {
        List<WTGEdge> path = paths.remove(0);
        WTGNode lastNode = path.get(path.size()-1).getTargetNode();
        for (WTGEdge edge : lastNode.getOutEdges()) {
          List<WTGEdge> newPath = Lists.newArrayList(path);
          newPath.add(edge);
          if (length != -1 && newPath.size() > length) {
            continue;
          }
          if (edge.getTargetNode() == dest) {
            // find target
            feasiblePaths.add(newPath);
            length = newPath.size();
          } else {
            paths.add(newPath);
          }
        }
      }
    }
    return feasiblePaths;
  }

  public List<StackOperation> getPushPopOperations(WTGEdge e) {
    return e.getStackOps();
  }

  public List<EventHandler> getCallbackSequence(WTGEdge e) {
    return e.getCallbacks();
  }

  public WindowStack generateWindowStack(List<WTGEdge> path) {
    return new WindowStack(path);
  }

  private boolean isLauncherToLauncher(WTGEdge edge) {
    return edge.getSourceNode() == edge.getTargetNode()
        && edge.getSourceNode().getWindow() instanceof NLauncherNode;
  }
}
