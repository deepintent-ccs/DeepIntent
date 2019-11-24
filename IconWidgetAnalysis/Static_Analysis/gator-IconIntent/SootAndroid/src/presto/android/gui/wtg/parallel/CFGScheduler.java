/*
 * CFGScheduler.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.parallel;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import presto.android.Configs;
import presto.android.Logger;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.wtg.analyzer.CFGAnalyzerInput;
import presto.android.gui.wtg.analyzer.CFGAnalyzerOutput;
import presto.android.gui.wtg.flowgraph.FlowgraphRebuilder;

public class CFGScheduler {
  public CFGScheduler(
      GUIAnalysisOutput guiOutput,
      FlowgraphRebuilder flowgraphRebuilder) {
    Preconditions.checkNotNull(guiOutput, "[Error]: guiOutput initialization parameter is null");
    Preconditions.checkNotNull(flowgraphRebuilder, "[Error]: flowgraph rebuilder initialization parameter is null");
    this.guiOutput = guiOutput;
    this.flowgraphRebuilder = flowgraphRebuilder;
    this.workerPool = new ArrayBlockingQueue<CFGWorker>(Configs.workerNum);
    initializeScheduler();
  }

  private void initializeScheduler() {
    for (int i = 0; i < Configs.workerNum; i++) {
      try {
        workerPool.put(new CFGWorker(this, guiOutput, flowgraphRebuilder));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public Map<CFGAnalyzerInput, CFGAnalyzerOutput> schedule(Set<CFGAnalyzerInput> inputs) {
    // the underline idea is to parallelise analyzeCallbackMethod
    // and leave the rest executed in sequence
    for (CFGAnalyzerInput input : inputs) {
      CFGWorker worker = null;
      try {
        worker = workerPool.take();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      if (worker == null) {
        Logger.err(getClass().getSimpleName(), "can not grab existing worker to do task");
      }
      // set the work to do + the input data
      worker.setTask(input);
      // start new thread to do this job
      new Thread(worker).start();
    }
    // wait every thread to finish
    while (workerPool.size() != Configs.workerNum) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    Map<CFGAnalyzerInput, CFGAnalyzerOutput> aggregateOutput = Maps.newHashMap();
    // aggregate the output from all the threads
    for (CFGWorker worker : workerPool) {
      Map<CFGAnalyzerInput, CFGAnalyzerOutput> partialOutput = worker.getOutput();
      for (CFGAnalyzerInput input : partialOutput.keySet()) {
        if (aggregateOutput.containsKey(input)) {
          Logger.err(getClass().getSimpleName(), "cfg analyzer input has been processed: " + input);
        }
        CFGAnalyzerOutput output = partialOutput.get(input);
        aggregateOutput.put(input, output);
      }
    }
    return aggregateOutput;
  }

  // thread pool
  final BlockingQueue<CFGWorker> workerPool;
  private final GUIAnalysisOutput guiOutput;
  private final FlowgraphRebuilder flowgraphRebuilder;
}
