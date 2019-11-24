package presto.android.gui.clients.energy;

import java.util.*;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import presto.android.Configs;
import presto.android.Logger;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.JimpleUtil;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.WTGAnalysisOutput;
import presto.android.gui.wtg.analyzer.CFGTraversal;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.util.Filter;
import presto.android.gui.wtg.util.QueryHelper;
import presto.android.gui.wtg.util.WTGUtil;
import soot.*;
import soot.jimple.Stmt;
import soot.toolkits.graph.UnitGraph;


public class EnergyAnalyzer {
  public long startTime = 0;
  public long endTime = 0;
  HashMultimap<Pair<NObjectNode, SootMethod>, ResNode> pairACQMap = HashMultimap.create();
  HashMultimap<Pair<NObjectNode, SootMethod>, ResNode> pairRELMap = HashMultimap.create();
  HashMultimap<Pair<NObjectNode, SootMethod>, ResNode> pairRemovedMap = HashMultimap.create();
  Set<Pair<NObjectNode, SootMethod>> allPairs;
  Set<SootMethod> allMethods;
  List<WTGEdge> allEdges;
  List<WTGNode> allNode;
  GUIAnalysisOutput guiOutput;
  private WTG wtg;
  private JimpleUtil jimpleUtil;
  private WTGUtil wtgUtil;

  public EnergyAnalyzer(WTG wtg, GUIAnalysisOutput guiOutput, WTGAnalysisOutput wtgOutput){
    this.wtg = wtg;
    this.guiOutput = guiOutput;
    this.allMethods = Sets.<SootMethod>newHashSet();

    this.wtgUtil = WTGUtil.v();
    this.jimpleUtil = JimpleUtil.v();

    VarUtil.v().guiOutput = guiOutput;
    VarUtil.v().wtgOutput = wtgOutput;
    VarUtil.v().wtg = wtg;
  }

  /**
   * Entry point of the analysis
   */
  public void analyze(){

    //Make a copy because they might be changed
    this.allEdges = Lists.newArrayList(wtg.getEdges());
    this.allNode = Lists.newArrayList(wtg.getNodes());
    //HashSet used to save all Pair<GUIWidget, callback/ eventHandler method>
    this.allPairs = Sets.newHashSet();

    for (WTGEdge curEdge : this.allEdges) {
      NObjectNode guiWidget = curEdge.getGUIWidget();

      //Get life cycle callbacks (e.g. onPause()) from current WTGEdge and generate pair for it
      List<EventHandler> curCallBacks = curEdge.getCallbacks();
      for (EventHandler ev : curCallBacks) {
        this.allPairs.add(new Pair<>(ev.getWindow(), ev.getEventHandler()));
      }

      //Get event handler (e.g. onClick()) from current WTGEdge and generate pair for it.
      Set<SootMethod> curHandlerSet = curEdge.getEventHandlers();
      for (SootMethod curMethod : curHandlerSet){
        this.allPairs.add(new Pair<>(guiWidget, curMethod));
      }
    }

    //Traverse all possible Pair<NObjectNode, SootMethod>
    doAllPairsTraverse();

    //Get the K value from the command line
    String Kval = Configs.getClientParamCode("WTPK");

    //If the K value does not exists, use 5 as default
    if (Kval == null) {
      Logger.verb("EnergyAnalyzer.Analyze", "K value for WTG Path is not specified, use 5 as default");
      VarUtil.v().K = 5;
    }else {
      try {
        String val = Kval.substring(4);
        VarUtil.v().K = Integer.decode(val);
      }catch (IndexOutOfBoundsException e){
        Logger.verb("EnergyAnalyzer.Analyze", "K value for WTG Path is not specified, use 5 as default");
        VarUtil.v().K = 5;
      }
      catch (NumberFormatException e){
        Logger.verb("EnergyAnalyzer.Analyze", "K value for WTG Path is not specified, use 5 as default");
        VarUtil.v().K = 5;
      }
    }

    //Do Pattern 1 and 2 Path generation and analysis
    Pattern1_2AnalysisG();
    //Output Statistic results
    outputStats();
  }

  /**
   * Find if the method is relevant with the given node.
   * e.g. a WTGEdge may contains an onPause() callback from another method
   * @param mtd input method
   * @param node input WTGNode
   * @return  return true if these two input within same window. otherwise return false
   */
  private boolean isMethodRelevant(SootMethod mtd, WTGNode node){
    NObjectNode window = node.getWindow();
    SootClass tcls = window.getClassType();
    SootClass mcls = mtd.getDeclaringClass();
    if (mcls == tcls){
      return true;
    }else {
      while (tcls.hasSuperclass()){
        tcls = tcls.getSuperclass();
        if (tcls == mcls){
          return true;
        }
      }
    }
    return false;
  }


  //Pattern 1 and 2 Analysis using generic DFS Path Generator
  private void Pattern1_2AnalysisG(){
    final String mtdTag = "EnergyAnalyzer.Pattern1_2AnalysisG";
    //Map used to save Pattern 1 Paths with energy defects.
    final Map<List<WTGEdge>, List<ResNode>> p1PathResMap = Maps.newHashMap();
    //Map used to save Pattern 2 Paths with energy defects.
    final Map<List<WTGEdge>, List<ResNode>> p2PathResMap = Maps.newHashMap();
    IPathFilter pattern1Filter = new IPathFilter() {
      @Override
      public boolean match(List<WTGEdge> P, Stack<NObjectNode> S) {

        if (P.size() < 2)
          return false;
        NObjectNode targetWindow = P.get(0).getTargetNode().getWindow();
        WTGEdge lastEdge = P.get(P.size() - 1);
        List<StackOperation> lastOpS = lastEdge.getStackOps();

        //Last edge should not be home/power/rotate
        EventType lastEvent = lastEdge.getEventType();
        if (lastEvent == EventType.implicit_rotate_event
                || lastEvent == EventType.implicit_home_event
                || lastEvent == EventType.implicit_power_event)
          return false;

        //Check if last edge have pop(w)
        boolean fPop = false;
        for (StackOperation curOp : lastOpS) {
          if (!curOp.isPushOp() && curOp.getWindow() == targetWindow)
            fPop = true;
        }
        if (!fPop)
          return false;

        //Make a copy of the stack
        Stack<NObjectNode> cpS = new Stack<>();
        cpS.addAll(S);
        //Undo last edges operation
        for (int i = lastOpS.size() - 1; i >= 0; i--) {
          StackOperation curOp = lastOpS.get(i);
          NObjectNode opWindow = curOp.getWindow();
          if (opWindow == targetWindow && (!curOp.isPushOp()) && cpS.isEmpty()) {
            //Stack is balanced(Empty). Currently it is a pop operation and op Window is the target Window
            VarUtil.v().P1Candidate++;
            List<ResNode> rmRes = EnergyAnalyzer.this.traverseCategory1Path(P);
            if (!rmRes.isEmpty()){
              p1PathResMap.put(Lists.<WTGEdge>newArrayList(P),rmRes);
            }
            return true;
          }
          //Undo the changes
          if (!curOp.isPushOp()) {
            //It is a pop
            cpS.push(opWindow);
          } else if (!cpS.isEmpty() && cpS.peek() == opWindow) {
            //It is a push and window match the top of the window stack
            cpS.pop();
          } else {
            Logger.err("DFSPATH", "ERROR: Stack is not balanced in isP1Candidate");
          }
        }
        return false;
      }
      @Override
      public String getFilterName() {
        return "Pattern1";
      }
    };

    IPathFilter pattern2Filter = new IPathFilter() {
      @Override
      public boolean match(List<WTGEdge> P, Stack<NObjectNode> S) {
        if (P.size() < 2){
          return false;
        }
        NObjectNode targetWindow = P.get(0).getTargetNode().getWindow();
        NObjectNode topActivity = getTopActivity(S);
        if (topActivity == null)
          //If the stack is empty or no activity inside return false
          return false;
        WTGEdge lastEdge = P.get(P.size() - 1);
        EventType evt = lastEdge.getEventType();
        if (topActivity == targetWindow &&
                (evt == EventType.implicit_home_event )) {
          VarUtil.v().P2Candidate++;
          List<ResNode> rmRes = EnergyAnalyzer.this.traverseCategory2Path(P);
          if (!rmRes.isEmpty()) {
            p2PathResMap.put(Lists.<WTGEdge>newArrayList(P),rmRes);
          }
          return true;
        }

        return false;
      }

      @Override
      public String getFilterName() {
        return "Patter2";
      }
    };

    //Gen Inital Edges
    List<WTGEdge> initEdges = Lists.newArrayList();
    for (WTGNode n : wtg.getNodes()){
      if(!(n.getWindow() instanceof NActivityNode)){
        //Ignore any window that is not Activity
        continue;
      }
      List<WTGEdge> validInboundEdges = Lists.newArrayList();
      for (WTGEdge curEdge : n.getInEdges()){
        switch (curEdge.getEventType()) {
          case implicit_back_event:
          case implicit_home_event:
          case implicit_rotate_event:
          case implicit_power_event:
            continue;
        }
        List<StackOperation> curStack = curEdge.getStackOps();
        if (curStack != null && !curStack.isEmpty()) {
          StackOperation curOp = curStack.get(curStack.size() - 1);
          //If last op of this inbound edge is push
          if (curOp.isPushOp()) {
            NObjectNode pushedWindow = curOp.getWindow();
            WTGNode pushedNode = wtg.getNode(pushedWindow);
            if (pushedNode == n) {
              validInboundEdges.add(curEdge);
            }
          }
        }
      }
      if (validInboundEdges.isEmpty()){
        //No inBound edges. Fake them
        WTGEdge fakeInEdge = FakeNodeEdgeGenerator.v().genFakeType1Path(n);
        validInboundEdges.add(fakeInEdge);
      }

      for (WTGEdge e : validInboundEdges) {
        initEdges.add(e);
      }
    }


    List<IPathFilter> pathFilters = Lists.newArrayList();
    pathFilters.add(pattern1Filter);
    pathFilters.add(pattern2Filter);
    DFSGenericPathGenerator pathGen = DFSGenericPathGenerator.create(
            pathFilters,null,initEdges,VarUtil.v().K);
    pathGen.doPathGenerationWithTarget();

    if (p1PathResMap.isEmpty()){
      Logger.verb(mtdTag, "Pattern 1 No Issue");
    }else{
      OutputReducer reducerC1 = new OutputReducer(OutputReducer.PathType.C1);
      reducerC1.parseOutput(p1PathResMap);
      VarUtil.v().uniqueC1 = reducerC1.getUniqueIssues();
      reducerC1.outputToFile(Configs.benchmarkName + "C1.log", OutputReducer.OutputType.MINIMAL);
      Logger.verb("\033[1;31mDEBUG\033[0m", "Pattern 1 defects output to " + reducerC1.absPath);
    }

    if (p2PathResMap.isEmpty()) {
      Logger.verb(mtdTag, "Pattern 2 No Issue");
    }else{
      OutputReducer reducerC2 = new OutputReducer(OutputReducer.PathType.C2);
      reducerC2.parseOutput(p2PathResMap);
      VarUtil.v().uniqueC2 = reducerC2.getUniqueIssues();
      reducerC2.outputToFile(Configs.benchmarkName + "C2.log", OutputReducer.OutputType.MINIMAL);
      Logger.verb("\033[1;31mDEBUG\033[0m", "Pattern 2 defects output to " + reducerC2.absPath);
    }

  }

  /**
   * Traverse on Category 1 Path using the description in the Paper Section 2.3
   * @param curPath The Category 1 path needs to be traversed
   * @return If this path contains a leak. return leaking resources. Otherwise return an empty list
   */
  public List<ResNode> traverseCategory1Path(List<WTGEdge> curPath){
    Stack<NObjectNode> windowStack = new Stack<>();
    NObjectNode targetNode = curPath.get(0).getTargetNode().getWindow();
    WTGNode targetWTGNode = curPath.get(0).getTargetNode();
    List<ResNode> retList = Lists.newArrayList();
    HashMultimap<NObjectNode, ResNode> rmACQMap = HashMultimap.create();
    Set<ResNode> m_sessionACQ;
    Set<ResNode> m_sessionREL;
    if (!(targetNode instanceof NActivityNode)){
      //If target Window is not an activity
      //Do noting on it
      return retList;
    }

    //Assume the src node is already in the stack
    windowStack.push(curPath.get(0).getSourceNode().getWindow());
    for (int i = 0; i < curPath.size(); i++){
      WTGEdge curEdge = curPath.get(i);
      List<StackOperation> ops = curEdge.getStackOps();
      for (StackOperation op :ops){
        NObjectNode opWindow = op.getWindow();
        if (op.isPushOp()){
          windowStack.push(opWindow);
        }else if (!windowStack.isEmpty()){
          windowStack.pop();
        }else{
          return retList;
        }
      }

      NObjectNode widget = curEdge.getGUIWidget();
      for (SootMethod curHandler : curEdge.getEventHandlers()){
        if (i == 0 || i == curPath.size() - 1){
          if (!isMethodRelevant(curHandler, targetWTGNode))
            continue;
        }

        Pair<NObjectNode, SootMethod> curPair = new Pair<>(widget, curHandler);
        m_sessionACQ = pairACQMap.get(curPair);
        m_sessionREL = pairRELMap.get(curPair);
        NObjectNode curTopWindow = getTopActivity(windowStack);

        if (!m_sessionREL.isEmpty()){
          for (ResNode curREL :m_sessionREL){
            matchAndRemoveRes(rmACQMap, curREL);
          }
        }

        if (curTopWindow == targetNode){
          addResNodeToMap(rmACQMap, m_sessionACQ);
        }

      }

      //Then do on Callbacks
      if (i == curPath.size() - 1){
        //Last edge
        //Check in onPause/onStop/onDestroy
        for (EventHandler evt : curEdge.getCallbacks()){
          SootMethod curMethod = evt.getEventHandler();
          String curSub = curMethod.getSubSignature();
          if (!(curSub.contains("onPause") || curSub.contains("onStop") || curSub.contains("onDestroy")))
            continue;
          Pair<NObjectNode, SootMethod> curPair = new Pair<>(evt.getWindow(), evt.getEventHandler());
          m_sessionACQ = pairACQMap.get(curPair);
          m_sessionREL = pairRELMap.get(curPair);
          NObjectNode curTopWindow = getTopActivity(windowStack);
          if (!m_sessionREL.isEmpty()){
            for (ResNode curREL : m_sessionREL){
              matchAndRemoveRes(rmACQMap, curREL);
            }
          }
          if (curTopWindow == targetNode){
            addResNodeToMap(rmACQMap, m_sessionACQ);
          }
        }
        continue;
      }

      for (EventHandler evt : curEdge.getCallbacks()){
        if (i == 0 || i == curPath.size() - 1){
          if (!isMethodRelevant(evt.getEventHandler(), targetWTGNode))
            continue;
        }
        Pair<NObjectNode, SootMethod> curPair = new Pair<>(evt.getWindow(), evt.getEventHandler());
        m_sessionACQ = pairACQMap.get(curPair);
        m_sessionREL = pairRELMap.get(curPair);
        NObjectNode curTopWindow = getTopActivity(windowStack);
        if (!m_sessionREL.isEmpty()){
          for (ResNode curREL : m_sessionREL){
            matchAndRemoveRes(rmACQMap, curREL);
          }
        }
        if (curTopWindow == targetNode){
          addResNodeToMap(rmACQMap, m_sessionACQ);
        }
      }
    }
    if (!rmACQMap.isEmpty()){
      for (NObjectNode curObj : rmACQMap.keySet()){
        Set<ResNode> curRESSet = rmACQMap.get(curObj);
        retList.addAll(curRESSet);
      }
    }
    return retList;
  }

  public List<ResNode> traverseCategory2Path(List<WTGEdge> curPath){
    Stack<NObjectNode> windowStack = new Stack<>();
    NObjectNode targetNode = curPath.get(0).getTargetNode().getWindow();
    WTGNode targetWTGNode = curPath.get(0).getTargetNode();
    List<ResNode> retList = Lists.newArrayList();
    HashMultimap<NObjectNode, ResNode> rmACQMap = HashMultimap.create();
    Set<ResNode> m_sessionACQ;
    Set<ResNode> m_sessionREL;
    if (!(targetNode instanceof NActivityNode)){
      //If target Window is not an activity
      //Do noting on it
      return retList;
    }

    //Assume the src node is already in the stack
    windowStack.push(curPath.get(0).getSourceNode().getWindow());
    for (int i = 0; i < curPath.size(); i++){
      WTGEdge curEdge = curPath.get(i);
      List<StackOperation> ops = curEdge.getStackOps();
      for (StackOperation op :ops){
        NObjectNode opWindow = op.getWindow();
        if (op.isPushOp()){
          windowStack.push(opWindow);
        }else if (!windowStack.isEmpty()){
          windowStack.pop();
        }else{
          return retList;
        }
      }

      NObjectNode widget = curEdge.getGUIWidget();
      for (SootMethod curHandler : curEdge.getEventHandlers()){
        if (i == 0 || i == curPath.size() - 1){
          if (!isMethodRelevant(curHandler, targetWTGNode))
            continue;
        }

        Pair<NObjectNode, SootMethod> curPair = new Pair<>(widget, curHandler);
        m_sessionACQ = pairACQMap.get(curPair);
        m_sessionREL = pairRELMap.get(curPair);
        NObjectNode curTopWindow = getTopActivity(windowStack);
        if (!m_sessionREL.isEmpty()){
          for (ResNode curREL :m_sessionREL){
            matchAndRemoveRes(rmACQMap, curREL);
          }
        }
        if (curTopWindow == targetNode){
          addResNodeToMap(rmACQMap, m_sessionACQ);
        }
      }

      //Then do on Callbacks
      for (EventHandler evt : curEdge.getCallbacks()){
        if (i == 0 ){
          if (!isMethodRelevant(evt.getEventHandler(), targetWTGNode))
            continue;
        }

        if (i == curPath.size() - 1){
          SootMethod curMethod = evt.getEventHandler();
          if (!isMethodRelevant(evt.getEventHandler(), targetWTGNode))
            continue;
          String curSub = curMethod.getSubSignature();
          if (curSub.contains("onResume") || curSub.contains("onStart") || curSub.contains("onRestart"))
            continue;
        }

        Pair<NObjectNode, SootMethod> curPair = new Pair<>(evt.getWindow(), evt.getEventHandler());
        m_sessionACQ = pairACQMap.get(curPair);
        m_sessionREL = pairRELMap.get(curPair);
        NObjectNode curTopWindow = getTopActivity(windowStack);
        if (!m_sessionREL.isEmpty()){
          for (ResNode curREL : m_sessionREL){
            matchAndRemoveRes(rmACQMap, curREL);
          }
        }
        if (curTopWindow == targetNode){
          addResNodeToMap(rmACQMap, m_sessionACQ);
        }
      }
    }
    if (!rmACQMap.isEmpty()){
      for (NObjectNode curObj : rmACQMap.keySet()){
        Set<ResNode> curRESSet = rmACQMap.get(curObj);
        retList.addAll(curRESSet);
      }
    }

    return retList;
  }

  private void addResNodeToMap(HashMultimap<NObjectNode, ResNode> m_ACQMAP, Set<ResNode> m_sessionACQL){
    if (!m_sessionACQL.isEmpty()){
      for (ResNode curACQ : m_sessionACQL){
        //Add this ACQ into m_ACQMAP if no duplicate exist
        //Different callbacks/handlers may call the same method which contains ACQ or REL. Therefore, duplicated
        //ResNode will be created in for these callbacks/handlers even though they are same resource
        //This is a workaround to prevent adding these duplicated ResNode to the Map.
        if (!m_ACQMAP.containsKey(curACQ.objectNode)) {
          //No entry. Absolutely no duplication. Add this ResNode to Map.
          m_ACQMAP.put(curACQ.objectNode, curACQ);
        } else{
          Set<ResNode> curResSet = m_ACQMAP.get(curACQ.objectNode);
          boolean bSame = false;
          for (ResNode curRes : curResSet) {
            if (curRes.compare(curACQ.objectNode, curACQ.stmt, curACQ.context)){
              //objectNode and Stmt and the method contains this Stmt is the same. Duplication detected
              bSame = true;
              break;
            }
          }
          if (!bSame) {
            //Add this ACQ into m_ACQMAP if no duplicate exist
            m_ACQMAP.put(curACQ.objectNode, curACQ);
          }
        }
      }
    }
  }

  private NObjectNode getTopActivity(Stack<NObjectNode> windowStack){
    if (windowStack.isEmpty())
      return null;

    for (int i = windowStack.size() - 1; i >= 0; i--){
      NObjectNode curObj = windowStack.get(i);
      if (curObj instanceof NActivityNode)
        return curObj;
    }

    return null;
  }


  /**
   * Get infeasible Edges for the target pair. If not exists, do the anaysis and generate it
   * @param curPair Target pair
   * @return  infeasible edges
   */
  private HashMultimap<Stmt, Stmt> getInfeasibleEdges(Pair<NObjectNode, SootMethod> curPair){
    HashMultimap<Stmt, Stmt> ret;
    boolean bRecreate = false;
    if (VarUtil.v().infeasibleEdgesMap.containsKey(curPair)){
      ret = VarUtil.v().infeasibleEdgesMap.get(curPair);
    }else{
      bRecreate = true;
      ret = HashMultimap.create();
      HashMultimap<Stmt, SootMethod> infeasibleCalls = HashMultimap.create();
      VarUtil.v().constantAnalysis.doAnalysis(curPair.getL(), curPair.getR(), ret, infeasibleCalls);
    }
    return ret;
  }

  /**
   * Get infeasible Calls for the target pair. If not exists, do the anaysis and generate it
   * @param curPair Target pair
   * @return  infeasible calls
   */
  private HashMultimap<Stmt, SootMethod> getInfeasibleCalls(Pair<NObjectNode, SootMethod> curPair){
    HashMultimap<Stmt, SootMethod> ret;
    boolean bRecreate = false;
    if (VarUtil.v().infeasibleCallsMap.containsKey(curPair)){
      ret = VarUtil.v().infeasibleCallsMap.get(curPair);
    }else{
      bRecreate = true;
      ret = HashMultimap.create();
      HashMultimap<Stmt, Stmt> infeasibleEdges = HashMultimap.create();
      VarUtil.v().constantAnalysis.doAnalysis(curPair.getL(), curPair.getR(), infeasibleEdges, ret);
    }
    return ret;
  }

  /**
   * Traverse all pairs and find all ACQs and RELs inside them.
   */
  private void doAllPairsTraverse(){


    final String mtdTag = "ALLMTDTR";   //Log tag

    //All method get, Do phase 1. Get All ACQ and REL
    //Following Sets use to save found ACQ and RELs from a single Pair.
    Set<ResNode> p_SessionACQ = Sets.newHashSet();
    Set<ResNode> p_SessionREL = Sets.newHashSet();
    List<ResNode> resNodeList = Lists.newArrayList();
    VarUtil.v().stmtResNodeMap = HashMultimap.create();

    for (Pair<NObjectNode, SootMethod> curPair: allPairs) {
      //For each pair of GUIWidget, handler/ lifecycle callback pair. Find ACQ and REL.
      p_SessionACQ.clear();
      p_SessionREL.clear();
      SootMethod curMethod = curPair.getR();
      //Get infeasibleEdges and infeasibleCalls of target method
      HashMultimap<Stmt, Stmt> curInfeasibleEdges = getInfeasibleEdges(curPair);
      HashMultimap<Stmt, SootMethod> curInfeasibleCalls = getInfeasibleCalls(curPair);

      //Traverse on method. Return True if any ACQ or REL is found.
      boolean bRes = traverseMethodFull(
              curMethod,
              p_SessionACQ,
              p_SessionREL,
              curInfeasibleEdges,
              curInfeasibleCalls);

      if (bRes) {
        for (ResNode curRes : p_SessionACQ) {
          pairACQMap.put(curPair, curRes);
          VarUtil.v().stmtResNodeMap.put(curRes.stmt, curRes);
          resNodeList.add(curRes);
        }

        for (ResNode curRes : p_SessionREL) {
          pairRELMap.put(curPair, curRes);
          VarUtil.v().stmtResNodeMap.put(curRes.stmt, curRes);
        }
      }
    }// End of for.

    //Phase 1 ended. Do Phase 2 part 1. traverse reversed ICFG of each ACQ that have the same REL
    for (Pair<NObjectNode, SootMethod> curPair: Sets.newHashSet(pairACQMap.keySet())){
      if (!pairRELMap.containsKey(curPair))
        continue;
      HashMultimap<Stmt, Stmt> curInfeasibleEdges = getInfeasibleEdges(curPair);
      HashMultimap<Stmt, SootMethod> curInfeasibleCalls = getInfeasibleCalls(curPair);
      //curPair contians both ACQ and REL
      Set<ResNode> acqResSet = Sets.newHashSet(pairACQMap.get(curPair));
      Set<ResNode> relResSet = Sets.newHashSet(pairRELMap.get(curPair));
      //For each REL in relSet
      for (ResNode curRel :relResSet){
        Set<ResNode> matchedAcqResSet = findMatchFromACQSet(acqResSet, curRel);
        if (matchedAcqResSet.isEmpty())
          //No matches
          continue;
        for (ResNode curAcq: matchedAcqResSet){
          boolean bDownwardExposed = EnergyUtils.v().reverseTraverseICFG(
                  curAcq,
                  curRel,
                  curPair,
                  curInfeasibleEdges,
                  curInfeasibleCalls);

          if (!bDownwardExposed){
            pairACQMap.get(curPair).remove(curAcq);
          }
        }
      }
    }

    //Phase 1 ended. Do Phase 2 part 2. Calculate the reachability of each REL
    for (Pair<NObjectNode, SootMethod> curPair : Sets.newHashSet(pairRELMap.keySet())) {
      Set<ResNode> resSet = Sets.newHashSet(pairRELMap.get(curPair));
      SootMethod curMethod = curPair.getR();
      //Get infeasibleEdges and infeasibleCalls of target method
      HashMultimap<Stmt, Stmt> curInfeasibleEdges = getInfeasibleEdges(curPair);
      HashMultimap<Stmt, SootMethod> curInfeasibleCalls = getInfeasibleCalls(curPair);

      for (ResNode curRes : resSet){
        boolean reached = traverseMethod(curMethod, curRes, curInfeasibleEdges, curInfeasibleCalls);
        if (reached) {
          //Remove the REL if there exists a path to exit without go through REL.
          pairRELMap.remove(curPair, curRes);
          //Add removed REL into a new Map, just in case it needs further analysis.
          pairRemovedMap.put(curPair, curRes);
        }
      }
    }
    //Phase 2 ended

    //Phase 3 Evaluate Serve Rate
    VarUtil.v().severeRateMap = Maps.newHashMap();
    for (ResNode curResNode : resNodeList){
      List<SootMethod> callBackList = EnergyUtils.v().extractListenerCallback(curResNode);
      boolean rel = false;
      for (SootMethod curCallback : callBackList){
        p_SessionACQ.clear();
        p_SessionREL.clear();
        traverseLocationChanged(curCallback, p_SessionREL, null, null);
        if (p_SessionREL.isEmpty())
          continue;
        boolean hasConcreteREL = false;
        for (ResNode curInsideRes : p_SessionREL){
          boolean reached = traverseMethod(curCallback, curInsideRes, null, null);
          if (!reached){
            hasConcreteREL = true;
          }
        }
        if (hasConcreteREL)
          rel = true;
      }
      if (rel){
        VarUtil.v().severeRateMap.put(curResNode, 1);
      }else{
        VarUtil.v().severeRateMap.put(curResNode, 0);
      }
    }
  }

  private Set<ResNode> findMatchFromACQSet(Set<ResNode> acqSet, ResNode relRes){
    Set<ResNode> ret = Sets.newHashSet();
    for (ResNode curACQ : acqSet){
      NObjectNode acqObj = curACQ.objectNode;
      if (relRes.objectNode == acqObj && relRes.getUnitType() == curACQ.getUnitType()){
        ret.add(curACQ);
      }
    }
    return ret;
  }

  /**
   * Remove a resource from resource map if REL match an entry in ACQ
   * @param rmACQ remained aquired resource map.
   * @param curNode resource which is REL
   * @return  return true if it matches, otherwise return false.
   */
  private boolean matchAndRemoveRes(HashMultimap<NObjectNode, ResNode> rmACQ, ResNode curNode){

    Preconditions.checkNotNull(rmACQ);
    boolean bWarning = false;

    if (rmACQ.containsKey(curNode.objectNode)){
      Set<ResNode> resNodeSet = Sets.newHashSet(rmACQ.get(curNode.objectNode));
      for (ResNode curResNode : resNodeSet){
        if (curNode.getUnitType() == curResNode.getUnitType()){
          //NObject node is the same and Unit Type is the same. match and remove
          rmACQ.remove(curNode.objectNode, curResNode);
        }
      }
      return true;
    } else if (bWarning){
      Logger.verb("MATCH", "Target: " + curNode.objectNode + " not matched");
    }

    return false;
  }

  /**
   * Traverse the target method without computing reachability. return true
   * @param handler method needs to be traversed
   * @param p_sessionACQ map used to save any ACQ resources found in this method
   * @param p_sessionREL map used to save any REL resources found in this method
   * @param infeasibleEdges infeasibleEdges from the constant propagation. Could be null
   * @param infeasibleCalls infeasibleCalls from the constant propagation. Could be null
   * @return  If any ACQ or REL is found, return true otherwise return false.
   */
  private boolean traverseMethodFull(
          SootMethod handler,
          Set<ResNode> p_sessionACQ,
          Set<ResNode> p_sessionREL,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls

  ) {

    final SootMethod inHandler = handler;

    final List<ResNode> m_sessionACQL = Lists.newArrayList();
    final List<ResNode> m_sessionRELL = Lists.newArrayList();
    final Set<Stmt> escapedStmts = Sets.newHashSet();
    final Map<Stmt, SootMethod> visitedStmts = Maps.newHashMap();
    final Map<SootMethod, UnitGraph> methodToCFG = Maps.newHashMap();
    if (infeasibleEdges == null)
      infeasibleEdges = HashMultimap.create();
    if (infeasibleCalls == null)
      infeasibleCalls = HashMultimap.create();
    final QueryHelper queryHelper = QueryHelper.v();

    final String mtdTag = "TRAVSEMTDFF";
    final String fltTag = "TRAVEMTDFF_FT";

    Filter<Stmt, SootMethod> fF = new Filter<Stmt, SootMethod>() {
      public boolean match(Stmt unit, SootMethod sm) {

        if (wtgUtil.isAcquireResourceCall(unit)) {
          //ACQ encountered
          Integer pos = wtgUtil.getAcquireResourceField(unit);
          if (pos == null) {
            throw new RuntimeException("[Error]: can not find the resource local for stmt: " + unit);
          }

          Local resLocal = null;
          if (pos == 0) {
            resLocal = jimpleUtil.receiver(unit);
          } else {
            Value argValue = unit.getInvokeExpr().getArg(pos - 1);
            if (!(argValue instanceof Local)) {
              throw new RuntimeException("[Error]: the resource local is not type of local for stmt: " + unit);
            }
            resLocal = (Local) argValue;
          }

          int x = 0;
          Set<NNode> backReachedNodes = queryHelper.allVariableValues(guiOutput.getFlowgraph().simpleNode(resLocal));
          for (NNode backReachedNode : backReachedNodes) {
            if (backReachedNode instanceof NObjectNode) {
              x++;
              NObjectNode resource = (NObjectNode) backReachedNode;
              ResNode curResNode = new ResNode(resource, unit, inHandler, sm);
              m_sessionACQL.add(curResNode);
            }
          }

          if (x == 0) {
            Logger.verb(fltTag, "\n\n!!! NO RESOURCE FOR ACQUIRE: " + unit + "  @  " + visitedStmts.get(unit) + "\n\n");
            return false;
          } else {
            return false;
          }
        }

        if (wtgUtil.isReleaseResourceCall(unit)) {
          //REL encountered

          Integer pos = wtgUtil.getReleaseResourceField(unit);
          if (pos == null) {
            throw new RuntimeException("[Error]: can not find the resource local for stmt: " + unit);
          }

          Local resLocal = null;
          if (pos == 0) {
            resLocal = jimpleUtil.receiver(unit);
          } else {
            Value argValue = unit.getInvokeExpr().getArg(pos - 1);
            if (!(argValue instanceof Local)) {
              throw new RuntimeException("[Error]: the resource local is not type of local for stmt: " + unit);
            }
            resLocal = (Local) argValue;
          }

          int x = 0;
          Set<NNode> backReachedNodes = queryHelper.allVariableValues(guiOutput.getFlowgraph().simpleNode(resLocal));
          for (NNode backReachedNode : backReachedNodes) {
            if (backReachedNode instanceof NObjectNode) {
              x++;
              NObjectNode resource = (NObjectNode) backReachedNode;
              ResNode curResNode = new ResNode(resource, unit, inHandler, sm);
              m_sessionRELL.add(curResNode);
            }
          }

          if (x == 0) {
            Logger.verb(fltTag, "\n\n!!! NO RESOURCE FOR ACQUIRE: " + unit + "  @  " + visitedStmts.get(unit) + "\n\n");
            return false;
          } else {
            return false;
          }

        }

        return false;
      }
    };
    List<Stmt> visitedSeq = Lists.newArrayList();
    IfNullUtil.v().forwardTraversalMod(
            handler,
            visitedStmts,
            visitedSeq,
            escapedStmts,
            methodToCFG,
            fF,
            infeasibleEdges,
            infeasibleCalls
    );

    if (m_sessionACQL.isEmpty() && m_sessionRELL.isEmpty()) {
      //No energy related operations
      return false;
    }

    if (!m_sessionACQL.isEmpty()) {
      p_sessionACQ.clear();
      for (ResNode curNode : m_sessionACQL) {
        p_sessionACQ.add(curNode);
      }
    }

    if (!m_sessionRELL.isEmpty()) {
      p_sessionREL.clear();
      for (ResNode curNode : m_sessionRELL) {
        p_sessionREL.add(curNode);
      }
    }
    return true;
  }

  /**
   * Traverse the target method and calculate the reachabilty of target Resource
   * @param handler target method needs to be traversed
   * @param tgtRes  target resource
   * @param infeasibleEdges infeasibleEdges from constant propagation
   * @param infeasibleCalls infeasibleCalls from constant propagation
   * @return If the EXIT is reached while traverse stopped
   * at the target Resource. return true otherwise return false
   */
  private boolean traverseMethod(
          SootMethod handler,
          final ResNode tgtRes,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls){

    final ResNode target = tgtRes;

    final SootMethod inHander = handler;
    CFGTraversal cfgTraversal = CFGTraversal.v();
    final Set<Stmt> escapedStmts = Sets.newHashSet();
    final Map<Stmt, SootMethod> visitedStmts = Maps.newHashMap();
    final Map<SootMethod, UnitGraph> methodToCFG = Maps.newHashMap();

    if (infeasibleEdges == null)
      infeasibleEdges = HashMultimap.create();
    if (infeasibleCalls == null)
      infeasibleCalls = HashMultimap.create();
    final String mtdTag = "TraverseMethod";

    Filter<Stmt, SootMethod> fF = new Filter<Stmt, SootMethod>() {
      public boolean match(Stmt unit, SootMethod sm) {

        if (unit.equals(tgtRes.stmt) && sm.equals(tgtRes.context)){
          return true;
        }else
          return false;
      }
    };

    List<Stmt> visitedSeq = Lists.newArrayList();

    IfNullUtil.v().forwardTraversalIfIgnore(
            handler,
            visitedStmts,
            visitedSeq,
            escapedStmts,
            methodToCFG,
            fF,
            infeasibleEdges,
            infeasibleCalls
    );

    UnitGraph handlerCFG = methodToCFG.get(handler);
    boolean reachToExit = false;
    for (Unit exitNode : handlerCFG.getTails()) {
      if (visitedStmts.containsKey(exitNode)) {
        reachToExit = true;
        break;
      }
    }

    return reachToExit;
  }

  /**
   * Traverse the target method without computing reachability. return true
   * @param handler method needs to be traversed
   * @param p_sessionREL map used to save any REL resources found in this method
   * @param infeasibleEdges infeasibleEdges from the constant propagation. Could be null
   * @param infeasibleCalls infeasibleCalls from the constant propagation. Could be null
   * @return  If any ACQ or REL is found, return true otherwise return false.
   */
  private boolean traverseLocationChanged(
          SootMethod handler,
          Set<ResNode> p_sessionREL,
          HashMultimap<Stmt, Stmt> infeasibleEdges,
          HashMultimap<Stmt, SootMethod> infeasibleCalls
  ) {

    final SootMethod inHandler = handler;

    final List<ResNode> m_sessionRELL = Lists.newArrayList();
    final Set<Stmt> escapedStmts = Sets.newHashSet();
    final Map<Stmt, SootMethod> visitedStmts = Maps.newHashMap();
    final Map<SootMethod, UnitGraph> methodToCFG = Maps.newHashMap();
    if (infeasibleEdges == null)
      infeasibleEdges = HashMultimap.create();
    if (infeasibleCalls == null)
      infeasibleCalls = HashMultimap.create();
    final QueryHelper queryHelper = QueryHelper.v();

    final String mtdTag = "TRAVLOC";
    final String fltTag = "TRAVLOC_FT";

    Filter<Stmt, SootMethod> fF = new Filter<Stmt, SootMethod>() {
      public boolean match(Stmt unit, SootMethod sm) {

        if (wtgUtil.isReleaseResourceCall(unit)) {
          ResNode curResNode = new ResNode(null, unit, inHandler, sm);
          m_sessionRELL.add(curResNode);
        }

        return false;
      }
    };
    List<Stmt> visitedSeq = Lists.newArrayList();
    IfNullUtil.v().forwardTraversalIfIgnore(
            handler,
            visitedStmts,
            visitedSeq,
            escapedStmts,
            methodToCFG,
            fF,
            infeasibleEdges,
            infeasibleCalls
    );

    if ( m_sessionRELL.isEmpty()) {
      //No energy related operations
      return false;
    }

    if (!m_sessionRELL.isEmpty()) {
      p_sessionREL.clear();
      for (ResNode curNode : m_sessionRELL) {
        p_sessionREL.add(curNode);
      }
    }
    return true;
  }


  protected void outputStats(){
    final String mtdTag = "STAT";
    int nodesCount = wtg.getNodes().size();
    int edgesCount = wtg.getEdges().size();
    int C1Count = 0;
    if (VarUtil.v().Category1PathsWithEnergyLeaks != null) {
      C1Count = VarUtil.v().Category1PathsWithEnergyLeaks.size();
    }

    int C2Count = 0;
    if (VarUtil.v().Category2PathsWithEnergyLeaks != null) {
      C2Count = VarUtil.v().Category2PathsWithEnergyLeaks.size();
    }
    endTime = System.currentTimeMillis();
    long passedTime = this.endTime - this.startTime;
    float runningSec = (float)passedTime/1000;
    Logger.verb(mtdTag, "K = " + VarUtil.v().K);

    String InfoLine = "App & Nodes & Edges &&C1 Unique &C2 Unique && Time";

    String curLine =  String.format(
            "%s &%d &%d &&%d &%d &&%f",
            Configs.benchmarkName,
            nodesCount,
            edgesCount,
            VarUtil.v().uniqueC1,
            VarUtil.v().uniqueC2,
            runningSec);
    Logger.verb(mtdTag, InfoLine);
    Logger.verb(mtdTag, curLine);
    Logger.verb("ENGALZClient", "Meminfo MAX: "
            + StatUtil.v().getMaxMem()
            + " Total: " + StatUtil.v().getTotalMem()
            + " Free: " + StatUtil.v().getFreeMem()
            + " Used: " + StatUtil.v().getUsedMem());
    Logger.verb(mtdTag, "P1 Count: " + VarUtil.v().P1Candidate + " P2 Count: " + VarUtil.v().P2Candidate);
    Logger.verb(mtdTag, "Total Count: " + (VarUtil.v().P1Candidate + VarUtil.v().P2Candidate));

  }
}