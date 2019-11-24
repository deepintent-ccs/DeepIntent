/*
 * CFGWorker.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.parallel;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import presto.android.Logger;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.wtg.analyzer.CFGAnalyzer;
import presto.android.gui.wtg.analyzer.CFGAnalyzerInput;
import presto.android.gui.wtg.analyzer.CFGAnalyzerOutput;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;

public class CFGWorker implements Runnable {
  public CFGWorker(
      CFGScheduler scheduler,
      GUIAnalysisOutput guiOutput,
      FlowgraphRebuilder flowgraphRebuilder) {
    this.scheduler = scheduler;
    this.cfgAnalyzer = new CFGAnalyzer(guiOutput, flowgraphRebuilder);
    this.output = Maps.newHashMap();
  }

  @Override
  public void run() {
    this.doTask();
    // unmark task when it is finished
    unsetTask();
    try {
      scheduler.workerPool.put(this);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void setTask(CFGAnalyzerInput input) {
    this.input = input;
  }

  private void unsetTask() {
    this.input = null;
  }

  public void doTask() {
    Preconditions.checkNotNull(input, "[Error]: cfg analyzer input is null");
    if (output.containsKey(input)) {
      Logger.err(getClass().getSimpleName(), "cfg analyzer input has been proccessed: " + input);
    }
    CFGAnalyzerOutput partialOutput = cfgAnalyzer.analyzeCallbackMethod(
        input.widget, input.handler, input.stmtFilter);
    output.put(input, partialOutput);
  }

  public Map<CFGAnalyzerInput, CFGAnalyzerOutput> getOutput() {
    return output;
  }

  private final CFGScheduler scheduler;
  private CFGAnalyzerInput input;
  private Map<CFGAnalyzerInput, CFGAnalyzerOutput> output;
  // initialize cfg edge builder
  private CFGAnalyzer cfgAnalyzer;
}
