package presto.android.gui.clients.energy;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import presto.android.Logger;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by zero on 10/26/15.
 */
public class EnergyDebugUtils {
  private static EnergyDebugUtils instance;
  private EnergyDebugUtils(){

  }

  public static EnergyDebugUtils v(){
    if (instance == null){
      instance = new EnergyDebugUtils();
    }
    return instance;
  }

  public void DEBUGDumpMap(Map<Stmt, SootMethod> visitedStmt) {
    final String mtdTag = "DEBUGDMP";
    Preconditions.checkNotNull(visitedStmt);
    Logger.verb(mtdTag, "Dump Visited Stmts");
    for (Stmt curStmt: visitedStmt.keySet()) {
      Logger.verb(mtdTag, "Visted: " + curStmt);
    }
    Logger.verb(mtdTag, "Dump Visited Stmts end");
  }

  public void DEBUGDumpList(List<Stmt> seq) {
    final String mtdTag = "DEBUGSEQDMP";
    Preconditions.checkNotNull(seq);
    Logger.verb(mtdTag, "Dump Visited Stmts");
    for (Stmt curStmt: seq) {
      Logger.verb(mtdTag, "Visited: "+ curStmt);
    }
    Logger.verb(mtdTag, "Dump Visited Stmts end");
  }

  public void dumpInfeasibleEdges(HashMultimap<Stmt, Stmt> infeasibleEdges){
    if (infeasibleEdges == null || infeasibleEdges.size() == 0)
      return;

    for (Stmt key : infeasibleEdges.keySet()){
      Set<Stmt> stmts = infeasibleEdges.get(key);
      if (stmts.isEmpty())
        continue;
      Logger.verb("INFEDGE", key.toString());
      for (Stmt stmt :stmts ){
        if (stmt != null)
          Logger.verb("INFEDGE", "\t" + stmt.toString());
      }
      Logger.verb("INFEDGE", "");
    }

  }

  public void dumpInfeasibleCalls(HashMultimap<Stmt, SootMethod> infeasibleCalls){
    if (infeasibleCalls == null || infeasibleCalls.size() == 0)
      return;

    for (Stmt key : infeasibleCalls.keySet()){
      Set<SootMethod> methods = infeasibleCalls.get(key);
      if (methods.isEmpty())
        continue;
      Logger.verb("INFCALL", key.toString());
      for (SootMethod curMethod :methods ){
        if (curMethod != null)
          Logger.verb("INFCALL", "\t" + curMethod.toString());
      }
      Logger.verb("INFCALL", "");
    }

  }

}
