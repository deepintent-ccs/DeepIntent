package presto.android.gui.clients;

import presto.android.Configs;
import presto.android.Debug;
import presto.android.Logger;
import presto.android.gui.GUIAnalysisClient;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.clients.energy.EnergyAnalyzer;
import presto.android.gui.clients.energy.StatUtil;
import presto.android.gui.wtg.WTGAnalysisOutput;
import presto.android.gui.wtg.WTGBuilder;

import presto.android.gui.clients.energy.VarUtil;
import presto.android.gui.wtg.ds.WTG;

@SuppressWarnings("unused")
public class EnergyAnalysisClient implements GUIAnalysisClient {

  public EnergyAnalysisClient(){
      
  }

  @Override
  public void run(GUIAnalysisOutput output) {
    // Firstly do WTGAnalysis
    // set start time if only CCFG analysis is counted

    VarUtil.v().guiOutput = output;
    if (!Configs.trackWholeExec) {
      Logger.verb(getClass().getSimpleName(), "Pre-running time " + Debug.v().getExecutionTime());
      Debug.v().setStartTime();
    }
    WTGBuilder wtgBuilder = new WTGBuilder();
    wtgBuilder.build(output);
    
    long execTime = Debug.v().getExecutionTime();
    WTGAnalysisOutput wtgAO = new WTGAnalysisOutput(output, wtgBuilder);

    WTG wtg = wtgAO.getWTG();
    
    Logger.verb(getClass().getSimpleName(), "Start Energy Analysis");

    //Log the start time for the energy analysis.
    long startime = System.currentTimeMillis();
    //Output current Mem info
    long curUsedMem = StatUtil.v().getUsedMem();

    EnergyAnalyzer eAnalyzer = new EnergyAnalyzer(wtg, output, wtgAO);
    eAnalyzer.startTime = startime;
    //Start energy analysis
    eAnalyzer.analyze();
  }

}
