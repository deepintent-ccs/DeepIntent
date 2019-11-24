/*
 * EpiccBasedIntentAnalysis.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import presto.android.Configs;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.dexpler.Util;
import soot.jimple.Stmt;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/*
 * This is an epicc-based intent analysis.
 */
public class EpiccBasedIntentAnalysis implements IntentAnalysisInterface {
  final String EPICC_LOG_DIR = System.getenv("EPICC_LOG_DIR");
  final String EPICC_LOG_FILE =
      EPICC_LOG_DIR + "/" + Configs.benchmarkName + ".txt";

  final String METHOD_SIG_MARKER = "  - ";
  final String INTENT_COUNT_MARKER = "Intent value: ";
  final String INTENT_COUNT_END_MARKER = " possible";

  // For each method, epicc reports in-order the startActivity* call statements.
  // For each statement, there is a set of possible target activities.
  HashMap<SootMethod, ArrayList<HashSet<String>>> methodAndTargets = Maps.newHashMap();

  // For each startActivity* call statement, the corresponding set of targets.
  HashMap<Stmt, HashSet<String>> stmtAndTargets = Maps.newHashMap();

  final Set<String> emptyTargetSet =
      Collections.unmodifiableSet(Collections.<String>emptySet());

  // Public APIs
  @Override
  public boolean isActivityLaunch(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod callee = s.getInvokeExpr().getMethod();
    if (!callee.getDeclaringClass().isLibraryClass()) {
      return false;
    }
    return callee.getName().startsWith("startActivity");
  }

  @Override
  public Set<String> getTargetActivities(Stmt s) {
    Set<String> targets = stmtAndTargets.get(s);
    if (targets == null) {
      targets = emptyTargetSet;
    }
    return targets;
  }

  @Override
  public Set<Stmt> getActivityLaunchCalls(String activityClassName) {
    fail("Not implemented!");
    return null;
  }

  @Override
  public Set<Stmt> getAllActivityLaunchCalls() {
    return stmtAndTargets.keySet();
  }

  // Implementation details start here

  private static EpiccBasedIntentAnalysis theInstance;

  private EpiccBasedIntentAnalysis() {}

  public static EpiccBasedIntentAnalysis v() {
    if (theInstance == null) {
      theInstance = new EpiccBasedIntentAnalysis();
      theInstance.runOnce();
    }
    return theInstance;
  }

  private void runOnce() {
    parseEpiccLogFile();
    constructStmtAndTargets();
    // TODO(tony): read in config for each startActivity
  }

  void parseEpiccLogFile() {
    File epiccLogFile = new File(EPICC_LOG_FILE);
    if (!epiccLogFile.exists()) {
      fail("EPICC log file cannot be found!");
    }

    try {
      BufferedReader br = new BufferedReader(new FileReader(epiccLogFile));
      String line = null;
      while ((line = br.readLine()) != null) {
        if (!line.startsWith(METHOD_SIG_MARKER)) {
          continue;
        }
        int openPara = line.indexOf('(');
        String classAndMethodNames =
            line.substring(METHOD_SIG_MARKER.length(), openPara);
        int lastSlash = classAndMethodNames.lastIndexOf('/');
        String className =
            classAndMethodNames.substring(0, lastSlash).replaceAll("/", ".");
        SootClass c = Scene.v().getSootClass(className);
        if (c.isPhantom()) {
          fail(className + " is phatom!");
        }
        String methodName = classAndMethodNames.substring(lastSlash + 1);
        String parameters = line.substring(openPara + 1, line.length() - 1);
        List<String> paraList = Util.splitParameters(parameters);
        List<Type> typeList = Lists.newArrayListWithExpectedSize(paraList.size());
        for (String s : paraList) {
          typeList.add(Util.getType(s));
        }
        SootMethod m = c.getMethod(methodName, typeList);
//        System.out.println("--- " + m);
        line = br.readLine();
        if (!line.startsWith(INTENT_COUNT_MARKER)) {
          continue;
        }
        int start = INTENT_COUNT_MARKER.length();
        int end = line.indexOf(INTENT_COUNT_END_MARKER, start);
        int values = Integer.parseInt(line.substring(start, end));
        HashSet<String> targetSet = Sets.newHashSet();
//        System.out.println("  * values: " + values);
        for (int i = 0; i < values; i++) {
          line = br.readLine();
          if (!line.startsWith("Package: ")) {
            continue;
          }
//          System.out.println("  * " + i + " -> " + line);
          start = line.lastIndexOf("Class: ") + "Class: ".length();
          end = line.indexOf(',', start);
          String target = line.substring(start, end).replaceAll("/", ".");
          if (Scene.v().getSootClass(target).isPhantom()) {
            fail(target + " is phantom.");
          }
          targetSet.add(target);
//          System.out.println("  * target: " + target);
        }
        ArrayList<HashSet<String>> listOfTargetSets = methodAndTargets.get(m);
        if (listOfTargetSets == null) {
          listOfTargetSets = Lists.newArrayList();
          methodAndTargets.put(m, listOfTargetSets);
        }
        listOfTargetSets.add(targetSet);
      }
      br.close();
    } catch (Exception e) {
      fail(e);
    }
  }

  void constructStmtAndTargets() {
    for (Map.Entry<SootMethod, ArrayList<HashSet<String>>> entry : methodAndTargets.entrySet()) {
      SootMethod m = entry.getKey();
//      System.out.println("--- " + m);
      ArrayList<HashSet<String>> listOfTargetSets = entry.getValue();
      Iterator<Unit> stmts = m.retrieveActiveBody().getUnits().iterator();
      int index = 0;
      while (stmts.hasNext()) {
        Stmt s = (Stmt) stmts.next();
        if (!s.containsInvokeExpr()) {
          continue;
        }
        SootMethod callee = s.getInvokeExpr().getMethod();
        SootClass calleeClass = callee.getDeclaringClass();
        if (!calleeClass.isLibraryClass()) {
          continue;
        }
        String calleeName = callee.getName();
        if (calleeName.equals("sendBroadcast")
            || calleeName.equals("bindService")
            || calleeName.equals("startService")
            || calleeName.equals("sendOrderedBroadcast")) {
          index++;
          continue;
        }
        if (!calleeName.startsWith("startActivity")) {
          continue;
        }
        stmtAndTargets.put(s, listOfTargetSets.get(index));
        index++;
//        System.out.println("  * " + s + " @ " + m);
      }
      if (index != listOfTargetSets.size()) {
        fail("Internal error.");
      }
    }
  }

  void fail(String msg) {
    throw new RuntimeException(msg);
  }

  void fail(Exception e) {
    throw new RuntimeException(e);
  }

  @Override
  public Set<LaunchConfiguration> getLaunchConfigurations(Stmt s, String targetActivity) {
    // TODO Auto-generated method stub
    return null;
  }
}
