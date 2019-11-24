/*
 * DemandVariableValueQuery.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import presto.android.Hierarchy;
import presto.android.gui.FixpointSolver.VarExtractor;
import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.listener.ListenerSpecification;
import soot.IntType;
import soot.Local;
import soot.RefType;
import soot.SootClass;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

public class DemandVariableValueQuery implements VariableValueQueryInterface {
  private static DemandVariableValueQuery theInstance;

  private Flowgraph flowgraph;
  private FixpointSolver solver;

  private GraphUtil graphUtil;
  private Hierarchy hier;
  private ListenerSpecification listenerSpec;

  DemandVariableValueQuery(Flowgraph flowgraph, FixpointSolver solver) {
    this.flowgraph = flowgraph;
    this.solver = solver;
    this.graphUtil = GraphUtil.v();
    this.hier = Hierarchy.v();
    this.listenerSpec = ListenerSpecification.v();
  }

  public static DemandVariableValueQuery v(
      Flowgraph flowgraph,FixpointSolver solver) {
    if (theInstance == null) {
      theInstance = new DemandVariableValueQuery(flowgraph, solver);
    }
    return theInstance;
  }

  Set<NObjectNode> valueSetForRefTypes(Local local) {
    NVarNode varNode = flowgraph.lookupVarNode(local);
    if (varNode == null) {
      return Collections.emptySet();
    }
    Set<NObjectNode> pts = Sets.newHashSet();

    // basic reachability
    Set<NVarNode> locals = Sets.newHashSet(varNode);
    for (NNode node : graphUtil.backwardReachableNodes(varNode)) {
      if (node instanceof NVarNode) {
        locals.add((NVarNode)node);
      } else if (node instanceof NObjectNode) {
        pts.add((NObjectNode)node);
      }
    }
    // fixpoint results
    extractFixpointSolution(solver.resultExtractor, pts, locals);

    return pts;
  }

  void extractFixpointSolution(VarExtractor extractor,
      Set<NObjectNode> resultSet, Set<NVarNode> locals) {
    Map<NOpNode, Set<NNode>> solutionMap = solver.solutionResults;
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

  @Override
  public Set<NIdNode> idVariableValues(Local local) {
    Preconditions.checkArgument(local.getType() instanceof IntType);
    NVarNode varNode = flowgraph.lookupVarNode(local);
    if (varNode == null) {
      return Collections.emptySet();
    }
    Set<NIdNode> pts = Sets.newHashSet();
    for (NNode node : graphUtil.backwardReachableNodes(varNode)) {
      if (node instanceof NIdNode) {
        pts.add((NIdNode)node);
      }
    }
    return pts;
  }

  @Override
  public Set<NObjectNode> activityVariableValues(Local local) {
    Preconditions.checkArgument(local.getType() instanceof RefType);
    SootClass c = ((RefType) local.getType()).getSootClass();
    if (!hier.applicationActivityClasses.contains(c)
        && !hier.libActivityClasses.contains(c)) {
      throw new RuntimeException(c + " is not an Activity");
    }
    return valueSetForRefTypes(local);
  }

  @Override
  public Set<NObjectNode> guiVariableValues(Local local) {
    Preconditions.checkArgument(local.getType() instanceof RefType);
    SootClass c = ((RefType) local.getType()).getSootClass();
    if (!hier.isGUIClass(c)) {
      throw new RuntimeException(c + " is not a GUI type");
    }
    return valueSetForRefTypes(local);
  }

  @Override
  public Set<NObjectNode> listenerVariableValues(Local local) {
    Preconditions.checkArgument(local.getType() instanceof RefType);
    SootClass c = ((RefType) local.getType()).getSootClass();
    if (!listenerSpec.isListenerType(c)) {
      throw new RuntimeException(c + " is not a listener type");
    }
    return valueSetForRefTypes(local);
  }
}
