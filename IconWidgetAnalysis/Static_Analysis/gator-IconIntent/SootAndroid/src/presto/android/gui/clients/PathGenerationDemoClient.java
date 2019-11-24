package presto.android.gui.clients;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import presto.android.Logger;
import presto.android.gui.GUIAnalysisClient;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.clients.energy.DFSGenericPathGenerator;
import presto.android.gui.clients.energy.IPathFilter;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.WTGAnalysisOutput;
import presto.android.gui.wtg.WTGBuilder;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;

import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Created by zero on 2/24/16.
 */
public class PathGenerationDemoClient implements GUIAnalysisClient {
  @Override
  public void run(GUIAnalysisOutput output) {

    //Perform WTG Construction
    WTGBuilder wtgBuilder = new WTGBuilder();
    wtgBuilder.build(output);
    WTGAnalysisOutput wtgAO = new WTGAnalysisOutput(output, wtgBuilder);
    WTG wtg = wtgAO.getWTG();

    //Create a placeholder filter class
    IPathFilter ph = new IPathFilter() {
      @Override
      public boolean match(List<WTGEdge> P, Stack<NObjectNode> S) {
        return true;
      }

      @Override
      public String getFilterName() {
        return "PlaceHolder";
      }
    };

    List<IPathFilter> pathFilterList = Lists.newArrayList();
    pathFilterList.add(ph);

    //Create Initial Edges.
    //The path generation will begin from these
    //Initial Edges

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
      initEdges.addAll(validInboundEdges);
    }

    Logger.verb("PathGenDemo", "Total Init Edges: " + initEdges.size());

    //Create Output Map
    Map<String, List<List<WTGEdge>>> outputMap = Maps.newHashMap();

    DFSGenericPathGenerator dg = DFSGenericPathGenerator.create(
            pathFilterList, null, initEdges, outputMap, false,false, 3);

    dg.doPathGeneration();

    Logger.verb("PathGenDemo", "K = " + 3 );
    Logger.verb("PathGenDemo", "Total path count: " + outputMap.get(ph.getFilterName()).size());
  }
}
