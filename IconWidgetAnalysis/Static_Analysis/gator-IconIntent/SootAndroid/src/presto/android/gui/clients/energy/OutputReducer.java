package presto.android.gui.clients.energy;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.ds.WTGEdge;
import soot.SootMethod;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by zero on 11/1/15.
 */
public class OutputReducer {
  public enum PathType{
    C1, C2
  }

  public enum OutputType{
    COMPLETE, MINIMAL
  }
  class Path{
    NObjectNode targetWindow;
    List<List<WTGEdge>> pathList;
    Set<ResNode> resourceSet;
    List<WTGEdge> minPath;
    Integer severeLevel;

    public boolean compareSet(Set<ResNode> another){
      if (another.size() == resourceSet.size()){
        for (ResNode iNode : another){
          if (!this.resourceSet.contains(iNode))
            return false;
        }
        return true;
      }
      return false;
    }

    public Path(NObjectNode window, Set<ResNode> resourceSet){
      this.targetWindow = window;
      this.resourceSet = resourceSet;
      this.pathList = Lists.newArrayList();
    }

    public void addPath(List<WTGEdge> l){
      if (minPath == null){
        minPath = l;
      }else if (minPath.size() > l.size()){
        minPath = l;
      }
      pathList.add(l);
    }

    public void calculateSevereLevel(){
      severeLevel = 0;
      if (!resourceSet.isEmpty()){
        Map<ResNode, Integer> sMap = VarUtil.v().severeRateMap;
        for (ResNode curRes : resourceSet){
          if (sMap.containsKey(curRes)){
            Integer i = sMap.get(curRes);
            if (i != null && i == 1)
              severeLevel = 1;
          }
        }
      }
    }
  }

  HashMultimap<NObjectNode, Path> mData;
  PathType mCategoryType;
  Map<List<WTGEdge>, List<ResNode>> mPathResMap;
  public String absPath = "";

  public OutputReducer(PathType c){
    this.mData = HashMultimap.create();
    this.mCategoryType = c;
  }

  public int getUniqueIssues(){
    int sum = 0;
    for (NObjectNode curObjNode : this.mData.keySet()){
      sum += this.mData.get(curObjNode).size();
    }
    return sum;
  }

  public void parseOutput(Map<List<WTGEdge>, List<ResNode>> pathResMap){
    this.mPathResMap = pathResMap;
    for (List<WTGEdge> curPath : pathResMap.keySet()){
      //While very unlikely. Test if the path is empty
      if (curPath.isEmpty())
        continue;

      NObjectNode curObjectNode = curPath.get(0).getTargetNode().getWindow();
      Set<ResNode> curResSet = Sets.newHashSet(pathResMap.get(curPath));
      if (!mData.containsKey(curObjectNode)){
        Path curPathNode = new Path(curObjectNode, curResSet);
        curPathNode.addPath(curPath);
        mData.put(curObjectNode, curPathNode);
      }else{
        //It does have an entry for this Node
        Set<Path> curPathSet = mData.get(curObjectNode);
        Path targetPathNode = null;
        for (Path curPathNode : curPathSet){
          if (curPathNode.compareSet(curResSet)){
            targetPathNode = curPathNode;
            break;
          }
        }
        if (targetPathNode == null){
          targetPathNode = new Path(curObjectNode, curResSet);
          targetPathNode.addPath(curPath);
          mData.put(curObjectNode, targetPathNode);
        }else{
          targetPathNode.addPath(curPath);
        }
      }
    }
  }

  private String parseLeakingResNode(Set<ResNode> resSet){
    StringBuilder sb = new StringBuilder();
    for (ResNode curNode : resSet){
      sb.append("\t" + curNode.toString() + "\n");
    }
    return sb.toString();
  }

  public void outputToFile(String fileName, OutputType type){
    if (this.mPathResMap == null)
      return;
    if (type ==  OutputType.COMPLETE){
      if (this.mCategoryType == PathType.C1)
        OutputUtil.v().saveEnergyIssuesC1ToFile(fileName, this.mPathResMap);
      else if (this.mCategoryType == PathType.C2)
        OutputUtil.v().saveEnergyIssuesC2ToFile(fileName, this.mPathResMap);
      return;
    }else if (type == OutputType.MINIMAL){
      File outputFile;
      try{
        outputFile = new File(fileName);
        if (!outputFile.exists()) {
          outputFile.createNewFile();
        } else {
          outputFile.delete();
          outputFile.createNewFile();
        }
        absPath = outputFile.getAbsolutePath();
        FileWriter fw = new FileWriter(outputFile.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("Paths with Energy Issues: \n");
        for (NObjectNode windowNode : this.mData.keySet()){
          bw.write("\nFor Window: " + windowNode.toString() + "\n");
          Set<Path> curPathSet = this.mData.get(windowNode);
          for (Path curPathNode : curPathSet){
            //Output the leaking ResNodes
            bw.write("\n\tLeaking Resources: \n");
            bw.write(parseLeakingResNode(curPathNode.resourceSet));
            curPathNode.calculateSevereLevel();
            if (curPathNode.severeLevel == 0)
              bw.write("\n\tSevere Rating: High\n");
            else if (curPathNode.severeLevel == 1)
              bw.write("\n\tSevere Rating: Low\n");

            //Output the minimal Path
            bw.write("\n\tLeaking Path:\n");
            for (WTGEdge curEdge: curPathNode.minPath){
              //String curLine = curEdge.toString();
              StringBuilder sb = new StringBuilder();
              sb.append("\t" + curEdge.toString());
              Set<SootMethod> handlerMethodSet = curEdge.getEventHandlers();
              for (SootMethod curMethod: handlerMethodSet){
                sb.append("\n\t\t"+curMethod.toString());
              }

              for (EventHandler evt : curEdge.getCallbacks()){
                SootMethod curMethod = evt.getEventHandler();
                sb.append("\n\t\t"+curMethod.toString());
              }
              sb.append("\n\n");
              bw.write(sb.toString());
            }
            bw.write("\tLeaking Path End\n");
          }
        }
        bw.close();
      }catch(Exception e){
        e.printStackTrace();
      }
    }
  }



}
