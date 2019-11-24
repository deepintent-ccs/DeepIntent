package presto.android.gui.clients.energy;

import com.google.common.collect.Sets;
import presto.android.Logger;
import presto.android.gui.graph.NObjectNode;

import java.util.Set;

/**
 * Created by zero on 11/25/15.
 */
public class ExperimentStat {
  private static ExperimentStat instance;

  private Set<NObjectNode> mtListeners;
  private Set<NObjectNode> mlListeners;
  private Set<NObjectNode> mp1Listeners;
  private Set<NObjectNode> mp2Listeners;

  public static ExperimentStat v(){
    if (instance == null)
      instance = new ExperimentStat();
    return instance;
  }

  public void addResNode(ResNode r){
    NObjectNode curObj = r.objectNode;
    mtListeners.add(r.objectNode);
  }

  public void addP1Path(ResNode r){
    NObjectNode curObj = r.objectNode;
    mp1Listeners.add(curObj);
    mlListeners.add(curObj);
  }

  public void addP2Path(ResNode r){
    NObjectNode curObj = r.objectNode;
    mp2Listeners.add(curObj);
    mlListeners.add(curObj);
  }

  public void outputToScreen(){
    Logger.verb("STAT", "Total Listeners & Leaking Listeners & Leaking P1 & Leaking P2");
    Logger.verb("STAT", "" + mtListeners.size() + " &"
            + mlListeners.size() + " &"
            + mp1Listeners.size() + " &"
            + mp2Listeners.size()
    );
  }

  public void outputToScreenDetailed(){
    Logger.verb("LEAKSTAT", "Total Listeners");
    for (NObjectNode o : mtListeners) {
      Logger.verb("LEAKSTAT", o.toString());
    }
    Logger.verb("LEAKSTAT", "Leaking Listeners");
    for (NObjectNode o : mlListeners) {
      Logger.verb("LEAKSTAT", o.toString());
    }
    Logger.verb("LEAKSTAT", "Leaking P1 Listeners");
    for (NObjectNode o : mp1Listeners) {
      Logger.verb("LEAKSTAT", o.toString());
    }
    Logger.verb("LEAKSTAT", "Leaking P2 Listeners");
    for (NObjectNode o : mp2Listeners) {
      Logger.verb("LEAKSTAT", o.toString());
    }
  }

  private ExperimentStat(){
    mtListeners = Sets.newHashSet();
    mlListeners = Sets.newHashSet();
    mp1Listeners = Sets.newHashSet();
    mp2Listeners = Sets.newHashSet();
  }

}
