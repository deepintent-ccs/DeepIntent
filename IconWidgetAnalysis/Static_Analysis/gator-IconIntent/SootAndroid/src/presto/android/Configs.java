/*
 * Configs.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.*;

import com.google.common.collect.Maps;
import soot.options.Options;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class Configs {
  public static String benchmarkName;

  // root of the project directory
  public static String project;
  public static boolean apkMode;

  // location of AndroidMainifest file
  public static String manifestLocation;

  // location of the resource xml root
  public static String resourceLocation;
  public static List<String> resourceLocationList = Lists.newArrayList();

  // location of root of application classes
  public static String classfileLocation;

  // root directory of Android SDK
  public static String sdkDir;

  public static String apiLevel;
  public static int numericApiLevel;

  public static String sysProj;

  public static String bytecodes;

  public static ArrayList<String> depJars;
  public static ArrayList<String> extLibs;

  // full path to android.jar
  public static String android;

  // jre jars
  public static String jre = "";

  // xml file describing listeners
  public static String listenerSpecFile;

  // --- boolean flags
  public static boolean verbose = false;

  public static boolean guiAnalysis;

  public static Set<String> debugCodes = Sets.newHashSet();

  public static Set<String> clients = Sets.newHashSet();

  public static boolean withCHA = false;

  // [wtg analysis] xml file describing the calls related with wtg
  public static String wtgSpecFile;

  // [wtg analysis] turn on/off implicit intent resolution
  public static boolean implicitIntent = false;

  // [wtg analysis] turn on/off context-sensitive resolution on event handler
  // i.e. provide GUI object while analyzing the target window
  public static boolean resolveContext = true;

  // [wtg analysis] turn on/off time tracking running time for whole execution
  public static boolean trackWholeExec = false;

  // [wtg analysis] generate edges related to rotate, power and home
  public static boolean hardwareEvent = true;

  // [wtg analysis] number of threads building wtg edges
  public static int workerNum = 16;

  // [wtg analysis] detect resource leak
  public static int detectLeak = -1;

  // [wtg analysis] backward traversal depth to find successor
  public static int sDepth = 4;

  // [wtg analysis] handle asynchronous operations specially
  public static AsyncOpStrategy asyncStrategy = AsyncOpStrategy.Default_EventHandler_Async;

  // [wtg analysis] generate test cases
  public static boolean genTestCase = false;

  // [test generation] allow loop in wtg forward traversal
  public static boolean allowLoop = false;

  // [test generation] path exploration length
  public static int epDepth = 3;

  public static boolean sanityCheck = false;

  // [test generation] test cases generation strategy
  public static TestGenStrategy testGenStrategy = null;

  public static boolean instrument = false;

  // Mock testing flags
  public static boolean mockScene = false;

  // hailong: arguments for clients
  public static Set<String> clientParams = Sets.newHashSet();

  // Path output file name
  public static String pathoutfilename = "";

  public static String monitoredClass = "";

  //public static boolean useAndroidStudio = false;

  public static String classListFile = "";

  public static String widgetMapFile = "/SootAndroid/scripts/consts/widgetMap";

  public static String libraryPackageFile = "";

  public static List<String> libraryPackages = null;

  public static boolean preRun = false;

  public static Set<String> onDemandClassSet = Sets.newHashSet();

  public static Map<String, String> widgetMap = Maps.newHashMap();

  public static void addLibraryPackage(String packageName) {
    if (libraryPackages == null) {
      libraryPackages = Lists.newArrayList();
    }
    libraryPackages.add(packageName);
  }

  public static boolean isLibraryClass(String className) {
    if (libraryPackages == null)
      return false;
    for (String pkg : libraryPackages) {
      if (pkg.equals(className) ||
          ((pkg.endsWith(".*") || pkg.endsWith("$*")) &&
            className.startsWith(pkg.substring(0, pkg.length() - 1)))
         ){
        return true;
      }
    }
    return false;
  }

  public static void processLibraryPkgFile() {
    if (libraryPackageFile.isEmpty()) {
      return;
    }
    try {
      FileReader fr = new FileReader(libraryPackageFile);
      BufferedReader br = new BufferedReader(fr);
      String curLine;
      while ((curLine = br.readLine()) != null) {
        if (!curLine.isEmpty()) {
          addLibraryPackage(curLine);
        }
      }
      br.close();
      fr.close();
    } catch (Exception e) {
    }
  }

  public static void processing() {

    if (!libraryPackageFile.isEmpty()) {
      processLibraryPkgFile();
    }

    bytecodes = Configs.classfileLocation;

    if (classfileLocation.endsWith(".apk") || project.endsWith(".apk")) {
      apkModeProcessing();
      //return;
    } else {
      depJars = Lists.newArrayList();
      File f = new File(project + "/libs");
      if (f.exists()) {
        File[] files = f.listFiles();
        for (File file : files) {
          String fn = file.getName();
          if (fn.endsWith(".jar")) {
            depJars.add(file.getAbsolutePath());
          }
        }
      }
    }

    numericApiLevel = Integer.parseInt(apiLevel.substring("android-".length()));
    sysProj = Configs.sdkDir + "/platforms/" + Configs.apiLevel + "/data";
    if (resourceLocation.indexOf(":") == -1) {
      //Only 1 res directory existed
      resourceLocationList.add(resourceLocation);
    } else {
      String[] resourceLocArray = resourceLocation.split(":");
      resourceLocationList.addAll(Arrays.asList(resourceLocArray));
      resourceLocation = resourceLocationList.get(0);
    }

    extLibs = Lists.newArrayList();

    validate();
  }

  static void apkModeProcessing() {
    apkMode = true;
    bytecodes = project;

    sysProj = Configs.sdkDir + "/platforms/" + Configs.apiLevel + "/data";
    Options.v().set_force_android_jar(Configs.android);
    Options.v().set_src_prec(Options.src_prec_apk);

    // make validate() happy
    depJars = Lists.newArrayList();
    extLibs = Lists.newArrayList();

    validate();
  }

  final static String GOOGLE_API_PREFIX = "Google Inc.:Google APIs:";

  public static void validate() {
    Class<Configs> cls = Configs.class;
    for (Field f : cls.getFields()) {
      if (f.getType().equals(String.class)) {
        try {
          Object res = f.get(null);
          if (res == null) {
            System.err.println("[Configs] You need to set `Configs."
                + f.getName() + "'");
            System.exit(-1);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static int getAndroidAPILevel() {
    Preconditions.checkNotNull(apiLevel);
    if (apiLevel.startsWith("android-")) {
      return Integer.parseInt(apiLevel.substring(8));
    } else if (apiLevel.startsWith("google-")) {
      return Integer.parseInt(apiLevel.substring(7));
    } else {
      return -1;
    }
  }

  public static String getClientParamCode(String subStr) {
    if (Configs.clientParams.size() == 0) {
      return null;
    }
    for (String curStr : Configs.clientParams){
      if (curStr.indexOf(subStr) == 0){
        return new String(curStr);
      }
    }
    return null;
  }

  public enum TestGenStrategy {
    All_Window_Coverage,
    All_Edge_Coverage,
    Feasible_Edge_Coverage,
  }

  public enum AsyncOpStrategy {
    Default_EventHandler_Async,     // handle Activity.runOnUiThread and View.post as part of event handler
    Default_Special_Async,         // handle Activity.runOnUiThread and View.post as special event
    All_Special_Async,              // handle all the ops defined in the wtg.xml as part of event handler
    All_EventHandler_Async;        // handle all the ops defined in the wtg.xml as special event
  }
}
