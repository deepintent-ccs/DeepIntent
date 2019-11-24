/* 
 * DumpsysMemInfoAnalysis.java - part of the LeakDroid project
 *
 * Copyright (c) 2013, The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the root
 * directory.
 */

package rua;

import java.io.*;

public class DumpsysMemInfoAnalysis {
  public static int[] intData = new int[10];

  //	public static ArrayList<TestResult> results = new ArrayList<TestResult>();
  public static TestResult result;

  public static int skipNumber = 0;
  public static int smoothRange = 10;

  // -skip n
  public static int firstFew = 0;

  public static boolean plotting = true;

  // -memFile fn
  public static String memFile;
  // -logFile fn
  public static String logFile;

  // -ics
  public static boolean ics = false;

  // -genDB: store data into ``database files''
  // format of this file:
  //	1st line:         testSignature
  //  subsequent lines: native,java
  public static boolean genDB = false;

  // -analyzeDB: read the ``database file'' and analyze (maybe plot as well)
  //	accepts one and only one parameter as database file name
  public static String dbFile = null;

  // -oldVersion: old version memory usage file without appName
  public static boolean oldVersion = false;

  // -overwrite: overwrite existing data
  public static boolean overwrite = false;

  // -binderOnly: analyze binder usage only
  public static boolean binderOnly = false;

  // -threadOnly: analyze thread usage only
  public static boolean threadOnly = false;

  //	// Application name
//	static String appName;
  static String[] appName2TestPkg = {
      "APV", "cx.hell.android.pdfview.test",
      "astrid", "com.timsu.astrid.test",
      "ConnectBot", "org.connectbot.test",
      "KeePassDroid", "com.android.keepass.test",
      "K9.1", "com.fsck.k9.test",
      "MyTracks", "com.google.android.maps.mytracks.test",
      "OsmAnd", "net.osmand.plus.test",
      "VLC", "org.videolan.vlc.test",
      "VuDroid", "org.vudroid.test",
      "AnkiDroid", "com.ichi2.anki.test",
  };
  static float decayFactor = .15f;

  public static void parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String s = args[i];
      if (s.equals("-memFile")) {
        memFile = args[++i];
      } else if (s.equals("-logFile")) {
        logFile = args[++i];
      } else if (s.equals("-skip")) {
        firstFew = Integer.parseInt(args[++i]) + 1;
      } else if (s.equals("-ics")) {
        ics = true;
//				TestResult.nativeLimit = TestResult.ONE_MEGA * 48;
//				TestResult.dalvikLimit = TestResult.ONE_MEGA * 24;
      } else if (s.equals("-genDB")) {
        genDB = true;
        plotting = false;
      } else if (s.equals("-oldVersion")) {
        oldVersion = true;
      } else if (s.equals("-overwrite")) {
        overwrite = true;
      } else if (s.equals("-analyzeDB")) {
        dbFile = args[++i];
      } else if (s.equals("-binderOnly")) {
        binderOnly = true;
      } else if (s.equals("-threadOnly")) {
        threadOnly = true;
      }
    }
  }

  /**
   * @param args Required:
   *             args[0] - mem usage log file
   *             args[1] - testing log file
   *             <p/>
   *             Optional:
   *             args[2] - process first few data sets
   */
  public static void main(String[] args) throws Exception {
//		if (args.length < 2) {
//			System.out.println("Wrong argument format!");
//			System.exit(-1);
//		}
//		if (args.length >= 3) {
//			firstFew = Integer.parseInt(args[2]);
//		}
    parseArgs(args);
    if (dbFile != null) {
      analyzeDB();
      return;
    }

    long start = System.currentTimeMillis();
//		String fn = args[0];
    BufferedReader br = new BufferedReader(new FileReader(memFile));
    String line = "";
    int numTests = 0;
    while ((line = br.readLine()) != null) {
      if (line.startsWith("--- BEGIN")) {
        numTests++;
        if (numTests == firstFew) break;
        // new test case marker
        String[] s = line.split(" ");
        String appName;
        String testId;
        if (oldVersion) {
          testId = s[2];
          appName = getAppNameFromTestId(testId);
        } else {
          appName = s[2];
          testId = s[3];
        }
        result = TestResult.getTestResult(appName, testId);
      } else if (line.contains("native   dalvik    other    total")) {
        if (null == result) continue;
        // new meminfo record
        line = br.readLine();
        readFirstTwo(line);
        int nativeSize = intData[0];
        int dalvikSize = intData[1];

        line = br.readLine();
        readFirstTwo(line);
        int nativeAllocated = intData[0];
        int dalvikAllocated = intData[1];
        result.addUsage(nativeSize, dalvikSize, nativeAllocated, dalvikAllocated);
      } else if (line.contains("Size    Alloc     Free")) {  // new format
        if (null == result) continue;
        br.readLine();  // ignore the separation line
        line = br.readLine();
        readMemOfNewFormat(line, "Native");
        int nativeSize = intData[0];
        int nativeAllocated = intData[1];

        line = br.readLine();
        readMemOfNewFormat(line, "Dalvik");
        int dalvikSize = intData[0];
        int dalvikAllocated = intData[1];
        result.addUsage(nativeSize, dalvikSize, nativeAllocated, dalvikAllocated);
      } else if (line.contains("Local Binders")) {
        if (null == result) continue;
        int idx = "   Local Binders:".length();
        int endIdx = line.indexOf("Proxy");
        while (true) {
          char c = line.charAt(idx);
          if (c >= '0' && c <= '9') break;
          else idx++;
        }
        while (true) {
          char c = line.charAt(endIdx);
          if (c >= '0' && c <= '9') break;
          else endIdx--;
        }
        int binder = 0;
        while (idx <= endIdx) {
          binder = 10 * binder + (line.charAt(idx) - '0');
          idx++;
        }
        result.addBinderUsage(binder);
      } else if (line.startsWith("THREAD: ")) {
        String[] str = line.split(" ");
        int t = Integer.parseInt(str[1]);
        result.lastThreads = t;
        result.addThreadUsage(t);
      }
    }
//		if (result != null) process(result);

    br.close();

//		fn = args[1];
    br = new BufferedReader(new FileReader(logFile));
    numTests = 0;
    while ((line = br.readLine()) != null) {
      if (line.startsWith("--- BEGIN")) {
        numTests++;
        if (numTests == firstFew) break;

        String[] s = line.split(" ");
        String appName;
        String testId;
        if (oldVersion) {
          testId = s[2];
          appName = getAppNameFromTestId(testId);
        } else {
          appName = s[2];
          testId = s[3];
        }
        result = TestResult.getTestResult(appName, testId);
      } else if (line.startsWith("--- TIME")) {
        String[] s = line.split(" ");
        result.time = Integer.parseInt(s[s.length - 2]);
      } else if (oldVersion && line.startsWith("Time: ")) {
        result.time = (int) Float.parseFloat(line.substring(6));
      } else if (line.startsWith("OK (1 test)")) {
        result.passed = true;
      } else if (line.startsWith("FAILURES!!!")) {
        result.passed = false;
      }
    }

    for (TestResult tr : TestResult.results.values()) {
      if (binderOnly) {
        tr.analyzeBinder();
        continue;
      }
      if (threadOnly) {
        tr.analyzeThreads();
        continue;
      }
      if (genDB) {
        genDB(tr);
        continue;
      }
      process(tr);
//			break;
    }

    long end = System.currentTimeMillis();
    long delta = end - start;
    if (plotting) {
      System.out.println("\nAnalysis time: " + delta + " ms; results saved in " + TestResult.outputDir);
      System.out.println("Black for native, and red for dalvik.");
      String scriptDir = System.getProperty("ScriptDir");
      String browseScript = scriptDir + "/browse.sh";
      if (new File(browseScript).exists()) {
        Thread.sleep(500);
        Runtime.getRuntime().exec(browseScript + " " + TestResult.outputDir);
      }
    } else if (genDB) {
      System.out.println("\nAnalysis time: " + delta + " ms; results saved in /tmp/meminfo/HPNIWP");
    }
  }

  public static String getAppNameFromTestId(String testId) {
    for (int i = 0; i < appName2TestPkg.length; i += 2) {
      if (testId.contains(appName2TestPkg[i + 1])) return appName2TestPkg[i];
    }
    throw new RuntimeException("Unknown tsetId: " + testId);
  }

  static void analyzeDB() throws Exception {
    assert dbFile != null;

    BufferedReader br = new BufferedReader(new FileReader(dbFile));
    String sig = br.readLine();
    int len = Integer.parseInt(br.readLine());
    String line = br.readLine();
    String[] s = line.split(",");
    float[] ns = new float[len];
    float[] ds = new float[len];
    for (int i = 0; i < len; i++) {
      line = br.readLine();
      s = line.split(",");
      ns[i] = Float.parseFloat(s[0]);
      ds[i] = Float.parseFloat(s[1]);
    }
    s = sig.split(" ");
    String testId = s[0];
    int i = testId.indexOf('#');
    assert i > 0;
    br.close();

    System.out.println("[PROCESSING] " + testId);
    System.out.print("--- native ratio: ");
    boolean nr = ratioRank(ns);
    System.out.print("--- dalvik ratio: ");
    boolean dr = ratioRank(ds);
    if (nr || dr) {
      System.out.println("[SUSPICIOUS] " + testId);
    }
    System.out.println();
  }

  static float ratioRankTest(float[] seq) {
    float max = 1;
    int leakPhases = 0;
    float rank = 0;
    int card = 0;
    float lastVolume = 1;
    for (int i = 1; i < seq.length; i++) {
      float thisVolume = seq[i];
      if (thisVolume > max * (1 - decayFactor)) {
        if (thisVolume > max) {
          max = thisVolume;
        }
        if (thisVolume > lastVolume) {
          float increase = (thisVolume / lastVolume - 1);
          if (increase < .05) {
            continue;
          }
          leakPhases++;
          System.out.println(increase);
          rank += leakPhases * increase;
          card += leakPhases;
          lastVolume = thisVolume;
        } // ignore slight fluctuation
      } else {
        leakPhases = 0;
      }
    }
    System.out.println("card: " + card);
    // normalize
    if (card == 0) {
      rank = -1;
    } else {
      rank = rank / card;
    }
    return rank;
  }

  static boolean ratioRank(float[] seq) {
    int contInc = -1;
    int contDec = -1;

    float lastVolume = 1;

    float rank = 0;
    for (int i = 1; i < seq.length; i++) {
      float thisVolume = seq[i];
      if (thisVolume > lastVolume) {
        contInc++;
        contDec = -1;
        rank += contInc * (thisVolume / lastVolume - 1);
      } else {
        contInc = -1;
        contDec++;
        rank -= contDec * (lastVolume / thisVolume - 1);
      }
      lastVolume = thisVolume;
    }
    rank = rank / ((contInc + 1) * contInc + (contDec + 1) * contDec + 1) * 1000;
    System.out.println(rank + ", " + seq.length);
    return false;
  }

  static boolean ratioRankCork(float[] seq) {
    float max = 1;
    int leakPhases = 0;
    float lastVolume = 1;
//		float maxRank = 0;
    float rank = 0;
    for (int i = 1; i < seq.length; i++) {
      float thisVolume = seq[i];
      if (thisVolume > max * (1 - decayFactor)) {
        leakPhases++;
        if (thisVolume > max) {
          max = thisVolume;
        }
        if (thisVolume > lastVolume) {
          rank += leakPhases * (thisVolume / lastVolume - 1);
        } else {
          rank -= leakPhases * (lastVolume / thisVolume - 1) / 2;
        }
      } else {
        rank = 0;
        leakPhases = 0;
      }
      lastVolume = thisVolume;
//			System.out.println(leakPhases + ", " + rank);
      if (leakPhases >= 30 && rank > 10) {
        System.out.println(leakPhases + ", " + rank + ", " + max);
        return true;
      }
    }
    return false;
  }

  static void genDB(final TestResult tr) throws Exception {
    System.out.println("[PROCESSING] " + tr.signature);
    String dir = "/tmp/meminfo/HPNIWP/" + tr.action + "/" + tr.appName + "/" + tr.mainClass;
    String filename = dir + "/" + tr.testCase;
    File file = new File(filename);
    if (file.exists() && !overwrite) {
      System.out.println("Data already exist: " + filename);
      return;
    }
    try {
      tr.convert();
    } catch (Exception ex) {
      System.out.println("[MISSING] " + tr.signature);
      if (file.exists()) {
        Runtime.getRuntime().exec("rm -f " + filename);
      }
      return;
    }
    Runtime.getRuntime().exec("mkdir -p " + dir);
    PrintWriter out = new PrintWriter(new FileWriter(file));

    float[] ns = tr.nativeSeq;
    float[] ds = tr.dalvikSeq;
    int len = ns.length;
    out.println(tr.signature);
    out.println(len);
    out.println(tr.nativeInit + "," + tr.dalvikInit);

    for (int i = 0; i < len; i++) {
      out.println(ns[i] + "," + ds[i]);
    }
    out.flush();
    out.close();
  }

  public static void process(final TestResult tr) throws InterruptedException {
    assert tr != null;
    System.out.println("--- Processing " + tr.testId + " " + tr.action);


    try {
      tr.convert();
      tr.plot();
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("  [PLOT-EXCEPTION] " + tr.testId + " " + tr.action);
//			return;
    }

    if (!tr.passed) {
      System.out.println("  [TEST-FAILING] " + tr.testId + " " + tr.action);
    }
  }

  /*
   * For,
   * 	Native     2257      772     2216    13944     4574      573
   *
   * read,
   * 	13944
   *  4574
   */
  public static void readMemOfNewFormat(String line, String prefix) {
    int start = line.indexOf(prefix);
    String s = line.substring(start + prefix.length() + 27);
    readFirstTwo(s);
  }

  public static void readFirstTwo(String line) {
    int cnt = 0;
    int num = -1;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c >= '0' && c <= '9') {
        if (num == -1) num = (c - '0');
        else num = num * 10 + (c - '0');
      } else {
        if (num != -1) {
          intData[cnt] = num;
          cnt++;
          if (cnt == 2) return;
          num = -1;  // reset;
        }
      }
    }
  }

}
/*
  Example

--- BEGIN org.vudroid.test.MainTestCase#testMainBrowserActivity ACTION: rotate.tmp.action
Applications Memory Usage (kB):^M
Uptime: 24250 Realtime: 24250^M
^M
** MEMINFO in pid 285 [org.vudroid] **^M 
                    native   dalvik    other    total^M
            size:     3616     2887      N/A     6503^M
       allocated:     3551     2468      N/A     6019^M
            free:       64      419      N/A      483^M
           (Pss):      946     1816     2032     4794^M
  (shared dirty):     1616     4076     2544     8236^M
    (priv dirty):      848      828      724     2400^M
 ^M
 Objects^M
           Views:        0        ViewRoots:        0^M  
     AppContexts:        0       Activities:        0^M  
          Assets:        3    AssetManagers:        3^M  
   Local Binders:        5    Proxy Binders:       11^M 
Death Recipients:        0^M  
 OpenSSL Sockets:        0^M  
 ^M
 SQL^M
            heap:        0       memoryUsed:        0^M  
pageCacheOverflo:        0  largestMemAlloc:        0^M  
 ^M
 ^M
 Asset Allocations^M
    zip:/data/app/org.vudroid.test-1.apk:/resources.arsc: 1K^M
Applications Memory Usage (kB):^M
Uptime: 34442 Realtime: 34442^M
^M
** MEMINFO in pid 285 [org.vudroid] **^M 
                    native   dalvik    other    total^M
            size:     4528     3527      N/A     8055^M
       allocated:     4173     3058      N/A     7231^M
            free:      354      469      N/A      823^M
           (Pss):     1889     2491     2069     6449^M
  (shared dirty):     1604     4068     1016     6688^M
    (priv dirty):     1792     1488     1508     4788^M
 ^M
 Objects^M
           Views:        0        ViewRoots:        0^M
     AppContexts:        0       Activities:        0^M
          Assets:        3    AssetManagers:        3^M
   Local Binders:        7    Proxy Binders:       11^M
Death Recipients:        0^M
 OpenSSL Sockets:        0^M
 ^M
 SQL^M
            heap:        0       memoryUsed:        0^M
pageCacheOverflo:        0  largestMemAlloc:        0^M
 ^M
 ^M
 Asset Allocations^M
    zip:/data/app/org.vudroid.test-1.apk:/resources.arsc: 1K^M
--- END org.vudroid.test.MainTestCase#testMainBrowserActivity ACTION: rotate.tmp.action

The END marker is optional.
*/

/* NEW MEMINFO format
  
Applications Memory Usage (kB):
Uptime: 1106414 Realtime: 1106414

** MEMINFO in pid 77 [system] **
                         Shared  Private     Heap     Heap     Heap
                   Pss    Dirty    Dirty     Size    Alloc     Free
                ------   ------   ------   ------   ------   ------
       Native     2257      772     2216    13944     4574      573
       Dalvik     6400    11084     5844    13255    11693     1562
       Cursor       12        0       12                           
       Ashmem     2272     1520     1512                           
    Other dev        4        0        0                           
     .so mmap     2139     1792      808                           
    .jar mmap        0        0        0                           
    .apk mmap     1191        0        0                           
    .ttf mmap       20        0        0                           
    .dex mmap        0        0        0                           
   Other mmap     3165        8       24                           
      Unknown     1582      304     1568                           
        TOTAL    19042    15480    11984    27199    16267     2135
 
 Objects
               Views:       10         ViewRootImpl:        1
         AppContexts:        6           Activities:        0
              Assets:        3        AssetManagers:        3
       Local Binders:      102        Proxy Binders:      139
    Death Recipients:       54
     OpenSSL Sockets:        0
 
 SQL
                heap:      211          MEMORY_USED:      211
  PAGECACHE_OVERFLOW:       26          MALLOC_SIZE:       46
 
 DATABASES
      pgsz     dbsz   Lookaside(b)          cache  Dbname
         1       16             26         6/11/2  accounts.db
         1       27             28         2/13/2  settings.db
                                           3/19/6  (pooled # 1) settings.db
 
 Asset Allocations
    zip:/system/app/SettingsProvider.apk:/resources.arsc: 11K
    zip:/data/app/org.videolan.vlc-1.apk:/resources.arsc: 119K


*/
