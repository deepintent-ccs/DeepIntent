/*
 * Ch6Client.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.LinkedList;
import java.util.Set;

import presto.android.Configs;
import presto.android.gui.GUIAnalysisClient;
import presto.android.gui.GUIAnalysisOutput;
import soot.SootClass;
import soot.jimple.Stmt;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class Ch6Client implements GUIAnalysisClient {
  GUIAnalysisOutput output;
  IntentAnalysisInterface intentAnalysis;

  @Override
  public void run(GUIAnalysisOutput output) {
    this.output = output;
    intentAnalysis = GatorIntentAnalysis.v(output);
    SootClass mainActivity = output.getMainActivity();
    String mainActivityName = mainActivity.getName();
    Set<String> covered = Sets.newHashSet(mainActivityName);
    LinkedList<String> work = Lists.newLinkedList();
    work.add(mainActivityName);
    while (!work.isEmpty()) {
      String sourceActivityClassName = work.removeFirst();
      for (Stmt startActivity : intentAnalysis.getActivityLaunchCalls(sourceActivityClassName)) {
        for (String targetActivityClassName : intentAnalysis.getTargetActivities(startActivity)) {
          if (!covered.contains(targetActivityClassName)) {
            covered.add(targetActivityClassName);
            work.addFirst(targetActivityClassName);
          }
        }
      }
    }
    int totalNum = output.getActivities().size();
    int coveredNum = covered.size();
    String percent = String.format("%.3f", (float) coveredNum / (float) totalNum * 100);
    System.out.printf("[ActivityCoverage] %s, %d, %d, %s%%\n",
        Configs.benchmarkName, coveredNum, totalNum, percent);
  }

}
