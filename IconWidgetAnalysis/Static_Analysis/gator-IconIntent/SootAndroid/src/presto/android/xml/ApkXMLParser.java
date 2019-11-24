/*
 * ApkXMLParser.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import org.xmlpull.v1.XmlPullParser;

import presto.android.Configs;
import presto.android.xml.XMLParser.AbstractXMLParser;
import soot.Scene;
import soot.toolkits.scalar.Pair;
import android.content.res.AXmlResourceParser;

// This is work-in-progress. Once this is done and some additional minor setting
// tweaks are done, the analysis will be ready to analyze APK files directly.
public class ApkXMLParser extends AbstractXMLParser {

  @Override
  public Set<Integer> getApplicationLayoutIdValues() {
    return null;
  }

  @Override
  public Set<Integer> getSystemLayoutIdValues() {
    return null;
  }

  @Override
  public Integer getSystemRLayoutValue(String layoutName) {
    return null;
  }

  @Override
  public String getApplicationRLayoutName(Integer value) {
    return null;
  }

  @Override
  public String getSystemRLayoutName(Integer value) {
    return null;
  }

  @Override
  public Set<Integer> getApplicationMenuIdValues() {
    return null;
  }

  @Override
  public Set<Integer> getSystemMenuIdValues() {
    return null;
  }

  @Override
  public String getApplicationRMenuName(Integer value) {
    return null;
  }

  @Override
  public String getSystemRMenuName(Integer value) {
    return null;
  }

  @Override
  public Set<Integer> getApplicationRIdValues() {
    return null;
  }

  @Override
  public Set<Integer> getSystemRIdValues() {
    return null;
  }

  @Override
  public Integer getSystemRIdValue(String idName) {
    return null;
  }

  @Override
  public String getApplicationRIdName(Integer value) {
    return null;
  }

  @Override
  public String getSystemRIdName(Integer value) {
    return null;
  }

  @Override
  public Set<Integer> getStringIdValues() {
    return null;
  }

  @Override
  public String getRStringName(Integer value) {
    return null;
  }

  @Override
  public String getStringValue(Integer idValue) {
    return null;
  }

  @Override
  public Iterator<String> getServices() { return services.iterator(); }

  @Override
  public AndroidView findViewById(Integer id) {
    return null;
  }

  @Override
  public Map<Integer, Pair<String, Boolean>> retrieveCallbacks() {
    return null;
  }

  private static ApkXMLParser theInstance;
  private ApkXMLParser() {
    try {
      doIt();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    System.out.println("--- exit for now");
    System.exit(0);
  }
  public static synchronized ApkXMLParser v() {
    if (theInstance == null) {
      theInstance = new ApkXMLParser();
    }
    return theInstance;
  }

  ZipFile apkFile;

  void doIt() throws Exception {
    apkFile = new ZipFile(Configs.project);
    readManifest();
  }

  void readManifest() throws Exception {
    InputStream manifest =
        apkFile.getInputStream(apkFile.getEntry("AndroidManifest.xml"));
    AXmlResourceParser parser = new AXmlResourceParser();
    parser.open(manifest);
    String androidNamespace = null;
    boolean isMain = false;
    boolean isLauncher = false;
    boolean inActivity = false;
    boolean inIntentFilter = false;
    int eventType = parser.getEventType();
    while (eventType != XmlPullParser.END_DOCUMENT) {
      String tagName = parser.getName();
      switch (eventType) {
        case XmlPullParser.START_TAG: {
//          System.out.println("tag: " + tagName);
          if (tagName.equals("manifest")) {
            androidNamespace = parser.getNamespace();
            appPkg = parser.getAttributeValue(null, "package");
          } else if (tagName.equals("activity")) {
            inActivity = true;
            String cls = Helper.getClassName(
                parser.getAttributeValue(androidNamespace, "name"), appPkg);
            if (cls != null) {
              activities.add(cls);
            }
            // TODO: main activity

            ActivityLaunchMode launchMode = ActivityLaunchMode.standard;
            String launchModeString = null;
            try {
              launchModeString = parser.getAttributeValue(androidNamespace, "launchMode");
            } catch (ArrayIndexOutOfBoundsException e) {
              if (Configs.verbose) {
                System.err.println("[WARNING] launchMode parse exception: " + e.getMessage());
              }
            }
            if (launchModeString != null && !launchModeString.isEmpty()) {
              launchMode = ActivityLaunchMode.valueOf(launchModeString);
            }
            activityAndLaunchModes.put(cls, launchMode);
          } else if (inActivity && tagName.equals("intent-filter")) {
            inIntentFilter = true;
          } else if (inIntentFilter && tagName.equals("action")) {
            String action = parser.getAttributeValue(androidNamespace, "name");
            if ("android.intent.action.MAIN".equals(action)) {
              isMain = true;
            }
          } else if (inIntentFilter && tagName.equals("category")) {
            String category = parser.getAttributeValue(androidNamespace, "name");
            if ("android.intent.category.LAUNCHER".equals(category)) {
              isLauncher = true;
            }
          }
          break;
        }
        case XmlPullParser.END_TAG: {
          if (tagName.equals("activity")) {
            if (isMain && isLauncher) {
              mainActivity = Scene.v().getSootClass(
                  activities.get(activities.size() - 1));
            }
            isMain = false;
            isLauncher = false;
            inActivity = false;
          } else if (tagName.equals("intent-filter")) {
            inIntentFilter = false;
          }
          break;
        }
      }
      eventType = parser.next();
    }
    // debug print
    System.out.println("appPkg=" + appPkg);
    System.out.println("mainActivity=" + mainActivity);
    for (String act : activities) {
      System.out.println("act: " + act);
    }
    parser.close();
  }

  public static void main(String[] args) {
    Configs.project = args[0];
    Configs.benchmarkName = args[1];
    Configs.mockScene = true;
    ApkXMLParser.v();
  }

@Override
public Set<Integer> getDrawableIdValues() {
	// TODO Auto-generated method stub
	return null;
}
}
