/*
 * MethodSequenceKLimitActivityTransitionAnalysis.java - part of the GATOR project
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

import presto.android.Configs;
import presto.android.Hierarchy;
import presto.android.gui.clients.ata.ActivityTransitionGraph.Edge;
import presto.android.gui.clients.ata.ActivityTransitionGraph.Node;
import soot.Scene;
import soot.SootClass;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class MethodSequenceKLimitActivityTransitionAnlaysis implements
    ActivityTransitionAnalysisInterface {

  // Allow method sequences of length up to "limit"
  int limit;

  public MethodSequenceKLimitActivityTransitionAnlaysis(int limit) {
    this.limit = limit;
  }

  Map<Integer, Set<Path>> histogram = Maps.newHashMap();

  void printHistogram() {
    long cumulative = 0;
    long c4 = 0;
    long c8 = 0;
    for (int i = 1; i <= limit; i++) {
      Set<Path> set = histogram.get(i);
      int count = (set == null ? 0 : set.size());
      cumulative += count;
      System.out.println(i + " -> " + count + " | " + cumulative);
      if (i == 4) {
        c4 = cumulative;
      } else if (i == 8) {
        c8 = cumulative;
      }
      if (set != null) {
        for (Path p : set) {
          System.out.println("[4RafabRe] " + p + " : " + i);
        }
      }
    }
    System.out.printf("[buDUh3za.%s] %d & %d\n", Configs.benchmarkName, c4, c8);
//    for (Path p : histogram.get(8)) {
//      System.out.println("[4RafabRe] " + p);
//    }
  }

  void add(Path p) {
    if (p.length() > limit) {
      return;
    }
    Integer length = p.length();
    Set<Path> set = histogram.get(length);
    if (set == null) {
      set = Sets.newHashSet();
      histogram.put(length, set);
    }
    set.add(p);
  }

  @Override
  public Set<ActivityStack> buildASTGAndGetPossibleStacks(String mainActivity,
      ActivityTransitionGraph atg, ActivityStackTransitionGraph unsedASTG,
      TransitionPolicy policy) {
    ActivityStack initStack = new ActivityStack(mainActivity);
//    astg.setInitStack(initStack);
    Set<ActivityStack> stacks = Sets.newHashSet(initStack);

    Path initPath = new Path();
    String s = dispatch(mainActivity, "onCreate");
    if (s != null) {
      initPath = initPath.extend(s);
      add(initPath);
    }
    s = dispatch(mainActivity, "onStart");
    if (s != null) {
      initPath = initPath.extend(s);
      add(initPath);
    }
    s = dispatch(mainActivity, "onResume");
    if (s != null) {
      initPath = initPath.extend(s);
      add(initPath);
    }

    LinkedList<Pair<ActivityStack, Path>> worklist = Lists.newLinkedList();
    worklist.add(new Pair<ActivityStack, Path>(initStack, initPath));
    while (!worklist.isEmpty()) {
      Pair<ActivityStack, Path> pair = worklist.removeFirst();
      ActivityStack currentStack = pair.getO1();
      Path currentPath = pair.getO2();
      String currentActivity = currentStack.top();
      Node currentNode = atg.getNode(currentActivity);
      for (Edge edge : currentNode.outgoingEdges) {
        Node targetNode = edge.target;
        String targetActivity = targetNode.activityClassName;
        // Look at launches
        for (LaunchConfiguration config : edge.configs) {
          ActivityStack newStack = policy.constructNewStack(currentStack,
              config, targetActivity);

          MethodSequence methodSequence =
              policy.getMethodSequence(currentStack, config, targetActivity);

          extendPath(currentPath, methodSequence, newStack, worklist);

//          astg.findOrCreateEdgeWithMethodSeq(
//              astg.getNode(currentStack),
//              astg.getNode(newStack),
//              methodSequence,
//              config);
//          stacks.add(newStack);
        }
      } // edges

      // rotate, homeAndBack, powerAndBack
      stackNotChange(currentPath, currentStack,
          rotateCalls, worklist);
      stackNotChange(currentPath, currentStack,
          homeCalls, worklist);
      stackNotChange(currentPath, currentStack,
          powerCalls, worklist);

      // Look at terminations
      if (currentStack.size() == 1) {
        // press BACK on main
        String sig = dispatch(mainActivity, "onPause");
        Path newPath = currentPath;
        if (sig != null) {
          newPath = newPath.extend(sig);
          add(newPath);
        }
        sig = dispatch(mainActivity, "onStop");
        if (sig != null) {
          newPath = newPath.extend(sig);
          add(newPath);
        }
        sig = dispatch(mainActivity, "onDestroy");
        if (sig != null) {
          newPath = newPath.extend(sig);
          add(newPath);
        }
      } else {
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

        extendPath(currentPath, methodSequence, newStack, worklist);
      }
    }
    printHistogram();
    return stacks;
  }

  void stackNotChange(Path currentPath, ActivityStack currentStack,
      String[] calls, LinkedList<Pair<ActivityStack, Path>> worklist) {
    String activity = currentStack.top();
    Path newPath = currentPath;
    int count = currentPath.length();
    for (String call : calls) {
      String sig = dispatch(activity, call);
      if (sig != null) {
        count++;
        if (count <= limit) {
          newPath = newPath.extend(sig);
          add(newPath);
        }
      }
    }

    if (count < limit && count > currentPath.length()) {
      worklist.add(new Pair<ActivityStack, Path>(currentStack, newPath));
    }
  }

  String[] rotateCalls = new String[] {
      "onPause", "onStop", "onDestroy",
      "onCreate", "onStart", "onResume",
  };

  String[] homeCalls = new String[] {
      "onPause", "onStop", "onRestart", "onStart", "onResume",
  };

  String[] powerCalls = new String[] {
      "onPause", "onResume",
  };

  void extendPath(Path currentPath, MethodSequence methodSequence,
      ActivityStack newStack, LinkedList<Pair<ActivityStack, Path>> worklist) {
    Path newPath = currentPath;
    int count = currentPath.length();
    for (Pair<String, String> call : methodSequence.sequence) {
      String className = call.getO1();
      String methodName = call.getO2();
      if (methodName.equals("<init>")) {
        continue;
      }
      String sig = dispatch(className, methodName);
      if (sig != null) {
        count++;
        if (count <= limit) {
          newPath = newPath.extend(sig);
          add(newPath);
        }
      }
    }
    if (count < limit) {
      worklist.add(new Pair<ActivityStack, Path>(newStack, newPath));
    }
  }

  Hierarchy hier = Hierarchy.v();

  String dispatch(String className, String methodName) {
    SootClass activity = Scene.v().getSootClass("android.app.Activity");
    String subsig = activity.getMethodByName(methodName).getSubSignature();
    SootClass receiverClass = Scene.v().getSootClass(className);
    SootClass matched = hier.matchForVirtualDispatch(subsig, receiverClass);
    if (matched.isApplicationClass()) {
      return matched.getMethod(subsig).getSignature();
    }
    return null;
  }
}
