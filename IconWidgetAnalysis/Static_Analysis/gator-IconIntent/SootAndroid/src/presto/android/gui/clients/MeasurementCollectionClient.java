/*
 * MeasurementCollectionClient.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients;

import java.util.Map;
import java.util.Set;

import presto.android.Debug;
import presto.android.gui.GUIAnalysisClient;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NSetListenerOpNode;

public class MeasurementCollectionClient implements GUIAnalysisClient {

  @Override
  public void run(GUIAnalysisOutput output) {
    // Prepare
    float averageReceivers = getAverage(output.operationNodeAndReceivers());
    float averageParameters = getAverage(output.operationNodeAndParameters());
    float averageResults = getAverage(output.operationNodeAndResults());
    float averageListeners = getListenersAverage(output);

    System.out.println("Average size of solution set");
    System.out.printf("  * receivers: %.2f\n", averageReceivers);
    System.out.printf("  * parameters: %.2f\n", averageParameters);
    System.out.printf("  * results: %.2f\n", averageResults);
    System.out.printf("  * listeners: %.2f\n", averageListeners);
  }

  // Supports only receivers, parameters, and results (lhs's)
  public float getAverage(Map<NOpNode, Set<NNode>> solution) {
    int sum = 0;
    int empty = 0;
    int size = solution.size();
    for (Set<NNode> s : solution.values()) {
      sum += s.size();
      if (s.isEmpty()) {
        empty++;
      }
    }
    float avg = (float) sum / (float) (size - empty);
    return avg;
  }

  public float getListenersAverage(GUIAnalysisOutput output) {
    Map<NOpNode, Set<NNode>> solutionReceivers =
        output.operationNodeAndReceivers();
    Map<NOpNode, Set<NNode>> solutionListeners =
        output.operationNodeAndListeners();

    int numViews = 0;
    int numListeners = 0;

    for (NOpNode node : output.operationNodes(NSetListenerOpNode.class)) {
      if (node.artificial) {
        continue;
      }
      Set<NNode> receivers = solutionReceivers.get(node);
      if (receivers == null || receivers.isEmpty()) {
        continue;
      }
      Set<NNode> listeners = solutionListeners.get(node);
      if (listeners == null || listeners.isEmpty()) {
        continue;
      }
      numViews += receivers.size();
      numListeners += (receivers.size() * listeners.size());
      boolean dead = ("" + node).contains("com.nexes.manager.WirelessManager");
      if (dead) {
        System.out.printf("[dead] listeners=%d, views=%d\n",
            (receivers.size() * listeners.size()), receivers.size());
      }
      // Report non-singleton listener sets
      if (listeners.size() > 1 || dead) {
        Debug.v().printf("\n--- %s\n", node.toString());
        for (NNode lst : listeners) {
          Debug.v().printf("  * %s: %s\n", "listener", lst.toString());
        }
      }
    }

    System.out.println("[listeners] #numListeners=" + numListeners
        + ", #numViews=" + numViews);
    return (float) numListeners / (float) numViews;
  }
}
