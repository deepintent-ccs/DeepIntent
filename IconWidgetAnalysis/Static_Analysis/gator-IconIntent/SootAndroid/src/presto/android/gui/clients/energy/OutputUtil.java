package presto.android.gui.clients.energy;

import presto.android.Logger;
import presto.android.gui.graph.NObjectNode;

import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.ds.WTGEdge;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import java.util.Map;
import java.util.Set;

/**
 * Created by zero on 7/29/15.
 */
public class OutputUtil {
  private OutputUtil(){

  }

  private static OutputUtil instance = null;

  public static OutputUtil v(){
    if (instance == null){
      instance = new OutputUtil();
    }
    return instance;
  }


  public void outputEnergyIssueC1PathsToScreen(Map<List<WTGEdge>, List<ResNode>> PathResMap){
    Logger.verb(this.getClass().getSimpleName(), "Path with Energy Issue: ");


    for (List<WTGEdge> curPath : PathResMap.keySet()){
      Logger.verb(this.getClass().getSimpleName(), "\n Type 1 Path: \n");

      //HashMultimap<NObjectNode,Stmt> resourceMap = PathResMap.get(curPath);
      List<ResNode> resourceMap = PathResMap.get(curPath);
      for (ResNode curRes : resourceMap) {
        NObjectNode curObjNode = curRes.objectNode;
        Stmt curStmt = curRes.stmt;
        Logger.verb(this.getClass().getSimpleName(), "  Leaking Node: " + curObjNode + " ON STMT " + curStmt + "\n");
      }

      Logger.verb(this.getClass().getSimpleName(), "\n");

      for (WTGEdge curEdge: curPath){
        String curLine = curEdge.toString();
	Set<SootMethod> handlerMethodSet = curEdge.getEventHandlers();
	for (SootMethod curMethod: handlerMethodSet){
	  curLine+="\n\t\t"+curMethod.toString();
	}
        for (EventHandler evt : curEdge.getCallbacks()){
          SootMethod curMethod = evt.getEventHandler();
          curLine+="\n\t\t"+curMethod.toString();
        }
        Logger.verb(this.getClass().getSimpleName(), "\t" + curLine + "\n\n");
      }
      Logger.verb(this.getClass().getSimpleName(), " Type 1 Path End\n");
    }
    Logger.verb(this.getClass().getSimpleName(), "\n");
  }

  public void outputEnergyIssueC2PathsToScreen(Map<List<WTGEdge>, List<ResNode>> PathResMap){
    Logger.verb(this.getClass().getSimpleName(), "Path with Energy Issue: ");


    for (List<WTGEdge> curPath : PathResMap.keySet()){
      Logger.verb(this.getClass().getSimpleName(), "\n Type 2 Path: \n");

      //HashMultimap<NObjectNode,Stmt> resourceMap = PathResMap.get(curPath);
      List<ResNode> resourceMap = PathResMap.get(curPath);
      for (ResNode curRes : resourceMap) {
        NObjectNode curObjNode = curRes.objectNode;
        Stmt curStmt = curRes.stmt;
        Logger.verb(this.getClass().getSimpleName(), "  Leaking Node: " + curObjNode + " ON STMT " + curStmt + "\n");
      }

      Logger.verb(this.getClass().getSimpleName(), "\n");

      for (WTGEdge curEdge: curPath){
        String curLine = curEdge.toString();
        Set<SootMethod> handlerMethodSet = curEdge.getEventHandlers();
	for (SootMethod curMethod: handlerMethodSet){
	  curLine+="\n\t\t"+curMethod.toString();
	}
        for (EventHandler evt : curEdge.getCallbacks()){
          SootMethod curMethod = evt.getEventHandler();
          curLine+="\n\t\t"+curMethod.toString();
        }
        Logger.verb(this.getClass().getSimpleName(), "\t" + curLine + "\n\n");
      }
      Logger.verb(this.getClass().getSimpleName(), " Type 2 Path End\n");
    }
    Logger.verb(this.getClass().getSimpleName(), "\n");
  }


  public void saveEnergyIssuesC1ToFile(String fileName,
                                       Map<List<WTGEdge>, List<ResNode>> pathResMap){

    try {
      File outputFile = new File(fileName);
      if (!outputFile.exists()) {
        outputFile.createNewFile();
      } else {
        outputFile.delete();
        outputFile.createNewFile();
      }
      FileWriter fw = new FileWriter(outputFile.getAbsoluteFile());
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write("Paths with Energy Issues: \n");


      for (List<WTGEdge> curPath : pathResMap.keySet()){
        bw.write("\n Type 1 Path: \n");

        //HashMultimap<NObjectNode,Stmt> resourceMap = pathResMap.get(curPath);
        List<ResNode> resourceMap = pathResMap.get(curPath);

        for (ResNode curRes : resourceMap) {

          NObjectNode curObjNode = curRes.objectNode;
          Stmt curStmt = curRes.stmt;

          bw.write("  Leaking Node: " + curObjNode + " ON STMT " + curStmt + " Context: "+ curRes.context + "\n");
        }

        bw.write("\n");

        for (WTGEdge curEdge: curPath){
          String curLine = curEdge.toString();
          Set<SootMethod> handlerMethodSet = curEdge.getEventHandlers();
	  for (SootMethod curMethod: handlerMethodSet){
	    curLine+="\n\t\t"+curMethod.toString();
	  }

          for (EventHandler evt : curEdge.getCallbacks()){
            SootMethod curMethod = evt.getEventHandler();
            curLine+="\n\t\t"+curMethod.toString();
          }
          bw.write("\t"+curLine+"\n\n");
        }
        bw.write(" Type 1 Path End\n");
      }

      bw.close();

    }catch (IOException e){
      e.printStackTrace();
    }
  }

  public void outputPathsToScreen(List<List<WTGEdge>> pathList){
    Logger.verb(this.getClass().getSimpleName(), "Monitored Paths: ");


    for (List<WTGEdge> curPath : pathList){
      Logger.verb(this.getClass().getSimpleName(), "\n Type Monitored Path: \n");

      for (WTGEdge curEdge: curPath){
        String curLine = curEdge.toString();
        Set<SootMethod> handlerMethodSet = curEdge.getEventHandlers();
	for (SootMethod curMethod: handlerMethodSet){
	  curLine+="\n\t\t"+curMethod.toString();
	}

        for (EventHandler evt : curEdge.getCallbacks()){
          SootMethod curMethod = evt.getEventHandler();
          curLine+="\n\t\t"+curMethod.toString();
        }
        Logger.verb(this.getClass().getSimpleName(), "\t" + curLine + "\n\n");
      }
      Logger.verb(this.getClass().getSimpleName(), " Type Monitored Path End\n");
    }
    Logger.verb(this.getClass().getSimpleName(), "\n");
  }

  public void saveEnergyIssuesC2ToFile(String fileName,
                                       Map<List<WTGEdge>, List<ResNode>> pathResMap){

    try {
      File outputFile = new File(fileName);
      if (!outputFile.exists()) {
        outputFile.createNewFile();
      } else {
        outputFile.delete();
        outputFile.createNewFile();
      }
      FileWriter fw = new FileWriter(outputFile.getAbsoluteFile());
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write("Paths with Energy Issues: \n");


      for (List<WTGEdge> curPath : pathResMap.keySet()){
        bw.write("\n Type 2 Path: \n");

        //HashMultimap<NObjectNode,Stmt> resourceMap = pathResMap.get(curPath);
        List<ResNode> resourceMap = pathResMap.get(curPath);

        for (ResNode curRes : resourceMap) {

          NObjectNode curObjNode = curRes.objectNode;
          Stmt curStmt = curRes.stmt;

          bw.write("  Leaking Node: " + curObjNode + " ON STMT " + curStmt + " Context: "+ curRes.context + "\n");
        }

        bw.write("\n");

        for (WTGEdge curEdge: curPath){
          String curLine = curEdge.toString();
          Set<SootMethod> handlerMethodSet = curEdge.getEventHandlers();
	  for (SootMethod curMethod: handlerMethodSet){
	    curLine+="\n\t\t"+curMethod.toString();
	  }

          for (EventHandler evt : curEdge.getCallbacks()){
            SootMethod curMethod = evt.getEventHandler();
            curLine+="\n\t\t"+curMethod.toString();
          }
          bw.write("\t"+curLine+"\n\n");
        }
        bw.write(" Type 2 Path End\n");
      }

      bw.close();

    }catch (IOException e){
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public void savePathsToFile(String fileName, List<List<WTGEdge>> Paths){

    try {
      File outputFile = new File(fileName);
      if (!outputFile.exists()) {
        outputFile.createNewFile();
      } else {
        outputFile.delete();
        outputFile.createNewFile();
      }
      FileWriter fw = new FileWriter(outputFile.getAbsoluteFile());
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write("Monitored Paths: \n");


      for (List<WTGEdge> curPath : Paths){
        bw.write("\n Type Monitored Path: \n");

        for (WTGEdge curEdge: curPath){
          String curLine = curEdge.toString();
          Set<SootMethod> handlerMethodSet = curEdge.getEventHandlers();
	  for (SootMethod curMethod: handlerMethodSet){
	    curLine+="\n\t\t"+curMethod.toString();
	  }

          for (EventHandler evt : curEdge.getCallbacks()){
            SootMethod curMethod = evt.getEventHandler();
            curLine+="\n\t\t"+curMethod.toString();
          }
          bw.write("\t"+curLine+"\n\n");
        }
        bw.write(" Type Monitored Path End\n");
      }

      bw.close();

    }catch (IOException e){
      e.printStackTrace();
    }
  }
}
