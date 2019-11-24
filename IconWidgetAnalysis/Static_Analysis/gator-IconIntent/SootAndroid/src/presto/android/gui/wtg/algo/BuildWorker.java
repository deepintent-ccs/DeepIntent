/*
 * BuildWorker.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.algo;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import presto.android.Logger;

public class BuildWorker implements Runnable {
  public BuildWorker(BuildScheduler scheduler) {
    Preconditions.checkNotNull(scheduler);
    this.scheduler = scheduler;
    this.output = Maps.newHashMap();
  }

  public void run() {
    this.doTask();
    // unmark task when it is finished
    unsetTask();
    try {
      scheduler.threadPool.put(this);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void setTask(AlgorithmInput input) {
    this.input = input;
  }

  private void unsetTask() {
    this.input = null;
  }

  public void doTask() {
    Preconditions.checkNotNull(input);
    if (output.containsKey(input)) {
      Logger.err(getClass().getSimpleName(), "input has been proccessed: " + input);
    }
    Algorithm algo = input.algo;
    AlgorithmOutput partialOutput = algo.execute(input);
    output.put(input, partialOutput);
  }

  public Map<AlgorithmInput, AlgorithmOutput> getOutput() {
    return output;
  }

  private final BuildScheduler scheduler;
  private AlgorithmInput input;
  private Map<AlgorithmInput, AlgorithmOutput> output;
}
