/*
 * ActivityTransitionAnalysisClient.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import presto.android.Configs;
import presto.android.Debug;
import presto.android.Hierarchy;
import presto.android.gui.GUIAnalysisClient;
import presto.android.gui.GUIAnalysisOutput;
import presto.android.gui.JimpleUtil;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.ActivityStack;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.MethodSequence;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.TransitionPolicy;
import presto.android.gui.clients.ata.ActivityTransitionGraph.DotPrinter;
import presto.android.gui.clients.ata.ActivityTransitionGraph.Edge;
import presto.android.gui.clients.ata.ActivityTransitionGraph.Node;
import presto.android.xml.XMLParser;
import presto.android.xml.XMLParser.ActivityLaunchMode;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ActivityTransitionAnalysisClient implements GUIAnalysisClient {
  final boolean compareWithEpicc = false;

  GUIAnalysisOutput output;
  IntentAnalysisInterface intentAnalysis;
  ActivityTransitionGraph atg;
  ActivityTransitionAnalysisInterface activityTransitionAnalysis;

  class Result {
    int oneLimitNodes = -1;
    int oneLimitEdges = -1;
    int twoLimitNodes = -1;
    int twoLimitEdges = -1;

    // covered nodes & edges
    int atgNodes;
    int atgEdges;

    // time in ms
    String atgTime;
    String stgTime;
  }
  Result result = new Result();

  @Override
  public void run(GUIAnalysisOutput output) {
    this.output = output;
    intentAnalysis = GatorIntentAnalysis.v(output);
    long start = System.nanoTime();
    buildActivityTransitionGraph();
    long end = System.nanoTime();
    result.atgTime = String.format("%.2f", (end - start) * 1.0e-06);
    boolean dot = 3 > 2;
    if (dot) {
      try {
        printActivityTransitionGraphToDot();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    ActivityStackTransitionGraph astg = new ActivityStackTransitionGraph();
    DefaultTransitionPolicy defaultPolicy = DefaultTransitionPolicy.v();

    stackMeasurements(astg, defaultPolicy);
//    controlFlow(astg, defaultPolicy);

//    Set<ActivityStack> twoDefault =
//        performKLimitActivityTransitionAnalysis(2, defaultPolicy);
//
//    System.out.printf("[wuXaqEp3.%s] %-25s && %6d & %6d && %6s & %6s \\\\\n",
//        Configs.benchmarkName,
//        "\\texttt{" + Configs.benchmarkName + "}",
//        result.oneLimitNodes, result.oneLimitEdges,
//        result.twoLimitNodes == -1 ? "-" : "" + result.twoLimitNodes,
//        result.twoLimitEdges == -1 ? "-" : "" + result.twoLimitEdges);

    // Compare with "naive" analysis
//    NaiveTransitionPolicy naivePolicy = NaiveTransitionPolicy.v();
//    Set<ActivityStack> oneNaive =
//        performKLimitActivityTransitionAnalysis(1, naivePolicy);
//    Set<ActivityStack> twoNaive =
//        performKLimitActivityTransitionAnalysis(2, naivePolicy);
//
//    compareStacks(1, oneDefault, oneNaive);
//    compareStacks(2, twoDefault, twoNaive);
  }

  void stackMeasurements(ActivityStackTransitionGraph astg, DefaultTransitionPolicy defaultPolicy) {
    standardVersusNonStandard();

    long start = System.nanoTime();


    performKLimitActivityTransitionAnalysis(1, defaultPolicy, astg);
    long end = System.nanoTime();
    result.stgTime = String.format("%.2f", (end - start) * 1.0e-06);
    System.out.printf("[wuXaqEp3.%s] %-25s && %6s & %6s && %6d & %6d \\\\\n",
        Configs.benchmarkName,
        "\\texttt{" + Configs.benchmarkName + "}",
        result.atgNodes, result.atgEdges,
        result.oneLimitNodes, result.oneLimitEdges);

    lifecycleCallbackAnalysis(astg);

    measureInterestingStacks(astg);
  }

  void lifecycleCallbackAnalysis(ActivityStackTransitionGraph astg) {
    ActivityStack initStack = astg.getInitStack();
    String mainActivity = initStack.top();
    Path initPath = constructPath(new String[] {
        mainActivity, "onCreate",
        mainActivity, "onStart",
        mainActivity, "onResume",
    });
    class Work {
      ActivityStackTransitionGraph.Node stack;
      Path path;
      int edges;
      Work(ActivityStackTransitionGraph.Node stack, Path path, int edges) {
        this.stack = stack;
        this.path = path;
        this.edges = edges;
      }
    }
    // queue for bread-first search
    LinkedList<Work> worklist = Lists.newLinkedList();
    worklist.add(new Work(astg.getNode(initStack), initPath, 1));
    int limit = 8;
    Map<Integer, Set<Path>> edgeCountAndPaths = Maps.newHashMap();

    for (int i = 1; i <= limit; i++) {
      edgeCountAndPaths.put(i, Sets.<Path>newHashSet());
    }
    edgeCountAndPaths.get(1).add(initPath);

    int[] seqCounts = new int[limit + 1];
    int[] seqLengthCounts = new int[limit + 1];
    seqCounts[1] = 1;
    seqLengthCounts[1] = initPath.length();
    while (!worklist.isEmpty()) {
      Work work = worklist.removeFirst();
      ActivityStackTransitionGraph.Node stack = work.stack;
      Path path = work.path;
      int edges = work.edges;

      for (ActivityStackTransitionGraph.Edge e : stack.outgoingEdges) {
        ActivityStackTransitionGraph.Node target = e.target;
        for (MethodSequence seq : e.sequences) {
          ArrayList<String> calls = Lists.newArrayList();
          for (Pair<String, String> classAndMethod : seq.sequence) {
            String c = dispatch(classAndMethod.getO1(), classAndMethod.getO2());
            if (c != null) {
              calls.add(c);
            }
          }
          Path newPath = path.extend(calls);
          int newEdges = edges + 1;
          edgeCountAndPaths.get(newEdges).add(newPath);
          seqCounts[newEdges]++;
          seqLengthCounts[newEdges] += newPath.length();
          if (newEdges < limit) {
            worklist.add(new Work(target, newPath, newEdges));
          }
        }
      }
      if (stack.stack.size() == 1) {
        String top = stack.stack.top();
        Path newPath = extendPath(path, new String[] {
            top, "onPause",
            top, "onStop",
            top, "onDestroy",
        });
        int newEdges = edges + 1;
        seqCounts[newEdges]++;
        seqLengthCounts[newEdges] += newPath.length();
        edgeCountAndPaths.get(newEdges).add(newPath);
      }
    }

    float avg2 = (float) seqLengthCounts[2] / (float) seqCounts[2];
    float avg4 = (float) seqLengthCounts[4] / (float) seqCounts[4];
    float avg6 = (float) seqLengthCounts[6] / (float) seqCounts[6];
    float avg8 = (float) seqLengthCounts[8] / (float) seqCounts[8];
    System.out.printf("[thucR7dr.%s] %-25s && %4d & %5s && %4d & %5s && %7d & %5s && %7d & %5s \\\\\n",
        Configs.benchmarkName,
        "\\texttt{" + Configs.benchmarkName + "}",
        seqCounts[2], String.format("%.2f", avg2),
        seqCounts[4], String.format("%.2f", avg4),
        seqCounts[6], String.format("%.2f", avg6),
        seqCounts[8], String.format("%.2f", avg8));

    float[] avgs = new float[limit + 1];
    for (int len = 2; len <= limit; len += 2) {
      Set<Path> paths = edgeCountAndPaths.get(len);
      seqCounts[len] = paths.size();
      int total = 0;
      for (Path p : paths) {
        total += p.length();
      }
      avgs[len] = (float) total / (float) seqCounts[len];
    }
    System.out.printf("[theZe3ed.%s] %-25s && %4d & %5s && %4d & %5s && %7d & %5s && %7d & %5s \\\\\n",
        Configs.benchmarkName,
        "\\texttt{" + Configs.benchmarkName + "}",
        seqCounts[2], String.format("%.2f", avgs[2]),
        seqCounts[4], String.format("%.2f", avgs[4]),
        seqCounts[6], String.format("%.2f", avgs[6]),
        seqCounts[8], String.format("%.2f", avgs[8]));
  }

  Hierarchy hier = Hierarchy.v();
  SootClass activity = Scene.v().getSootClass("android.app.Activity");

  String dispatch(String className, String methodName) {
    String subsig = activity.getMethodByName(methodName).getSubSignature();
    SootClass receiverClass = Scene.v().getSootClass(className);
    SootClass matched = hier.matchForVirtualDispatch(subsig, receiverClass);
    if (matched.isApplicationClass()) {
      return matched.getMethod(subsig).getSignature();
    }
    return null;
  }

  ArrayList<String> constructAppCalls(String[] classAndMethods) {
    ArrayList<String> appCalls = Lists.newArrayList();
    for (int i = 0; i < classAndMethods.length; i += 2) {
      String className = classAndMethods[i];
      String methodName = classAndMethods[i + 1];
      String call = dispatch(className, methodName);
      if (call != null) {
        appCalls.add(call);
      }
    }
    return appCalls;
  }

  Path constructPath(String[] classAndMethods) {
    return new Path(constructAppCalls(classAndMethods));
  }

  Path extendPath(Path path, String[] classAndMethods) {
    return path.extend(constructAppCalls(classAndMethods));
  }

  void controlFlow(ActivityStackTransitionGraph astg, DefaultTransitionPolicy defaultPolicy) {
    int callLengthLimit = 8;
    MethodSequenceKLimitActivityTransitionAnlaysis methodSequenceAnalysis =
        new MethodSequenceKLimitActivityTransitionAnlaysis(callLengthLimit);
    String mainActivity = output.getMainActivity().getName();
    methodSequenceAnalysis.buildASTGAndGetPossibleStacks(mainActivity, atg, astg, defaultPolicy);
  }

  void measureInterestingStacks(ActivityStackTransitionGraph astg) {
    int total = 0;
    int interesting = 0;
    int relaxedInteresting = 0;
    for (ActivityStackTransitionGraph.Node source : astg.getNodes()) {
      String sourceActivity = source.stack.top();
      for (ActivityStackTransitionGraph.Edge edge : source.outgoingEdges) {
        ActivityStackTransitionGraph.Node target = edge.target;
        String targetActivity = target.stack.top();
        boolean selfTransition = sourceActivity.equals(targetActivity);
        for (LaunchConfiguration config : edge.configs) {
          if (config.isFinish()) {
            continue;
          }
          if (config.isNonStandard(selfTransition)
              && edge.source.stack.size() <= 3) {
            Debug.v().printf("%s | %s\n", edge, config);
          }
          total++;
          if (!hasFinishEdge(target, source)) {
            interesting++;
            if (!(config.isSet(LaunchConfiguration.FLAG_CLEAR_TOP)
                || config.isSet(LaunchConfiguration.FLAG_REORDER_TO_FRONT)
                || (config.isSet(LaunchConfiguration.FLAG_SINGLE_TOP)
                    && selfTransition))) {
              System.out.println(" * src: " + source);
              System.out.println(" * tgt: " + target);
              System.out.println(" * config: " + config);
              throw new RuntimeException();
            }

            if (hasFinishEdgeRelaxed(target, source)) {
              relaxedInteresting++;
            }
          }
        }
      }
    }
    System.out.println("[InterestingStacks] " + Configs.benchmarkName + ", "
        + interesting + " / " + total + " / " + (float) interesting / (float) total
        + " / " + relaxedInteresting);
    checkNonStandMatchingFinishInATG();
  }

  void checkNonStandMatchingFinishInATG() {
    int nonStandard = 0;
    int nonStandardNoMatching = 0;
    SootClass mainActivity = output.getMainActivity();
    String mainActivityClassName = mainActivity.getName();
    Set<String> covered = Sets.newHashSet(mainActivityClassName);
    LinkedList<String> worklist = Lists.newLinkedList();
    worklist.add(mainActivityClassName);
    while (!worklist.isEmpty()) {
      String activity = worklist.remove();
      Node node = atg.getNode(activity);
      for (Edge edge : node.outgoingEdges) {
        int count = atg.countNonStandard(edge);
        nonStandard += count;
        if (count > 0) {
          if (!atg.hasInverseFinishEdge(edge)) {
            System.out.println("{invert} " + edge);
            nonStandardNoMatching += count;
          }
        }

        String target = edge.target.activityClassName;
        if (covered.contains(target)) {
          continue;
        }
        covered.add(target);
        worklist.add(target);
      }
    }
    System.out.println("[CheckNonStandardOnATG] " + Configs.benchmarkName
        + ", " + nonStandard + ", " + nonStandardNoMatching);
  }

  boolean hasFinishEdge(ActivityStackTransitionGraph.Node source,
      ActivityStackTransitionGraph.Node target) {
    for (ActivityStackTransitionGraph.Edge edge : source.outgoingEdges) {
      for (LaunchConfiguration config : edge.configs) {
        if (config.isFinish()) {
          atg.addFinishEdge(edge.source.stack.top(), edge.target.stack.top());
        }
        if (config.isFinish() && edge.target.equals(target)) {
          return true;
        }
      }
    }
    return false;
  }

  boolean hasFinishEdgeRelaxed(ActivityStackTransitionGraph.Node source,
      ActivityStackTransitionGraph.Node target) {
    for (ActivityStackTransitionGraph.Edge edge : source.outgoingEdges) {
      for (LaunchConfiguration config : edge.configs) {
        if (config.isFinish() && edge.target.stack.top().equals(target.stack.top())) {
          return true;
        }
      }
    }
    return false;
  }

  void compareStacks(int k,
      Set<ActivityStack> defaultStack, Set<ActivityStack> naiveStack) {
    if (abortAnalysis(k)) {
      return;
    }
    System.out.printf("\n=== Comparing %d-limit stacks - default v.s. naive\n", k);
    for (ActivityStack s : defaultStack) {
      if (!naiveStack.contains(s)) {
        System.out.println("  - " + s);
      }
    }
    for (ActivityStack s : naiveStack) {
      if (!defaultStack.contains(s)) {
        System.out.println("  + " + s);
      }
    }
    System.out.println("===\n");
  }

  Set<ActivityStack> performKLimitActivityTransitionAnalysis(
      int k, TransitionPolicy policy, ActivityStackTransitionGraph astg) {
    if (abortAnalysis(k)) {
      return Collections.emptySet();
    }

    activityTransitionAnalysis = new KLimitActivityTransitionAnalysis(k);
    String mainActivity = output.getMainActivity().getName();
    Set<ActivityStack> stacks =
        activityTransitionAnalysis.buildASTGAndGetPossibleStacks(mainActivity, atg, astg, policy);
    System.out.printf("\n=== %d-limit possible ActivityStack's (Total# %d, %s)\n",
        k, stacks.size(), policy.getClass().getSimpleName());
    int maxStackDepth = 0;
    for (ActivityStack s : stacks) {
      int size = s.size();
      if (size > maxStackDepth) {
        maxStackDepth = size;
      }
    }
    System.out.printf("[preJA8as.%s] %d\n", Configs.benchmarkName, maxStackDepth);
    if (Configs.verbose) {
      for (ActivityStack s : stacks) {
        System.out.println("  * " + s);
      }
      System.out.println("===\n");
    }
    int nodes = 0;
    int edges = 0;
    int launchEdges = 0;
    for (ActivityStackTransitionGraph.Node node : astg.getNodes()) {
      nodes++;
      for (ActivityStackTransitionGraph.Edge edge : node.outgoingEdges) {
        edges += edge.configs.size();
        for (LaunchConfiguration config : edge.configs) {
          if (!config.isFinish()) {
            launchEdges++;
          }
        }
      }
    }
    System.out.println("launchEdges: " + launchEdges);
    if (k == 1) {
      result.oneLimitNodes = nodes;
      result.oneLimitEdges = edges;
    } else if (k == 2) {
      result.twoLimitNodes = nodes;
      result.twoLimitEdges = edges;
    }
    return stacks;
  }

  boolean abortAnalysis(int k) {
    if (k > 1) {
      if (Configs.benchmarkName.equals("FBReader")
          || Configs.benchmarkName.equals("XBMC")
          || Configs.benchmarkName.equals("NPR")) {
        return true;
      }
    }
    return false;
  }

  void buildActivityTransitionGraph() {
    atg = new ActivityTransitionGraph();
    // For each node, build the edges
    for (SootClass c : output.getActivities()) {
      String sourceActivityClassName = c.getName();
      Node source = atg.getNode(sourceActivityClassName);
      for (Stmt startActivity : intentAnalysis.getActivityLaunchCalls(sourceActivityClassName)) {
        for (String targetActivityClassName : intentAnalysis.getTargetActivities(startActivity)) {
          Node target = atg.getNode(targetActivityClassName);
          for (LaunchConfiguration config : intentAnalysis.getLaunchConfigurations(startActivity, targetActivityClassName)) {
            atg.findOrCreateEdgeWithConfig(source, target, config);
          }
        }
      }
    }
    // Print metric
    SootClass mainActivity = output.getMainActivity();
    String mainActivityClassName = mainActivity.getName();
    Set<String> covered = Sets.newHashSet(mainActivityClassName);
    LinkedList<String> worklist = Lists.newLinkedList();
    worklist.add(mainActivityClassName);
    int coveredEdges = 0;
    while (!worklist.isEmpty()) {
      String activity = worklist.remove();
      Node node = atg.getNode(activity);
//      coveredEdges += node.outgoingEdges.size();
      for (Edge edge : node.outgoingEdges) {
        coveredEdges += edge.configs.size();
        String target = edge.target.activityClassName;
        if (covered.contains(target)) {
          continue;
        }
        covered.add(target);
        worklist.add(target);
      }
    }
    int totalNum = atg.nodeCount();
    int coveredNum = covered.size();
    String percent = String.format("%.3f", (float) coveredNum / (float) totalNum * 100);
    System.out.printf("[ActivityCoverage] %s, %d, %d, %s%%\n",
        Configs.benchmarkName, coveredNum, totalNum, percent);

    result.atgNodes = coveredNum;
    result.atgEdges = coveredEdges;

//    printCoveredActivity(covered);
  }

  void printCoveredActivity(Set<String> covered) {
    for (String s : covered) {
      Debug.v().printf("[%s] %s\n", "5afEphuP", s);
    }
  }

  void standardVersusNonStandard() {
    int standard = 0;
    int nonStandard = 0;
    SootClass mainActivity = output.getMainActivity();
    String mainActivityClassName = mainActivity.getName();
    Set<String> covered = Sets.newHashSet(mainActivityClassName);
    LinkedList<String> worklist = Lists.newLinkedList();
    worklist.add(mainActivityClassName);
    while (!worklist.isEmpty()) {
      String activity = worklist.remove();
      Node node = atg.getNode(activity);
      for (Edge edge : node.outgoingEdges) {
        String target = edge.target.activityClassName;
        for (LaunchConfiguration config : edge.configs) {
          if (config.isInvalid()) {
            continue;
          }
          if (config.isSet(LaunchConfiguration.FLAG_CLEAR_TOP)
              || config.isSet(LaunchConfiguration.FLAG_REORDER_TO_FRONT)
              || (config.isSet(LaunchConfiguration.FLAG_SINGLE_TOP)
                  && activity.equals(target))) {
            nonStandard++;
          } else if (config.isSet(LaunchConfiguration.FLAG_STANDARD)
              || (config.isSet(LaunchConfiguration.FLAG_SINGLE_TOP)
                  && !activity.equals(target))) {
            standard++;
          } else {
            throw new RuntimeException();
          }
        }
        if (covered.contains(target)) {
          continue;
        }
        covered.add(target);
        worklist.add(target);
      }
    }
    int total = standard + nonStandard;
    float standardPercent = (float) standard / (float) total * 100f;
    float nonStandardPercent = 100f - standardPercent;
    System.out.printf("[StandardVersuseNonStandard] %s, %d, %s%%, %d, %s%%\n",
        Configs.benchmarkName,
        standard, String.format("%.2f", standardPercent),
        nonStandard, String.format("%.2f", nonStandardPercent));
  }

  void printActivityTransitionGraphToDot() throws Exception {
    DotPrinter printer = new DotPrinter();
    File file = File.createTempFile(Configs.benchmarkName + "-ATG-", ".dot");
    PrintWriter out = new PrintWriter(file);
    printer.print(atg, out);
    out.close();
    System.out.println(
        "\033[1;31mATG saved to " + file.getAbsolutePath() + "\033[0m");
  }

  // DEBUG
  void debugPrints() {
    // Some debug prints
//    printAllActivityAndLaunchModes();
//    printAllStartActivityAndTargets();
    printAll();
//    printATG();
  }

  void printATG() {
    for (SootClass c : output.getActivities()) {
      String sourceActivityClassName = c.getName();
      Node source = atg.getNode(sourceActivityClassName);
      System.out.println("* source: " + source);
      for (Edge edge : source.outgoingEdges) {
        System.out.println("  * target: " + edge.target);
        for (LaunchConfiguration config : edge.configs) {
          System.out.println("    * config: " + config);
        }
      }
    }
  }

  void printAll() {
    System.out.println("\n=== Activity and startActivity calls");
    for (SootClass activity : output.getActivities()) {
      String activityClassName = activity.getName();
      System.out.println("\n--- " + activityClassName);
      for (Stmt s : intentAnalysis.getActivityLaunchCalls(activityClassName)) {
        System.out.println("  * " + s + " @ " + JimpleUtil.v().lookup(s)
            + " | " + s.hashCode());
      }
    }
    System.out.println("\n=== startActivity call details");
    for (Stmt s : intentAnalysis.getAllActivityLaunchCalls()) {
      SootMethod m = JimpleUtil.v().lookup(s);
      System.out.println("\n--- " + s + " @ " + m + " | " + s.hashCode());
      for (String activity : intentAnalysis.getTargetActivities(s)) {
        // ignore
        if (activity.equals("ANY")) {
          continue;
        }
        if (Scene.v().getSootClass(activity).isPhantom()) {
          System.out.println("  * phantom-target: " + activity);
          continue;
        }
        System.out.println("  * target: " + activity);
        for (LaunchConfiguration config : intentAnalysis.getLaunchConfigurations(s, activity)) {
          System.out.println("    * " + config);
        }
      }
    }
  }

  void printAllActivityAndLaunchModes() {
    XMLParser xml = XMLParser.Factory.getXMLParser();
    for (Iterator<String> iter = xml.getActivities(); iter.hasNext();) {
      String activity = iter.next();
      ActivityLaunchMode launchMode = xml.getLaunchMode(activity);
      System.out.println("! " + activity + " -> " + launchMode);
    }
  }

  void printAllStartActivityAndTargets() {
    EpiccBasedIntentAnalysis epiccIntentAnalysis = null;
    if (compareWithEpicc) {
      epiccIntentAnalysis = EpiccBasedIntentAnalysis.v();
    }
    for (Stmt s : intentAnalysis.getAllActivityLaunchCalls()) {
      SootMethod m = JimpleUtil.v().lookup(s);
      System.out.println("* " + s + " @ " + m);
      Set<String> targets = intentAnalysis.getTargetActivities(s);
      if (targets.isEmpty()) {
        System.out.println("  * target: <null>");
      } else {
        for (String tgt : targets) {
          System.out.println("  * target: " + tgt);
        }
      }
      if (compareWithEpicc) {
        Set<String> epiccTargets = epiccIntentAnalysis.getTargetActivities(s);
        if (epiccTargets.isEmpty()) {
          System.out.println("  * epiccTarget: <null>");
        } else {
          for (String tgt : epiccTargets) {
            System.out.println("  * epiccTarget: " + tgt);
          }
        }
      }
    }
  }
}
