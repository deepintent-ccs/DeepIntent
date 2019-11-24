package presto.android.gui.clients.energy;


import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import presto.android.gui.GUIAnalysisOutput;

import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.WTGAnalysisOutput;
import presto.android.gui.wtg.analyzer.ConstantAnalysis;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import soot.SootMethod;
import soot.jimple.Stmt;


import java.util.*;

/**
 * Created by zero on 7/29/15.
 */
public class VarUtil {

  public WTGNode fakeWTGNode = null;

  public Set<NObjectNode> widgetSet =  Collections.synchronizedSet(new HashSet<NObjectNode>());


  public ConstantAnalysis constantAnalysis = null;

  public Map<Pair<NObjectNode, SootMethod>, HashMultimap<Stmt, Stmt>> infeasibleEdgesMap;

  public Map<Pair<NObjectNode, SootMethod>, HashMultimap<Stmt, SootMethod>> infeasibleCallsMap;

  public GUIAnalysisOutput guiOutput;

  public List<List<WTGEdge>> Category1PathsWithEnergyLeaks;

  public List<List<WTGEdge>> Category2PathsWithEnergyLeaks;

  public HashMultimap<Stmt, ResNode> stmtResNodeMap;

  public Map<ResNode, Integer> severeRateMap;

  public int K;

  public WTGAnalysisOutput wtgOutput;

  public WTG wtg;

  public int uniqueC1;

  public int uniqueC2;

  public int P1Candidate;

  public int P2Candidate;

  private VarUtil(){
    infeasibleCallsMap = Maps.newHashMap();
    infeasibleEdgesMap = Maps.newHashMap();
    uniqueC1 = 0;
    uniqueC2 = 0;
    P1Candidate = 0;
    P2Candidate = 0;
  }

  private static VarUtil instance;
  public static VarUtil v(){
    if (instance == null){
      instance = new VarUtil();
    }
    return instance;
  }



}
