package presto.android.gui.clients.energy;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import presto.android.Logger;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.listener.EventType;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Created by zero on 11/12/15.
 */
public class DFSPathGenerator {
  private static DFSPathGenerator instance;
  public Map<List<WTGEdge>, List<ResNode>> p1PathResMap;
  public Map<List<WTGEdge>, List<ResNode>> p2PathResMap;
  private EnergyAnalyzer eaInstance;
  private int K;
  public int P1Count = 0;
  public int P2Count = 0;

  private DFSPathGenerator(){
    p1PathResMap = Maps.newHashMap();
    p2PathResMap = Maps.newHashMap();
  }

  public static DFSPathGenerator v(){
    if (instance == null)
      instance = new DFSPathGenerator();
    return instance;
  }

  public void genAndTraverse(WTG wtg, EnergyAnalyzer eaInstance, int K){
    this.eaInstance = eaInstance;
    this.K = K;
    Collection<WTGNode> allNodes = wtg.getNodes();
    for (WTGNode n : allNodes){
      if(!(n.getWindow() instanceof NActivityNode)){
        //Ignore any window that is not Activity
        continue;
      }

      //Find all valid inbound edges for node n
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

        List<WTGEdge> P = Lists.newArrayList();
        Stack<NObjectNode> S = new Stack<>();
        S.add(e.getTargetNode().getWindow());
        P.add(e);
        DFSTraverse(P, S, e.getTargetNode());

      }

    }
  }

  private void DFSTraverse(List<WTGEdge> P, Stack<NObjectNode> S, WTGNode n){
    //Do something with P1 and P2 here
    StatUtil.v().getUsedMem();
    if(P.size() > K)
      return;
    if (isP1CandidateNEO(P, S)){
      //If it is a Pattern 1 Path
      this.P1Count += 1;
      List<ResNode> rmRes = eaInstance.traverseCategory1Path(P);
      if (!rmRes.isEmpty()){
        //If it contains energy issues
        this.p1PathResMap.put(Lists.newArrayList(P), rmRes);
      }
      return;
    }

    if (isP2CandidateNEO(P, S)){
      //If it is a Pattern 2 Path
      this.P2Count += 1;
      List<ResNode> rmRes = eaInstance.traverseCategory2Path(P);
      if (!rmRes.isEmpty()){
        //If it contains energy issues
        this.p2PathResMap.put(Lists.newArrayList(P), rmRes);
      }
      return;
    }


    Collection<WTGEdge> outEdge = n.getOutEdges();
    for (WTGEdge e : outEdge){
      if (P.contains(e))
        //Does not allow cycles in the Path
        continue;
      if (! canAppend(P, S, e)){
        continue;
      }
      doAppend(P, S, e);
      DFSTraverse(P, S, e.getTargetNode());
      unDoAppend(P,S, e);
    }
  }

  boolean isP1Candidate(List<WTGEdge> P, Stack<NObjectNode> S){
    //targetWindow is the target WTGNode of first edge in the path
    NObjectNode targetWindow = P.get(0).getTargetNode().getWindow();
    if (P.get(P.size() - 1).getSourceNode().getWindow() == targetWindow && S.isEmpty()){
      //Check the last Pop
      List<StackOperation> stackOps = P.get(P.size() - 1).getStackOps();
      if (stackOps.isEmpty())
        return false;
      NObjectNode opWindow = stackOps.get(stackOps.size() - 1).getWindow();
      if (!stackOps.get(stackOps.size() - 1).isPushOp() && opWindow == targetWindow){
        //The last popped window should be target window
        return true;
      }else
        return false;
    }

    return false;
  }

  boolean isP1CandidateNEO(List<WTGEdge> P, Stack<NObjectNode> S){
    //targetWindow is the target WTGNode of the first edge in the path
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
    for (StackOperation curOp : lastOpS){
      if (!curOp.isPushOp() && curOp.getWindow() == targetWindow)
        fPop = true;
    }
    if (!fPop)
      return false;

    //Make a copy of the stack
    Stack<NObjectNode> cpS = new Stack<>();
    cpS.addAll(S);
    //Undo last edges operation
    for (int i = lastOpS.size() - 1; i >= 0; i--){
      StackOperation curOp = lastOpS.get(i);
      NObjectNode opWindow = curOp.getWindow();
      if (opWindow == targetWindow && (!curOp.isPushOp()) && cpS.isEmpty()){
        //Stack is balanced(Empty). Currently it is a pop operation and op Window is the target Window
        return true;
      }
      //Undo the changes
      if (! curOp.isPushOp()){
        //It is a pop
        cpS.push(opWindow);
      }else if (!cpS.isEmpty() && cpS.peek() == opWindow){
        //It is a push and window match the top of the window stack
        cpS.pop();
      }else{
        DumpPath(P);
        Logger.err("DFSPATH", "ERROR: Stack is not balanced in isP1Candidate");
      }
    }
    return false;
  }

  private void DumpPath(List<WTGEdge> P){
    Logger.verb("DUMPEDGE", "");
    for (WTGEdge curEdge : P){
      Logger.verb("DUMPEDGE", curEdge.toString());
    }
    Logger.verb("DUMPEDGE", "");
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

  boolean isP2CandidateNEO(List<WTGEdge> P, Stack<NObjectNode> S){
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
            (evt == EventType.implicit_home_event ))
      return true;

    return false;
  }

  boolean isP2Candidate(List<WTGEdge> P, Stack<NObjectNode> S){
    WTGEdge lastEdge = P.get(P.size() - 1);
    EventType evt = lastEdge.getEventType();
    //Stack is not empty and last event is HOME or POWER return true
    if (!S.isEmpty() &&
            (evt == EventType.implicit_home_event || evt == EventType.implicit_home_event)){
      return true;
    }
    return false;
  }

  boolean canAppend(List<WTGEdge> P, Stack<NObjectNode> S, WTGEdge e){
    //Assume S is simulated window stack of P and it is balanced
    Stack<NObjectNode> cpS = new Stack<NObjectNode>();
    cpS.addAll(S);
    List<StackOperation> stackOps = e.getStackOps();
    for (StackOperation op : stackOps){
      NObjectNode opWindow = op.getWindow();
      if (op.isPushOp()){
        cpS.push(opWindow);
      }else if (!cpS.isEmpty() && cpS.peek() == opWindow){
        cpS.pop();
      }else{
        return false;
      }
    }

    if (cpS.isEmpty()){
      return true;
    }

    if (cpS.peek() == e.getTargetNode().getWindow())
      return true;
    return false;
  }

  void doAppend(List<WTGEdge> P, Stack<NObjectNode> S, WTGEdge e){
    //Assume e is a feasible edge to be added to P
    P.add(e);
    List<StackOperation> sOps = e.getStackOps();
    for (StackOperation op : sOps){
      NObjectNode opWindow = op.getWindow();
      if (op.isPushOp()){
        S.push(opWindow);
      }else if (! S.isEmpty() && S.peek() == op.getWindow()){
        S.pop();
      }else{
        Logger.err("DFSPATH", "ERROR: Edge is not feasible!");
      }
    }
  }

  void unDoAppend(List<WTGEdge> P, Stack<NObjectNode> S, WTGEdge e) {
    //Assue e is the last edge in P
    if (P.remove(P.size() - 1) != e){
      Logger.err("DFSPATH", "ERROR: Last edge in Path is not match with Param");
    }

    List<StackOperation> sOps = e.getStackOps();
    for (int i = sOps.size() - 1; i >= 0; i--){
      StackOperation op = sOps.get(i);
      NObjectNode opWindow = op.getWindow();
      if (! op.isPushOp()){
        //It is a pop
        S.push(opWindow);
      }else if (! S.isEmpty() && S.peek() == opWindow){
        S.pop();
      }else{
        Logger.err("DFSPATH", "ERROR: Stack not balanced while undoAppend");
      }
    }
  }
}
