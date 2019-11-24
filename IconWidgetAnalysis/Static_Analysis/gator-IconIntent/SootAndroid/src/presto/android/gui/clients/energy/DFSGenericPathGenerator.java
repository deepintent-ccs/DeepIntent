package presto.android.gui.clients.energy;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import presto.android.Logger;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;

import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Created by zero on 2/15/16.
 */
public class DFSGenericPathGenerator {
  private List<IPathFilter> m_pathFilters;
  private List<IEdgeFilter> m_edgeFilters;
  private int K;
  private List<WTGEdge> m_initEdges;
  private boolean m_allowRepeatedEdge;
  private Map<String, List<List<WTGEdge>>> m_matchedPath;
  private boolean m_stopAtMatch;

  private DFSGenericPathGenerator(
          List<IPathFilter> pathFilters,
          List<IEdgeFilter> edgeFilters,
          List<WTGEdge> initEdges,
          Map<String, List<List<WTGEdge>>> matchedPath,
          boolean stopAtMatch,
          boolean allowCycle,
          int K){
    this.K = K;
    this.m_pathFilters = pathFilters;
    this.m_edgeFilters = edgeFilters;
    this.m_initEdges = initEdges;
    this.m_allowRepeatedEdge = allowCycle;
    this.m_matchedPath = matchedPath;
    this.m_stopAtMatch = stopAtMatch;
    if (this.m_pathFilters == null){
      this.m_pathFilters = Lists.newArrayList();
    }
    if (this.m_edgeFilters == null) {
      this.m_edgeFilters = Lists.newArrayList();
    }

    Preconditions.checkNotNull(this.m_initEdges, "No init edges specified");
  }

  public static DFSGenericPathGenerator create(
          List<IPathFilter> pathFilters,
          List<IEdgeFilter> edgeFilters,
          List<WTGEdge> initEdges,
          int K){
    DFSGenericPathGenerator instance =
            new DFSGenericPathGenerator(
                    pathFilters, edgeFilters, initEdges, null, true, false, K);
    return instance;
  }

  public static DFSGenericPathGenerator create(
          List<IPathFilter> pathFilters,
          List<IEdgeFilter> edgeFilters,
          List<WTGEdge> initEdges,
          boolean allowRepeatedEdge,
          int K){
    DFSGenericPathGenerator instance =
            new DFSGenericPathGenerator(
                    pathFilters, edgeFilters, initEdges, null, true, allowRepeatedEdge, K);
    return instance;
  }

  /***
   * Create a DFS Path Generator
   * @param pathFilters specify the path filter. Could be null or empty
   * @param edgeFilters specify the edge filter. Could be null or empty
   * @param initEdges specify the initial Edges. Should not be null or empty
   * @param matchedPath Maps used to save matched paths. If you do not want to save
   *                    any matched paths, leave it as null
   * @param allowRepeatedEdge Allow cyclic flag
   * @param K The max path length
   * @return Created DFS Path Generator Object
   */
  public static DFSGenericPathGenerator create(
          List<IPathFilter> pathFilters,
          List<IEdgeFilter> edgeFilters,
          List<WTGEdge> initEdges,
          Map<String, List<List<WTGEdge>>> matchedPath,
          boolean allowRepeatedEdge,
          int K){
    DFSGenericPathGenerator instance =
            new DFSGenericPathGenerator(
                    pathFilters,
                    edgeFilters,
                    initEdges,
                    matchedPath,
                    true,
                    allowRepeatedEdge,
                    K);
    return instance;
  }

  /***
   * Create a DFS Path Generator
   * @param pathFilters specify the path filter. Could be null or empty
   * @param edgeFilters specify the edge filter. Could be null or empty
   * @param initEdges specify the initial Edges. Should not be null or empty
   * @param matchedPath Maps used to save matched paths. If you do not want to save
   *                    any matched paths, leave it as null
   * @param stopAtMatch Stop when path filter return true flag.
   * @param allowRepeatedEdge Allow cyclic flag
   * @param K The max path length
   * @return
   */
  public static DFSGenericPathGenerator create(
          List<IPathFilter> pathFilters,
          List<IEdgeFilter> edgeFilters,
          List<WTGEdge> initEdges,
          Map<String, List<List<WTGEdge>>> matchedPath,
          boolean stopAtMatch,
          boolean allowRepeatedEdge,
          int K){
    DFSGenericPathGenerator instance =
            new DFSGenericPathGenerator(
                    pathFilters,
                    edgeFilters,
                    initEdges,
                    matchedPath,
                    stopAtMatch,
                    allowRepeatedEdge,
                    K);
    return instance;
  }

  /***
   * Add a path filter to the DFS Path Generator
   * @param pF the path Filter
   */
  public void addPathFilter(IPathFilter pF) {
    if (this.m_pathFilters == null)
      this.m_pathFilters = Lists.newArrayList();
    if (pF != null && !this.m_pathFilters.contains(pF))
      this.m_pathFilters.add(pF);
  }

  /***
   * Add an edge filter to the DFS Path Generator
   * @param eF
   */
  public void addEdgeFilter(IEdgeFilter eF) {
    if (this.m_edgeFilters == null)
      this.m_edgeFilters = Lists.newArrayList();
    if (eF != null && this.m_edgeFilters.contains(eF))
      this.m_edgeFilters.add(eF);
  }

  public void doPathGeneration(){
    for (WTGEdge initEdge : this.m_initEdges) {
      List<WTGEdge> P = Lists.newArrayList();
      Stack<NObjectNode> S = new Stack<NObjectNode>();
      S.add(initEdge.getSourceNode().getWindow());
      if (!canAppend(P, S, initEdge))
        continue;
      doAppend(P, S, initEdge);
      doDFS(P, S, initEdge.getTargetNode());
      unDoAppend(P, S, initEdge);
    }
  }

  public void doPathGenerationWithTarget(){
    String mtdTag = "DFSGenericPathGenerator.doPathGenerationWtihTarget";
    for (WTGEdge initEdge : this.m_initEdges){
      List<WTGEdge> P = Lists.newArrayList();
      Stack<NObjectNode> S = new Stack<>();
      S.add(initEdge.getTargetNode().getWindow());
      P.add(initEdge);
      doDFS(P,S, initEdge.getTargetNode());
    }
  }

  private void doDFS(List<WTGEdge> P, Stack<NObjectNode> S, WTGNode n){
    //If current path size larger than K
    //Stop traversal
    if(P.size() > K)
      return;


    //Check those path filters
    boolean matched = false;
    for (IPathFilter pF : this.m_pathFilters) {
      if (pF.match(P, S)){
        matched = true;
        addToMap(pF, P);
      }
    }
    if (matched && this.m_stopAtMatch)
      return;

    //Expand path
    for (WTGEdge e: n.getOutEdges()) {
      if ((!m_allowRepeatedEdge) && P.contains(e))
        continue;

      boolean bDiscard = false;
      for (IEdgeFilter eF : this.m_edgeFilters) {
        if (eF.discard(e, P, S)) {
          bDiscard = true;
          break;
        }
      }
      if (bDiscard)
        continue;

      if (!canAppend(P,S, e)){
        continue;
      }
      doAppend(P, S, e);
      doDFS(P, S, e.getTargetNode());
      unDoAppend(P,S, e);
    }

  }

  private void addToMap(IPathFilter pF, List<WTGEdge> P) {
    if (this.m_matchedPath != null) {
      if (!this.m_matchedPath.containsKey(pF.getFilterName())) {
        this.m_matchedPath.put(
                pF.getFilterName(), Lists.<List<WTGEdge>>newArrayList());
      }
      List<List<WTGEdge>> pathList = this.m_matchedPath.get(pF.getFilterName());
      pathList.add(Lists.<WTGEdge>newArrayList(P));
    }
  }

  private boolean canAppend(List<WTGEdge> P, Stack<NObjectNode> S, WTGEdge e){
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

  private void doAppend(List<WTGEdge> P, Stack<NObjectNode> S, WTGEdge e){
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

  private void unDoAppend(List<WTGEdge> P, Stack<NObjectNode> S, WTGEdge e) {
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
