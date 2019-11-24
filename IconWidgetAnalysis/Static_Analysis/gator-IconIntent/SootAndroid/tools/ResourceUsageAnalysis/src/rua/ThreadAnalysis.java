/* 
 * ThreadAnalysis.java - part of the LeakDroid project
 *
 * Copyright (c) 2013, The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the root
 * directory.
 */

package rua;

import java.io.BufferedReader;
import java.io.FileReader;

public class ThreadAnalysis {
  //	public static String logFile;
  // -memFile fn
  public static String memFile;


  public static boolean oldVersion = false;

  public static void parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String s = args[i];
      if (s.equals("-memFile")) {
        memFile = args[++i];
      } else if (s.equals("-oldVersion")) {
        oldVersion = true;
      }
    }
  }

  public static void main(String[] args) throws Exception {
    parseArgs(args);
    BufferedReader br = new BufferedReader(new FileReader(memFile));
    String line = "";
    ThreadUsage curr = null;
    while ((line = br.readLine()) != null) {
      if (line.startsWith("--- BEGIN")) {
        String[] s = line.split(" ");
        String appName;
        String testId;
        String action;
        if (oldVersion) {
          testId = s[2];
          action = s[4];
          appName = DumpsysMemInfoAnalysis.getAppNameFromTestId(testId);
        } else {
          appName = s[2];
          testId = s[3];
          action = s[5];
        }
        curr = ThreadUsage.getThreadUsage(appName, testId, action);
      } else if (line.startsWith("THREAD: ")) {
        String[] s = line.split(" ");
        int v = Integer.parseInt(s[1]);
        curr.add(v);
      }
    }
    br.close();

    for (ThreadUsage tu : ThreadUsage.tus.values()) {
      tu.convert();
      tu.plot();
    }
    System.out.println("Result saved to " + ThreadUsage.outputDir);
  }
}
