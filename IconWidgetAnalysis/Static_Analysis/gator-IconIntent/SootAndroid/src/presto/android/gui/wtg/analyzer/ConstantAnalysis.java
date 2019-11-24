/*
 * ConstantAnalysis.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.analyzer;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import presto.android.Hierarchy;
import presto.android.Logger;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.JimpleUtil;
import presto.android.gui.clients.energy.Pair;
import presto.android.gui.clients.energy.VarUtil;
import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph.Edge;
import presto.android.gui.wtg.util.WTGUtil;
import presto.android.gui.wtg.util.QueryHelper;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.EqExpr;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.NeExpr;
import soot.jimple.ParameterRef;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.TableSwitchStmt;
import soot.jimple.ThisRef;
import soot.jimple.VirtualInvokeExpr;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

public class ConstantAnalysis {
  // constants
  private final Object ANY = new Object();
  private final boolean LOOKFORREF = true;
  private final boolean LOOKFORINT = false;
  // input
  private GUIAnalysisOutput guiOutput;
  private FlowgraphRebuilder rebuilder;
  private Map<Local, Object> constSolution;
  private Multimap<Local, Local> jumpSolution; // local flow-to set
  // utils
  private Hierarchy hier = Hierarchy.v();
  private QueryHelper queryHelper = QueryHelper.v();
  private AndroidCallGraph cg = AndroidCallGraph.v();
  private WTGUtil wtgUtil = WTGUtil.v();
  private JimpleUtil jimpleUtil = JimpleUtil.v();

  public ConstantAnalysis(GUIAnalysisOutput output, FlowgraphRebuilder r) {
    guiOutput = output;
    rebuilder = r;
    constSolution = Maps.newHashMap();
    jumpSolution = HashMultimap.create();
  }

  public ConstantAnalysis(ConstantAnalysis another) {
    guiOutput = another.guiOutput;
    rebuilder = another.rebuilder;
    constSolution = Maps.newHashMap();
    jumpSolution = HashMultimap.create();
  }

  // this API is provided for GUI object constant propagation
  public void doAnalysis(NObjectNode guiObject, SootMethod handler,
      HashMultimap<Stmt, Stmt> infeasibleEdges,
      HashMultimap<Stmt, SootMethod> infeasibleCalls) {

    //Debug begin
    VarUtil.v().widgetSet.add(guiObject);

    //Debug end
    //New feature
    Pair<NObjectNode, SootMethod> key = new Pair<>(guiObject, handler);
    VarUtil.v().infeasibleEdgesMap.put(key, infeasibleEdges);
    VarUtil.v().infeasibleCallsMap.put(key, infeasibleCalls);
    //End new feature

    reset();
    Local guiLocal = null;
    if (guiOutput.isLifecycleHandler(handler)) {
      // for the onCreate callbacks, we should consider thisRef instead
      guiLocal = jimpleUtil.thisLocal(handler);
    } else {
      guiLocal = guiOutput.getViewLocal(handler);
    }
    if (guiLocal == null) {
      return;
    }
    // first resolve the const values for reference locals
    constRefFixPoint(guiObject, guiLocal, handler);
    // use the const reference propagation result to help resolve int locals
    constRefIntFixPoint(guiObject, handler);
    // identify infeasible edges
    detectInfeasibleEdge(handler, infeasibleEdges, infeasibleCalls);
  }

  // this API is provided for Integer constant propagation
  public void doAnalysis(Integer id, SootMethod handler,
      HashMultimap<Stmt, Stmt> infeasibleEdges, HashMultimap<Stmt, SootMethod> infeasibleMethods) {
    Preconditions.checkNotNull(id);
    reset();
    // use the const int propagation result to help resolve int locals
    constIntFixPoint(id, handler);
    // identify infeasible edges
    detectInfeasibleEdge(handler, infeasibleEdges, infeasibleMethods);
  }

  public Object getConstSolution(Local l) {
    return constSolution.get(l);
  }

  private void reset() {
    constSolution.clear();
    jumpSolution.clear();
  }

  private void constRefFixPoint(NObjectNode guiObject, Local guiLocal, SootMethod handler) {
    List<Local> workList = Lists.newArrayList();
    beforeRefFixPoint(guiObject, guiLocal, handler, workList);
    doRefFixPoint(workList);
  }

  private void beforeRefFixPoint(NObjectNode guiObject, Local guiLocal,
      SootMethod handler, List<Local> workList) {
    updateConstSolution(workList, guiLocal, guiObject);
    addToJump(guiLocal, guiLocal);
    List<SootMethod> reachableMethods = Lists.newArrayList(handler);
    Set<SootMethod> memberSet = Sets.newHashSet(handler);
    while (!reachableMethods.isEmpty()) {
      SootMethod mtd = reachableMethods.remove(0);
      Body body = null;
      synchronized(mtd) {
        body = mtd.retrieveActiveBody();
      }
      Set<Local> formalParamLocals = Sets.newHashSet();
      {
        // find all formal parameter locals
        Iterator<Unit> stmts = body.getUnits().iterator();
        while (stmts.hasNext()) {
          Stmt s = (Stmt) stmts.next();
          if (s instanceof IdentityStmt
              && (isInterestedStmt(s, guiObject, LOOKFORREF))) {
            Local lop = (Local) ((IdentityStmt) s).getLeftOp();
            Value rop = ((IdentityStmt) s).getRightOp();
            if (rop instanceof ThisRef || rop instanceof ParameterRef) {
              addToJump(lop, lop);
              formalParamLocals.add(lop);
            }
          }
        }
      }
      {
        // resolve the jumpToSet for each formal parameter local
        Iterator<Unit> stmts = body.getUnits().iterator();
        while (stmts.hasNext()) {
          Stmt s = (Stmt) stmts.next();
          if (!(s instanceof AssignStmt) || !isInterestedStmt(s, guiObject, LOOKFORREF)) {
            continue;
          }
          Local lop = (Local) ((AssignStmt) s).getLeftOp();
          Value rop = ((AssignStmt) s).getRightOp();
          if (rop instanceof Local || rop instanceof CastExpr) {
            NNode lVarNode = rebuilder.lookupNode(lop);
            Set<NNode> backReachedNodes = queryHelper.allVariableValues(lVarNode);
            for (NNode backReachedNode : backReachedNodes) {
              if (backReachedNode instanceof NVarNode) {
                Local l = ((NVarNode) backReachedNode).l;
                if (formalParamLocals.contains(l)) {
                  addToJump(l, lop);
                }
              }
            }
          } else if (rop instanceof StaticInvokeExpr) {
            SootMethod callee = ((StaticInvokeExpr) rop).getMethod();
            SootClass declaringClz = callee.getDeclaringClass();
            if (declaringClz.isApplicationClass()) {
              NNode lVarNode = rebuilder.lookupNode(lop);
              Set<NNode> backReachedNodes = queryHelper.allVariableValues(lVarNode);
              for (NNode backReachedNode : backReachedNodes) {
                if (backReachedNode instanceof NVarNode) {
                  Local l = ((NVarNode) backReachedNode).l;
                  if (formalParamLocals.contains(l)) {
                    addToJump(l, lop);
                  }
                }
              }
            } else {
              conservativeUpdate(workList, lop);
            }
          } else if (rop instanceof InstanceInvokeExpr) {
            for (Edge outgoing : cg.getEdge(s)) {
              SootMethod callee = outgoing.target;
              SootClass declaringClz = callee.getDeclaringClass();
              if (declaringClz.isApplicationClass()) {
                NNode lVarNode = rebuilder.lookupNode(lop);
                Set<NNode> backReachedNodes = queryHelper.allVariableValues(lVarNode);
                for (NNode backReachedNode : backReachedNodes) {
                  if (backReachedNode instanceof NVarNode) {
                    Local l = ((NVarNode) backReachedNode).l;
                    if (formalParamLocals.contains(l)) {
                      addToJump(l, lop);
                    }
                  }
                }
              } else {
                conservativeUpdate(workList, lop);
              }
            }
          } else {
            conservativeUpdate(workList, lop);
          }
        }
      }
      // add callees
      addReachableCall(mtd, null, memberSet, reachableMethods);
    }
  }

  private void doRefFixPoint(List<Local> workList) {
    // stage 1
    while (!workList.isEmpty()) {
      Local l = workList.remove(0);
      Object localConstSolution = constSolution.get(l);
      if (localConstSolution == null) {
        Logger.err(getClass().getSimpleName(), "can not find the value for local: " + l);
      }
      NVarNode varNode = (NVarNode)rebuilder.lookupNode(l);
      if (varNode == null) {
        continue;
      }
      Map<Local, Stmt> formalParamLocals = rebuilder.actualFlowToFormalLocal(l);
      for (NNode succ : varNode.getSuccessors()) {
        if (!(succ instanceof NVarNode)) {
          continue;
        }
        Local succLocal = ((NVarNode) succ).l;
        if (formalParamLocals == null
            || !formalParamLocals.containsKey(succLocal)) {
          // same method
          updateConstSolution(workList, succLocal, localConstSolution);
        } else {
          // invocation to formal parameter
          Stmt caller = formalParamLocals.get(succLocal);
          if (!(caller instanceof AssignStmt)) {
            continue;
          }
          Value lhsValue = ((AssignStmt) caller).getLeftOp();
          if (!(lhsValue instanceof Local)) {
            continue;
          }
          NVarNode lhsNode = (NVarNode) rebuilder.lookupNode(lhsValue);
          Collection<Local> flowToLocals = jumpSolution.get(succLocal);
          if (flowToLocals == null || flowToLocals.isEmpty()) {
            continue;
          }
          for (Local flowToLocal : flowToLocals) {
            NNode flowToLocalNode = rebuilder.lookupNode(flowToLocal);
            for (NNode succ2 : flowToLocalNode.getSuccessors()) {
              if (succ2 == lhsNode) {
                updateConstSolution(workList, lhsNode.l, localConstSolution);
              }
            }
          }
        }
      }
    }

    // stage 2
    for (Local l : jumpSolution.keySet()) {
      if (constSolution.get(l) != null) {
        workList.add(l);
      }
    }
    // corrected code
    Map<Local, Object> copyOfConstSolution = Maps.newHashMap();
    for (Local l : constSolution.keySet()) {
      if (constSolution.get(l) == null) {
        continue;
      }
      copyOfConstSolution.put(l, constSolution.get(l));
    }
    for (Local l : copyOfConstSolution.keySet()) {
      Map<Local, Stmt> formalParamLocalToCaller = rebuilder.actualFlowToFormalLocal(l);
      if (formalParamLocalToCaller == null || formalParamLocalToCaller.isEmpty()) {
        continue;
      }
      for (Local formalParamLocal : formalParamLocalToCaller.keySet()) {
        updateConstSolution(workList, formalParamLocal, copyOfConstSolution.get(l));
      }
    }
    while (!workList.isEmpty()) {
      Local l = workList.remove(0);
      Collection<Local> flowtoLocals = jumpSolution.get(l);
      if (flowtoLocals == null || flowtoLocals.isEmpty()) {
        continue;
      }
      Object localConstSolution = constSolution.get(l);
      for (Local flowtoLocal : flowtoLocals) {
        updateConstSolution(workList, flowtoLocal, localConstSolution);
        Map<Local, Stmt> formalParamLocals = rebuilder.actualFlowToFormalLocal(flowtoLocal);
        if (formalParamLocals != null) {
          for (Local formalParamLocal : formalParamLocals.keySet()) {
            Object formalLocalSolution = constSolution.get(flowtoLocal);
            updateConstSolution(workList, formalParamLocal, formalLocalSolution);
          }
        }
      }
    }
  }

  private void detectInfeasibleEdge(SootMethod handler, HashMultimap<Stmt, Stmt> infeasibleEdges,
      HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    Set<SootMethod> memberSet = Sets.newHashSet(handler);
    List<SootMethod> workList = Lists.newArrayList(handler);
    while (!workList.isEmpty()) {
      SootMethod mtd = workList.remove(0);
      Body body = null;
      UnitGraph cfg = null;
      synchronized(mtd) {
        body = mtd.retrieveActiveBody();
        cfg = new ExceptionalUnitGraph(body);
      }
      Iterator<Unit> stmts = body.getUnits().iterator();
      while (stmts.hasNext()) {
        Stmt s = (Stmt) stmts.next();
        Set<SootMethod> escapedCallees = addReachableCall(null, s, memberSet, workList);
        infeasibleCalls.putAll(s, escapedCallees);
        if (!(s instanceof IfStmt) && !(s instanceof TableSwitchStmt)
            && !(s instanceof LookupSwitchStmt)) {
          continue;
        }
        if (s instanceof IfStmt) {
          handleIfStmt(cfg, (IfStmt) s, infeasibleEdges);
        } else if (s instanceof TableSwitchStmt) {
          handleTableSwitchStmt(cfg, (TableSwitchStmt) s, infeasibleEdges);
        } else if (s instanceof LookupSwitchStmt) {
          handleLookupSwitchStmt(cfg, (LookupSwitchStmt) s, infeasibleEdges);
        } else {
          Logger.err(getClass().getSimpleName(), "can not handle the stmt: " + s);
        }
      }
    }
  }
  private void constRefIntFixPoint(NObjectNode guiObject, SootMethod handler) {
    List<Local> workList = Lists.newArrayList();
    Set<Stmt> interestedStmts = Sets.newHashSet();
    beforeRefIntFixPoint(guiObject, handler, workList, interestedStmts);
    doRefIntFixPoint(workList, interestedStmts);
  }
  private void beforeRefIntFixPoint(NObjectNode guiObject, SootMethod handler, List<Local> workList,  Set<Stmt> interestedStmts) {
    List<SootMethod> reachableMethods = Lists.newArrayList(handler);
    Set<SootMethod> memberSet = Sets.newHashSet(handler);
    while (!reachableMethods.isEmpty()) {
      SootMethod mtd = reachableMethods.remove(0);
      Body body = null;
      synchronized(mtd) {
        body = mtd.retrieveActiveBody();
      }
      Set<Local> formalParamLocals = Sets.newHashSet();
      {
        // find all formal parameter locals
        Iterator<Unit> stmts = body.getUnits().iterator();
        while (stmts.hasNext()) {
          Stmt s = (Stmt) stmts.next();
          if (s instanceof IdentityStmt
              && (isInterestedStmt(s, guiObject, LOOKFORREF) || isInterestedStmt(s, guiObject, LOOKFORINT))) {
            Local lop = (Local) ((IdentityStmt) s).getLeftOp();
            formalParamLocals.add(lop);
          }
        }
      }
      {
        Iterator<Unit> stmts = body.getUnits().iterator();
        while (stmts.hasNext()) {
          Stmt s = (Stmt) stmts.next();
          if (s instanceof InvokeStmt) {
            for (Edge callee : cg.getEdge(s)) {
              intConstantPropagationAtCall(workList, s, callee.target);
            }
          }
          if (!(isInterestedStmt(s, guiObject, LOOKFORINT))) {
            continue;
          }
          Local lop = (Local) ((DefinitionStmt) s).getLeftOp();
          Value rop = ((DefinitionStmt) s).getRightOp();
          if (s instanceof AssignStmt && rop instanceof IntConstant) {
            // case 1
            updateConstSolution(workList, lop, ((IntConstant) rop).value);
          } else if (s instanceof AssignStmt && wtgUtil.isGetIdCall(s)) {
            // case 2
            Integer srcPos = wtgUtil.getGetIdField(s);
            if (srcPos == null) {
              Logger.err(getClass().getSimpleName(), "can not find the view local for stmt: " + s);
            }
            Local viewLocal = null;
            if (srcPos == 0) {
              viewLocal = jimpleUtil.receiver(s);
            } else {
              Value argValue = s.getInvokeExpr().getArg(srcPos - 1);
              if (!(argValue instanceof Local)) {
                Logger.err(getClass().getSimpleName(), "the view local is not type of local");
              }
              viewLocal = (Local) argValue;
            }
            Object constValue = constSolution.get(viewLocal);
            if (constValue == null || constValue == ANY) {
              updateConstSolution(workList, lop, ANY);
            } else if (constValue instanceof NNode) {
              NIdNode idNode = ((NNode) constValue).idNode;
              if (idNode == null) {
                updateConstSolution(workList, lop, ANY);
              } else {
                updateConstSolution(workList, lop, idNode.getIdValue());
              }
            }
          } else if (s instanceof AssignStmt
              && (rop instanceof EqExpr || rop instanceof NeExpr)) {
            interestedStmts.add(s);
            // case 3
            boolean equalOp;
            if (rop instanceof EqExpr) {
              equalOp = true;
            } else {
              equalOp = false;
            }
            Value op1 = ((ConditionExpr) rop).getOp1();
            Value op2 = ((ConditionExpr) rop).getOp2();
            Type op1Type = op1.getType();
            Type op2Type = op2.getType();
            if (op1Type instanceof IntType && op2Type instanceof IntType) {
              // case 3, a
              if (op1 instanceof Local) {
                NNode op1Node = rebuilder.lookupNode(op1);
                for (NNode backReachedNode : queryHelper.allVariableValues(op1Node)) {
                  if (backReachedNode instanceof NVarNode
                      && formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                    addToJump((((NVarNode) backReachedNode).l), (Local) op1);
                  }
                }
              }
              if (op2 instanceof Local) {
                NNode op2Node = rebuilder.lookupNode(op2);
                for (NNode backReachedNode : queryHelper.allVariableValues(op2Node)) {
                  if (backReachedNode instanceof NVarNode
                      && formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                    addToJump((((NVarNode) backReachedNode).l), (Local) op2);
                  }
                }
              }
            } else if (op1 instanceof Local && op2 instanceof Local
                && op1Type instanceof RefType && op2Type instanceof RefType) {
              // case 3, b
              Object const1 = constSolution.get(op1);
              Object const2 = constSolution.get(op2);
              if (const1 == null || const2 == null || const1 == ANY
                  || const2 == ANY) {
                updateConstSolution(workList, lop, ANY);
              } else if (const1 instanceof NNode && const2 instanceof NNode) {
                if (const1 == const2) {
                  if (equalOp) {
                    updateConstSolution(workList, lop, 1);
                  } else {
                    updateConstSolution(workList, lop, 0);
                  }
                } else {
                  if (equalOp) {
                    updateConstSolution(workList, lop, 0);
                  } else {
                    updateConstSolution(workList, lop, 1);
                  }
                }
              }
            } else {
              // case 3, c
              updateConstSolution(workList, lop, ANY);
            }
          } else if (s instanceof IdentityStmt
              && (rop instanceof ThisRef || rop instanceof ParameterRef)) {
            // case 4
              addToJump(lop, lop);
          } else if (s instanceof AssignStmt
              && (rop instanceof Local || rop instanceof CastExpr)) {
            // case 5
            Set<NNode> backReachedNodes = queryHelper
                .allVariableValues(rebuilder.lookupNode(lop));
            for (NNode backReachedNode : backReachedNodes) {
              if (backReachedNode instanceof NVarNode) {
                if (formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                  addToJump(((NVarNode) backReachedNode).l, lop);
                }
              }
            }
          } else if (s instanceof AssignStmt && rop instanceof StaticInvokeExpr) {
            // case 6
            SootMethod callee = ((StaticInvokeExpr) rop).getMethod();
            if (callee.getDeclaringClass().isApplicationClass()) {
              Set<NNode> backReachedNodes = queryHelper.allVariableValues(rebuilder.lookupNode(lop));
              for (NNode backReachedNode : backReachedNodes) {
                if (backReachedNode instanceof NVarNode) {
                  if (formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                    addToJump(((NVarNode) backReachedNode).l, lop);
                  }
                }
              }
            } else {
              updateConstSolution(workList, lop, ANY);
            }
          } else if (s instanceof AssignStmt && rop instanceof InstanceInvokeExpr) {
            // case 7
            for (Edge outgoing : cg.getEdge(s)) {
              SootMethod callee = outgoing.target;
              SootClass declaringClz = callee.getDeclaringClass();
              if (declaringClz.isApplicationClass()) {
                NNode lVarNode = rebuilder.lookupNode(lop);
                Set<NNode> backReachedNodes = queryHelper.allVariableValues(lVarNode);
                for (NNode backReachedNode : backReachedNodes) {
                  if (backReachedNode instanceof NVarNode) {
                    if (formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                      addToJump(((NVarNode) backReachedNode).l, lop);
                    }
                  }
                }
              } else {
                updateConstSolution(workList, lop, ANY);
              }
            }
          } else {
            // case 8
            updateConstSolution(workList, lop, ANY);
          }
        }
      }
      // add callees
      addReachableCall(mtd, null, memberSet, reachableMethods);
    }
  }
  private void doRefIntFixPoint(List<Local> workList, Set<Stmt> interestedStmts) {
    {
      // stage 1
      while (!workList.isEmpty()) {
        Local l = workList.remove(0);
        NVarNode varNode = (NVarNode) rebuilder.lookupNode(l);
        if (varNode == null) {
          // for int return value from library call, NVarNode is null
          continue;
        }
        Object localConstSolution = constSolution.get(l);
        if (localConstSolution == null) {
          Logger.err(getClass().getSimpleName(), "can not find the value for local: " + l);
        }
        Map<Local, Stmt> formalParamLocals = rebuilder.actualFlowToFormalLocal(l);
        for (NNode succ : varNode.getSuccessors()) {
          if (!(succ instanceof NVarNode)) {
            continue;
          }
          Local succLocal = ((NVarNode) succ).l;
          if (formalParamLocals == null
              || !formalParamLocals.containsKey(succLocal)) {
            // same method
            updateConstSolution(workList, succLocal, localConstSolution);
          } else {
            // invocation to formal parameter
            Stmt caller = formalParamLocals.get(succLocal);
            if (!(caller instanceof AssignStmt)) {
              continue;
            }
            Value lhsValue = ((AssignStmt) caller).getLeftOp();
            if (!(lhsValue instanceof Local)) {
              continue;
            }
            NVarNode lhsNode = (NVarNode) rebuilder.lookupNode(lhsValue);
            Collection<Local> flowToLocals = jumpSolution.get(succLocal);
            if (flowToLocals == null || flowToLocals.isEmpty()) {
              continue;
            }
            for (Local flowToLocal : flowToLocals) {
              NNode flowToLocalNode = rebuilder.lookupNode(flowToLocal);
              for (NNode succ2 : flowToLocalNode.getSuccessors()) {
                if (succ2 == lhsNode) {
                  updateConstSolution(workList, lhsNode.l, localConstSolution);
                }
              }
            }
          }
        }
      }
    }
    {
      // stage 2
      for (Local l : jumpSolution.keySet()) {
        if (constSolution.get(l) != null) {
          workList.add(l);
        }
      }
      // corrected code
      Map<Local, Object> copyOfConstSolution = Maps.newHashMap();
      for (Local l : constSolution.keySet()) {
        if (constSolution.get(l) == null) {
          continue;
        }
        copyOfConstSolution.put(l, constSolution.get(l));
      }
      for (Local l : copyOfConstSolution.keySet()) {
        Map<Local, Stmt> formalParamLocalToCaller = rebuilder
            .actualFlowToFormalLocal(l);
        if (formalParamLocalToCaller == null
            || formalParamLocalToCaller.isEmpty()) {
          continue;
        }
        for (Local formalParamLocal : formalParamLocalToCaller.keySet()) {
          updateConstSolution(workList, formalParamLocal,
              copyOfConstSolution.get(l));
        }
      }
      while (!workList.isEmpty()) {
        Local l = workList.remove(0);
        Collection<Local> flowtoLocals = jumpSolution.get(l);
        if (flowtoLocals == null || flowtoLocals.isEmpty()) {
          continue;
        }
        Object localConstSolution = constSolution.get(l);
        for (Local flowtoLocal : flowtoLocals) {
          updateConstSolution(workList, flowtoLocal, localConstSolution);
          Map<Local, Stmt> formalParamLocals = rebuilder
              .actualFlowToFormalLocal(flowtoLocal);
          if (formalParamLocals != null) {
            for (Local formalParamLocal : formalParamLocals.keySet()) {
              Object formalLocalSolution = constSolution.get(flowtoLocal);
              updateConstSolution(workList, formalParamLocal,
                  formalLocalSolution);
            }
          }
        }
      }
    }
    {
      // stage 3
      for (Stmt s : interestedStmts) {
        Local lop = (Local) ((AssignStmt) s).getLeftOp();
        ConditionExpr rop = (ConditionExpr) ((AssignStmt) s).getRightOp();
        boolean equalOp = false;
        if (rop instanceof EqExpr) {
          equalOp = true;
        } else {
          equalOp = false;
        }
        Value op1 = rop.getOp1();
        Value op2 = rop.getOp2();
        Type op1Type = op1.getType();
        Type op2Type = op2.getType();
        if (!(op1Type instanceof IntType) || !(op2Type instanceof IntType)) {
          continue;
        }
        if (op1 instanceof Local && op2 instanceof Local) {
          Object const1 = constSolution.get(op1);
          Object const2 = constSolution.get(op2);
          if (const1 == null || const2 == null) {
            updateConstSolution(workList, lop, ANY);
          } else if (const1 == ANY || const2 == ANY) {
            updateConstSolution(workList, lop, ANY);
          } else if (!(const1 instanceof Integer)
              || !(const2 instanceof Integer)) {
            Logger.err(getClass().getSimpleName(), "");
          } else {
            if (equalOp) {
              if (((Integer) const1).intValue() == ((Integer) const2)
                  .intValue()) {
                updateConstSolution(workList, lop, 1);
              } else {
                updateConstSolution(workList, lop, 0);
              }
            } else {
              if (((Integer) const1).intValue() == ((Integer) const2)
                  .intValue()) {
                updateConstSolution(workList, lop, 0);
              } else {
                updateConstSolution(workList, lop, 1);
              }
            }
          }
        } else if ((op1 instanceof Local && op2 instanceof IntConstant)
            || (op2 instanceof Local && op1 instanceof IntConstant)) {
          Object constValue = null;
          int intValue = 0;
          if ((op1 instanceof Local && op2 instanceof IntConstant)) {
            constValue = constSolution.get(op1);
            intValue = ((IntConstant) op2).value;
          } else {
            constValue = constSolution.get(op2);
            intValue = ((IntConstant) op1).value;
          }
          if (constValue == null || constValue == ANY) {
            updateConstSolution(workList, lop, ANY);
          } else if (!(constValue instanceof Integer)) {
            Logger.err(getClass().getSimpleName(), "");
          } else {
            if (equalOp) {
              if (((Integer) constValue).intValue() == intValue) {
                updateConstSolution(workList, lop, 1);
              } else {
                updateConstSolution(workList, lop, 0);
              }
            } else {
              if (((Integer) constValue).intValue() == intValue) {
                updateConstSolution(workList, lop, 0);
              } else {
                updateConstSolution(workList, lop, 1);
              }
            }
          }
        }
      }
      // stage 1
      while (!workList.isEmpty()) {
        Local l = workList.remove(0);
        Object localConstSolution = constSolution.get(l);
        if (localConstSolution == null) {
          Logger.err(getClass().getSimpleName(), "can not find the value for local: " + l);
        }
        NVarNode varNode = (NVarNode) rebuilder.lookupNode(l);
        if (varNode == null) {
          continue;
        }
        Map<Local, Stmt> formalParamLocals = rebuilder
            .actualFlowToFormalLocal(l);
        for (NNode succ : varNode.getSuccessors()) {
          if (!(succ instanceof NVarNode)) {
            continue;
          }
          Local succLocal = ((NVarNode) succ).l;
          if (formalParamLocals == null
              || !formalParamLocals.containsKey(succLocal)) {
            // same method
            updateConstSolution(workList, succLocal, localConstSolution);
          } else {
            // invocation to formal parameter
            Stmt caller = formalParamLocals.get(succLocal);
            if (!(caller instanceof AssignStmt)) {
              continue;
            }
            Value lhsValue = ((AssignStmt) caller).getLeftOp();
            if (!(lhsValue instanceof Local)) {
              continue;
            }
            NVarNode lhsNode = (NVarNode) rebuilder.lookupNode(lhsValue);
            Collection<Local> flowToLocals = jumpSolution.get(succLocal);
            if (flowToLocals == null || flowToLocals.isEmpty()) {
              continue;
            }
            for (Local flowToLocal : flowToLocals) {
              NNode flowToLocalNode = rebuilder.lookupNode(flowToLocal);
              for (NNode succ2 : flowToLocalNode.getSuccessors()) {
                if (succ2 == lhsNode) {
                  updateConstSolution(workList, lhsNode.l, localConstSolution);
                }
              }
            }
          }
        }
      }
      // stage 2
      for (Local l : jumpSolution.keySet()) {
        if (constSolution.get(l) != null) {
          workList.add(l);
        }
      }
      // corrected code
      Map<Local, Object> copyOfConstSolution = Maps.newHashMap();
      for (Local l : constSolution.keySet()) {
        if (constSolution.get(l) == null) {
          continue;
        }
        copyOfConstSolution.put(l, constSolution.get(l));
      }
      for (Local l : copyOfConstSolution.keySet()) {
        Map<Local, Stmt> formalParamLocalToCaller = rebuilder
            .actualFlowToFormalLocal(l);
        if (formalParamLocalToCaller == null
            || formalParamLocalToCaller.isEmpty()) {
          continue;
        }
        for (Local formalParamLocal : formalParamLocalToCaller.keySet()) {
          updateConstSolution(workList, formalParamLocal,
              copyOfConstSolution.get(l));
        }
      }
      while (!workList.isEmpty()) {
        Local l = workList.remove(0);
        Collection<Local> flowtoLocals = jumpSolution.get(l);
        if (flowtoLocals == null || flowtoLocals.isEmpty()) {
          continue;
        }
        Object localConstSolution = constSolution.get(l);
        for (Local flowtoLocal : flowtoLocals) {
          updateConstSolution(workList, flowtoLocal, localConstSolution);
          Map<Local, Stmt> formalParamLocals = rebuilder
              .actualFlowToFormalLocal(flowtoLocal);
          if (formalParamLocals != null) {
            for (Local formalParamLocal : formalParamLocals.keySet()) {
              Object formalLocalSolution = constSolution.get(flowtoLocal);
              updateConstSolution(workList, formalParamLocal,
                  formalLocalSolution);
            }
          }
        }
      }
    }
  }

  private void constIntFixPoint(int id, SootMethod handler) {
    List<Local> workList = Lists.newArrayList();
    Set<Stmt> interestedStmts = Sets.newHashSet();
    beforeIntFixPoint(id, handler, workList, interestedStmts);
    doIntFixPoint(workList, interestedStmts);
  }

  private void beforeIntFixPoint(int id, SootMethod handler, List<Local> workList,  Set<Stmt> interestedStmts) {
    updateConstId(id, handler, workList);
    List<SootMethod> reachableMethods = Lists.newArrayList();
    Set<SootMethod> memberSet = Sets.newHashSet();
    memberSet.add(handler);
    reachableMethods.add(handler);
    while (!reachableMethods.isEmpty()) {
      SootMethod mtd = reachableMethods.remove(0);
      Body body = null;
      synchronized(mtd) {
        body = mtd.retrieveActiveBody();
      }
      Set<Local> formalParamLocals = Sets.newHashSet();
      {
        // find all formal parameter locals
        Iterator<Unit> stmts = body.getUnits().iterator();
        while (stmts.hasNext()) {
          Stmt s = (Stmt) stmts.next();
          if (s instanceof IdentityStmt
              && (isInterestedStmt(s, null, false))) {
            Local lop = (Local) ((IdentityStmt) s).getLeftOp();
            formalParamLocals.add(lop);
          }
        }
      }
      {
        Iterator<Unit> stmts = body.getUnits().iterator();
        while (stmts.hasNext()) {
          Stmt s = (Stmt) stmts.next();
          if (s instanceof InvokeStmt) {
            for (Edge callee : cg.getEdge(s)) {
              intConstantPropagationAtCall(workList, s, callee.target);
            }
          }
          if (!(isInterestedStmt(s, null, false))) {
            continue;
          }
          Local lop = (Local) ((DefinitionStmt) s).getLeftOp();
          Value rop = ((DefinitionStmt) s).getRightOp();
          if (s instanceof AssignStmt && rop instanceof IntConstant) {
            // case 1
            updateConstSolution(workList, lop, ((IntConstant) rop).value);
          } else if (s instanceof AssignStmt && wtgUtil.isGetIdCall(s)) {
            // case 2
            Integer srcPos = wtgUtil.getGetIdField(s);
            if (srcPos == null) {
              Logger.err(getClass().getSimpleName(), "can not find the view local for stmt: " + s);
            }
            Local viewLocal = null;
            if (srcPos == 0) {
              viewLocal = jimpleUtil.receiver(s);
            } else {
              Value argValue = s.getInvokeExpr().getArg(srcPos - 1);
              if (!(argValue instanceof Local)) {
                Logger.err(getClass().getSimpleName(), "the view local is not type of local");
              }
              viewLocal = (Local) argValue;
            }
            Object constValue = constSolution.get(viewLocal);
            if (constValue == null || constValue == ANY) {
              updateConstSolution(workList, lop, ANY);
            } else if (constValue instanceof NNode) {
              NIdNode idNode = ((NNode) constValue).idNode;
              if (idNode == null) {
                updateConstSolution(workList, lop, ANY);
              } else {
                updateConstSolution(workList, lop, idNode.getIdValue());
              }
            }
          } else if (s instanceof AssignStmt
              && (rop instanceof EqExpr || rop instanceof NeExpr)) {
            interestedStmts.add(s);
            // case 3
            boolean equalOp;
            if (rop instanceof EqExpr) {
              equalOp = true;
            } else {
              equalOp = false;
            }
            Value op1 = ((ConditionExpr) rop).getOp1();
            Value op2 = ((ConditionExpr) rop).getOp2();
            Type op1Type = op1.getType();
            Type op2Type = op2.getType();
            if (op1Type instanceof IntType && op2Type instanceof IntType) {
              // case 3, a
              if (op1 instanceof Local) {
                NNode op1Node = rebuilder.lookupNode(op1);
                for (NNode backReachedNode : queryHelper.allVariableValues(op1Node)) {
                  if (backReachedNode instanceof NVarNode
                      && formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                    addToJump((((NVarNode) backReachedNode).l), (Local) op1);
                  }
                }
              }
              if (op2 instanceof Local) {
                NNode op2Node = rebuilder.lookupNode(op2);
                for (NNode backReachedNode : queryHelper.allVariableValues(op2Node)) {
                  if (backReachedNode instanceof NVarNode
                      && formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                    addToJump((((NVarNode) backReachedNode).l), (Local) op2);
                  }
                }
              }
            } else if (op1 instanceof Local && op2 instanceof Local
                && op1Type instanceof RefType && op2Type instanceof RefType) {
              // case 3, b
              Object const1 = constSolution.get(op1);
              Object const2 = constSolution.get(op2);
              if (const1 == null || const2 == null || const1 == ANY
                  || const2 == ANY) {
                updateConstSolution(workList, lop, ANY);
              } else if (const1 instanceof NNode && const2 instanceof NNode) {
                if (const1 == const2) {
                  if (equalOp) {
                    updateConstSolution(workList, lop, 1);
                  } else {
                    updateConstSolution(workList, lop, 0);
                  }
                } else {
                  if (equalOp) {
                    updateConstSolution(workList, lop, 0);
                  } else {
                    updateConstSolution(workList, lop, 1);
                  }
                }
              }
            } else {
              // case 3, c
              updateConstSolution(workList, lop, ANY);
            }
          } else if (s instanceof IdentityStmt
              && (rop instanceof ThisRef || rop instanceof ParameterRef)) {
            // case 4
              addToJump(lop, lop);
          } else if (s instanceof AssignStmt
              && (rop instanceof Local || rop instanceof CastExpr)) {
            // case 5
            Set<NNode> backReachedNodes = queryHelper
                .allVariableValues(rebuilder.lookupNode(lop));
            for (NNode backReachedNode : backReachedNodes) {
              if (backReachedNode instanceof NVarNode) {
                if (formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                  addToJump(((NVarNode) backReachedNode).l, lop);
                }
              }
            }
          } else if (s instanceof AssignStmt && rop instanceof StaticInvokeExpr) {
            // case 6
            SootMethod callee = ((StaticInvokeExpr)rop).getMethod();
            if (callee.getDeclaringClass().isApplicationClass()) {
              Set<NNode> backReachedNodes = queryHelper
                  .allVariableValues(rebuilder.lookupNode(lop));
              for (NNode backReachedNode : backReachedNodes) {
                if (backReachedNode instanceof NVarNode) {
                  if (formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                    addToJump(((NVarNode) backReachedNode).l, lop);
                  }
                }
              }
            } else {
              updateConstSolution(workList, lop, ANY);
            }
          } else if (s instanceof AssignStmt && rop instanceof InstanceInvokeExpr) {
            // case 7
            for (Edge outgoing : cg.getEdge(s)) {
              SootMethod callee = outgoing.target;
              SootClass declaringClz = callee.getDeclaringClass();
              if (declaringClz.isApplicationClass()) {
                NNode lVarNode = rebuilder.lookupNode(lop);
                Set<NNode> backReachedNodes = queryHelper
                    .allVariableValues(lVarNode);
                for (NNode backReachedNode : backReachedNodes) {
                  if (backReachedNode instanceof NVarNode) {
                    if (formalParamLocals.contains(((NVarNode) backReachedNode).l)) {
                      addToJump(((NVarNode) backReachedNode).l, lop);
                    }
                  }
                }
              } else {
                updateConstSolution(workList, lop, ANY);
              }
            }
          } else {
            // case 8
            updateConstSolution(workList, lop, ANY);
          }
        }
      }
      // add callees
      for (Edge outgoing : cg.getOutgoingEdges(mtd)) {
        if (outgoing.target.getDeclaringClass().isApplicationClass()
            && outgoing.target.isConcrete()
            && memberSet.add(outgoing.target)) {
          reachableMethods.add(outgoing.target);
        }
      }
    }
  }

  private void updateConstId(int id, SootMethod handler, List<Local> workList) {
    // find context related GUI
    boolean found = false;
    Body body = null;
    synchronized(handler) {
      body = handler.retrieveActiveBody();
    }
    Iterator<Unit> stmts = body.getUnits().iterator();
    while (stmts.hasNext()) {
      Stmt s = (Stmt) stmts.next();
      if (s instanceof IdentityStmt && (isInterestedStmt(s, null, false))) {
        Local lop = (Local) ((IdentityStmt) s).getLeftOp();
        updateConstSolution(workList, lop, id);
        addToJump(lop, lop);
        if (found) {
          Logger.err(getClass().getSimpleName(), "there should be only one argument related to id:\n"
              + handler.retrieveActiveBody());
        } else {
          found = true;
        }
      }
    }
  }

  private void doIntFixPoint(List<Local> workList, Set<Stmt> interestedStmts) {
    {
      // stage 1
      while (!workList.isEmpty()) {
        Local l = workList.remove(0);
        NVarNode varNode = (NVarNode) rebuilder.lookupNode(l);
        if (varNode == null) {
          // for int return value from library call, NVarNode is null
          continue;
        }
        Object localConstSolution = constSolution.get(l);
        if (localConstSolution == null) {
          Logger.err(getClass().getSimpleName(), "can not find the value for local: " + l);
        }
        Map<Local, Stmt> formalParamLocals = rebuilder.actualFlowToFormalLocal(l);
        for (NNode succ : varNode.getSuccessors()) {
          if (!(succ instanceof NVarNode)) {
            continue;
          }
          Local succLocal = ((NVarNode) succ).l;
          if (formalParamLocals == null
              || !formalParamLocals.containsKey(succLocal)) {
            // same method
            updateConstSolution(workList, succLocal, localConstSolution);
          } else {
            // invocation to formal parameter
            Stmt caller = formalParamLocals.get(succLocal);
            if (!(caller instanceof AssignStmt)) {
              continue;
            }
            Value lhsValue = ((AssignStmt) caller).getLeftOp();
            if (!(lhsValue instanceof Local)) {
              continue;
            }
            NVarNode lhsNode = (NVarNode) rebuilder.lookupNode(lhsValue);
            Collection<Local> flowToLocals = jumpSolution.get(succLocal);
            if (flowToLocals == null || flowToLocals.isEmpty()) {
              continue;
            }
            for (Local flowToLocal : flowToLocals) {
              NNode flowToLocalNode = rebuilder.lookupNode(flowToLocal);
              for (NNode succ2 : flowToLocalNode.getSuccessors()) {
                if (succ2 == lhsNode) {
                  updateConstSolution(workList, lhsNode.l, localConstSolution);
                }
              }
            }
          }
        }
      }
    }
    {
      // stage 2
      for (Local l : jumpSolution.keySet()) {
        if (constSolution.get(l) != null) {
          workList.add(l);
        }
      }
      // corrected code
      Map<Local, Object> copyOfConstSolution = Maps.newHashMap();
      for (Local l : constSolution.keySet()) {
        if (constSolution.get(l) == null) {
          continue;
        }
        copyOfConstSolution.put(l, constSolution.get(l));
      }
      for (Local l : copyOfConstSolution.keySet()) {
        Map<Local, Stmt> formalParamLocalToCaller = rebuilder
            .actualFlowToFormalLocal(l);
        if (formalParamLocalToCaller == null
            || formalParamLocalToCaller.isEmpty()) {
          continue;
        }
        for (Local formalParamLocal : formalParamLocalToCaller.keySet()) {
          updateConstSolution(workList, formalParamLocal,
              copyOfConstSolution.get(l));
        }
      }
      while (!workList.isEmpty()) {
        Local l = workList.remove(0);
        Collection<Local> flowtoLocals = jumpSolution.get(l);
        if (flowtoLocals == null || flowtoLocals.isEmpty()) {
          continue;
        }
        Object localConstSolution = constSolution.get(l);
        for (Local flowtoLocal : flowtoLocals) {
          updateConstSolution(workList, flowtoLocal, localConstSolution);
          Map<Local, Stmt> formalParamLocals = rebuilder
              .actualFlowToFormalLocal(flowtoLocal);
          if (formalParamLocals != null) {
            for (Local formalParamLocal : formalParamLocals.keySet()) {
              Object formalLocalSolution = constSolution.get(flowtoLocal);
              updateConstSolution(workList, formalParamLocal,
                  formalLocalSolution);
            }
          }
        }
      }
    }
    {
      // stage 3
      for (Stmt s : interestedStmts) {
        Local lop = (Local) ((AssignStmt) s).getLeftOp();
        ConditionExpr rop = (ConditionExpr) ((AssignStmt) s).getRightOp();
        boolean equalOp = false;
        if (rop instanceof EqExpr) {
          equalOp = true;
        } else {
          equalOp = false;
        }
        Value op1 = rop.getOp1();
        Value op2 = rop.getOp2();
        Type op1Type = op1.getType();
        Type op2Type = op2.getType();
        if (!(op1Type instanceof IntType) || !(op2Type instanceof IntType)) {
          continue;
        }
        if (op1 instanceof Local && op2 instanceof Local) {
          Object const1 = constSolution.get(op1);
          Object const2 = constSolution.get(op2);
          if (const1 == null || const2 == null) {
            updateConstSolution(workList, lop, ANY);
          } else if (const1 == ANY || const2 == ANY) {
            updateConstSolution(workList, lop, ANY);
          } else if (!(const1 instanceof Integer)
              || !(const2 instanceof Integer)) {
            Logger.err(getClass().getSimpleName(), "");
          } else {
            if (equalOp) {
              if (((Integer) const1).intValue() == ((Integer) const2)
                  .intValue()) {
                updateConstSolution(workList, lop, 1);
              } else {
                updateConstSolution(workList, lop, 0);
              }
            } else {
              if (((Integer) const1).intValue() == ((Integer) const2)
                  .intValue()) {
                updateConstSolution(workList, lop, 0);
              } else {
                updateConstSolution(workList, lop, 1);
              }
            }
          }
        } else if ((op1 instanceof Local && op2 instanceof IntConstant)
            || (op2 instanceof Local && op1 instanceof IntConstant)) {
          Object constValue = null;
          int intValue = 0;
          if ((op1 instanceof Local && op2 instanceof IntConstant)) {
            constValue = constSolution.get(op1);
            intValue = ((IntConstant) op2).value;
          } else {
            constValue = constSolution.get(op2);
            intValue = ((IntConstant) op1).value;
          }
          if (constValue == null || constValue == ANY) {
            updateConstSolution(workList, lop, ANY);
          } else if (!(constValue instanceof Integer)) {
            Logger.err(getClass().getSimpleName(), "");
          } else {
            if (equalOp) {
              if (((Integer) constValue).intValue() == intValue) {
                updateConstSolution(workList, lop, 1);
              } else {
                updateConstSolution(workList, lop, 0);
              }
            } else {
              if (((Integer) constValue).intValue() == intValue) {
                updateConstSolution(workList, lop, 0);
              } else {
                updateConstSolution(workList, lop, 1);
              }
            }
          }
        }
      }
      // stage 1
      while (!workList.isEmpty()) {
        Local l = workList.remove(0);
        Object localConstSolution = constSolution.get(l);
        if (localConstSolution == null) {
          Logger.err(getClass().getSimpleName(), "can not find the value for local: " + l);
        }
        NVarNode varNode = (NVarNode) rebuilder.lookupNode(l);
        if (varNode == null) {
          continue;
        }
        Map<Local, Stmt> formalParamLocals = rebuilder
            .actualFlowToFormalLocal(l);
        for (NNode succ : varNode.getSuccessors()) {
          if (!(succ instanceof NVarNode)) {
            continue;
          }
          Local succLocal = ((NVarNode) succ).l;
          if (formalParamLocals == null
              || !formalParamLocals.containsKey(succLocal)) {
            // same method
            updateConstSolution(workList, succLocal, localConstSolution);
          } else {
            // invocation to formal parameter
            Stmt caller = formalParamLocals.get(succLocal);
            if (!(caller instanceof AssignStmt)) {
              continue;
            }
            Value lhsValue = ((AssignStmt) caller).getLeftOp();
            if (!(lhsValue instanceof Local)) {
              continue;
            }
            NVarNode lhsNode = (NVarNode) rebuilder.lookupNode(lhsValue);
            Collection<Local> flowToLocals = jumpSolution.get(succLocal);
            if (flowToLocals == null || flowToLocals.isEmpty()) {
              continue;
            }
            for (Local flowToLocal : flowToLocals) {
              NNode flowToLocalNode = rebuilder.lookupNode(flowToLocal);
              for (NNode succ2 : flowToLocalNode.getSuccessors()) {
                if (succ2 == lhsNode) {
                  updateConstSolution(workList, lhsNode.l, localConstSolution);
                }
              }
            }
          }
        }
      }
      // stage 2
      for (Local l : jumpSolution.keySet()) {
        if (constSolution.get(l) != null) {
          workList.add(l);
        }
      }
      // corrected code
      Map<Local, Object> copyOfConstSolution = Maps.newHashMap();
      for (Local l : constSolution.keySet()) {
        if (constSolution.get(l) == null) {
          continue;
        }
        copyOfConstSolution.put(l, constSolution.get(l));
      }
      for (Local l : copyOfConstSolution.keySet()) {
        Map<Local, Stmt> formalParamLocalToCaller = rebuilder
            .actualFlowToFormalLocal(l);
        if (formalParamLocalToCaller == null
            || formalParamLocalToCaller.isEmpty()) {
          continue;
        }
        for (Local formalParamLocal : formalParamLocalToCaller.keySet()) {
          updateConstSolution(workList, formalParamLocal,
              copyOfConstSolution.get(l));
        }
      }
      while (!workList.isEmpty()) {
        Local l = workList.remove(0);
        Collection<Local> flowtoLocals = jumpSolution.get(l);
        if (flowtoLocals == null || flowtoLocals.isEmpty()) {
          continue;
        }
        Object localConstSolution = constSolution.get(l);
        for (Local flowtoLocal : flowtoLocals) {
          updateConstSolution(workList, flowtoLocal, localConstSolution);
          Map<Local, Stmt> formalParamLocals = rebuilder
              .actualFlowToFormalLocal(flowtoLocal);
          if (formalParamLocals != null) {
            for (Local formalParamLocal : formalParamLocals.keySet()) {
              Object formalLocalSolution = constSolution.get(flowtoLocal);
              updateConstSolution(workList, formalParamLocal,
                  formalLocalSolution);
            }
          }
        }
      }
    }
  }

  private void updateConstSolution(List<Local> workList, Local l, Object v) {
    Object constValue = constSolution.get(l);
    if (constValue == null) {
      constSolution.put(l, v);
      workList.add(l);
    } else if (constValue == v || constValue == ANY) {
    } else if (constValue instanceof Integer
        && v instanceof Integer
        && ((Integer)constValue).intValue() == ((Integer)v).intValue()) {
    } else if (constValue != v) {
      constSolution.put(l, ANY);
      workList.add(l);
    } else {
      Logger.err(getClass().getSimpleName(), "impossible situation");
    }
  }
  private void conservativeUpdate(List<Local> workList, Local l) {
    // Look at the GUI analysis solution for x; for each element z of this solution,
    // call updateSolution(x,z);
    NVarNode varNode = (NVarNode) rebuilder.lookupNode(l);
    if (varNode == null) {
      return;
    }
    Set<NNode> backReachedNodes = queryHelper.allVariableValues(varNode);
    for (NNode backReachedNode : backReachedNodes) {
      if (backReachedNode instanceof NObjectNode) {
        updateConstSolution(workList, l, backReachedNode);
      }
    }
  }
  private void addToJump(Local from, Local to) {
    jumpSolution.put(from, to);
  }
  private boolean isInterestedStmt(Stmt s, NObjectNode contextObject, boolean lookForRef) {
    if (s instanceof DefinitionStmt) {
      Value lop = ((DefinitionStmt)s).getLeftOp();
      if (lop instanceof Local) {
        Type llType = lop.getType();
        if (lookForRef && llType instanceof RefType) {
          SootClass llocalClz = ((RefType) llType).getSootClass();
          SootClass guiClz = contextObject.getClassType();
          if (hier.isSubclassOf(guiClz, llocalClz)) {
            return true;
          }
        } else if (!lookForRef) {
          return isIntType(lop);
        }
      }
    }
    return false;
  }
  private void handleIfStmt(UnitGraph cfg, IfStmt s, HashMultimap<Stmt, Stmt> infeasibleEdges) {
    Value conditionValue = s.getCondition();
    GoTo goTo = eval(conditionValue);
    if (goTo == GoTo.unrelated) {
      return;
    } else if (goTo == GoTo.true_branch) {
      for (Unit u : cfg.getSuccsOf(s)) {
        if (u != s.getTarget()) {
          infeasibleEdges.put(s, (Stmt) u);
        }
      }
    } else {
      infeasibleEdges.put(s, s.getTarget());
    }
  }
  private void handleTableSwitchStmt(UnitGraph cfg, TableSwitchStmt s, HashMultimap<Stmt, Stmt> infeasibleEdges) {
    Value key = s.getKey();
    if (!(key instanceof Local)) {
      return;
    }
    Object o = constSolution.get(key);
    if (o == null || o == ANY) {
      return;
    } else if (!(o instanceof Integer)) {
      Logger.err(getClass().getSimpleName(), "the const value of the key local: " + key + " is type of " + o.getClass());
    } else {
      int lowIdx = s.getLowIndex();
      int highIdx = s.getHighIndex();
      int entry = ((Integer)o).intValue();
      Unit target = null;
      if (entry >= lowIdx && entry <= highIdx) {
        target = s.getTarget(entry - lowIdx);
      } else {
        target = s.getDefaultTarget();
      }
      for (int i = lowIdx; i <= highIdx; i++) {
        Unit u = s.getTarget(i-lowIdx);
        if (u == target) {
          continue;
        }
        infeasibleEdges.put(s, (Stmt) u);
      }
      Unit u = s.getDefaultTarget();
      if (u != target) {
        infeasibleEdges.put(s, (Stmt) u);
      }
    }
  }
  private void handleLookupSwitchStmt(UnitGraph cfg, LookupSwitchStmt s, HashMultimap<Stmt, Stmt> infeasibleEdges) {
    Value key = s.getKey();
    if (!(key instanceof Local)) {
      return;
    }
    Object o = constSolution.get(key);
    if (o == null || o == ANY) {
      return;
    } else if (!(o instanceof Integer)) {
      Logger.err(getClass().getSimpleName(), "the const value of the key local: " + key + " is type of " + o.getClass());
    } else {
      int entry = ((Integer)o).intValue();
      Unit target = null;
      for (int i = 0; i < s.getLookupValues().size(); i++) {
        int lookupValue = s.getLookupValue(i);
        if (lookupValue == entry) {
          target = s.getTarget(i);
          break;
        }
      }
      if (target == null) {
        target = s.getDefaultTarget();
      }
      for (int i = 0; i < s.getLookupValues().size(); i++) {
        Unit u = s.getTarget(i);
        if (u == target) {
          continue;
        }
        infeasibleEdges.put(s, (Stmt) u);
      }
      Unit u = s.getDefaultTarget();
      if (u != target) {
        infeasibleEdges.put(s, (Stmt) u);
      }
    }
  }

  private boolean isIntType(Value v) {
    Type type = v.getType();
    if (type instanceof IntType) {
      return true;
    } else if (type instanceof RefType) {
      SootClass sc = ((RefType) type).getSootClass();
      if (sc == Scene.v().getSootClass("java.lang.Integer")) {
        return true;
      }
    }
    return false;
  }

  private GoTo eval(Value v) {
    if (v instanceof Local) {
      Object value = constSolution.get(v);
      if (value == null || value == ANY) {
        return GoTo.unrelated;
      }
      if (value instanceof Integer) {
        if (((Integer) value).intValue() == 1) {
          return GoTo.true_branch;
        } else {
          return GoTo.false_branch;
        }
      } else {
        Logger.err(getClass().getSimpleName(), "the const value is not null, any nor Integer, it is " + value.getClass());
        return null;
      }
    } else if (v instanceof ConditionExpr) {
      boolean equalOp = true;
      if (v instanceof EqExpr) {
        equalOp = true;
      } else if (v instanceof NeExpr) {
        equalOp = false;
      } else {
        return GoTo.unrelated;
      }
      Value op1 = ((ConditionExpr) v).getOp1();
      Value op2 = ((ConditionExpr) v).getOp2();
      if (op1.getType() instanceof RefType) {
        if (op1 instanceof Local && op2 instanceof Local) {
          Object o1 = constSolution.get(op1);
          Object o2 = constSolution.get(op2);
          if (o1 == null || o1 == ANY || o2 == null || o2 == ANY) {
            return GoTo.unrelated;
          } else if (o1 == o2) {
            if (equalOp) {
              return GoTo.true_branch;
            } else {
              return GoTo.false_branch;
            }
          } else {
            if (!equalOp) {
              return GoTo.true_branch;
            } else {
              return GoTo.false_branch;
            }
          }
        } else {
          return GoTo.unrelated;
        }
      } else if (op1.getType() instanceof IntType) {
        Integer v1 = null;
        Integer v2 = null;
        if (op1 instanceof Local) {
          Object o1 = constSolution.get(op1);
          if (o1 == null || o1 == ANY) {
            return GoTo.unrelated;
          } else if (o1 instanceof Integer) {
            v1 = (Integer)o1;
          } else {
            return GoTo.unrelated;
          }
        } else if (op1 instanceof IntConstant) {
          v1 = ((IntConstant) op1).value;
        } else {
          return GoTo.unrelated;
        }
        if (op2 instanceof Local) {
          Object o2 = constSolution.get(op2);
          if (o2 == null || o2 == ANY) {
            return GoTo.unrelated;
          } else if (o2 instanceof Integer) {
            v2 = (Integer)o2;
          } else {
            return GoTo.unrelated;
          }
        } else if (op2 instanceof IntConstant) {
          v2 = ((IntConstant) op2).value;
        } else {
          return GoTo.unrelated;
        }

        if (v1.intValue() == v2.intValue()) {
          if (equalOp) {
            return GoTo.true_branch;
          } else {
            return GoTo.false_branch;
          }
        } else {
          if (!equalOp) {
            return GoTo.true_branch;
          } else {
            return GoTo.false_branch;
          }
        }
      } else {
        return GoTo.unrelated;
      }
    } else {
      return GoTo.unrelated;
    }
  }

  private Set<SootMethod> addReachableCall(SootMethod mtd, Stmt s, Set<SootMethod> memberSet, List<SootMethod> reachableMethods) {
    Preconditions.checkArgument((mtd != null && s == null) || (mtd == null && s != null));
    Set<SootMethod> infeasibleCallees = Sets.newHashSet();
    Set<Edge> outgoings = null;
    if (mtd != null) {
      outgoings = cg.getOutgoingEdges(mtd);
    } else {
      outgoings = cg.getEdge(s);
    }
    for (Edge outgoing : outgoings) {
      if (outgoing.callSite.getInvokeExpr() instanceof InterfaceInvokeExpr
          || outgoing.callSite.getInvokeExpr() instanceof VirtualInvokeExpr) {
        // we don't handle SpecialInvokeExpr
        Local rcv = jimpleUtil.receiver(outgoing.callSite);
        Type rcvType = rcv.getType();
        if (rcvType instanceof RefType) {
          Object solution = constSolution.get(rcv);
          if (solution != null && solution != ANY && solution instanceof NObjectNode) {
            SootClass type = ((NObjectNode) solution).getClassType();
            if (type == null) {
              // if we can not find the type for solution, set it to any
              Logger.err(getClass().getSimpleName(), "can not find the type of solution: " + solution);
            }
            if (type.isConcrete()) {
              // we have constant resolution to refine the call graph
              SootMethod tgt = hier.virtualDispatch(outgoing.target,
                  ((NObjectNode) solution).getClassType());
              if (tgt != null && tgt.getDeclaringClass().isApplicationClass()
                  && tgt.isConcrete() && memberSet.add(tgt)) {
                reachableMethods.add(tgt);
              } else {
                infeasibleCallees.add(tgt);
              }
              continue;
            }
          }
        }
      }
      if (outgoing.target.getDeclaringClass().isApplicationClass()
          && outgoing.target.isConcrete()
          && memberSet.add(outgoing.target)) {
        reachableMethods.add(outgoing.target);
      } else {
        infeasibleCallees.add(outgoing.target);
      }
    }
    return infeasibleCallees;
  }

  private void intConstantPropagationAtCall(List<Local> workList, Stmt caller, SootMethod callee) {
    // Check & filter
    InvokeExpr ie = caller.getInvokeExpr();
    if (wtgUtil.isIgnoredMethod(callee)) {
      return;
    }
    if (!callee.getDeclaringClass().isApplicationClass()) {
      return;
    }
    if (!callee.isConcrete()) {
      return; // could happen for native methods
    }
    // Parameter binding
    Body b = callee.retrieveActiveBody();
    Iterator<Unit> stmts = b.getUnits().iterator();
    int num_param = callee.getParameterCount();
    if (!callee.isStatic()) {
      num_param++;
    }
    Local receiverLocal = null;
    for (int i = 0; i < num_param; i++) {
      Stmt s = (Stmt) stmts.next();
      Value actual;
      if (ie instanceof InstanceInvokeExpr) {
        if (i == 0) {
          receiverLocal = jimpleUtil.receiver(ie);
          actual = receiverLocal;
        } else {
          actual = ie.getArg(i - 1);
        }
      } else {
        actual = ie.getArg(i);
      }
      if (!(s instanceof DefinitionStmt)) {
        continue;
      }
      Local formal = jimpleUtil.lhsLocal(s);
      if (actual instanceof IntConstant) {
        updateConstSolution(workList, formal, ((IntConstant) actual).value);
      }
    }
  }

  enum GoTo {
    unrelated,
    true_branch,
    false_branch,
  }
}
