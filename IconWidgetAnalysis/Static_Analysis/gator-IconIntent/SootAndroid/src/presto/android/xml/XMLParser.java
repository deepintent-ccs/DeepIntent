/*
 * XMLParser.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import presto.android.Configs;
import soot.Scene;
import soot.SootClass;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public interface XMLParser {
  abstract class AbstractXMLParser implements XMLParser {
    Map<String, ActivityLaunchMode> activityAndLaunchModes = Maps.newHashMap();

    @Override
    public ActivityLaunchMode getLaunchMode(String activityClassName) {
      return activityAndLaunchModes.get(activityClassName);
    }

    protected String appPkg;

    protected final ArrayList<String> activities = Lists.newArrayList();
    protected final ArrayList<String> services = Lists.newArrayList();

    protected SootClass mainActivity;

    @Override
    public SootClass getMainActivity() {
      return mainActivity;
    }

    @Override
    public Iterator<String> getActivities() {
      return activities.iterator();
    }

    @Override
    public int getNumberOfActivities() {
      return activities.size();
    }

    @Override
    public String getAppPackageName() {
      return appPkg;
    }
  }

  class Helper {
    // These are declared in the manifest, but no corresponding .java/.class
    // files are available. Most likely, code was deleted while manifest has
    // not been updated.
    static Set<String> astridMissingActivities = Sets.newHashSet(
        "com.todoroo.astrid.tags.reusable.FeaturedListActivity",
        "com.todoroo.astrid.actfm.TagViewWrapperActivity",
        "com.todoroo.astrid.actfm.TagCreateActivity",
        "com.todoroo.astrid.gtasks.auth.GtasksAuthTokenProvider",
        "com.todoroo.astrid.reminders.NotificationWrapperActivity");
    static Set<String> nprMissingActivities = Sets.newHashSet(
        "com.crittercism.NotificationActivity");

    public static String getClassName(String classNameFromXml, String appPkg) {
      if ('.' == classNameFromXml.charAt(0)) {
        classNameFromXml = appPkg + classNameFromXml;
      }
      if (!classNameFromXml.contains(".")) {
        classNameFromXml = appPkg + "." + classNameFromXml;
      }
      if (Configs.benchmarkName.equals("Astrid")
          && astridMissingActivities.contains(classNameFromXml)) {
        return null;
      }
      if (Configs.benchmarkName.equals("NPR")
          && nprMissingActivities.contains(classNameFromXml)) {
        return null;
      }
      if (Scene.v().getSootClass(classNameFromXml).isPhantom()) {
        System.out.println("[WARNNING] : "+ classNameFromXml + 
            " is declared in AndroidManifest.xml, but phantom.");
        return null;
       // throw new RuntimeException(
       //     classNameFromXml + " is declared in AndroidManifest.xml, but phantom.");
      }
      return classNameFromXml;
    }
  }

  // A hacky factory method. It's good enough since the number of possible
  // XMLParser implementations is very limited. We may even end up with only
  // one and push the diff logic into the existing parser.
  class Factory {
    public static XMLParser getXMLParser() {
//      if (Configs.apkMode) {
//        return ApkXMLParser.v();
//      }
      return DefaultXMLParser.v();
    }
  }
  // === layout, id, string, menu xml files

  // R.layout.*
  public Set<Integer> getApplicationLayoutIdValues();

  public Set<Integer> getSystemLayoutIdValues();

  public Integer getSystemRLayoutValue(String layoutName);

  public String getApplicationRLayoutName(Integer value);

  public String getSystemRLayoutName(Integer value);

  // R.menu.*
  public Set<Integer> getApplicationMenuIdValues();

  public Set<Integer> getSystemMenuIdValues();

  public String getApplicationRMenuName(Integer value);

  public String getSystemRMenuName(Integer value);

  // R.id.*
  public Set<Integer> getApplicationRIdValues();

  public Set<Integer> getSystemRIdValues();

  public Integer getSystemRIdValue(String idName);

  public String getApplicationRIdName(Integer value);

  public String getSystemRIdName(Integer value);

  // R.string.*
  public Set<Integer> getStringIdValues();

  public String getRStringName(Integer value);

  public String getStringValue(Integer idValue);
  
  
  // R.drawable.*
  public Set<Integer> getDrawableIdValues();
  

  // === AndroidManifest.xml
  public SootClass getMainActivity();

  public Iterator<String> getActivities();

  public Iterator<String> getServices();

  public int getNumberOfActivities();

  public String getAppPackageName();

  public enum ActivityLaunchMode {
    standard,
    singleTop,
    singleTask,
    singleInstance
  }

  public ActivityLaunchMode getLaunchMode(String activityClassName);

  // === APIs for layout xml files

  // Given a view id, find static abstraction of the matched view.
  public AndroidView findViewById(Integer id);

  // retrieve callbacks defined in xml
  public Map<Integer, Pair<String, Boolean>> retrieveCallbacks();



}
