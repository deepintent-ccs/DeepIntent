/*
 * Ch5Client.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients;

import java.util.Map;
import java.util.Set;

import presto.android.Configs;
import presto.android.Debug;
import presto.android.Hierarchy;
import presto.android.gui.FixpointSolver;
import presto.android.gui.Flowgraph;
import presto.android.gui.GUIAnalysisClient;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.GraphUtil;
import presto.android.gui.graph.NAddView1OpNode;
import presto.android.gui.graph.NAddView2OpNode;
import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NFindView1OpNode;
import presto.android.gui.graph.NFindView2OpNode;
import presto.android.gui.graph.NFindView3OpNode;
import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NInflNode;
import presto.android.gui.graph.NInflate1OpNode;
import presto.android.gui.graph.NInflate2OpNode;
import presto.android.gui.graph.NLayoutIdNode;
import presto.android.gui.graph.NMenuIdNode;
import presto.android.gui.graph.NMenuInflateOpNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.graph.NSetIdOpNode;
import presto.android.gui.graph.NSetListenerOpNode;
import presto.android.gui.graph.NViewAllocNode;
import presto.android.gui.graph.NWidgetIdNode;
import soot.SootClass;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

public class Ch5Client implements GUIAnalysisClient {
  GUIAnalysisOutput output;
  Hierarchy hier = Hierarchy.v();
  GraphUtil graphUtil = GraphUtil.v();
  Flowgraph flowgraph;
  FixpointSolver solver;
  MeasurementCollectionClient measurementClient = new MeasurementCollectionClient();

  @Override
  public void run(GUIAnalysisOutput output) {
    this.output = output;
    this.flowgraph = output.getFlowgraph();
    this.solver = output.getSolver();
    table1();
    table2();
    table3();
  }

  // App & Classes & Methods && activities & menus & dialogs & ids (L/V) & views (I/A) & listeners
  void table1() {
    int classes = 0;
    int methods = 0;
    for (SootClass c : hier.appClasses) {
      if (c.getName().startsWith("FakeName_")) {
        continue;
      }
      classes++;
      methods += c.getMethodCount();
    }
    int activities = output.getActivities().size();
    int menus = menuCount();
    int dialogs = output.getDialogs().size();
    int layoutIds = usedLayoutIds();
    int viewIds = usedViewIds();
    int inflatedViews = 0;
    int allocatedViews = 0;
    for (NNode n : flowgraph.allNNodes) {
      if (n instanceof NInflNode) {
        inflatedViews++;
//        SootClass c = ((NInflNode)n).c;
//        if (!hier.isSubclassOf(c, Scene.v().getSootClass("android.view.View"))) {
//          System.out.println("[WARNING] Infl: " + c);
//        }
//        System.out.println("  [Infl] " + n);
        continue;
      }
      if (n instanceof NViewAllocNode) {
        allocatedViews++;
        continue;
      }
    }
    int listeners = listenerCount();
    // App & Classes & Methods && activities & menus & dialogs & ids (L/V) & views (I/A) & listeners
    System.out.printf("[xace2UWA.%s] %-25s & %5d & %5d && %3d & %3d & %3d & %8s & %8s & %4d \\\\\n",
        Configs.benchmarkName,
        "\\texttt{" + Configs.benchmarkName + "}", classes, methods,
        activities, menus, dialogs,
        layoutIds + "/" + viewIds,
        inflatedViews + "/" + allocatedViews,
        listeners);
  }

  // App & Inflate & FindView & AddView & SetId & SetListener \\
  void table2() {
    int inflate = countNonArtificialNodes(NOpNode.getNodes(NInflate1OpNode.class))
        + countNonArtificialNodes(NOpNode.getNodes(NInflate2OpNode.class))
        + countNonArtificialNodes(NOpNode.getNodes(NMenuInflateOpNode.class));
    int findView = countNonArtificialNodes(NOpNode.getNodes(NFindView1OpNode.class))
        + countNonArtificialNodes(NOpNode.getNodes(NFindView2OpNode.class))
        + countNonArtificialNodes(NOpNode.getNodes(NFindView3OpNode.class));
    int addView = countNonArtificialNodes(NOpNode.getNodes(NAddView1OpNode.class))
        + countNonArtificialNodes(NOpNode.getNodes(NAddView2OpNode.class));
    int setId = countNonArtificialNodes(NOpNode.getNodes(NSetIdOpNode.class));
    int setListener = countNonArtificialNodes(NOpNode.getNodes(NSetListenerOpNode.class));
    System.out.printf("[HEdr8yUn.%s] %-25s & %4d & %4d & %4d & %2d & %4d \\\\\n",
        Configs.benchmarkName,
        "\\texttt{" + Configs.benchmarkName + "}",
        inflate, findView, addView, setId, setListener);
  }

  // App & Time && receivers & parameters & results & listeners
  void table3() {
    String receivers = averageSolutionSizeString(solver.solutionReceivers, "receiver");
    String parameters = averageSolutionSizeString(solver.solutionParameters, "parameter");
    String results = averageSolutionSizeString(solver.solutionResults, "result");
    String listeners = averageSolutionSizeString(solver.solutionListeners, "listener");
    System.out.printf("[paSa4uBr.%s] %-25s & %5s && %5s & %5s & %5s & %5s \\\\\n",
        Configs.benchmarkName,
        "\\texttt{" + Configs.benchmarkName + "}",
        String.format("%2.2f", output.getRunningTimeInNanoSeconds() * 1.0e-09),
        receivers, parameters, results, listeners);
  }

  <E> int filteredSetSize(Set<E> set, Predicate<E> countIfTrue) {
    int size = 0;
    for (E element : set) {
      if (countIfTrue.apply(element)) {
        size++;
      }
    }
    return size;
  }

  final Predicate<NOpNode> filterArtificialNodes = new Predicate<NOpNode>() {
    @Override
    public boolean apply(NOpNode node) {
      return !node.artificial;
    }
  };

  int countNonArtificialNodes(Set<NOpNode> nodes) {
    return filteredSetSize(nodes, filterArtificialNodes);
  }

  String averageSolutionSizeString(Map<NOpNode, Set<NNode>> solution, String type) {

    float averageSolutionSize = (solution == solver.solutionListeners ?
        measurementClient.getListenersAverage(output) : averageSolutionSize(solution, type));
    return averageSolutionSize == -1 ? "-" : String.format("%.2f", averageSolutionSize);
  }

  float averageSolutionSize(Map<NOpNode, Set<NNode>> solution, String type) {
    int nodes = 0;
    int objects = 0;
    for (Map.Entry<NOpNode, Set<NNode>> entry : solution.entrySet()) {
      NOpNode opNode = entry.getKey();
      if (opNode.artificial) {
        continue;
      }
      Set<NNode> set = entry.getValue();
      if (set.isEmpty()) {
        continue;
      }
      nodes++;
      objects += set.size();
      boolean dead = ("" + opNode).contains("com.nexes.manager.WirelessManager");
      if (dead) {
        System.out.printf("  [dead] #objects=%d, #nodes=%d\n", set.size(), 1);
      }
      if (set.size() > 1 || dead) {
        Debug.v().printf("\n--- %s\n", opNode.toString());
        for (NNode rcv : set) {
          Debug.v().printf("  * %s: %s\n", type, rcv.toString());
        }
      }
    }
    System.out.printf("[%s] #objects=%d, #nodes=%d\n", type, objects, nodes);
    if (nodes == 0) {
      return -1;
    } else {
      return (float) objects / (float) nodes;
    }
  }

  int menuCount() {
    Set<NOptionsMenuNode> optionsMenus = Sets.newHashSet();
    Set<NContextMenuNode> contextMenus = Sets.newHashSet();
    for (SootClass act : output.getActivities()) {
      NOptionsMenuNode optionsMenu = output.getOptionsMenu(act);
      if (optionsMenu != null) {
        optionsMenus.add(optionsMenu);
      }
      for (NNode root : output.getActivityRoots(act)) {
        for (NNode node : graphUtil.descendantNodes(root)) {
          contextMenus.addAll(output.getContextMenus((NObjectNode) node));
        }
      }
    }
    for (NDialogNode dialog : output.getDialogs()) {
      for (NNode root : output.getDialogRoots(dialog)) {
        for (NNode node : graphUtil.descendantNodes(root)) {
          contextMenus.addAll(output.getContextMenus((NObjectNode) node));
        }
      }
    }
    int menus = optionsMenus.size() + contextMenus.size();
    return menus;
  }

  int listenerCount() {
    Set<NNode> listeners = Sets.newHashSet();
    for (Map.Entry<NOpNode, Set<NNode>> entry : solver.solutionListeners.entrySet()) {
      if (entry.getKey().artificial) {
        continue;
      }
      listeners.addAll(entry.getValue());
    }
    return listeners.size();
  }

  int usedLayoutIds() {
    Set<NLayoutIdNode> usedLayoutIds = Sets.newHashSet();
    for (Set<NLayoutIdNode> nodes : solver.reachingLayoutIds.values()) {
      usedLayoutIds.addAll(nodes);
    }
    Set<NMenuIdNode> usedMenuIds = Sets.newHashSet();
    for (Set<NMenuIdNode> nodes : solver.reachingMenuIds.values()) {
      usedMenuIds.addAll(nodes);
    }
    return usedLayoutIds.size() + usedMenuIds.size();
  }

  int usedViewIds() {
    Set<NWidgetIdNode> usedViewIds = Sets.newHashSet();
    for (Set<NIdNode> nodes : solver.reachingViewIds.values()) {
      for (NIdNode id : nodes) {
        if (id instanceof NWidgetIdNode) {
          usedViewIds.add((NWidgetIdNode)id);
        }
      }
    }
    return usedViewIds.size();
  }
}
