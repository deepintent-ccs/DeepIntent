/*
 * FlowgraphRebuilder.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.flowgraph;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import presto.android.Configs;
import presto.android.Hierarchy;
import presto.android.Logger;
import presto.android.Configs.AsyncOpStrategy;
import presto.android.gui.GraphUtil;
import presto.android.gui.Flowgraph;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.JimpleUtil;
import presto.android.gui.graph.NAllocNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NStringConstantNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.listener.ListenerSpecification;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph.Edge;
import presto.android.gui.wtg.intent.IntentField;
import presto.android.gui.wtg.util.WTGUtil;
import presto.android.gui.wtg.util.QueryHelper;
import soot.ArrayType;
import soot.Body;
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
import soot.jimple.ClassConstant;
import soot.jimple.DefinitionStmt;
import soot.jimple.Expr;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;
import soot.jimple.NullConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class FlowgraphRebuilder {
  // util
  private Hierarchy hier = Hierarchy.v();
  private WTGUtil wtgUtil = WTGUtil.v();
  private JimpleUtil jimpleUtil = JimpleUtil.v();
  private AndroidCallGraph callgraph = AndroidCallGraph.v();
  private QueryHelper queryHelper = QueryHelper.v();

  // keep record of class constants
  private final Map<SootClass, NClassConstantNode> allNClassConstantNodes = Maps.newHashMap();
  // flow graph
  private final Flowgraph flowgraph;
  // MenuItem.setIntent(Intent): stmt -> pair<varMenuItemNode, varIntentNode>
  private final Map<Stmt, Pair<NVarNode, NVarNode>> menuItemtoIntent = Maps.newHashMap();
  // NObjectNode -> Set<Runnable>
  private final Map<NObjectNode, Set<NAllocNode>> bindRunnable = Maps.newHashMap();
  // stmt -> Set<NObjectNode>
  private final Map<Stmt, Set<NObjectNode>> stmtToBindObject = Maps.newHashMap();
  // mapping: actual value, at stmt, flows to formal local
  private final Map<Local, Map<Local, Stmt>> actualParamtoFormalLocal = Maps.newHashMap();
  // mapping: container local -> stmt reading container element
  private final Map<Local, Set<Stmt>> varsAtContainerRead = Maps.newHashMap();
  // mapping: container local -> stmt writing container element
  private final Map<Local, Set<Stmt>> varsAtContainerWrite = Maps.newHashMap();
  // set of asynchonous operations stmts
  private final List<Pair<Stmt, SootMethod>> asyncStmts = Lists.newArrayList();
  // set of android.os.Handler initialization stmts
  private final List<Pair<Stmt, SootMethod>> handlerInitStmts = Lists.newArrayList();

  private FlowgraphRebuilder(GUIAnalysisOutput output) {
    flowgraph = output.getFlowgraph();
    build();
  }

  private void rebuildFlow() {
    for (SootClass appClz : hier.appClasses) {
      for (SootMethod method : appClz.getMethods()) {
        if (!method.isConcrete()) {
          continue;
        }
        if (wtgUtil.isIgnoredMethod(method)) {
          continue;
        }
        Body b = method.retrieveActiveBody();
        Iterator<Unit> unitItr = b.getUnits().snapshotIterator();
        while (unitItr.hasNext()) {
          Stmt s = (Stmt) unitItr.next();
          if (Configs.asyncStrategy == AsyncOpStrategy.Default_Special_Async
              || Configs.asyncStrategy == AsyncOpStrategy.All_Special_Async) {
            collectAsyncRelatedStmts(method, s);
          }
          if (s.containsInvokeExpr()) {
            SootMethod callee = s.getInvokeExpr().getMethod();
            if (!wtgUtil.isIgnoredMethod(callee)) {
              buildCallGraph(method, s);
            } else {
              // if we should ignore this method for some reasons, e.g., factory or wrapper, for they
              // could create impossible flows
              removeFlowAtCall(s, callee);
            }
          }
          if (processStmt(s)) {
            continue;
          }
          // try to find the flow to another method
          if (s.containsInvokeExpr()) {
            InvokeExpr ie = s.getInvokeExpr();
            // SootMethod stm = ie.getMethod();
            Set<Edge> callees = callgraph.getEdge(s);  
            // flow graph edges at non-virtual calls
            if (ie instanceof StaticInvokeExpr
                || ie instanceof SpecialInvokeExpr) {
              for (Edge callEdge : callees) {
                SootMethod callee = callEdge.target;
                if (callee.getDeclaringClass().isApplicationClass()) {
                  processFlowAtCall(s, callee);
                }
              }
              continue;
            }
            // flow graph edges at virtual calls
            Local rcv_var = jimpleUtil.receiver(ie);
            Type rcv_t = rcv_var.getType();
            // could be ArrayType, for clone() calls
            if (!(rcv_t instanceof RefType)) {
              continue;
            }
            // handle simple container with subclasses of java.util.List and java.util.Map
            recordReadWriteContainer(s, method);
            // handle flow through calls
            for (Edge callee : callees) {
              SootMethod trg = callee.target;
              if (trg != null && trg.getDeclaringClass().isApplicationClass()) {
                processFlowAtCall(s, trg);
              }
            }
            continue;
          } // the statement was a call
          if (!(s instanceof DefinitionStmt)) {
            continue;
          }
          DefinitionStmt ds = (DefinitionStmt) s;
          Value lhs = ds.getLeftOp();
          Value rhs = ds.getRightOp();
          if (rhs instanceof ClassConstant) {
            NNode nn_lhs = simpleNode(lhs), nn_rhs = simpleNode(rhs);
            if (nn_lhs != null && nn_rhs != null) {
              nn_rhs.addEdgeTo(nn_lhs, s);
            }
          }
        }
      }
    }
  }

 private void recordReadWriteContainer(Stmt s, SootMethod callingMethod) {
    if (s == null || !s.containsInvokeExpr()) {
      return;
    }
    InvokeExpr ie = s.getInvokeExpr();
    if (!(ie instanceof InstanceInvokeExpr)) {
      return;
    }
    Local rcv = jimpleUtil.receiver(ie);
    if (rcv == null) {
      return;
    }
    Type type = rcv.getType();
    if (!(type instanceof RefType)) {
      return;
    }
    if (wtgUtil.isWriteContainerCall(s)) {
      recordVarAtContainerWrite(rcv, s);
    }
    if (wtgUtil.isReadContainerCall(s)) {
      recordVarAtContainerRead(rcv, s);
    }
  }

  private void processFlowAtCall(Stmt caller, SootMethod callee) {
    // Check & filter
    InvokeExpr ie = caller.getInvokeExpr();
    if (wtgUtil.isIgnoredMethod(callee)) {
      return;
    }
    if (!callee.getDeclaringClass().isApplicationClass()) {
      Logger.err(getClass().getSimpleName(), "callee should be within application to build the flow from actual to formal param");
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
        Logger.verb(getClass().getSimpleName(), "the first N stmts should pass parameter. Stmt: "
            + s + "\nMethod: " + callee.retrieveActiveBody());
        continue;
      }
      Local formal = jimpleUtil.lhsLocal(s);
      if (actual instanceof ClassConstant) {
        NNode rhsNode = simpleNode(actual);
        NNode lhsNode = simpleNode(formal);
        if (rhsNode != null && lhsNode != null) {
          rhsNode.addEdgeTo(lhsNode);
        }
      }
      // record the mapping from formal local to actual local
      if (formal != null && actual != null && actual instanceof Local) {
        Map<Local, Stmt> formals = actualParamtoFormalLocal.get(actual);
        if (formals == null) {
          formals = Maps.newHashMap();
          actualParamtoFormalLocal.put((Local) actual, formals);
        }
        formals.put(formal, caller);
      }
    }
  }

  private void postBuildFlow() {
    // link the flow through Runnable.run()
    buildFlowThroughRunnable();
    // link the flow through container reads and writes
    buildFlowThroughContainer();
  }

 private void buildFlowThroughRunnable() {
    for (Map.Entry<Stmt, Set<NObjectNode>> stmtToThreadEntry : stmtToBindObject.entrySet()) {
      Stmt s = stmtToThreadEntry.getKey();
      SootMethod caller = jimpleUtil.lookup(s);
      Set<NObjectNode> bindNodes = stmtToThreadEntry.getValue();
      if (bindNodes == null || bindNodes.isEmpty()) {
        Logger.verb(getClass().getSimpleName(), "can not find thread node for stmt: " + s);
        continue;
      }
      for (NObjectNode bindNode : bindNodes) {
        Set<NAllocNode> runnableNodes = bindRunnable.get(bindNode);
        if (runnableNodes == null) {
          // it is possible that void run() is defined in Thread
          SootClass sc = bindNode.getClassType();
          if (sc != null && sc.isConcrete()) {
            SootMethod tgt = hier.virtualDispatch(wtgUtil.runnableRunMethodSubSig, sc);
            if (tgt != null) {
              callgraph.add(caller, tgt, s);
            }
          }
          continue;
        }
        for (NAllocNode runnable : runnableNodes) {
          SootClass runnableClass = runnable.getClassType();
          if (runnableClass != null && runnableClass.isConcrete()) {
            SootMethod runnableRunMethod = hier.virtualDispatch(
                wtgUtil.runnableRunMethodSubSig, runnableClass);
            if (runnableRunMethod == null) {
              Logger.err(getClass().getSimpleName(), "Runnable class: "
                  + runnableClass + " does not declare "
                  + wtgUtil.runnableRunMethodSubSig);
            }
            callgraph.add(caller, runnableRunMethod, s);
          }
        }
      }
    }
  }

 private void buildFlowThroughContainer() {
    GraphUtil graphUtil = GraphUtil.v();
    for (Expr e : flowgraph.allNAllocNodes.keySet()) {
      if (!(e.getType() instanceof RefType)) {
        continue;
      }
      Set<Stmt> writes = Sets.newHashSet();
      Set<Stmt> reads = Sets.newHashSet();
      Set<NNode> reachedContainers = graphUtil.reachableNodes(flowgraph.allNAllocNodes
          .get(e));
      for (NNode reachedContainer : reachedContainers) {
        if (!(reachedContainer instanceof NVarNode)) {
          continue;
        }
        NVarNode varNode = (NVarNode) reachedContainer;
        if (varsAtContainerRead.containsKey(varNode.l)) {
          reads.addAll(varsAtContainerRead.get(varNode.l));
        }
        if (varsAtContainerWrite.containsKey(varNode.l)) {
          writes.addAll(varsAtContainerWrite.get(varNode.l));
        }
      }
      for (Stmt src : writes) {
        Integer srcPos = wtgUtil.getWriteContainerField(src);
        if (srcPos == null) {
          Logger.verb(getClass().getSimpleName(), "the target of write container stmt can not be found: " + src);
          continue;
        }
        NNode sn = null;
        if (srcPos.intValue() < 0) {
          sn = varNode(jimpleUtil.lhsLocal(src));
        } else {
          sn = simpleNode(src.getInvokeExpr().getArg(srcPos.intValue()-1));
        }
        if (sn == null) {
          continue;
        }
        for (Stmt tgt : reads) {
          Integer tgtPos = wtgUtil.getReadContainerField(tgt);
          if (tgtPos == null) {
            Logger.verb(getClass().getSimpleName(), "the target of read container stmt can not be found: " + tgt);
            continue;
          }
          NNode tn = null;
          if (tgtPos.intValue() < 0) {
            if (tgt instanceof DefinitionStmt) {
              tn = varNode(jimpleUtil.lhsLocal(tgt));
            }
          } else {
            tn = simpleNode(tgt.getInvokeExpr().getArg(tgtPos.intValue()-1));
          }
          if (tn == null) {
            continue;
          }
          sn.addEdgeTo(tn);
        }
      }
    }
  }

  private void recordVarAtContainerRead(Local rcv, Stmt s) {
    Set<Stmt> stmts = varsAtContainerRead.get(rcv);
    if (stmts == null) {
      stmts = Sets.newHashSet();
      varsAtContainerRead.put(rcv, stmts);
    }
    stmts.add(s);
  }

  private void recordVarAtContainerWrite(Local rcv, Stmt s) {
    Set<Stmt> stmts = varsAtContainerWrite.get(rcv);
    if (stmts == null) {
      stmts = Sets.newHashSet();
      varsAtContainerWrite.put(rcv, stmts);
    }
    stmts.add(s);
  }

  private boolean processStmt(Stmt s) {
    boolean success = createPropagation(s);
    if (success) {
      return true;
    }
    NOpNode opNode = createOpNode(s);
    if (opNode != null) {
      flowgraph.allNNodes.add(opNode);
      return true;
    }
    modelUntrackedValue(s);
    return false;
  }

  private NOpNode createOpNode(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    // set def in intents.xml
    {
      NOpNode startActivity = createStartActivityOpNode(s);
      if (startActivity != null) {
        return startActivity;
      }
    }

    {
      NOpNode setIntentContent = createSetIntentContentOpNode(s);
      if (setIntentContent != null) {
        return setIntentContent;
      }
    }

    {
      NOpNode createIntent = createCreateIntentOpNode(s);
      if (createIntent != null) {
        return createIntent;
      }
    }
    {
      NOpNode getIntent = createGetIntentOpNode(s);
      if (getIntent != null) {
        return getIntent;
      }
    }
    // MenuItem.setIntent(Intent)
    // we don't create really opnode for this stmt
    // instead, we just remember their mapping
    {
      NOpNode menuItemSetIntent = createMenuItemSetIntentOpNode(s);
      if (menuItemSetIntent != null) {
        return menuItemSetIntent;
      }
    }
    // object.getClass()
    {
      NOpNode getClass = createGetClassOpNode(s);
      if (getClass != null) {
        return getClass;
      }
    }
    /*
    {
      NOpNode defineIntentContent = createDefineIntentContentOpNode(s);
      if (defineIntentContent != null) {
        return defineIntentContent;
      }
    }
    */
    return null;
  }

  private boolean createPropagation(Stmt s) {
    if (!(s instanceof DefinitionStmt) || !s.containsInvokeExpr()) {
      return false;
    }
    InvokeExpr ie = s.getInvokeExpr();
    SootMethod callee = ie.getMethod();
    Map<String, Integer> fields = null;
    if (wtgUtil.isIntentPropagationCall(s)) {
      fields = wtgUtil.getIntentPropagationFields(s);
    } else if (wtgUtil.isValuePropagationCall(s)) {
      fields = wtgUtil.getValuePropagationFields(s);
    } else {
      return false;
    }
    Integer src = fields.get("srcPosition");
    Integer tgt = fields.get("tgtPosition");
    if (src == null || tgt == null) {
      if (src == null) {
        Logger.err(getClass().getSimpleName(), "you have not specified the source for propagation call to " + callee);
      }
      if (tgt == null) {
        Logger.err(getClass().getSimpleName(), "you have not specified the target for propagation call to " + callee);
      }
    }
    NNode srcNode = null, tgtNode = null;
    if (src == 0) {
      Local rcvLocal = jimpleUtil.receiver(ie);
      srcNode = simpleNode(rcvLocal);
    } else if (src > 0) {
      Value arg = ie.getArg(src-1);
      srcNode = simpleNode(arg);
    } else {
      Logger.err(getClass().getSimpleName(), "the source intent idx should not be less than 0 for propagation call to " + callee);
    }
    if (tgt == -1) {
      Local lhsLocal = jimpleUtil.lhsLocal(s);
      tgtNode = simpleNode(lhsLocal);
    } else {
      Logger.err(getClass().getSimpleName(), "the tgt intent idx should be -1 for propagation call to " + callee);
    }
    srcNode.addEdgeTo(tgtNode);
    return true;
  }

  private NOpNode createStartActivityOpNode(Stmt s) {
    InvokeExpr ie = s.getInvokeExpr();
    if (!wtgUtil.isStartActivityCall(s)) {
      return null;
    }
    Pair<Integer, Integer> idxPair = wtgUtil.getStartActivityIntentField(s);
    int intentIdx = idxPair.getO2();
    int cxtIdx = idxPair.getO1();
    Value cxtLocal = null;
    if (cxtIdx != 0) {
      cxtLocal = ie.getArg(cxtIdx-1);
    } else {
      cxtLocal = jimpleUtil.receiver(ie);
    }
    Value arg = ie.getArg(intentIdx-1);
    if (arg instanceof NullConstant) {
      return null;
    }
    if (!(arg instanceof Local)) {
      return null;
    }
    Local parameterLocal = (Local) arg;
    Type paraType = parameterLocal.getType();
    if (paraType instanceof ArrayType) {
      paraType = ((ArrayType)paraType).getElementType();
    }
    SootClass baseClass = null;
    if (paraType instanceof RefType) {
      baseClass = ((RefType)paraType).getSootClass();
    } else {
      Logger.err(getClass().getSimpleName(), "the type of " + intentIdx + "'th arg is not RefType for stmt: " + s);
    }
    boolean isIntent = wtgUtil.isIntentType(baseClass);
    if (!isIntent) {
      Logger.err(getClass().getSimpleName(), "the type of " + intentIdx + "'th arg is not Intent for stmt: " + s);
    }
    NNode activityNode = simpleNode(cxtLocal);
    NNode intentNode = varNode(parameterLocal);
    Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s));
    NOpNode startActivity = new NStartActivityOpNode(activityNode, intentNode, callSite);
    return startActivity;
  }

  private NOpNode createSetIntentContentOpNode(Stmt s) {
    InvokeExpr ie = s.getInvokeExpr();
    Local receiverLocal = (ie instanceof InstanceInvokeExpr) ? jimpleUtil.receiver(ie) : null;
    if (receiverLocal == null) {
      return null;
    }
    Type rcvType = receiverLocal.getType();
    if (!(rcvType instanceof RefType)) {
      return null;
    }
    boolean isSetIntentCall= wtgUtil.isSetIntentContentCall(s);
    if (!isSetIntentCall) {
      return null;
    }
    NNode lhsNode = (s instanceof DefinitionStmt) ? varNode(jimpleUtil.lhsLocal(s)) : null;
    Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s));
    NNode rcvNode = ( receiverLocal == null ) ? null : varNode(receiverLocal);
    NSetIntentContentOpNode setIntentContentNode = new NSetIntentContentOpNode(lhsNode, rcvNode, callSite);
    Set<Pair<Integer, IntentField>> fields = wtgUtil.getSetIntentContentFields(s);
    for (Pair<Integer, IntentField> field : fields) {
      int idx = field.getO1();
      IntentField content = field.getO2();
      Value arg = ie.getArg(idx-1);
      NNode argNode = simpleNode(arg);
      if (argNode == null) {
        if (!(arg instanceof NullConstant)) {
          Logger.verb(getClass().getSimpleName(), "we can not find the NNode for the " + idx + "'th arg for stmt: " + s);
        }
        continue;
      }
      if (content != IntentField.Uri) {
        argNode.addEdgeTo(setIntentContentNode);
        setIntentContentNode.addContent(argNode, content);
      } else {
        NAnyValueNode anyValueNode = NAnyValueNode.ANY;
        anyValueNode.addEdgeTo(setIntentContentNode);
        for (IntentField myContent : IntentField.values()) {
          if (myContent.isPartOfUriField()) {
            setIntentContentNode.addContent(anyValueNode, myContent);
          }
        }
      }
    }
    return setIntentContentNode;
  }

  // let's try to hack MyTracks IntentUtils.newIntent(Context,Class)
  private NOpNode createCreateIntentOpNode(Stmt s) {
    InvokeExpr ie = s.getInvokeExpr();
    boolean isCreateIntentCall= wtgUtil.isCreateIntentCall(s);
    if (!isCreateIntentCall) {
      return null;
    }
    NNode lhsNode = (s instanceof DefinitionStmt) ? varNode(jimpleUtil.lhsLocal(s)) : null;
    Pair<Stmt, SootMethod> callSite = new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s));
    NNode intentAllocNode = flowgraph.allocNode(ie);
    Local rcvLocal = Jimple.v().newLocal("rcvLocal", RefType.v(wtgUtil.intentClass));
    NNode rcvNode = simpleNode(rcvLocal);
    intentAllocNode.addEdgeTo(rcvNode);
    NSetIntentContentOpNode setIntentContentNode = new NSetIntentContentOpNode(lhsNode, rcvNode, callSite);
    Set<Pair<Integer, IntentField>> fields = wtgUtil.getCreateIntentFields(s);
    for (Pair<Integer, IntentField> field : fields) {
      int idx = field.getO1();
      IntentField content = field.getO2();
      Value arg = ie.getArg(idx-1);
      NNode argNode = simpleNode(arg);
      if (argNode == null) {
        if (!(arg instanceof NullConstant)) {
          Logger.verb(getClass().getSimpleName(), "we can not find the NNode for the " + idx + "'th arg for stmt: " + s);
        }
        continue;
      }
      argNode.addEdgeTo(setIntentContentNode);
      setIntentContentNode.addContent(argNode, content);
    }
    return setIntentContentNode;
  }

  private void removeFlowAtCall(Stmt caller, SootMethod callee) {
    // Check & filter
    InvokeExpr ie = caller.getInvokeExpr();
    if (!callee.getDeclaringClass().isApplicationClass()) {
      Logger.err(getClass().getSimpleName(), "");
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
      Local formal = jimpleUtil.lhsLocal(s);
      if (!jimpleUtil.interesting(formal.getType())) {
        continue;
      }
      NVarNode lhsNode = varNode(formal);
      NNode rhsNode = simpleNode(actual);
      if (rhsNode != null) {
        rhsNode.removeEdgeTo(lhsNode);
      }
    }
    // Now, do something for the return
    if (caller instanceof InvokeStmt) {
      return; // no ret val
    }
    Local lhs_at_call = jimpleUtil.lhsLocal(caller);
    if (!jimpleUtil.interesting(lhs_at_call.getType())) {
      return;
    }
    NNode lhsNode = varNode(lhs_at_call);
    while (stmts.hasNext()) {
      Stmt d = (Stmt) stmts.next();
      if (!(d instanceof ReturnStmt)) {
        continue;
      }
      Value retval = ((ReturnStmt) d).getOp();
      NNode returnValueNode = simpleNode(retval);
      if (returnValueNode != null) {
        returnValueNode.removeEdgeTo(lhsNode);
      }
    }
  }
  private NOpNode createGetIntentOpNode(Stmt s) {
    boolean is= wtgUtil.isGetIntentCall(s);
    if (!is) {
      return null;
    }
    if (!(s instanceof DefinitionStmt)) {
      return null;
    }
    Local lhs = jimpleUtil.lhsLocal(s);
    Local rcvLocal = jimpleUtil.receiver(s);
    NNode lhsNode = simpleNode(lhs);
    Type rcvType = rcvLocal.getType();
    SootClass rcvClz = null;
    if (rcvType instanceof RefType) {
      rcvClz = ((RefType) rcvType).getSootClass();
    }
    if (rcvClz == null) {
      return null;
    }
    NNode rcvNode = simpleNode(rcvLocal);
    Pair<Stmt, SootMethod> callsite = new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s));
    NOpNode getIntent = new NGetIntentOpNode(lhsNode, rcvNode, callsite);
    // presume that we can not resolve the intent, which will be undo in IntentAnalysis.rebuildPropagation
    NAnyValueNode.ANY.addEdgeTo(lhsNode);
    return getIntent;
  }

  private NOpNode createMenuItemSetIntentOpNode(Stmt s) {
    InvokeExpr ie = s.getInvokeExpr();
    boolean isSetIntentCall = wtgUtil.isMenuItemSetIntentCall(s);
    if (!isSetIntentCall) {
      return null;
    }
    Pair<Integer, Integer> itemAndIntentPos = wtgUtil.getMenuItemSetIntentField(s);
    NVarNode menuItemNode = null, intentNode = null;
    int itemPos = itemAndIntentPos.getO1();
    if (itemPos != 0) {
      Logger.err(getClass().getSimpleName(), "menu item is supposed to be receiver for stmt: " + s);
    }
    menuItemNode = varNode(jimpleUtil.receiver(s));
    int intentPos = itemAndIntentPos.getO2();
    if (intentPos <= 0) {
      Logger.err(getClass().getSimpleName(), "intent item is supposed not to be receiver/lhs for stmt: " + s);
    }
    intentNode = varNode((Local)ie.getArg(intentPos-1));
    Pair<NVarNode, NVarNode> pair = menuItemtoIntent.get(s);
    if (pair == null) {
      pair = new Pair<NVarNode, NVarNode>(menuItemNode, intentNode);
      menuItemtoIntent.put(s, pair);
    }
    if (s instanceof DefinitionStmt) {
      Value lhsValue = jimpleUtil.lhs(s);
      NNode lhsNode = simpleNode(lhsValue);
      menuItemNode.addEdgeTo(lhsNode);
    }
    return null;
  }
  private NOpNode createGetClassOpNode(Stmt s) {
    if (!(s instanceof DefinitionStmt)) {
      return null;
    }
    InvokeExpr ie = s.getInvokeExpr();
    boolean isGetClassCall = wtgUtil.isGetClassCall(s);
    if (!isGetClassCall) {
      return null;
    }
    Integer srcPos = wtgUtil.getGetClassField(s);
    if (srcPos == null) {
      return null;
    }
    Value srcLocal = null;
    if (srcPos < 0) {
      Logger.err(getClass().getSimpleName(), "can not find the src for get class opnode at stmt: " + s);
    } else if (srcPos == 0) {
      srcLocal = jimpleUtil.receiver(ie);
    } else {
      srcLocal = ie.getArg(srcPos-1);
    }
    NNode srcNode = simpleNode(srcLocal);
    Pair<Stmt, SootMethod> callsite = new Pair<Stmt, SootMethod>(s, jimpleUtil.lookup(s));
    Local lhsLocal = jimpleUtil.lhsLocal(s);
    NNode lhsNode = simpleNode(lhsLocal);
    NOpNode getClass = new NGetClassOpNode(lhsNode, srcNode, callsite);
    return getClass;
  }

  private NVarNode varNode(Local l) {
    return flowgraph.varNode(l);
  }

  private NNode simpleNode(Value x) {
    if (x instanceof FieldRef) {
      return flowgraph.fieldNode(((FieldRef) x).getField());
    }
    if (x instanceof Local) {
      return flowgraph.varNode((Local) x);
    }
    if (x instanceof ClassConstant) {
      String clsName = null;
      clsName = ((ClassConstant)x).value.replace('/', '.');
      if (clsName.charAt(0) == '[') {
        // if it is constant array class, we ignore it
        // e.g., in DaoReflectionHelpers of astrid,
        // we got stmt like return getStaticFieldByReflection(model, Property[].class, "PROPERTIES");
        return null;
      }
      SootClass sc = Scene.v().getSootClass(clsName);
      NClassConstantNode clsConstNode = classConstNode(sc);
      return clsConstNode;
    }
    if (x instanceof StringConstant) {
      String value = ((StringConstant) x).value;
      NStringConstantNode node = flowgraph.allNStringConstantNodes.get(value);
      if (node == null) {
        node = flowgraph.stringConstantNode(value);
        if (node == null) {
          Logger.err(getClass().getSimpleName(), "the string constant node can not be created");
        }
      }
      return node;
    }
    if (x instanceof NewExpr) {
      return allocNode((Expr) x);
    }
    if (x instanceof CastExpr) {
      return simpleNode(((CastExpr) x).getOp());
    }
    return null;
  }

  private NClassConstantNode classConstNode(SootClass cls) {
    NClassConstantNode x = this.allNClassConstantNodes.get(cls);
    if (x != null) {
      return x;
    }
    x = new NClassConstantNode(cls);
    this.allNClassConstantNodes.put(cls, x);
    flowgraph.allNNodes.add(x);
    return x;
  }

  private NAllocNode allocNode(Expr e) {
    NAllocNode x = flowgraph.allNAllocNodes.get(e);
    if (x != null) {
      return x;
    }
    SootClass c = ((NewExpr) e).getBaseType().getSootClass();
    // only care about new Intent, the rest will be handled by Flowgraph itself
    if (wtgUtil.isIntentType(c)) {
      Logger.err(getClass().getSimpleName(), "we find NIntentAllocNode !");
    }
    return x;
  }
  // remember the pair of <NObjectNode, runnable>
  private void handleBindImplicitMethodCall(Stmt s) {
    InvokeExpr ie = s.getInvokeExpr();
    SootClass runnableClz = wtgUtil.runnableClass;
    SootClass bindToObjectClass = wtgUtil.bindObjectType(ie.getMethod());
    List<Value> args = ie.getArgs();
    Value runnableArg = null;
    for (int i = 0; i < args.size(); i++) {
      Value arg = args.get(i);
      Type argT = arg.getType();
      if (argT instanceof RefType) {
        SootClass argClz = ((RefType) argT).getSootClass();
        if (hier.isSubclassOf(argClz, runnableClz)) {
          runnableArg = arg;
          break;
        }
      }
    }
    if (runnableArg == null) {
      Logger.err(getClass().getSimpleName(), "can not find Runnable arg for stmt: " + s);
    }
    Local rcvLocal = jimpleUtil.receiver(ie);
    NVarNode rcvNode = varNode(rcvLocal);
    Set<NNode> rcvBackReachedNodes = queryHelper.allVariableValues(rcvNode);
    NNode argNode = simpleNode(runnableArg);
    Set<NNode> argBackReachedNodes = queryHelper.allVariableValues(argNode);
    for (NNode rcvBackReachedNode : rcvBackReachedNodes) {
      if (!(rcvBackReachedNode instanceof NObjectNode)) {
        continue;
      }
      SootClass rcvClass = ((NObjectNode) rcvBackReachedNode).getClassType();
      if (!hier.isSubclassOf(rcvClass, bindToObjectClass)) {
        continue;
      }
      Set<NAllocNode> runnableNodes = bindRunnable.get(rcvBackReachedNode);
      if (runnableNodes == null) {
        runnableNodes = Sets.newHashSet();
        bindRunnable.put((NObjectNode)rcvBackReachedNode, runnableNodes);
      }
      for (NNode argBackReachedNode : argBackReachedNodes) {
        if (!(argBackReachedNode instanceof NAllocNode)) {
          continue;
        }
        SootClass argClass = ((NAllocNode) argBackReachedNode).getClassType();
        if (!hier.isSubclassOf(argClass, runnableClz)) {
          continue;
        }
        runnableNodes.add((NAllocNode)argBackReachedNode);
      }
    }
  }
  private void handleRunBindImplicitMethodCall(Stmt s) {
    Set<NObjectNode> bindNodes = stmtToBindObject.get(s);
    if (bindNodes == null) {
      bindNodes = Sets.newHashSet();
      stmtToBindObject.put(s, bindNodes);
    }
    InvokeExpr ie = s.getInvokeExpr();
    SootClass bindToObjectClass = wtgUtil.bindObjectType(ie.getMethod());
    Local rcvLocal = jimpleUtil.receiver(ie);
    NVarNode rcvNode = varNode(rcvLocal);
    Set<NNode> rcvBackReachedNodes = queryHelper.allVariableValues(rcvNode);
    for (NNode rcvBackReachedNode : rcvBackReachedNodes) {
      if (!(rcvBackReachedNode instanceof NObjectNode)) {
        continue;
      }
      SootClass rcvClass = ((NObjectNode) rcvBackReachedNode).getClassType();
      if (!hier.isSubclassOf(rcvClass, bindToObjectClass)) {
        continue;
      }
      bindNodes.add((NObjectNode)rcvBackReachedNode);
    }
  }
  private void handleExecImplicitMethodCall(Stmt s) {
    SootMethod caller = jimpleUtil.lookup(s);
    InvokeExpr ie = s.getInvokeExpr();
    SootClass runnableClz = wtgUtil.runnableClass;
    Value runnableArg = ie.getArg(wtgUtil.getAsyncMethodCallRunnableArg(s));
    if (runnableArg == null) {
      Logger.err(getClass().getSimpleName(), "can not find Runnable arg for stmt: " + s);
    }
    NNode argNode = simpleNode(runnableArg);
    Set<NNode> argBackReachedNodes = queryHelper.allVariableValues(argNode);
    for (NNode argBackReachedNode : argBackReachedNodes) {
      if (!(argBackReachedNode instanceof NAllocNode)) {
        continue;
      }
      SootClass argClass = ((NAllocNode) argBackReachedNode).getClassType();
      if (!hier.isSubclassOf(argClass, runnableClz)) {
        continue;
      }
      SootMethod callee = hier.virtualDispatch(wtgUtil.runnableRunMethodSubSig, argClass);
      if (callee == null) {
        Logger.err(getClass().getSimpleName(), "runnable class: " + argClass + " hasn't declared method: " + wtgUtil.runnableRunMethodSubSig);
      }
      callgraph.add(caller, callee, s);
    }
  }

  // reuse Tony's code to build callgraph for wtg analysis
  // modify the way callee is resolved, and add special handling for Thread.start and AsyncTask
  private void buildCallGraph(SootMethod source, Stmt s) {
    InvokeExpr ie = s.getInvokeExpr();
    SootMethod callee = ie.getMethod();
    // check for special invocations
    // if it is binding stmt, e.g., new Thread(Runnable)
    if (wtgUtil.isBindImplicitMethodCall(s)) {
      handleBindImplicitMethodCall(s);
      return;
    }
    // if it is binder running stmt, e.g., Thread.start()
    if (wtgUtil.isRunBindImplicitMethodCall(s)) {
      handleRunBindImplicitMethodCall(s);
      return;
    }
    // if it is directly running stmt, e.g., Activity.runOnUiThread(Runnable), View.post(Runnable)
    if (wtgUtil.isAsyncMethodCall(s)) {
      if (Configs.asyncStrategy == AsyncOpStrategy.Default_EventHandler_Async
          || Configs.asyncStrategy == AsyncOpStrategy.All_EventHandler_Async) {
        // if config not to handle async operation specially, call graph is not built
        handleExecImplicitMethodCall(s);
      }
      return;
    }
    if (ie instanceof StaticInvokeExpr
        || ie instanceof SpecialInvokeExpr) {
      callgraph.add(source, callee, s);
      return;
    }
    // flow graph edges at virtual calls
    Local rcv_var = jimpleUtil.receiver(ie);
    Type rcv_t = rcv_var.getType();
    // could be ArrayType, for clone() calls
    if (!(rcv_t instanceof RefType)) {
      return;
    }
    // the first step: try to resolve the callee based on the flow graph we built
    NVarNode rcvNode = flowgraph.lookupVarNode(rcv_var);
    Set<NNode> backReachedNodes = Sets.newHashSet();
    if (rcvNode != null) {
      backReachedNodes = queryHelper.allVariableValues(rcvNode);
    }
    boolean resolvePointsTo = false;
    for (NNode backReachedNode : backReachedNodes) {
      if (!(backReachedNode instanceof NObjectNode)) {
        continue;
      }
      SootClass sc = ((NObjectNode)backReachedNode).getClassType();
      if (sc != null && sc.isConcrete()) {
        SootMethod tgt = (sc == null) ? null : hier.virtualDispatch(callee, sc);
        if (tgt != null) {
          resolvePointsTo = true;
          callgraph.add(source, tgt, s);
        }
      }
    }
    if (resolvePointsTo) {
      // if we can find the points-to set solution
      return;
    }
    SootClass clz = ((RefType)rcv_var.getType()).getSootClass();
    if (hier.applicationActivityClasses.contains(clz)
        || hier.libActivityClasses.contains(clz)
        || hier.isGUIClass(clz)
        || ListenerSpecification.v().isListenerType(clz)) {
      // check it should have points-to set
      return;
    }
    // second step: try conservative way to resolve the callee
    SootClass stc = ((RefType) rcv_t).getSootClass();
    for (Iterator<SootClass> tgtItr = hier.getConcreteSubtypes(stc).iterator(); tgtItr.hasNext();) {
      SootClass sub = (SootClass) tgtItr.next();
      if (sub != null && sub.isConcrete()) {
        SootMethod tgt = hier.virtualDispatch(callee, sub);
        if (tgt != null) {
          callgraph.add(source, tgt, s);
        }
      }
    }
  }

  private void modelUntrackedValue(Stmt s) {
    if (s.containsInvokeExpr()) {
      InvokeExpr ie = s.getInvokeExpr();
      if (ie instanceof InstanceInvokeExpr) {
        // for now, we choose to hack Intent.setComponent(ComponentName)
        Local rcvLocal = jimpleUtil.receiver(ie);
        SootMethod mtd = ie.getMethod();
        Type rcvType = rcvLocal.getType();
        if (rcvType instanceof RefType) {
          boolean isIntent = wtgUtil.isIntentType(((RefType) rcvType).getSootClass());
          String mtdName = mtd.getSubSignature();
          if (isIntent && mtdName.equals("android.content.Intent setComponent(android.content.ComponentName)")) {
            NNode cmpNameNode = simpleNode(ie.getArg(0));
            if (cmpNameNode != null) {
              NNode anyValueNode = NAnyValueNode.ANY;
              anyValueNode.addEdgeTo(cmpNameNode);
            }
          }
        }
      }
    }
    if (!(s instanceof DefinitionStmt)) {
      return;
    }
    Value lhs = ((DefinitionStmt)s).getLeftOp();
    if (!(lhs instanceof Local)) {
      return;
    }
    Local lhsLocal = (Local)lhs;
    Type lhsType = lhsLocal.getType();
    if (!(lhsType instanceof RefType)) {
      return;
    }
    SootClass lhsClass = ((RefType)lhsType).getSootClass();
    if (!lhsClass.getName().equals("java.lang.String")
        && !lhsClass.getName().equals("java.lang.Class")
        && !wtgUtil.isIntentType(lhsClass)) {
      return;
    }
    NNode lhsNode = simpleNode(lhsLocal);
    Value rhs = ((DefinitionStmt)s).getRightOp();
    if (!(rhs instanceof InvokeExpr) && !(rhs instanceof CastExpr)) {
      return;
    }
    if (rhs instanceof CastExpr) {
      NAnyValueNode.ANY.addEdgeTo(lhsNode);
    } else if (rhs instanceof InstanceInvokeExpr) {
      SootMethod mtd = ((InstanceInvokeExpr) rhs).getMethod();
      Local rcvLocal = jimpleUtil.receiver(s);
      Type rcvType = rcvLocal.getType();
      if (rcvType instanceof RefType) {
        SootClass rcvClass = ((RefType) rcvType).getSootClass();
        for (SootClass subClass : hier.getSubtypes(rcvClass)) {
          if (!subClass.declaresMethod(mtd.getSubSignature())) {
            continue;
          }
          if (!subClass.isApplicationClass()) {
            NAnyValueNode.ANY.addEdgeTo(lhsNode);
            break;
          }
        }
      }
    } else {
      SootMethod mtd = ((InvokeExpr) rhs).getMethod();
      SootClass declaringClz = mtd.getDeclaringClass();
      if (!declaringClz.isApplicationClass()) {
        NAnyValueNode.ANY.addEdgeTo(lhsNode);
      }
    }
  }

  private void build() {
    rebuildFlow();
    postBuildFlow();
  }
  
  private void collectAsyncRelatedStmts(SootMethod currentMethod, Stmt s) {
    /**
     * Here are a list of APIs interested
     * 1. Activity.runOnUiThread(Runnable action)
     * 2. View.post(Runnable action)
     * 3. View.postDelayed(Runnable action, long delayMillis)
     * 4. AsyncTask.execute(...)
     */
    if (wtgUtil.isAsyncMethodCall(s)) {
      asyncStmts.add(new Pair<Stmt, SootMethod>(s, currentMethod));
    } else if (s instanceof AssignStmt) {
      Value rhs = ((AssignStmt) s).getRightOp();
      if (rhs instanceof NewExpr) {
        RefType newType = ((NewExpr) rhs).getBaseType();
        if (hier.isSubclassOf(newType.getSootClass(), wtgUtil.handlerClass)) {
          handlerInitStmts.add(new Pair<Stmt, SootMethod>(s, currentMethod));
        }
      }
    }
  }
  
  public List<Pair<Stmt, SootMethod>> getAsyncStmts() {
    return asyncStmts;
  }
  
  public List<Pair<Stmt, SootMethod>> getHandlerInitStmts() {
    return handlerInitStmts;
  }

  public Pair<NVarNode, NVarNode> getMenuItemAndTargetAt(Stmt s) {
    return menuItemtoIntent.get(s);
  }

  public NClassConstantNode lookupClassConstantNode(SootClass sc) {
    return this.allNClassConstantNodes.get(sc);
  }

  public NNode lookupNode(Value x) {
    if (x instanceof FieldRef) {
      return flowgraph.lookupFieldNode(((FieldRef)x).getField());
    }
    if (x instanceof Local) {
      return flowgraph.lookupVarNode((Local)x);
    }
    if (x instanceof ClassConstant) {
      String clsName = null;
      clsName = ((ClassConstant)x).value.replace('/', '.');
      if (clsName.charAt(0) == '[') {
        // if it is constant array class, we ignore it
        // e.g., in DaoReflectionHelpers of astrid,
        // we got stmt like return getStaticFieldByReflection(model, Property[].class, "PROPERTIES");
        return null;
      }
      SootClass sc = Scene.v().getSootClass(clsName);
      return this.lookupClassConstantNode(sc);
    }
    if (x instanceof StringConstant) {
      String value = ((StringConstant) x).value;
      return flowgraph.allNStringConstantNodes.get(value);
    }
    // if it is new Intent or createIntentCall
    if (flowgraph.allNAllocNodes.containsKey(x)) {
      return flowgraph.allNAllocNodes.get(x);
    }
    return null;
  }

  public Map<Local, Stmt> actualFlowToFormalLocal(Value actual) {
    Map<Local, Stmt> stmtToLocal = actualParamtoFormalLocal.get(actual);
    return stmtToLocal;
  }

  public static synchronized FlowgraphRebuilder v(GUIAnalysisOutput guiOutput) {
    if (rebuilder == null) {
      rebuilder = new FlowgraphRebuilder(guiOutput);
    }
    return rebuilder;
  }
  private static FlowgraphRebuilder rebuilder;
}
