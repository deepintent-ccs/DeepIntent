/* 
 * TestResult.java - part of the LeakDroid project
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
import java.util.LinkedHashMap;
import java.util.Scanner;

public class TestResult {
  public static final float ONE_MEGA = 1024.0f;
  public static final double THRESHOLD = 0.01;
  public static String appName;
  public static int HOUR_IN_SEC = 3600;
  public static int TWO_HOUR_IN_SEC = HOUR_IN_SEC * 2;
  public static int EIGHT_HOUR_IN_SEC = HOUR_IN_SEC * 8;
  public static LinkedHashMap<String, TestResult> results = new LinkedHashMap<String, TestResult>();
  public static int threadCnt = 1;
  public static String outputDir;
  public static String shortOutputDir;
  public static int cnt = 1;
  static int binderCnt = 1;

  static {
    char[] d = new char[6];
    for (int i = 0; i < 6; i++) {
      d[i] = (char) (new java.util.Random().nextInt(25) + 65);
    }
    shortOutputDir = new String(d);
  }

  public float nativeLimit = 32 * ONE_MEGA;
  public float dalvikLimit = 16 * ONE_MEGA;
  public int time;
  public int lastThreads;
  public boolean passed;
  public String name;
  public String mainClass;
  public String testCase;
  public String signature;
  public String testId;
  public String action;
  public String title;
  public ArrayList<HeapMemUsage> usage;
  public float nativeInit;
  public float dalvikInit;
  public float[] nativeSeq;
  public float[] dalvikSeq;
  public ArrayList<BinderUsage> binderUsage;
  public float[] binders;
  public float bindersInit;
  public ArrayList<Integer> threads;
  public float[] convertedThreads;
  public float threadsInit;
  int skipCount = 0;

  private TestResult(String appName, String testId) {
    TestResult.appName = appName;
    this.testId = testId;
    this.signature = testId;
    int i = testId.indexOf('#');
    assert i > 0;
    this.name = testId.substring(i + 5);
    this.mainClass = testId.substring(0, i);
    this.testCase = testId.substring(i + 1);
    title = mainClass + "\n" + testCase;
    usage = new ArrayList<HeapMemUsage>();

    binderUsage = new ArrayList<BinderUsage>();

    threads = new ArrayList<Integer>();

    shortOutputDir = name;
    outputDir = "/home/zhanhail/workspace/sootandroid-workspace/stat/meminfo/" + appName + "/" + shortOutputDir;

    // VLC runs on 4.0.3
    if (appName.equals("VLC")) {
      nativeLimit = 48 * ONE_MEGA;
      dalvikLimit = 24 * ONE_MEGA;
    }
  }

  public static TestResult getTestResult(String appName, String testId) {
    String sig = testId;
    TestResult tr = results.get(sig);
    if (tr == null) {
      tr = new TestResult(appName, testId);
      assert tr.signature.equals(sig);
      results.put(sig, tr);
    }
    return tr;
  }

  public static void simplePlot(String title, float nativeInit, float dalvikInit, float[] nativeSeq, float[] dalvikSeq) {
    RCaller caller = new RCaller();
    caller.setRExecutable("/usr/bin/R");
    caller.setRscriptExecutable("/usr/bin/Rscript");

    RCode code = new RCode();

    code.addFloatArray("x", nativeSeq);
    code.addFloatArray("y", dalvikSeq);
    assert (nativeSeq.length == dalvikSeq.length);
    code.addRCode("t <- 1:length(x)");
    code.addRCode("cx <- cor(x=x, y=t)");
    code.addRCode("cx <- round(cx * cx, digits=6)");  // r-squared
    code.addRCode("cy <- cor(x=y, y=t)");
    code.addRCode("cy <- round(cy * cy, digits=6)"); // r-squared
    code.addRCode("tx <- round(lm(x~t)$coefficients[2], digits=6)");
    code.addRCode("ty <- round(lm(y~t)$coefficients[2], digits=6)");
//		code.addRCode("res <- c(cx, cy, tx, ty)");

    File file = null;
    try {
      file = code.startPlot();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    code.addRCode("matplot(t, cbind(x,y), pch=15, type='b', ylab='Normalized Heap (" + nativeInit + ", " + dalvikInit + ") KB ', xlab=paste('\\n\\nTime\\nr2(native): ', cx, ', r2(dalvik): ', cy, '\\nalpha(native): ', tx, ', alpha(dalvik): ', ty), main='" +
        title + "')");
    code.endPlot();

    caller.setRCode(code);
    caller.runOnly();
    System.out.println("Plot saved in " + file.toString());
//        try {
////        	Runtime.getRuntime().exec("mkdir -p " + outputDir);
////			Runtime.getRuntime().exec("mv " + file.toString() + " " + outputDir + "/" + cnt + ".png");
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}
//        cnt++;
  }

  public void addUsage(int nativeSize, int dalvikSize, int nativeAllocated, int dalvikAllocated) {
    if (skipCount < DumpsysMemInfoAnalysis.skipNumber) {
      skipCount++;
      return;
    }
    HeapMemUsage hmu = new HeapMemUsage(nativeSize, dalvikSize, nativeAllocated, dalvikAllocated);
    usage.add(hmu);
//		System.out.println("native: " + nativeSize + ", " + nativeAllocated);
//		System.out.println("dalvik: " + dalvikSize + ", " + dalvikAllocated);
  }

  public void addThreadUsage(int t) {
    threads.add(t);
  }

  public void analyzeThreads() {
    if (threads.isEmpty()) {
      System.err.println("Not enough thread data!");
      return;
    }
    if (time == 0) {
      System.out.print("What is running time (in seconds)? ");
      Scanner scan = new Scanner(System.in);
      time = scan.nextInt();
      scan.close();
    }
    int size = threads.size();
    float timePerRep = (float) time / (float) size;
    ArrayList<Integer> tmp = new ArrayList<Integer>();

    for (int i = 0; ; i += 10) {
      int idx = (int) (i / timePerRep);
      if (idx >= threads.size()) break;
      int t = threads.get(idx);
//			System.out.print(t + " ");
      tmp.add(t);
    }
//		System.out.println();
//		System.out.println("[SIZE] " + threads.size() + ", " + tmp.size());
    convertedThreads = new float[tmp.size()];
    for (int i = 0; i < tmp.size(); i++) {
      convertedThreads[i] = tmp.get(i);
    }
    threadsInit = convertedThreads[0];
    for (int i = convertedThreads.length - 1; i >= 0; i--) {
      convertedThreads[i] /= convertedThreads[0];
    }

    RCaller caller = new RCaller();
    caller.setRExecutable("/usr/bin/R");
    caller.setRscriptExecutable("/usr/bin/Rscript");

    RCode code = new RCode();

    code.addFloatArray("x", convertedThreads);
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

    code.addRCode("plot(x~t, pch=15, type='b', ylab='Number of Threads (init=" + threadsInit + ")', " +
        "xlab=paste('\\n\\nTime\\nr2: ', cx, 'alpha: ', tx), " +
        "main='" + title + "')");
    code.endPlot();

    caller.setRCode(code);
    caller.runOnly();
//        System.out.println("Plot saved in " + file.toString());

    if (DumpsysMemInfoAnalysis.plotting) {
      try {
        Runtime.getRuntime().exec("mkdir -p " + outputDir);
        Runtime.getRuntime().exec("mv " + file.toString() + " " + outputDir + "/t" + threadCnt + ".png");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    threadCnt++;
  }

  public void addBinderUsage(int binders) {
    binderUsage.add(new BinderUsage(binders));
  }

  public void analyzeBinder() {
    if (binderUsage.isEmpty()) {
      System.err.println("Not enough binder info!");
      return;
    }
    binders = new float[binderUsage.size()];
    for (int i = 0; i < binderUsage.size(); i++) {
      BinderUsage bu = binderUsage.get(i);
      binders[i] = bu.binders;
    }
    bindersInit = binders[0];
    // normalize
    for (int i = binders.length - 1; i >= 0; i--) {
      binders[i] = binders[i] / binders[0];
    }

    RCaller caller = new RCaller();
    caller.setRExecutable("/usr/bin/R");
    caller.setRscriptExecutable("/usr/bin/Rscript");

    RCode code = new RCode();

    code.addFloatArray("x", binders);
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

    code.addRCode("plot(x~t, pch=15, type='b', ylab='Number of Binders (init=" + bindersInit + ")', " +
        "xlab=paste('\\n\\nTime\\nr2: ', cx, 'alpha: ', tx), " +
        "main='" + title + "')");
    code.endPlot();

    caller.setRCode(code);
    caller.runOnly();
//        System.out.println("Plot saved in " + file.toString());

    if (DumpsysMemInfoAnalysis.plotting) {
      try {
        Runtime.getRuntime().exec("mkdir -p " + outputDir);
        Runtime.getRuntime().exec("mv " + file.toString() + " " + outputDir + "/b" + binderCnt + ".png");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    binderCnt++;
  }

  public void convert() {
    int origSize = usage.size();
    int[] origNativeSeq = new int[origSize];
    int[] origDalvikSeq = new int[origSize];
    for (int i = 0; i < origSize; i++) {
      HeapMemUsage hmu = usage.get(i);
      origNativeSeq[i] = hmu.nativeAllocated;
      origDalvikSeq[i] = hmu.dalvikAllocated;
    }
    // smoothing
    int size = origSize - DumpsysMemInfoAnalysis.smoothRange + 1;
    if (size <= 0) {
      System.out.println("Not enough data collected");
      throw new RuntimeException();
    }
    nativeSeq = new float[size];
    dalvikSeq = new float[size];
    for (int i = 0; i < size; i++) {
      int nativeSum = 0;
      int dalvikSum = 0;
      for (int j = 1; j <= DumpsysMemInfoAnalysis.smoothRange; j++) {
        nativeSum += origNativeSeq[i + DumpsysMemInfoAnalysis.smoothRange - j];
        dalvikSum += origDalvikSeq[i + DumpsysMemInfoAnalysis.smoothRange - j];
      }
      nativeSeq[i] = nativeSum / DumpsysMemInfoAnalysis.smoothRange;
      dalvikSeq[i] = dalvikSum / DumpsysMemInfoAnalysis.smoothRange;
    }

    // normalize
    nativeInit = nativeSeq[0];
    dalvikInit = dalvikSeq[0];
//		System.out.println("init: " + nativeInit + ", " + dalvikInit);
    for (int i = 0; i < size; i++) {
//			System.out.println("[" + i + "] OLD: " + nativeSeq[i] + ", " + dalvikSeq[i]);
      nativeSeq[i] = nativeSeq[i] / nativeInit;
      dalvikSeq[i] = dalvikSeq[i] / dalvikInit;

//			System.out.println("[" + i + "] NEW: " + nativeSeq[i] + ", " + dalvikSeq[i]);
    }
  }

  public void debugPrint() {
    System.out.println(testId + "    " + action);
    for (HeapMemUsage hmu : usage) {
      System.out.println("  " + hmu.nativeSize + ", " + hmu.dalvikSize + ", " +
          hmu.nativeAllocated + ", " + hmu.dalvikAllocated);
    }
  }

  public void plot() {
    RCaller caller = new RCaller();
    caller.setRExecutable("/usr/bin/R");
    caller.setRscriptExecutable("/usr/bin/Rscript");

    RCode code = new RCode();

    code.addFloatArray("x", nativeSeq);
    code.addFloatArray("y", dalvikSeq);
    assert (nativeSeq.length == dalvikSeq.length);
    code.addRCode("t <- 1:length(x)");
    code.addRCode("cx <- cor(x=x, y=t)");
    code.addRCode("cx <- round(cx * cx, digits=6)");  // r-squared
    code.addRCode("cy <- cor(x=y, y=t)");
    code.addRCode("cy <- round(cy * cy, digits=6)"); // r-squared
    code.addRCode("tx <- round(lm(x~t)$coefficients[2], digits=6)");
    code.addRCode("ty <- round(lm(y~t)$coefficients[2], digits=6)");
    code.addRCode("res <- c(cx, cy, tx, ty)");

    File file = null;
    if (DumpsysMemInfoAnalysis.plotting) {
      try {
        file = code.startPlot();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      code.addRCode("matplot(t, cbind(x,y), pch=15, type='b', ylab='Normalized Heap (" + nativeInit + ", " + dalvikInit + ") KB ', xlab=paste('\\n\\nTime\\nr2(native): ', cx, ', r2(dalvik): ', cy, '\\nalpha(native): ', tx, ', alpha(dalvik): ', ty), main='" +
          title + "')");
      code.endPlot();
    }

    caller.setRCode(code);
    caller.runAndReturnResult("res");
    double[] res = caller.getParser().getAsDoubleArray("res");
    System.out.println("  alpha(native): " + res[2] + ", alpha(dalvik): " + res[3]);
    System.out.println("  r2(native): " + res[0] + ", r2(dalvik): " + res[1]);

    int nativePredict = -1;
    if (res[2] > 0) {
      nativePredict = (int) ((nativeLimit / nativeInit - 1.0f) / res[2]);
    }
    int dalvikPredict = -1;
    if (res[3] > 0) {
      dalvikPredict = (int) ((dalvikLimit / dalvikInit - 1.0f) / res[3]);
    }

    System.out.println("<tr>");
    System.out.println("  <td>" + this.name + "</td>");
    System.out.println("  <td>" + (int) this.time + "</td>");
    System.out.println("  <td>" + this.lastThreads + "</td>");
    System.out.println("  <td>" + res[2] + "</td>");
    System.out.println("  <td>" + res[3] + "</td>");
    System.out.println("  <td>" + nativeInit + "</td>");
    System.out.println("  <td>" + dalvikInit + "</td>");
    System.out.println("  <td>" + (nativePredict > 0 ? nativePredict : "N/A") + "</td>");
    System.out.println("  <td>" + (dalvikPredict > 0 ? dalvikPredict : "N/A") + "</td>");

    int suspicious = 0;
    if ((nativePredict > 0 && nativePredict <= TWO_HOUR_IN_SEC) ||
        (dalvikPredict > 0 && dalvikPredict <= TWO_HOUR_IN_SEC)) {
      System.out.println("  <td>HIGHLY-SUSPICIOUS</td>");
      suspicious = 1;
    } else if ((nativePredict > 0 && nativePredict <= EIGHT_HOUR_IN_SEC) ||
        (dalvikPredict > 0 && dalvikPredict <= EIGHT_HOUR_IN_SEC)) {
      System.out.println("  <td>MODERATELY-SUSPICIOUS</td>");
      suspicious = 2;
    } else {
      System.out.println("  <td>NOT-SUSPICIOUS</td>");
    }
    System.out.println("  <td><a href=\"" + shortOutputDir + "/" + cnt + ".png\">link</a></td>");

    System.out.println("  <td><a href=\"" + shortOutputDir + "/b" + binderCnt + ".png\">link</a></td>");
    System.out.println("  <td><a href=\"" + shortOutputDir + "/t" + threadCnt + ".png\">link</a></td>");

    System.out.println("</tr>");

    if (suspicious == 1) {
      System.out.println("[HIGHLY-SUSPICIOUS] " + testId + " " + action);
    } else if (suspicious == 2) {
      System.out.println("[MODERATELY-SUSPICIOUS] " + testId + " " + action);
    }

    if (DumpsysMemInfoAnalysis.plotting) {
      try {
        Runtime.getRuntime().exec("mkdir -p " + outputDir);
        Runtime.getRuntime().exec("mv " + file.toString() + " " + outputDir + "/" + cnt + ".png");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    cnt++;
  }
}
