/*
 * QueryHelper.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import presto.android.gui.FixpointSolver;
import presto.android.gui.GUIAnalysis;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NVarNode;

public class QueryHelper {
  private static QueryHelper instance;
  private FixpointSolver solver;

  public static synchronized QueryHelper v() {
    if (instance == null) {
      instance = new QueryHelper();
    }
    return instance;
  }

  private QueryHelper() {
    GUIAnalysis guiAnalysis = GUIAnalysis.v();
    this.solver = guiAnalysis.fixpointSolver;
  }
  // get all nnode that is back reachable from this local
  private Set<NNode> allValueSetFor(NNode n) {
    NNode startingNode = n;
    if (startingNode == null) {
      return Collections.emptySet();
    }
    Set<NObjectNode> refNodes = Sets.newHashSet();
    Set<NVarNode> localNodes = Sets.newHashSet();
    Set<NNode> otherNodes = Sets.newHashSet();
    for (NNode node : backwardReachableNodes(startingNode)) {
      if (node instanceof NVarNode) {
        localNodes.add((NVarNode)node);
      } else if (node instanceof NObjectNode) {
        refNodes.add((NObjectNode)node);
      } else {
        otherNodes.add(node);
      }
    }
    // fixpoint results
    extractFixpointSolution(solver.solutionParameters,
        parameterExtractor, refNodes, localNodes);
    extractFixpointSolution(solver.solutionReceivers,
        receiverExtractor, refNodes, localNodes);
    extractFixpointSolution(solver.solutionResults,
        resultExtractor, refNodes, localNodes);
    extractFixpointSolution(solver.solutionListeners,
        parameterExtractor, refNodes, localNodes);
    // merge them together
    otherNodes.addAll(refNodes);
    otherNodes.addAll(localNodes);
    return otherNodes;
  }

  private void extractFixpointSolution(Map<NOpNode, Set<NNode>> solutionMap,
      VarExtractor extractor, Set<NObjectNode> resultSet, Set<NVarNode> locals) {
    for (Map.Entry<NOpNode, Set<NNode>> entry : solutionMap.entrySet()) {
      NOpNode opNode = entry.getKey();
      NVarNode local = extractor.extract(opNode);
      if (locals.contains(local)) {
        for (NNode resultNode : entry.getValue()) {
          resultSet.add((NObjectNode) resultNode);
        }
      }
    }
  }
  // this method will help find all possible back reachable NNode
  // it will not stop traversing even it reaches NOpNode
  private Set<NNode> backwardReachableNodes(NNode n) {
    // p("[BackwardReachable] " + n);
    Set<NNode> res = Sets.newHashSet();
    findBackwardReachableNodes(n, res);
    return res;
  }

  private void findBackwardReachableNodes(NNode start, Set<NNode> reachableNodes) {
    LinkedList<NNode> worklist = Lists.newLinkedList();
    worklist.add(start);
    reachableNodes.add(start);
    while (!worklist.isEmpty()) {
      NNode n = worklist.remove();
      for (NNode s : n.getPredecessors()) {
        if (reachableNodes.contains(s)) {
          continue;
        }
        if (s instanceof NOpNode) {
          if (!(start instanceof NOpNode)) {
            reachableNodes.add(s);
          }
        } else {
          worklist.add(s);
          reachableNodes.add(s);
        }
      }
    }
  }

  public Set<NNode> allVariableValues(NNode n) {
    return allValueSetFor(n);
  }

  private interface VarExtractor {
    public NVarNode extract(NOpNode opNode);
  }

  private final VarExtractor parameterExtractor = new VarExtractor() {
    @Override
    public NVarNode extract(NOpNode opNode) {
      return (NVarNode) opNode.getParameter();
    }
  };

  private final VarExtractor resultExtractor = new VarExtractor() {
    @Override
    public NVarNode extract(NOpNode opNode) {
      return opNode.getLhs();
    }
  };

  private final VarExtractor receiverExtractor = new VarExtractor() {
    @Override
    public NVarNode extract(NOpNode opNode) {
      return opNode.getReceiver();
    }
  };
}
