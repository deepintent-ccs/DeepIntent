package presto.android.gui.clients.energy;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jas.Var;
import presto.android.Logger;
import presto.android.gui.JimpleUtil;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.WTGAnalysisOutput;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.flowgraph.AndroidCallGraph;
import presto.android.gui.wtg.util.Filter;
import presto.android.gui.wtg.util.WTGUtil;
import soot.*;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.*;

/**
 * Created by zero on 9/28/15.
 */
public class EnergyUtils {
  public enum TraverseType{
    C1, C2
  }

  private EnergyUtils(){
    wtgUtil = WTGUtil.v();
    cg = AndroidCallGraph.v();
    jimpleUtil = JimpleUtil.v();
  }
  static private EnergyUtils instance;
  static public EnergyUtils v(){
    if (instance == null) {
      instance = new EnergyUtils();
    }
    return instance;
  }

  private WTGUtil wtgUtil;
  private AndroidCallGraph cg;
  private JimpleUtil jimpleUtil;

  private WTGAnalysisOutput wtgOutput = VarUtil.v().wtgOutput;

  public HashMultimap<Stmt, Stmt> reverseInfeasibleEdges(HashMultimap<Stmt, Stmt> infeasibleEdges){
    HashMultimap<Stmt, Stmt> retMap = HashMultimap.create();
    for (Stmt pred: infeasibleEdges.keySet()){
      Set<Stmt> tarSet = infeasibleEdges.get(pred);
      for (Stmt target : tarSet){
        retMap.put(target, pred);
      }
    }
    return retMap;
  }

  public boolean reverseTraversal(
          SootMethod handler,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
          Set<Stmt> escapedStmts,
          Map<SootMethod, UnitGraph> methodToCFG,
          Filter<Stmt, SootMethod> filter,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    if (handler == null) {
      Logger.err("REVERSETRV", "Can not perform reverse traversal since the handler is null");
    }
    List<Stmt> workingList = Lists.newArrayList();
    UnitGraph handlerCFG = createOrGetCFG(methodToCFG, handler);

    for (Unit exitNode : handlerCFG.getTails()){
      propagate(visitedStmts, workingList, visitedSeq, (Stmt) exitNode, handler);
    }

    return reverseTraverse(workingList, visitedStmts, visitedSeq, escapedStmts, methodToCFG,
            filter, infeasibleEdges, infeasibleCalls);
  }

  public void dumpVisitedSeq(List<Stmt> visitedSeq) {
    Logger.verb("DUMPSEQ", "");
    for (Stmt s : visitedSeq){
      Logger.verb("DUMPSEQ", s.toString());
    }
    Logger.verb("DUMPSEQ", "");
  }

  public void dumpWorkList(List<Stmt> workList) {
    Logger.verb("DUMPWKL", "");
    for (Stmt s : workList){
      Logger.verb("DUMPWKL", s.toString());
    }
    Logger.verb("DUMPWKL", "");
  }

  public void dumpPred(Collection<Unit> c){
    Logger.verb("DUMPPRED", "");
    for (Unit u : c){
      Logger.verb("DUMPPRED", u.toString());
    }
    Logger.verb("DUMPPRED", "");
  }

  private boolean reverseTraverse(
          List<Stmt> workingList,
          Map<Stmt, SootMethod> visitedStmts,
          List<Stmt> visitedSeq,
          Set<Stmt> escapedStmts,
          Map<SootMethod, UnitGraph> methodToCFG,
          Filter<Stmt, SootMethod> filter,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls) {
    boolean unexpected = false;
    Set<SootMethod> visitedMethods = Sets.newHashSet();
    final String mtdTag = "RVSTRV";

    while (!workingList.isEmpty()) {
      //dumpWorkList(workingList);
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
              && !currentCFG.getHeads().contains(currentStmt)) {
        Collection<Unit> predecessors = currentCFG.getPredsOf(currentStmt);
        for (Unit pred : predecessors){
          Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
          if (tgts != null && tgts.contains(pred))
            continue;
          propagate(visitedStmts, workingList, visitedSeq, (Stmt) pred, currentCxt);
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
            for (Unit entryNode : tgtCFG.getTails()) {
              propagate(visitedStmts, visitedSeq, workingList, (Stmt) entryNode, target);
            }
            if (visitedMethods.contains(target)) {
              for (Unit pred: currentCFG.getPredsOf(currentStmt)){
                Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
                if (tgts != null && tgts.contains(pred))
                  continue;
                propagate(visitedStmts, visitedSeq, workingList, (Stmt) pred, currentCxt);
              }
            }
          }
        }
        // if the target can not be found, then we conservatively think there is
        // no transition
        if (!findTarget) {
          for (Unit pred: currentCFG.getPredsOf(currentStmt)){
            Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
            if (tgts != null && tgts.contains(pred))
              continue;
            propagate(visitedStmts, visitedSeq, workingList, (Stmt) pred, currentCxt);
          }
        }
      } // end of else if
      // case 3: currentStmt is the exit point and callingContext is not in
      // visitedMethods
      else if (currentCFG.getHeads().contains(currentStmt)
              && !visitedMethods.contains(currentCxt)){
        visitedMethods.add(currentCxt);
        createOrGetCFG(methodToCFG, currentCxt);
        Set<AndroidCallGraph.Edge> incomings = cg.getIncomingEdges(currentCxt);
        for (AndroidCallGraph.Edge e : incomings) {
          Stmt caller = e.callSite;
          if (visitedStmts.containsKey(caller)) {
            SootMethod callerCxt = visitedStmts.get(caller);
            UnitGraph callerCFG = createOrGetCFG(methodToCFG, callerCxt);
            for (Unit pred : callerCFG.getPredsOf(caller)) {
              Set<Stmt> tgts = infeasibleEdges.get(currentStmt);
              if (tgts != null && tgts.contains(pred))
                continue;
              propagate(visitedStmts, visitedSeq, workingList, (Stmt) pred, callerCxt);
            }
          }
        }
      }// End of else if
    }
    return unexpected;
  }

  private UnitGraph createOrGetCFG(Map<SootMethod, UnitGraph> methodToCFG,
                                   SootMethod mtd) {
    UnitGraph cfg = methodToCFG.get(mtd);
    if (cfg == null) {
      cfg = new ExceptionalUnitGraph(mtd.retrieveActiveBody());
      methodToCFG.put(mtd, cfg);
    }
    return cfg;
  }

  public boolean reverseTraverseICFG(
          final ResNode acqRes,
          final ResNode relRes,
          final Pair<NObjectNode, SootMethod> curPair,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls){

    if (infeasibleEdges == null)
      infeasibleEdges = HashMultimap.create();
    else
      infeasibleEdges = reverseInfeasibleEdges(infeasibleEdges);

    if (infeasibleCalls == null)
      infeasibleCalls = HashMultimap.create();

    Map<Stmt, SootMethod> visitedStmts = Maps.newHashMap();
    List<Stmt> visitedSeq = Lists.newArrayList();
    Set<Stmt> escapedStmts = Sets.newHashSet();
    Map<SootMethod, UnitGraph> methodToCFG = Maps.newHashMap();
    final HashMultimap<Stmt, ResNode> stmtResNodeMap = VarUtil.v().stmtResNodeMap;

    Filter<Stmt, SootMethod> F = new Filter<Stmt, SootMethod>() {
      public boolean match(Stmt unit, SootMethod sm) {
        if (wtgUtil.isReleaseResourceCall(unit)) {
          //REL encountered
          if (stmtResNodeMap.containsKey(unit)) {
            Set<ResNode> curRelSet = stmtResNodeMap.get(unit);
            for (ResNode curRel : curRelSet){
              if (curRel.objectNode == relRes.objectNode
                      && curRel.getUnitType() == relRes.getUnitType()){
                return true;
              }
            }
          }
        }
        return false;
      }
    };

    reverseTraversal(
            curPair.getR(),
            visitedStmts,
            visitedSeq,
            escapedStmts,
            methodToCFG,
            F,
            infeasibleEdges,
            infeasibleCalls

    );

    if (visitedStmts.containsKey(acqRes.stmt)){
      return true;
    }else{
      return false;
    }
  }

  private void propagate(
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

  public List<SootMethod> extractListenerCallback(ResNode res){
    List<SootMethod> retList = Lists.newArrayList();
    if (!(res.stmt instanceof InvokeStmt)){
      return retList;
    }
    WTGUtil wtgUtil = WTGUtil.v();
    Integer pos = wtgUtil.getAcquireResourceField(res.stmt);
    if (pos == null)
      return retList;
    if (pos == 0)
      return retList;
    SootClass listenerClass = res.objectNode.getClassType();
    if (listenerClass.declaresMethod("void onLocationChanged(android.location.Location)")){

      SootMethod locationChangedListener = listenerClass.getMethod("void onLocationChanged(android.location.Location)");
      retList.add(locationChangedListener);
    }
    return retList;
  }

  /**
   * Do type 1 traverse on selected entry node
   * @param entryNode The starter node of the analysis
   * @param k         Maximum traverse depth
   * @param allowLoop is Loop allowed
   * @return          The paths that satisfy type 1 analysis
   */
  public List<List<WTGEdge>> genEnergyIssueType1Path(WTGNode entryNode, int k, boolean allowLoop){

    boolean bDEBUG = false;
    final String mtdTag = "GENC1";
    List<List<WTGEdge>> ret = new ArrayList<>();

//        if (this.staticModel == null){
//            //Fatal Error
//            Logger.err(this.getClass().getSimpleName(), "Static Model does not exist");
//            return null;
//        }

    //Get all inbound edges to the target node
    List<WTGEdge> inboundEdges = new ArrayList<WTGEdge>();
    for(WTGEdge curEdge: entryNode.getInEdges()){
      List<StackOperation> curStack = curEdge.getStackOps();
//      if (curEdge.getEventType() == EventType.implicit_back_event){
//        continue;
//      }
      switch (curEdge.getEventType()){
        case implicit_back_event:
        case implicit_home_event:
        case implicit_rotate_event:
        case implicit_power_event:
          continue;
      }
      if (curStack!= null && !curStack.isEmpty() ){
        StackOperation curOp = curStack.get(curStack.size() - 1);
        if (curOp.isPushOp()){
          NObjectNode pushedWindow = curOp.getWindow();

          WTGNode pushedNode = VarUtil.v().wtg.getNode(pushedWindow);;
          if (pushedNode == entryNode){
            inboundEdges.add(curEdge);
          }
        }
      }

    }

    //Check the number of the nodes
    if (bDEBUG){
      Logger.verb(mtdTag, "Node: "+ entryNode.toString() + " Size of Inbound Edges: "
              + inboundEdges.size());
    }

    //form path using BFS
    if (inboundEdges.isEmpty() && (entryNode.getWindow() instanceof NActivityNode)){
      WTGEdge fakeInEdge = FakeNodeEdgeGenerator.v().genFakeType1Path(entryNode);
      inboundEdges.add(fakeInEdge);
    }


    while (!inboundEdges.isEmpty()){
      WTGEdge entryEdge = inboundEdges.remove(0);
      //Logger.verb(mtdTag, "Traverse on inbound edge: " + entryEdge);
      //workingList.push(entryEdge);
      DFSTraverseClass traverser = new DFSTraverseClass(
              VarUtil.v().wtg, entryEdge, entryNode, VarUtil.v().wtgOutput, k, TraverseType.C1);

      List<List<WTGEdge>> res = traverser.doDFS();
      if (res == null){
        continue;
      }
      for (List<WTGEdge> curList : res){
        ret.add(curList);
      }
    }
    return ret;
  }

  public List<List<WTGEdge>> genEnergyIssueType2Path(WTGNode entryNode, int k, boolean allowLoop){

    boolean bDEBUG = false;
    final String mtdTag = "GENC2";
    List<List<WTGEdge>> ret = Lists.newArrayList();

    Preconditions.checkNotNull(entryNode);

    //Get all inbound edges to the target node

    List<WTGEdge> inboundEdges = Lists.newArrayList();
    for (WTGEdge curEdge : entryNode.getInEdges()){
      List<StackOperation> curStack = curEdge.getStackOps();
      switch (curEdge.getEventType()) {
        case implicit_back_event:
        case implicit_home_event:
        case implicit_rotate_event:
        case implicit_power_event:
          continue;
      }

      if (curStack != null && !curStack.isEmpty()) {
        StackOperation curOp = curStack.get(curStack.size() - 1);
        if (curOp.isPushOp()) {
          NObjectNode pushedWindow = curOp.getWindow();
          WTGNode pushedNode = VarUtil.v().wtg.getNode(pushedWindow);
          if (pushedNode == entryNode) {
            inboundEdges.add(curEdge);
          }
        }
      }
    }

    if (bDEBUG){
      Logger.verb(mtdTag, "Node: "+ entryNode.toString() + " Size of Inbound Edges: "
              + inboundEdges.size());
    }

    if (inboundEdges.isEmpty() && (entryNode.getWindow() instanceof NActivityNode)) {
      WTGEdge fakeInEdge = FakeNodeEdgeGenerator.v().genFakeType1Path(entryNode);
      inboundEdges.add(fakeInEdge);
    }

    while (!inboundEdges.isEmpty()) {
      WTGEdge entryEdge = inboundEdges.remove(0);
      DFSTraverseClass traverser = new DFSTraverseClass(
              VarUtil.v().wtg, entryEdge, entryNode, VarUtil.v().wtgOutput, k, TraverseType.C2);

      List<List<WTGEdge>> res = traverser.doDFS();
      if (res == null)
        continue;

      for (List<WTGEdge> curList : res) {
        ret.add(curList);
      }
    }
    return ret;
  }

  class DFSTraverseClass{

    public List<List<WTGEdge>> pathCollection;
    private int k;
    private ArrayList<WTGEdge> curPath;
    private WTGEdge mentryEdge;
    //private StaticGUIModel mModel;
    private WTGNode mEntryNode;
    private WTGAnalysisOutput mccfg;
    private TraverseType trType;
    private WTG wtg;
    private boolean validity;

    public DFSTraverseClass(
            WTG wtg,
            WTGEdge entryEdge,
            WTGNode entryNode,
            WTGAnalysisOutput ccfg,
            int K,
            TraverseType tt){
      this.wtg = wtg;
      this.mentryEdge = entryEdge;
      this.mEntryNode = entryNode;
      this.mccfg = ccfg;
      this.k = K;
      this.trType = tt;
      this.validity = true;
    }

    public List<WTGEdge> expandFeasibleEdge() {
      List<WTGEdge> feasibleEdges = Lists.newArrayList();
      if (this.curPath.isEmpty()) {
        return feasibleEdges;
      }
      WTGEdge lastEdge = curPath.get(curPath.size() - 1);
      WTGNode lastNode = lastEdge.getTargetNode();
      if (!this.validity) {
        feasibleEdges.addAll(lastNode.getOutEdges());
        return feasibleEdges;
      }

      Stack<NObjectNode> windowStack = simulateWindowStackStrict(this.curPath);
      if (windowStack == null ||windowStack.isEmpty()) {
        // invalid path to the node
        return feasibleEdges;
      }

      for (WTGEdge outEdge : lastNode.getOutEdges()) {
        Stack<NObjectNode> newStack = new Stack<NObjectNode>();
        newStack.addAll(windowStack);
        newStack = processEdge(newStack, outEdge);
        NObjectNode targetWindow = outEdge.getTargetNode().getWindow();
        if (!newStack.isEmpty() && newStack.peek() == targetWindow) {
          feasibleEdges.add(outEdge);
        }
      }
      return feasibleEdges;
    }

    private Stack<NObjectNode> simulateWindowStack(List<WTGEdge> trace) {
      Stack<NObjectNode> windowStack = new Stack<NObjectNode>();
      // assuming the very beginning node already exists in the stack
      windowStack.push(trace.get(0).getSourceNode().getWindow());
      for (WTGEdge staticEdge : trace) {
        windowStack = processEdgeStrict(windowStack, staticEdge);
        if (windowStack.isEmpty() || windowStack.peek() != staticEdge.getTargetNode().getWindow()) {
          windowStack.clear();
          return windowStack;
        }
      }
      return windowStack;
    }

    private Stack<NObjectNode> simulateWindowStackStrict(List<WTGEdge> trace){
      Stack<NObjectNode> windowStack = new Stack<>();

      windowStack.push(trace.get(0).getSourceNode().getWindow());
      for (WTGEdge curEdge:trace){
        List<StackOperation> ops = curEdge.getStackOps();
        for(StackOperation curOp:ops){
          NObjectNode windowNode = curOp.getWindow();
          if (curOp.isPushOp()){
            windowStack.push(windowNode);
          }else{
            if ((!windowStack.isEmpty()) && windowStack.peek() == windowNode){
              windowStack.pop();
            }else{
              return null;
            }
          }
        }
      }
      return windowStack;
    }

    private Stack<NObjectNode> processEdge(
            Stack<NObjectNode> windowStack, WTGEdge staticEdge) {
      Preconditions.checkNotNull(windowStack,
              "[Error]: initial window stack shouldn't be null");
      Preconditions.checkNotNull(staticEdge,
              "[Error]: edge to be processed shouldn't be null");
      for (StackOperation stackOp : staticEdge.getStackOps()) {
        NObjectNode opWindow = stackOp.getWindow();
        if (stackOp.isPushOp()) {
          windowStack.push(opWindow);
        } else {
          boolean found = false;
          while (!windowStack.isEmpty()) {
            NObjectNode topWindow = windowStack.pop();
            if (topWindow == opWindow) {
              found = true;
              break;
            }
          }
          if (!found) {
            return windowStack;
          }
        }
      }
      return windowStack;
    }

    private Stack<NObjectNode> processEdgeStrict(Stack<NObjectNode> windowStack, WTGEdge staticEdge){
      Preconditions.checkNotNull(windowStack,
              "[Error]: initial window stack shouldn't be null");
      Preconditions.checkNotNull(staticEdge,
              "[Error]: edge to be processed shouldn't be null");
      for(StackOperation curOp : staticEdge.getStackOps()){
        NObjectNode opWindow = curOp.getWindow();
        if (curOp.isPushOp()){
          windowStack.push(opWindow);
        }else{
          if ((!windowStack.isEmpty()) && windowStack.peek() == opWindow){
            windowStack.pop();
          }else{
            return new Stack<>();
          }
        }
      }
      return windowStack;
    }

    private void dumpPath(List<WTGEdge> curPath) {

      final String mtdTag = "DUMPPATH";
      Logger.verb(mtdTag, "Dump Path:");
      for (WTGEdge curEdge : curPath) {

        Logger.verb(mtdTag, "\t" + curEdge.toString());
        for (SootMethod curMethod : curEdge.getEventHandlers()) {
          Logger.verb(mtdTag, "\t" + curMethod.toString());
        }

        for (EventHandler curEvt : curEdge.getCallbacks()){
          Set<SootMethod> curMtdSet = curEdge.getEventHandlers();
          for (SootMethod curMethod : curMtdSet) {
            Logger.verb(mtdTag, curMethod.toString());
          }
        }
      }
      Logger.verb(mtdTag, "Dump End");
    }

    private void dumpStack(Stack<NObjectNode> s) {
      final String mtdTag = "DUMPSTACK";
      Logger.verb(mtdTag, "Dump Stack");

      for (int i = 0; i < s.size(); i++){
        Logger.verb(mtdTag, s.get(i).toString());
      }

      Logger.verb(mtdTag, "Dump Ended");
    }

    private boolean isLastPopWindow(WTGEdge curEdge){
      List<StackOperation> curOp = curEdge.getStackOps();
      if (!curOp.isEmpty()){
        StackOperation lastOp = curOp.get(curOp.size() - 1);
        if (!lastOp.isPushOp()) {
          NObjectNode nWindow = lastOp.getWindow();
          WTGNode popedWindow = wtg.getNode(nWindow);

          if (popedWindow == this.mEntryNode){
            return true;
          }
        }
      }
      return false;
    }

    private boolean isStackBalanced(List<WTGEdge> curPath){

      Stack<NObjectNode> windowsStack = new Stack<NObjectNode>();

      for (WTGEdge curEdge : curPath){
        List<StackOperation> opStack = curEdge.getStackOps();
        if (!opStack.isEmpty()){
          for (StackOperation curOp : opStack){
            NObjectNode curOpWindow = curOp.getWindow();
            if (curOp.isPushOp()){
              windowsStack.push(curOpWindow);
              continue;
            }
            if (!curOp.isPushOp()){
              NObjectNode prevWindow = null;
              if (!windowsStack.isEmpty()) {
                prevWindow = windowsStack.pop();
              }else{
                return false;
              }
              if (prevWindow != curOpWindow){
                return false;
              }
              continue;
            }
          }
        }
      }

      if (windowsStack.isEmpty()){
        return true;
      }
      return false;
    }

    private boolean isStackNotEmpty(List<WTGEdge> curPath) {
      Stack<NObjectNode> windowsStack = new Stack<NObjectNode>();

      for (WTGEdge curEdge : curPath) {
        List<StackOperation> opStack = curEdge.getStackOps();
        if (!opStack.isEmpty()) {
          for (StackOperation curOp : opStack) {
            NObjectNode curOpWindow = curOp.getWindow();
            if (curOp.isPushOp()){
              windowsStack.push(curOpWindow);
              continue;
            }

            if (!curOp.isPushOp()){
              NObjectNode popedWindow = null;
              if (windowsStack.isEmpty()){
                return false;
              }else{
                popedWindow = windowsStack.pop();
              }

              if (popedWindow != curOpWindow){

//                dumpStack(windowsStack);
//                dumpPath(curPath);

                Logger.verb(
                        "StackSimu", "Stack not balance, in Stack: " + popedWindow.toString() +
                                " targetNode: " + curOpWindow
                );
                return false;
              }
              continue;
            }

          }
        }
      }

      if (windowsStack.isEmpty()) {
        return false;
      } else{
        return true;
      }
    }

    private boolean isLastEdgeHOMEPWR(List<WTGEdge> curPath) {
      WTGEdge lastEdge = curPath.get(curPath.size() - 1);
      EventType evt = lastEdge.getEventType();
      if (evt == EventType.implicit_home_event || evt == EventType.implicit_power_event) {
        return true;
      }

      return false;
    }

    public List<List<WTGEdge>> doDFS(){


      if (this.trType == TraverseType.C1) {
        this.curDepth = 0;
        this.pathCollection = com.google.common.collect.Lists.newArrayList();
        this.curPath = new ArrayList<WTGEdge>();
        DFSTraverseC1(this.mentryEdge);
      }

      if (this.trType == TraverseType.C2) {
        this.curDepth = 0;
        this.pathCollection = Lists.newArrayList();
        this.curPath = Lists.newArrayList();
        DFSTraverseC2(this.mentryEdge);
      }
      return pathCollection;
    }

    private int curDepth = 0;
    public void DFSTraverseC1(WTGEdge curEntryEdge){

      curDepth ++;

      if (curDepth > k){
//        Logger.verb(this.getClass().getSimpleName(), "K exceeded");
//        for (WTGEdge curEdge: this.curPath){
//          Logger.verb(this.getClass().getSimpleName(),"\t Edge in path: " + curEdge.toString());
//        }
//        Logger.verb(this.getClass().getSimpleName(), " ");
        curDepth--;
        return;
      }

      if (curEntryEdge == null){
        curDepth--;
        return;
      }

      this.curPath.add(curEntryEdge);
      //Check if the last Edge is pop
      if (isLastPopWindow(curEntryEdge) && ((!this.validity) || isStackBalanced(this.curPath))){
        ArrayList<WTGEdge> copyList = new ArrayList<WTGEdge>(this.curPath);
        pathCollection.add(copyList);
        curPath.remove(curPath.size() - 1);
        curDepth--;
        return;
      }

      List<WTGEdge> tgtEdges = this.expandFeasibleEdge();

      for (WTGEdge curEdge : tgtEdges){
        EventType curType = curEdge.getEventType();
        if (curType == EventType.implicit_home_event
                || curType == EventType.implicit_power_event){
          continue;
        }
        if (curEdge == curEntryEdge){
          continue;
        }
        if (curPath.contains(curEdge)){
          continue;
        }
        DFSTraverseC1(curEdge);
      }

      this.curPath.remove(this.curPath.size() - 1);
      curDepth--;
    }

    public void DFSTraverseC2(WTGEdge curEntryEdge) {
      curDepth++;

      if (curDepth > k){
        curDepth--;
        return;
      }

      if (curEntryEdge == null){
        curDepth--;
        return;
      }

      this.curPath.add(curEntryEdge);
      //Check if this path satisfy the requirement.

      //Stack should not be empty and last edge is HOME or POWER
      if (this.validity && (!isStackNotEmpty(curPath))){
        //Stack already empty. Path discard
        curPath.remove(curPath.size() - 1);
        curDepth--;
        return;
      }

      if (isLastEdgeHOMEPWR(curPath)){
        //Found
        ArrayList<WTGEdge> copyList = Lists.newArrayList(this.curPath);
        pathCollection.add(copyList);
        curPath.remove(curPath.size() - 1);
        curDepth--;
        return;
      }

      List<WTGEdge> tgtEdges = this.expandFeasibleEdge();

      for (WTGEdge curEdge: tgtEdges){
        EventType curType = curEdge.getEventType();
        if (curEdge == curEntryEdge){
          continue;
        }
        if (curPath.contains(curEdge)){
          continue;
        }
        DFSTraverseC2(curEdge);
      }

      this.curPath.remove(this.curPath.size() - 1);
      curDepth--;

    }

  }
}
