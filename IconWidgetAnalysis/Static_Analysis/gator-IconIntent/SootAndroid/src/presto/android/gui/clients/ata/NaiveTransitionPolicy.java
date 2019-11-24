/*
 * NaiveTransitionPolicy.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.ActivityStack;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.MethodSequence;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.TransitionPolicy;

public class NaiveTransitionPolicy implements TransitionPolicy {
  private static NaiveTransitionPolicy theInstance;

  public static NaiveTransitionPolicy v() {
    if (theInstance == null) {
      theInstance = new NaiveTransitionPolicy();
    }
    return theInstance;
  }

  @Override
  public ActivityStack constructNewStack(ActivityStack currentStack,
      LaunchConfiguration config, String targetActivity) {
    ActivityStack newStack = new ActivityStack(currentStack);
    newStack.push(targetActivity);
    return newStack;
  }

  @Override
  public MethodSequence getMethodSequence(ActivityStack currentStack,
      LaunchConfiguration config, String targetActivity) {
    MethodSequence seq = new MethodSequence();
    String a = currentStack.top();
    String b = targetActivity;
    seq.add(a, "onPause");
    seq.add(b, "<init>");
    seq.add(b, "onCreate");
    seq.add(b, "onStart");
    seq.add(b, "onResume");
    seq.add(a, "onStop");
    return seq;
  }

}
