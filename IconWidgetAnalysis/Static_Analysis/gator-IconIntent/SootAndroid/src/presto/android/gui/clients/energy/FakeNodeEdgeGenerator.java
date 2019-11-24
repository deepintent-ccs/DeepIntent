package presto.android.gui.clients.energy;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.listener.EventType;

import presto.android.gui.wtg.flowgraph.NFakeType1Node;

import presto.android.gui.wtg.EventHandler;
import presto.android.gui.wtg.RootTag;
import presto.android.gui.wtg.StackOperation;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import soot.SootMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by zero on 7/29/15.
 */
public class FakeNodeEdgeGenerator {
  private FakeNodeEdgeGenerator(){

  }
  private static FakeNodeEdgeGenerator instance = null;

  public static FakeNodeEdgeGenerator v(){
    if (instance == null){
      instance = new FakeNodeEdgeGenerator();
    }
    return instance;
  }

  public List<SootMethod> genStartUpCallbacksforNode(WTGNode entryNode, GUIAnalysisOutput guiOutput){
    ArrayList<SootMethod> ret = Lists.newArrayList();

    Set<SootMethod> handlers = guiOutput.getLifecycleHandlers(entryNode.getWindow().getClassType());
    //Find onCreate
    SootMethod onCreate = getCallbacksFromSet(handlers, "void onCreate(android.os.Bundle)");
    if (onCreate != null){
      ret.add(onCreate);
    }
    //Find onStart
    SootMethod onStart = getCallbacksFromSet(handlers, "void onStart()");
    if (onStart != null){
      ret.add(onStart);
    }
    //Find onResume
    SootMethod onResume = getCallbacksFromSet(handlers, "void onResume()");
    if (onResume != null){
      ret.add(onResume);
    }
    return ret;
  }

  public List<SootMethod> genCloseUpCallbacksforNode(WTGNode entryNode, GUIAnalysisOutput guiOutput){
    ArrayList<SootMethod> ret = Lists.newArrayList();
    Set<SootMethod> handlers = guiOutput.getLifecycleHandlers(entryNode.getWindow().getClassType());

    //Find onPause
    SootMethod onPause = getCallbacksFromSet(handlers, "void onPause()");
    if (onPause != null){
      ret.add(onPause);
    }

    //Find void onStop()
    SootMethod onStop = getCallbacksFromSet(handlers, "void onStop()");
    if (onStop != null){
      ret.add(onStop);
    }

//    Find void onDestroy()
    SootMethod onDestroy = getCallbacksFromSet(handlers, "void onDestroy()");
    if (onDestroy != null){
      ret.add(onDestroy);
    }

    return ret;

  }

  public SootMethod getCallbacksFromSet(Set<SootMethod> mtdSet, String subsig){
    if (mtdSet == null){
      return null;
    }

    for (SootMethod curMethod :mtdSet){
      if (curMethod.getSignature().contains(subsig)){
        return curMethod;
      }
    }
    return null;
  }

  /**
   * Generate fake edges to orphan nodes
   * @param entryNode Orphan node
   * @return The entry edge
   */
  public WTGEdge genFakeType1Path(WTGNode entryNode){
    Preconditions.checkNotNull(VarUtil.v().guiOutput);
    GUIAnalysisOutput guiOutput = VarUtil.v().guiOutput;

    if (VarUtil.v().fakeWTGNode == null){
      VarUtil.v().fakeWTGNode = new WTGNode(NFakeType1Node.NODE);

    }
    List<WTGEdge> ret = Lists.newArrayList();

    Set<EventHandler> inHandlerSet = Sets.newHashSet();
    Set<SootMethod> inHandlerMethodSet = Sets.newHashSet();
    List<EventHandler> inCallbackList = Lists.newArrayList();
    List<SootMethod> inCallbacks = genStartUpCallbacksforNode(entryNode, guiOutput);
    for(SootMethod curMethod :inCallbacks){
      EventHandler curHandler = new EventHandler(entryNode.getWindow(),
              NFakeType1Node.NODE,
              EventType.implicit_launch_event,
              curMethod);
      inCallbackList.add(curHandler);
    }
    if (inCallbackList.isEmpty()){
      return null;
    }

    EventHandler startActivity = new EventHandler(
            entryNode.getWindow(),
            entryNode.getWindow(),
            EventType.implicit_launch_event, null);
    inHandlerSet.add(startActivity);

    List<StackOperation> inStackOp = Lists.newArrayList();
    inStackOp.add(new StackOperation(StackOperation.OpType.push, entryNode.getWindow()));

//    WTGNode entryWTGNode = VarUtil.v().staticModelWTGNodeMap.get(entryNode);



    WTGEdge inWTGEdge = new WTGEdge(VarUtil.v().fakeWTGNode,
            entryNode,
            inHandlerSet,
            RootTag.start_activity,
            inStackOp,
            inCallbackList
    );

    Set<EventHandler> outHandlerSet = Sets.newHashSet();
    Set<SootMethod> outHandlerMethodSet = Sets.newHashSet();
    List<EventHandler> outCallbackList = Lists.newArrayList();
    List<SootMethod> outCallbacks = genCloseUpCallbacksforNode(entryNode, guiOutput);
    for(SootMethod curMethod :outCallbacks){
      EventHandler curHandler = new EventHandler(entryNode.getWindow(),
              entryNode.getWindow(),
              EventType.implicit_back_event,
              curMethod);
      outCallbackList.add(curHandler);
    }

    EventHandler shutActivity = new EventHandler(
            NFakeType1Node.NODE,
            NFakeType1Node.NODE,
            EventType.implicit_back_event,
            null);

    outHandlerSet.add(shutActivity);

    List<StackOperation> outStackOp = Lists.newArrayList();
    outStackOp.add(new StackOperation(StackOperation.OpType.pop, entryNode.getWindow()));

    WTGEdge outWTGEdge = new WTGEdge(
            entryNode,
            VarUtil.v().fakeWTGNode,
            outHandlerSet,
            RootTag.implicit_back,
            outStackOp,
            outCallbackList
    );


    entryNode.addInEdge(inWTGEdge);
    entryNode.addOutEdge(outWTGEdge);


    ret.add(inWTGEdge);
    ret.add(outWTGEdge);

    return inWTGEdge;
  }



}
