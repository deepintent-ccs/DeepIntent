/* 
 * ThreadUsage.java - part of the LeakDroid project
 *
 * Copyright (c) 2013, The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the root
 * directory.
 */

package rua;

import rcaller.RCaller;
import rcaller.RCode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ThreadUsage {
  public static HashMap<String, ThreadUsage> tus =
      new HashMap<String, ThreadUsage>();
  public static String outputDir;
  public static String shortOutputDir;
  public static int cnt = 1;

  static {
    char[] d = new char[6];
    for (int i = 0; i < 6; i++) {
      d[i] = (char) (new java.util.Random().nextInt(25) + 65);
    }
    shortOutputDir = new String(d);
    outputDir = "/tmp/meminfo/" + shortOutputDir;
  }

  ArrayList<Integer> values;
  int[] data;
  String appName;
  String testId;
  String action;
  String sig;
  String name;
  String mainClass;
  String testCase;
  String title;

  private ThreadUsage(String appName, String testId, String action, String sig) {
    this.appName = appName;
    this.testId = testId;
    this.action = action;
    this.sig = sig;

    int i = testId.indexOf('#');
    assert i > 0;
    this.name = testId.substring(i + 5);
    this.mainClass = testId.substring(0, i);
    this.testCase = testId.substring(i + 1);
    title = mainClass + "\n" + testCase + ", " + action;

    values = new ArrayList<Integer>();
  }

  public static ThreadUsage getThreadUsage(String appName, String testId, String action) {
    String sig = testId + " " + action;
    ThreadUsage tu = tus.get(sig);
    if (tu == null) {
      tu = new ThreadUsage(appName, testId, action, sig);
      tus.put(sig, tu);
    }
    return tu;
  }

  public void add(int v) {
    values.add(v);
  }

  public void convert() {
    data = new int[values.size()];
    for (int i = 0; i < values.size(); i++) {
      data[i] = values.get(i);
    }
  }

  public void plot() {
    RCaller caller = new RCaller();
    caller.setRExecutable("/usr/bin/R");
    caller.setRscriptExecutable("/usr/bin/Rscript");

    RCode code = new RCode();
    code.addIntArray("x", data);
    code.addRCode("t <- 1:length(x)");
    code.addRCode("cx <- cor(x=x, y=t)");
    code.addRCode("cx <- round(cx * cx, digits=6)");  // r-squared
    code.addRCode("tx <- round(lm(x~t)$coefficients[2], digits=6)");

    File file = null;
    try {
      file = code.startPlot();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    code.addRCode("plot(x~t, pch=15, type='b', ylab='Number of Threads', " +
        "xlab=paste('\\nTime\\nr2: ', cx, 'alpha: ', tx), " +
        "main='" + title + "')");
    code.endPlot();

    caller.setRCode(code);
    caller.runOnly();
//        System.out.println("Plot saved in " + file.toString());

    try {
      Runtime.getRuntime().exec("mkdir -p " + outputDir);
      Runtime.getRuntime().exec("mv " + file.toString() + " " + outputDir + "/" + cnt + ".png");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    cnt++;
  }
}
