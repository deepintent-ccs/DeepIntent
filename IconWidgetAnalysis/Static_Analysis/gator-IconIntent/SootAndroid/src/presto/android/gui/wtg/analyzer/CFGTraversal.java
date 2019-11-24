/*
 * CFGTraversal.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.analyzer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import presto.android.Logger;
import presto.android.gui.clients.energy.IfNullUtil;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph.Edge;
import presto.android.gui.wtg.util.WTGUtil;
import presto.android.gui.wtg.util.Filter;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class CFGTraversal {
  // call grpah
  private AndroidCallGraph cg = AndroidCallGraph.v();
  // wtg util
  private WTGUtil wtgUtil = WTGUtil.v();

  private CFGTraversal() {
  }

  public boolean forwardTraversal(
          SootMethod handler,
          Map<Stmt, SootMethod> visitedStmts,
          Set<Stmt> escapedStmts,
          Map<SootMethod, UnitGraph> methodToCFG,
          Filter<Stmt, SootMethod> filter,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    if (handler == null) {
      Logger.err(getClass().getSimpleName(), "can not perform forward traversal since the handler is null");
    }
    List<Stmt> workingList = Lists.newArrayList();
    UnitGraph handlerCFG = createOrGetCFG(methodToCFG, handler);
    for (Unit entryNode : handlerCFG.getHeads()) {
      propagate(visitedStmts, workingList, (Stmt) entryNode, handler);
    }
    return traverse(workingList, visitedStmts, escapedStmts, methodToCFG,
            filter, infeasibleEdges, infeasibleCalls);
//    return IfNullUtil.v().traverseWithIfFix(workingList, visitedStmts, Lists.<Stmt>newArrayList(), escapedStmts, methodToCFG,
//            filter, infeasibleEdges, infeasibleCalls);
  }

  private boolean traverse(
          List<Stmt> workingList,
          Map<Stmt, SootMethod> visitedStmts,
          Set<Stmt> escapedStmts,
          Map<SootMethod, UnitGraph> methodToCFG,
          Filter<Stmt, SootMethod> filter,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    boolean unexpected = false;
    Set<SootMethod> visitedMethods = Sets.newHashSet();
    while (!workingList.isEmpty()) {
      Stmt currentStmt = workingList.remove(0);
      SootMethod currentCxt = visitedStmts.get(currentStmt);
      if (currentCxt == null) {
        Logger.err(getClass().getSimpleName(), "can not find the calling context for stmt: "
                + currentStmt);
      }
      if (wtgUtil.isIgnoredMethod(currentCxt)) {
        continue;
      }
      if (filter.match(currentStmt, currentCxt)) {
        if (escapedStmts != null) {
          escapedStmts.add(currentStmt);
        }
        continue;
      }
      UnitGraph currentCFG = createOrGetCFG(methodToCFG, currentCxt);
      // switch case for 3 conditions
      // case 1: currentStmt is not a call and not exit of cfg
      if (!currentStmt.containsInvokeExpr()
              && !currentCFG.getTails().contains(currentStmt)) {
        Collection<Unit> success = currentCFG.getSuccsOf(currentStmt);
        for (Unit succ : success) {
          Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
          if (tgts != null && tgts.contains(succ)) {
            continue;
          }
          propagate(visitedStmts, workingList, (Stmt) succ, currentCxt);
        }
      }
      // case 2: currentStmt is a call but not a call to
      // startActivity/showDialog/etc.
      else if (currentStmt.containsInvokeExpr()) {
        Set<Edge> outgoings = cg.getEdge(currentStmt);
        Set<SootMethod> infeasibleCallees = infeasibleCalls.get(currentStmt);
        boolean findTarget = false;
        for (Edge outgoing : outgoings) {
          SootMethod target = outgoing.target;
          if (infeasibleCallees.contains(target)) {
            // we need to ignore analyzing callee
            continue;
          }
          if (target.getDeclaringClass().isApplicationClass()
                  && target.isConcrete()) {
            findTarget = true;
            UnitGraph tgtCFG = createOrGetCFG(methodToCFG, target);
            for (Unit entryNode : tgtCFG.getHeads()) {
              propagate(visitedStmts, workingList, (Stmt) entryNode, target);
            }
            if (visitedMethods.contains(target)) {
              for (Unit succ : currentCFG.getSuccsOf(currentStmt)) {
                Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
                if (tgts != null && tgts.contains(succ)) {
                  continue;
                }
                propagate(visitedStmts, workingList, (Stmt) succ, currentCxt);
              }
            }
          }
        }
        // if the target can not be found, then we conservatively think there is
        // no transition
        if (!findTarget) {
          for (Unit succ : currentCFG.getSuccsOf(currentStmt)) {
            Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
            if (tgts != null && tgts.contains(succ)) {
              continue;
            }
            propagate(visitedStmts, workingList, (Stmt) succ, currentCxt);
          }
        }
      }
      // case 3: currentStmt is the exit point and callingContext is not in
      // visitedMethods
      else if (currentCFG.getTails().contains(currentStmt)
              && !visitedMethods.contains(currentCxt)) {
        visitedMethods.add(currentCxt);
        createOrGetCFG(methodToCFG, currentCxt);
        Set<Edge> incomings = cg.getIncomingEdges(currentCxt);
        for (Edge e : incomings) {
          Stmt caller = e.callSite;
          if (visitedStmts.containsKey(caller)) {
            SootMethod callerCxt = visitedStmts.get(caller);
            UnitGraph callerCFG = createOrGetCFG(methodToCFG, callerCxt);
            for (Unit succ : callerCFG.getSuccsOf(caller)) {
              Set<Stmt> tgts = infeasibleEdges.get(caller);
              if (tgts != null && tgts.contains(succ)) {
                continue;
              }
              propagate(visitedStmts, workingList, (Stmt) succ, callerCxt);
            }
          }
        }
      }
    }
    return unexpected;
  }

  private void propagate(Map<Stmt, SootMethod> visitedStmts,
                         List<Stmt> workingList, Stmt s, SootMethod cxt) {
    if (!visitedStmts.containsKey(s)) {
      visitedStmts.put(s, cxt);
      workingList.add(s);
    }
  }

  private UnitGraph createOrGetCFG(Map<SootMethod, UnitGraph> methodToCFG,
                                   SootMethod mtd) {
    UnitGraph cfg = methodToCFG.get(mtd);
    if (cfg == null) {
      synchronized(mtd) {
        cfg = new ExceptionalUnitGraph(mtd.retrieveActiveBody());
      }
      methodToCFG.put(mtd, cfg);
    }
    return cfg;
  }

  public static synchronized CFGTraversal v() {
    if (traversal == null) {
      traversal = new CFGTraversal();
    }
    return traversal;
  }

  private static CFGTraversal traversal;
}
