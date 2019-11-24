/*
 * KLimitActivityTransitionAnalysis.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import presto.android.MultiMapUtil;
import presto.android.gui.clients.ata.ActivityTransitionGraph.Edge;
import presto.android.gui.clients.ata.ActivityTransitionGraph.Node;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class KLimitActivityTransitionAnalysis
  implements ActivityTransitionAnalysisInterface {

  public int limit;

  public KLimitActivityTransitionAnalysis(int limit) {
    this.limit = limit;
  }

  // Code generation data structures (bad design...)
  Map<String, Set<MethodSequence>> activityCode = Maps.newHashMap();

  @Override
  public Set<ActivityStack> buildASTGAndGetPossibleStacks(String mainActivity,
      ActivityTransitionGraph atg, ActivityStackTransitionGraph astg,
      TransitionPolicy policy) {
    ActivityStack initStack = new ActivityStack(mainActivity);
    astg.setInitStack(initStack);
    Set<ActivityStack> stacks = Sets.newHashSet(initStack);
    LinkedList<ActivityStack> worklist = Lists.newLinkedList();
    worklist.add(initStack);
    while (!worklist.isEmpty()) {
      ActivityStack currentStack = worklist.remove();
      String currentActivity = currentStack.top();
      Node currentNode = atg.getNode(currentActivity);
      for (Edge edge : currentNode.outgoingEdges) {
        Node targetNode = edge.target;
        String targetActivity = targetNode.activityClassName;
        // Look at launches
        for (LaunchConfiguration config : edge.configs) {
          ActivityStack newStack = policy.constructNewStack(currentStack,
              config, targetActivity);

          int count = count(newStack, targetActivity);
          if (count <= limit) {
//
//            String naivePreviousActivity = newStack.naivePreviousActivity();
//            if (naivePreviousActivity != null
//                && !currentActivity.equals(naivePreviousActivity)) {
//              System.out.println("   !!! " + currentStack + " ===> " + newStack);
//            }

            MethodSequence methodSequence =
                policy.getMethodSequence(currentStack, config, targetActivity);
            astg.findOrCreateEdgeWithMethodSeq(
                astg.getNode(currentStack),
                astg.getNode(newStack),
                methodSequence,
                config);
            MultiMapUtil.addKeyAndHashSetElement(
                activityCode, currentStack.top(), methodSequence);
            if (!stacks.contains(newStack)) {
              stacks.add(newStack);
              worklist.add(newStack);
            }
          }
        }
      } // edges
      // Look at terminations
      if (currentStack.size() == 1) {
        continue;
      }
      ActivityStack newStack = new ActivityStack(currentStack);
      newStack.pop();
      String a = currentStack.top();
      String b = newStack.top();
      MethodSequence methodSequence =
          new MethodSequence()
          .add(a, "onPause")
          .add(b, "onRestart")
          .add(b, "onStart")
          .add(b, "onResume")
          .add(a, "onStop")
          .add(a, "onDestroy");
      astg.findOrCreateEdgeWithMethodSeq(
          astg.getNode(currentStack),
          astg.getNode(newStack),
          methodSequence,
          LaunchConfiguration.FINISH_CONFIGURATION);
      MultiMapUtil.addKeyAndHashSetElement(
          activityCode, currentStack.top(), methodSequence);
      if (!stacks.contains(newStack)) {
        stacks.add(newStack);
        worklist.add(newStack);
      }
    }

//    codeGen();
    return stacks;
  }

  void codeGen() {
    for (Map.Entry<String, Set<MethodSequence>> entry : activityCode.entrySet()) {
      String act = entry.getKey();
      System.out.println("*** " + act);
      for (MethodSequence seq : entry.getValue()) {
        System.out.println("  * " + seq);
      }
    }
  }

  /*
   * Count the number of occurrences of the specified activity in the
   * specified stack.
   */
  int count(ActivityStack stack, String activity) {
    int counter = 0;
    for (String s : stack.topToBottomActivities) {
      if (s.equals(activity)) {
        counter++;
      }
    }
    return counter;
  }

}
