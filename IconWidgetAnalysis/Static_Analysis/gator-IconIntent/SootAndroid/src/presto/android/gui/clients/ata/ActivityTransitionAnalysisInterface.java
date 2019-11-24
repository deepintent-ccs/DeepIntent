/*
 * ActivityTransitionAnalysisInterface.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;

import soot.toolkits.scalar.Pair;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public interface ActivityTransitionAnalysisInterface {
  class ActivityStack {
    LinkedList<String> topToBottomActivities;

    public ActivityStack() {
      this(Lists.<String>newLinkedList());
    }

    public ActivityStack(LinkedList<String> activities) {
      this.topToBottomActivities = activities;
    }

    public ActivityStack(String activity) {
      this();
      topToBottomActivities.add(activity);
    }

    public ActivityStack(ActivityStack otherStack) {
      topToBottomActivities = Lists.newLinkedList(otherStack.topToBottomActivities);
    }

    public String top() {
      if (topToBottomActivities.isEmpty()) {
        return null;
      } else {
        return topToBottomActivities.getFirst();
      }
    }

    public void push(String activity) {
      topToBottomActivities.addFirst(activity);
    }

    public String pop() {
      if (topToBottomActivities.isEmpty()) {
        return null;
      }
      return topToBottomActivities.removeFirst();
    }

    public int count(String activity) {
      int c = 0;
      for (String s : topToBottomActivities) {
        if (s.equals(activity)) {
          c++;
        }
      }
      return c;
    }

    public int size() {
      return topToBottomActivities.size();
    }

    public int find(String activity) {
      int found = -1;
      int i = 0;
      for (String a : topToBottomActivities) {
        if (a.equals(activity)) {
          found = i;
          break;
        }
        i++;
      }
      return found;
    }

    // top but one
    public String naivePreviousActivity() {
      if (topToBottomActivities.size() <= 1) {
        return null;
      } else {
        return topToBottomActivities.get(1);
      }
    }

    public String get(int i) {
      if (i < 0 || i >= topToBottomActivities.size()) {
        throw new RuntimeException();
      }
      return topToBottomActivities.get(i);
    }

    public boolean clearTop(String activity) {
      boolean found = false;
      for (String currentActivity : topToBottomActivities) {
        if (currentActivity.equals(activity)) {
          found = true;
        }
      }
      if (found) {
        while (!topToBottomActivities.getFirst().equals(activity)) {
          topToBottomActivities.removeFirst();
        }
      }
      return found;
    }

    public boolean reorderToFront(String activity) {
      int found = -1;
      int i = 0;
      for (String currentActivity : topToBottomActivities) {
        if (currentActivity.equals(activity)) {
          found = i;
          break;
        }
        i++;
      }
      if (found != -1) {
        topToBottomActivities.addFirst(topToBottomActivities.remove(found));
        return true;
      } else {
        return false;
      }
    }

    @Override
    public String toString() {
      return
          "|<-top- "
          + Joiner.on(',').join(topToBottomActivities)
          + " -bot->|";
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(topToBottomActivities.toArray());
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ActivityStack)) {
        return false;
      }
      ActivityStack otherStack = (ActivityStack) other;
      return topToBottomActivities.equals(otherStack.topToBottomActivities);
    }
  }

  public class MethodSequence {
    public ArrayList<Pair<String, String>> sequence = Lists.newArrayList();

    public MethodSequence add(Pair<String, String> call) {
      sequence.add(call);
      return this;
    }

    public MethodSequence add(String className, String methodName) {
      sequence.add(new Pair<String, String>(className, methodName));
      return this;
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer("<");
      for (int i = 0; i < sequence.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        Pair<String, String> call = sequence.get(i);
        sb.append(call.getO1()).append("::").append(call.getO2());
      }
      sb.append(">");
      return sb.toString();
    }
  }

  interface TransitionPolicy {
    public ActivityStack constructNewStack(ActivityStack currentStack,
        LaunchConfiguration config, String targetActivity);

    public MethodSequence getMethodSequence(ActivityStack currentStack,
        LaunchConfiguration config, String targetActivity);
  }

  /*
   * Given the main activity and the ActivityTransitionGraph, what is the set
   * of possible ActivityStack's.
   */
  public Set<ActivityStack> buildASTGAndGetPossibleStacks(
      String mainActivity, ActivityTransitionGraph atg,
      ActivityStackTransitionGraph astg, TransitionPolicy policy);
}
