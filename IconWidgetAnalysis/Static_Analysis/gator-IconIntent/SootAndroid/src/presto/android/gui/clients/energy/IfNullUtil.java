package presto.android.gui.clients.energy;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import presto.android.Logger;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph;
import presto.android.gui.wtg.util.Filter;
import presto.android.gui.wtg.util.QueryHelper;
import presto.android.gui.wtg.util.WTGUtil;
import soot.*;
import soot.Scene;
import soot.jimple.*;
import soot.toolkits.exceptions.ThrowableSet;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by zero on 10/26/15.
 */
public class IfNullUtil {
  private static IfNullUtil instance;

  // call grpah
  private AndroidCallGraph cg;
  // wtg util
  private WTGUtil wtgUtil;

  private IfNullUtil(){
    cg = AndroidCallGraph.v();
    wtgUtil = WTGUtil.v();
  }

  public static IfNullUtil v(){
    if (instance == null){
      instance = new IfNullUtil();
    }
    return instance;
  }



  private void propagateMod(
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> workingList,
          List<Stmt> visitedSeq,
          Stmt s,
          SootMethod cxt) {
    if (!visitedStmts.containsKey(s)) {
      visitedStmts.put(s, cxt);
      workingList.add(s);
      visitedSeq.add(s);
    }
  }

  /**
   * A modification of original traverse method. Add support to
   * bypass several exception handler.
   * @param workingList
   * @param visitedStmts
   * @param visitedSeq
   * @param escapedStmts
   * @param methodToCFG
   * @param filter
   * @param infeasibleEdges
   * @param infeasibleCalls
   * @return
   */
  private boolean traverseMod(
          List<Stmt> workingList,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
          Set<Stmt> escapedStmts,
          Map<SootMethod, UnitGraph> methodToCFG,
          Filter<Stmt, SootMethod> filter,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    final String mtdTag = "TRVMOD";
    final boolean bDebug = false;
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
      ExceptionalUnitGraph currentCFG = createOrGetECFG(methodToCFG, currentCxt);

      // switch case for 4 conditions
      // case 1: currentStmt is not a call and not exit of cfg
      if (!currentStmt.containsInvokeExpr()
              && !currentCFG.getTails().contains(currentStmt)) {
        //Do propagation for un-exceptional successors
        Collection<Unit> unExceptionalSucessors = currentCFG.getUnexceptionalSuccsOf(currentStmt);
        //Collection<Unit> success = currentCFG.getSuccsOf(currentStmt);
        for (Unit succ : unExceptionalSucessors) {
          Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
          if (tgts != null && tgts.contains(succ)) {
            continue;
          }
          propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
        }
        //Do propagation for exceptional successors, if this exception types should not ignored
        Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = currentCFG.getExceptionDests(currentStmt);
        for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
          ThrowableSet thSet = curDst.getThrowables();
          Unit exSucc = curDst.getHandlerNode();
          if (exSucc == null){
            continue;
          }
          Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
          boolean ignore = true;
          for (RefType curRefType : exceptionTypes) {
            if (!ExceptionTypes.v().isIgnoredException(curRefType)){
              ignore = false;
            }
          }
          if (!ignore) {
            propagateMod(visitedStmts, workingList, visitedSeq, (Stmt)exSucc, currentCxt);
          }
        }
      }
      // case 2: currentStmt is a call but not a call to
      // startActivity/showDialog/etc.
      else if (currentStmt.containsInvokeExpr()) {
        Set<AndroidCallGraph.Edge> outgoings = cg.getEdge(currentStmt);
        Set<SootMethod> infeasibleCallees = infeasibleCalls.get(currentStmt);
        boolean findTarget = false;
        for (AndroidCallGraph.Edge outgoing : outgoings) {
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
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) entryNode, target);
            }
            if (visitedMethods.contains(target)) {
              //Do propagation of un-exceptional succ
              Collection<Unit> unException = currentCFG.getUnexceptionalSuccsOf(currentStmt);
              for (Unit succ : unException) {
                Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
                if (tgts != null && tgts.contains(succ)) {
                  continue;
                }
                propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
              }
              //Do propagation of exceptional succ
              Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = currentCFG.getExceptionDests(currentStmt);
              for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
                ThrowableSet thSet = curDst.getThrowables();
                Unit exSucc = curDst.getHandlerNode();
                if (exSucc == null){
                  continue;
                }
                Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
                boolean ignore = true;
                for (RefType curRefType : exceptionTypes) {
                  //Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
                  if (!ExceptionTypes.v().isIgnoredException(curRefType)){
                    ignore = false;
                  }
                }
                if (!ignore) {
                  propagateMod(visitedStmts, workingList, visitedSeq, (Stmt)exSucc, currentCxt);
                }
              }
            }
          }
        }
        // if the target can not be found, then we conservatively think there is
        // no transition
        if (!findTarget) {
          //Do propagation for unexception successors
          Collection<Unit> unException = currentCFG.getUnexceptionalSuccsOf(currentStmt);
          for (Unit succ : unException) {
            Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
            if (tgts != null && tgts.contains(succ)) {
              continue;
            }
            propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
          }
          //Do propagation for exceptional successors
          Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = currentCFG.getExceptionDests(currentStmt);
          for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
            ThrowableSet thSet = curDst.getThrowables();
            Unit exSucc = curDst.getHandlerNode();
            if (exSucc == null){
              continue;
            }
            Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
            boolean ignore = true;
            for (RefType curRefType : exceptionTypes) {
              //Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
              if (!ExceptionTypes.v().isIgnoredException(curRefType)){
                ignore = false;
              }
            }
            if (!ignore) {
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt)exSucc, currentCxt);
            }
          }
        }
      }
      // case 3: currentStmt is the exit point and callingContext is not in
      // visitedMethods
      else if (currentCFG.getTails().contains(currentStmt)
              && !visitedMethods.contains(currentCxt)) {
        visitedMethods.add(currentCxt);
        createOrGetCFG(methodToCFG, currentCxt);
        Set<AndroidCallGraph.Edge> incomings = cg.getIncomingEdges(currentCxt);
        for (AndroidCallGraph.Edge e : incomings) {
          Stmt caller = e.callSite;
          if (visitedStmts.containsKey(caller)) {
            SootMethod callerCxt = visitedStmts.get(caller);
            UnitGraph callerCFG = createOrGetCFG(methodToCFG, callerCxt);
            for (Unit succ : callerCFG.getSuccsOf(caller)) {
              Set<Stmt> tgts = infeasibleEdges.get(caller);
              if (tgts != null && tgts.contains(succ)) {
                continue;
              }
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, callerCxt);
            }
          }
        }
      }
    }
    return unexpected;
  }


  public boolean forwardTraversalMod(
          SootMethod handler,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
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
      propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) entryNode, handler);
    }
    return traverseMod(workingList, visitedStmts, visitedSeq, escapedStmts, methodToCFG,
            filter, infeasibleEdges, infeasibleCalls);
  }

  public boolean forwardTraversalIfFix(
          SootMethod handler,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
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
      propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) entryNode, handler);
    }
    return traverseWithIfFix(workingList, visitedStmts, visitedSeq, escapedStmts, methodToCFG,
            filter, infeasibleEdges, infeasibleCalls);
  }

  public boolean forwardTraversalIfIgnore(
          SootMethod handler,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
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
      propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) entryNode, handler);
    }
    return traverseWithIfIgnore(workingList, visitedStmts, visitedSeq, escapedStmts, methodToCFG,
            filter, infeasibleEdges, infeasibleCalls);
  }

  private boolean propageIfStmt(
          Stmt currentStmt,
          SootMethod currentCxt,
          List<Stmt> workingList,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
          ExceptionalUnitGraph currentCFG

  ){
    final String mtdTag = "PROIF";

    IfStmt curIfStmt = (IfStmt) currentStmt;
    Value curIFVal = curIfStmt.getCondition();
    if (curIFVal instanceof NeExpr || curIFVal instanceof EqExpr) {

      BinopExpr binExpr = (BinopExpr) curIFVal;
      Value Op1 = binExpr.getOp1();
      Value Op2 = binExpr.getOp2();
      //Logger.verb(mtdTag, "Op1: " + Op1 + " Type: " + Op1.getClass().getSimpleName());
      //Logger.verb(mtdTag, "Op2: " + Op1 + " Type: " + Op2.getClass().getSimpleName());
      if (Op1 instanceof NullConstant || Op2 instanceof  NullConstant){
        //Logger.verb(mtdTag, "IF Statement: " + curIfStmt + " Type: " + curIfStmt.getClass().getSimpleName());
        Local curLocal = null;
        if (Op1 instanceof Local) {
          curLocal = (Local)Op1;
        }
        if (Op2 instanceof Local) {
          curLocal = (Local)Op2;
        }
        if (curLocal != null) {
          if (curIFVal instanceof NeExpr) {
            //Not equal
            Unit target = curIfStmt.getTarget();
            if (isLocalNotNull(curLocal, currentCxt)){
              if (false){
                Logger.verb(mtdTag, "IfStmt: " + curIfStmt + " local is not null");
                //Logger.verb(mtdTag, "Target: " + target);
              }
              propagateMod(visitedStmts, workingList,visitedSeq, (Stmt) target, currentCxt);
              return true;
            }
            if (false){
              Logger.verb(mtdTag, "IfStmt: " + curIfStmt + " local is null");
              //Logger.verb(mtdTag, "Target: " + target);
            }
            return false;

          } else if (curIFVal instanceof EqExpr) {
            //equal
            Unit ftarget = curIfStmt.getTarget();
            List<Unit> targets = Lists.newArrayList(targets = currentCFG.getUnexceptionalSuccsOf(curIfStmt));
            if (targets.contains(ftarget)) {
              targets.remove(ftarget);
            }else{
              Logger.verb(mtdTag, "IF EQ NULL. target not found in succsor");
            }

            if (isLocalNotNull(curLocal, currentCxt)) {
              if (false){
                Logger.verb(mtdTag, "IfStmt: " + curIfStmt + " local is not null");
              }

              for (Unit target: targets) {
                //Logger.verb(mtdTag, "Target: " + target);
                propagateMod(visitedStmts, workingList,visitedSeq, (Stmt) target, currentCxt);
              }
              return true;
            }
            if (false){
              Logger.verb(mtdTag, "IfStmt: " + curIfStmt + " local is null");
            }
            return false;
          }
        }
      }
    }

    return false;
  }

  private boolean propageIfStmtIgnore(
          Stmt currentStmt,
          SootMethod currentCxt,
          List<Stmt> workingList,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
          ExceptionalUnitGraph currentCFG

  ){
    final String mtdTag = "PROIF";

    IfStmt curIfStmt = (IfStmt) currentStmt;
    Value curIFVal = curIfStmt.getCondition();
    if (curIFVal instanceof NeExpr || curIFVal instanceof EqExpr) {

      BinopExpr binExpr = (BinopExpr) curIFVal;
      Value Op1 = binExpr.getOp1();
      Value Op2 = binExpr.getOp2();
      //Logger.verb(mtdTag, "Op1: " + Op1 + " Type: " + Op1.getClass().getSimpleName());
      //Logger.verb(mtdTag, "Op2: " + Op1 + " Type: " + Op2.getClass().getSimpleName());
      if (Op1 instanceof NullConstant || Op2 instanceof  NullConstant){
        //Logger.verb(mtdTag, "IF Statement: " + curIfStmt + " Type: " + curIfStmt.getClass().getSimpleName());
        Local curLocal = null;
        if (Op1 instanceof Local) {
          curLocal = (Local)Op1;
        }
        if (Op2 instanceof Local) {
          curLocal = (Local)Op2;
        }
        if (curLocal != null) {
          if (curIFVal instanceof NeExpr) {
            //Not equal
            Unit target = curIfStmt.getTarget();
            propagateMod(visitedStmts, workingList,visitedSeq, (Stmt) target, currentCxt);
            if (false){
              Logger.verb(mtdTag, "IfStmt: " + curIfStmt + " local is null");
              //Logger.verb(mtdTag, "Target: " + target);
            }
            return true;

          } else if (curIFVal instanceof EqExpr) {
            //equal
            Unit ftarget = curIfStmt.getTarget();
            List<Unit> targets = Lists.newArrayList(targets = currentCFG.getUnexceptionalSuccsOf(curIfStmt));
            if (targets.contains(ftarget)) {
              targets.remove(ftarget);
            }else{
              Logger.verb(mtdTag, "IF EQ NULL. target not found in succsor");
            }


            for (Unit target: targets) {
              //Logger.verb(mtdTag, "Target: " + target);
              propagateMod(visitedStmts, workingList,visitedSeq, (Stmt) target, currentCxt);
            }
            return true;
          }
        }
      }
    }

    return false;
  }


  private synchronized List<Unit> getUnexceptionalSuccssor(ExceptionalUnitGraph currentCFG, Stmt currentStmt){
    return Lists.newArrayList(currentCFG.getUnexceptionalSuccsOf(currentStmt));
  }

  private synchronized List<ExceptionalUnitGraph.ExceptionDest> getExceptionalDest(ExceptionalUnitGraph currentCFG, Stmt currentStmt){
    return Lists.newArrayList(Lists.newArrayList(currentCFG.getExceptionDests(currentStmt)));
  }

  public boolean traverseWithIfFix(
          List<Stmt> workingList,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
          Set<Stmt> escapedStmts,
          Map<SootMethod, UnitGraph> methodToCFG,
          Filter<Stmt, SootMethod> filter,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    final boolean bDebug = false;
    final String mtdTag = "TRVIF";
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

      ExceptionalUnitGraph currentCFG = createOrGetECFG(methodToCFG, currentCxt);

      // switch case for 4 conditions
      // case 1: currentStmt is not a call and not exit of cfg
      if (!currentStmt.containsInvokeExpr()
              && !currentCFG.getTails().contains(currentStmt)) {

        //If it is a if stmt
        if (currentStmt instanceof IfStmt) {
          if (propageIfStmt(currentStmt, currentCxt, workingList, visitedStmts, visitedSeq, currentCFG)){
            continue;
          }
        } // End of IF Stmt traverse

        //Do propagation for un-exceptional successors
        Collection<Unit> unExceptionalSucessors = getUnexceptionalSuccssor(currentCFG, currentStmt);

        for (Unit succ : unExceptionalSucessors) {
          Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
          if (tgts != null && tgts.contains(succ)) {
            continue;
          }
          propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
        }
        //Do propagation for exceptional successors
        if (true) {
          Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = getExceptionalDest(currentCFG, currentStmt);
          for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
            ThrowableSet thSet = curDst.getThrowables();
            Unit exSucc = curDst.getHandlerNode();
            if (exSucc == null) {
              continue;
            }
            Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
            boolean ignore = true;
            for (RefType curRefType : exceptionTypes) {
              if (bDebug)
                Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
              if (!ExceptionTypes.v().isIgnoredException(curRefType)) {
                ignore = false;
              }
            }
            if (!ignore) {
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) exSucc, currentCxt);
            }
          }
        }
      }
      // case 2: currentStmt is a call but not a call to
      // startActivity/showDialog/etc.
      else if (currentStmt.containsInvokeExpr()) {
        Set<AndroidCallGraph.Edge> outgoings = cg.getEdge(currentStmt);
        Set<SootMethod> infeasibleCallees = infeasibleCalls.get(currentStmt);
        boolean findTarget = false;
        for (AndroidCallGraph.Edge outgoing : outgoings) {
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
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) entryNode, target);
            }
            if (visitedMethods.contains(target)) {
              //Do propagation of un-exceptional succ
              Collection<Unit> unException = Lists.newArrayList(currentCFG.getUnexceptionalSuccsOf(currentStmt));
              for (Unit succ : unException) {
                Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
                if (tgts != null && tgts.contains(succ)) {
                  continue;
                }
                propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
              }
              //Do propagation of exceptional succ
              if (true) {
                Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = Lists.newArrayList(currentCFG.getExceptionDests(currentStmt));
                for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
                  ThrowableSet thSet = curDst.getThrowables();
                  Unit exSucc = curDst.getHandlerNode();
                  if (exSucc == null) {
                    continue;
                  }
                  Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
                  boolean ignore = true;
                  for (RefType curRefType : exceptionTypes) {
                    if (bDebug)
                      Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
                    if (!ExceptionTypes.v().isIgnoredException(curRefType)) {
                      ignore = false;
                    }
                  }
                  if (!ignore) {
                    propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) exSucc, currentCxt);
                  }
                }
              }
            }
          }
        }
        // if the target can not be found, then we conservatively think there is
        // no transition
        if (!findTarget) {
          //Do propagation for unexception successors
          Collection<Unit> unException = Lists.newArrayList(currentCFG.getUnexceptionalSuccsOf(currentStmt));
          for (Unit succ : unException) {
            Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
            if (tgts != null && tgts.contains(succ)) {
              continue;
            }
            propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
          }
          //Do propagation for exceptional successors
          if (true) {
            Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = Lists.newArrayList(currentCFG.getExceptionDests(currentStmt));
            for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
              ThrowableSet thSet = curDst.getThrowables();
              Unit exSucc = curDst.getHandlerNode();
              if (exSucc == null) {
                continue;
              }
              Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
              boolean ignore = true;
              for (RefType curRefType : exceptionTypes) {
                if (bDebug)
                  Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
                if (!ExceptionTypes.v().isIgnoredException(curRefType)) {
                  ignore = false;
                }
              }
              if (!ignore) {
                propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) exSucc, currentCxt);
              }
            }
          }
        }
      }
      // case 3: currentStmt is the exit point and callingContext is not in
      // visitedMethods
      else if (currentCFG.getTails().contains(currentStmt)
              && !visitedMethods.contains(currentCxt)) {
        visitedMethods.add(currentCxt);
        createOrGetCFG(methodToCFG, currentCxt);
        Set<AndroidCallGraph.Edge> incomings = cg.getIncomingEdges(currentCxt);
        for (AndroidCallGraph.Edge e : incomings) {
          Stmt caller = e.callSite;
          if (visitedStmts.containsKey(caller)) {
            SootMethod callerCxt = visitedStmts.get(caller);
            //UnitGraph callerCFG = createOrGetCFG(methodToCFG, callerCxt);
            ExceptionalUnitGraph callerCFG = createOrGetECFG(methodToCFG, callerCxt);
            Collection<Unit> unException = Lists.newArrayList(callerCFG.getUnexceptionalSuccsOf(caller));
            for (Unit succ : unException) {
              Set<Stmt> tgts = infeasibleEdges.get(caller);
              if (tgts != null && tgts.contains(succ)) {
                continue;
              }
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, callerCxt);
            }
            //Do propagation for exceptional successors
            if (true) {
              Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = Lists.newArrayList(callerCFG.getExceptionDests(caller));
              for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
                ThrowableSet thSet = curDst.getThrowables();
                Unit exSucc = curDst.getHandlerNode();
                if (exSucc == null) {
                  continue;
                }
                Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
                boolean ignore = true;
                for (RefType curRefType : exceptionTypes) {
                  if (bDebug)
                    Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
                  if (!ExceptionTypes.v().isIgnoredException(curRefType)) {
                    ignore = false;
                  }
                }
                if (!ignore) {
                  propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) exSucc, currentCxt);
                }
              }
            }
          }
        }
      }
    }
    return unexpected;
  }

  public boolean traverseWithIfIgnore(
          List<Stmt> workingList,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
          Set<Stmt> escapedStmts,
          Map<SootMethod, UnitGraph> methodToCFG,
          Filter<Stmt, SootMethod> filter,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    final boolean bDebug = false;
    final String mtdTag = "TRVIF";
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

      ExceptionalUnitGraph currentCFG = createOrGetECFG(methodToCFG, currentCxt);

      // switch case for 4 conditions
      // case 1: currentStmt is not a call and not exit of cfg
      if (!currentStmt.containsInvokeExpr()
              && !currentCFG.getTails().contains(currentStmt)) {

        //If it is a if stmt
        if (currentStmt instanceof IfStmt) {
          if (propageIfStmtIgnore(currentStmt, currentCxt, workingList, visitedStmts, visitedSeq, currentCFG)){
            continue;
          }
        } // End of IF Stmt traverse

        //Do propagation for un-exceptional successors
        Collection<Unit> unExceptionalSucessors = getUnexceptionalSuccssor(currentCFG, currentStmt);

        for (Unit succ : unExceptionalSucessors) {
          Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
          if (tgts != null && tgts.contains(succ)) {
            continue;
          }
          propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
        }
        //Do propagation for exceptional successors
        if (true) {
          Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = getExceptionalDest(currentCFG, currentStmt);
          for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
            ThrowableSet thSet = curDst.getThrowables();
            Unit exSucc = curDst.getHandlerNode();
            if (exSucc == null) {
              continue;
            }
            Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
            boolean ignore = true;
            for (RefType curRefType : exceptionTypes) {
              if (bDebug)
                Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
              if (!ExceptionTypes.v().isIgnoredException(curRefType)) {
                ignore = false;
              }
            }
            if (!ignore) {
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) exSucc, currentCxt);
            }
          }
        }
      }
      // case 2: currentStmt is a call but not a call to
      // startActivity/showDialog/etc.
      else if (currentStmt.containsInvokeExpr()) {
        Set<AndroidCallGraph.Edge> outgoings = cg.getEdge(currentStmt);
        Set<SootMethod> infeasibleCallees = infeasibleCalls.get(currentStmt);
        boolean findTarget = false;
        for (AndroidCallGraph.Edge outgoing : outgoings) {
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
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) entryNode, target);
            }
            if (visitedMethods.contains(target)) {
              //Do propagation of un-exceptional succ
              Collection<Unit> unException = Lists.newArrayList(currentCFG.getUnexceptionalSuccsOf(currentStmt));
              for (Unit succ : unException) {
                Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
                if (tgts != null && tgts.contains(succ)) {
                  continue;
                }
                propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
              }
              //Do propagation of exceptional succ
              if (true) {
                Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = Lists.newArrayList(currentCFG.getExceptionDests(currentStmt));
                for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
                  ThrowableSet thSet = curDst.getThrowables();
                  Unit exSucc = curDst.getHandlerNode();
                  if (exSucc == null) {
                    continue;
                  }
                  Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
                  boolean ignore = true;
                  for (RefType curRefType : exceptionTypes) {
                    if (bDebug)
                      Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
                    if (!ExceptionTypes.v().isIgnoredException(curRefType)) {
                      ignore = false;
                    }
                  }
                  if (!ignore) {
                    propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) exSucc, currentCxt);
                  }
                }
              }
            }
          }
        }
        // if the target can not be found, then we conservatively think there is
        // no transition
        if (!findTarget) {
          //Do propagation for unexception successors
          Collection<Unit> unException = Lists.newArrayList(currentCFG.getUnexceptionalSuccsOf(currentStmt));
          for (Unit succ : unException) {
            Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
            if (tgts != null && tgts.contains(succ)) {
              continue;
            }
            propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, currentCxt);
          }
          //Do propagation for exceptional successors
          if (true) {
            Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = Lists.newArrayList(currentCFG.getExceptionDests(currentStmt));
            for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
              ThrowableSet thSet = curDst.getThrowables();
              Unit exSucc = curDst.getHandlerNode();
              if (exSucc == null) {
                continue;
              }
              Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
              boolean ignore = true;
              for (RefType curRefType : exceptionTypes) {
                if (bDebug)
                  Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
                if (!ExceptionTypes.v().isIgnoredException(curRefType)) {
                  ignore = false;
                }
              }
              if (!ignore) {
                propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) exSucc, currentCxt);
              }
            }
          }
        }
      }
      // case 3: currentStmt is the exit point and callingContext is not in
      // visitedMethods
      else if (currentCFG.getTails().contains(currentStmt)
              && !visitedMethods.contains(currentCxt)) {
        visitedMethods.add(currentCxt);
        createOrGetCFG(methodToCFG, currentCxt);
        Set<AndroidCallGraph.Edge> incomings = cg.getIncomingEdges(currentCxt);
        for (AndroidCallGraph.Edge e : incomings) {
          Stmt caller = e.callSite;
          if (visitedStmts.containsKey(caller)) {
            SootMethod callerCxt = visitedStmts.get(caller);
            //UnitGraph callerCFG = createOrGetCFG(methodToCFG, callerCxt);
            ExceptionalUnitGraph callerCFG = createOrGetECFG(methodToCFG, callerCxt);
            Collection<Unit> unException = Lists.newArrayList(callerCFG.getUnexceptionalSuccsOf(caller));
            for (Unit succ : unException) {
              Set<Stmt> tgts = infeasibleEdges.get(caller);
              if (tgts != null && tgts.contains(succ)) {
                continue;
              }
              propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) succ, callerCxt);
            }
            //Do propagation for exceptional successors
            if (true) {
              Collection<ExceptionalUnitGraph.ExceptionDest> exceptDest = Lists.newArrayList(callerCFG.getExceptionDests(caller));
              for (ExceptionalUnitGraph.ExceptionDest curDst : exceptDest) {
                ThrowableSet thSet = curDst.getThrowables();
                Unit exSucc = curDst.getHandlerNode();
                if (exSucc == null) {
                  continue;
                }
                Set<RefType> exceptionTypes = retriveExceptionSet(thSet);
                boolean ignore = true;
                for (RefType curRefType : exceptionTypes) {
                  if (bDebug)
                    Logger.verb(mtdTag, "For STMT:" + currentStmt + "Exception Types:" + curRefType);
                  if (!ExceptionTypes.v().isIgnoredException(curRefType)) {
                    ignore = false;
                  }
                }
                if (!ignore) {
                  propagateMod(visitedStmts, workingList, visitedSeq, (Stmt) exSucc, currentCxt);
                }
              }
            }
          }
        }
      }
    }
    return unexpected;
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

  private ExceptionalUnitGraph createOrGetECFG(
          Map<SootMethod, UnitGraph> methodToCFG,
          SootMethod mtd) {
    UnitGraph cfg = methodToCFG.get(mtd);
    if (cfg == null || !(cfg instanceof  ExceptionalUnitGraph)) {
      synchronized (mtd) {
        cfg = new ExceptionalUnitGraph(mtd.retrieveActiveBody());
      }
      methodToCFG.put(mtd, cfg);
    }
    return (ExceptionalUnitGraph)cfg;
  }



  private Set<RefType> retriveExceptionSet(ThrowableSet t){

    Field exceptionField = null;
    try {
      exceptionField = t.getClass().getDeclaredField("exceptionsIncluded");
    }
    catch (NoSuchFieldException e){
      return null;
    }

    exceptionField.setAccessible(true);
    Set ret = null;
    try {
      ret = (Set)exceptionField.get(t);
    }
    catch (IllegalAccessException e) {
      return null;
    }

    Set<RefType> retD = Sets.newHashSet();
    for (Object o :ret){
      if (o instanceof  RefType) {
        retD.add((RefType) o);
      }
    }
    return retD;
  }

  private boolean isLocalNotNull(Local l, SootMethod context) {
    int x = 0;
    final String mtdTag = "LOCNULL";
    QueryHelper queryHelper = QueryHelper.v();
    Set<NNode> backReachedNodes = queryHelper.allVariableValues(VarUtil.v().guiOutput.getFlowgraph().simpleNode(l));
    boolean bDebug = false;
    if (bDebug) {
      if (backReachedNodes == null || backReachedNodes.size() == 0) {
        Logger.verb(mtdTag, "For Local " + l.toString() + " Queryhelper return empty");
      } else {
        for (NNode curNode : backReachedNodes) {
          Logger.verb(mtdTag, "For Local " + l.toString() + " Queryhelper return " + curNode.toString());
        }
      }
    }
    for (NNode backReachedNode : backReachedNodes) {
      if (backReachedNode instanceof NObjectNode) {
        x++;
        NObjectNode resource = (NObjectNode) backReachedNode;
        return true;
      }
    }
    return false;
  }

  public static class ExceptionTypes{
    private static ExceptionTypes instance = null;

    private Set<RefType> ignoredTypes;

    private ExceptionTypes(){
      ignoredTypes = Sets.newHashSet();

      //Scene.v().loadNecessaryClasses();
      //Scene.v().loadClassAndSupport("java.lang.OutOfMemoryError");



      RefType igt = Scene.v().getSootClass("java.lang.OutOfMemoryError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.InternalError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.UnknownError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.StackOverflowError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.ThreadDeath").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.NoClassDefFoundError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.IncompatibleClassChangeError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.NoSuchFieldError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.IllegalAccessError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.ClassCircularityError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.LinkageError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.NullPointerException").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.InternalError").getType();
      ignoredTypes.add(igt);

      igt = Scene.v().getSootClass("java.lang.NoClassDefFoundError").getType();
      ignoredTypes.add(igt);
      igt = Scene.v().getSootClass("java.lang.VerifyError").getType();
      ignoredTypes.add(igt);
      igt = Scene.v().getSootClass("java.lang.ClassCastException").getType();
      ignoredTypes.add(igt);
      igt = Scene.v().getSootClass("java.lang.NegativeArraySizeException").getType();
      ignoredTypes.add(igt);
      igt = Scene.v().getSootClass("java.lang.IllegalMonitorStateException").getType();
      ignoredTypes.add(igt);
      igt = Scene.v().getSootClass("java.lang.ArrayStoreException").getType();
      ignoredTypes.add(igt);
      igt = Scene.v().getSootClass("java.lang.ArrayIndexOutOfBoundsException").getType();
      ignoredTypes.add(igt);

    }

    public static synchronized ExceptionTypes v(){
      if (instance == null) {
        instance = new ExceptionTypes();
      }
      return instance;
    }

    public boolean isIgnoredException(RefType t){
      if (this.ignoredTypes.contains(t)){
        return true;
      }

      //if (Scene.v().)
      Logger.verb("IGNTPY", "Exception Type: " + t + " ignored");
      return false;
    }



  }

}
