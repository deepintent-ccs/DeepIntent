/*
 * IntentAnalysisInfo.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.intent;

import java.util.Map;
import java.util.Set;

import presto.android.Logger;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class IntentAnalysisInfo {
  // ANY means the intent is not determined. Simply speaking we can not find the intent object
  public static final String Any = "ANY";
  // UNKOWN_TARGET_ACTIVITY means the target activity could be out side of current activity
  public static final String UnknownTargetActivity = "UNKNOWN_TARGET_ACTIVITY";
  // default category
  public static final String DefaultCategory = "android.intent.category.DEFAULT";
  private final static Set<IntentField> keySet = Sets.newHashSet();

  static {
    keySet.add(IntentField.SrcActivity);
    keySet.add(IntentField.TgtActivity);
    keySet.add(IntentField.Action);
    keySet.add(IntentField.Category);
    keySet.add(IntentField.MimeType);
    keySet.add(IntentField.Scheme);
    keySet.add(IntentField.Host);
    keySet.add(IntentField.Port);
    keySet.add(IntentField.Path);
    keySet.add(IntentField.All);
    keySet.add(IntentField.ImplicitTgtActivity);
  }

  private Map<IntentField, Set<String>> mydata;

  public IntentAnalysisInfo() {
    mydata = Maps.newHashMap();
    for (IntentField key : keySet) {
      Set<String> value = Sets.newHashSet();
      mydata.put(key, value);
    }
  }

  public boolean addData(IntentField key, String value) {
    if (!keySet.contains(key)) {
      Logger.err(getClass().getSimpleName(), "can not find key " + key + ", you can define new one if you want");
    }
    Set<String> values = this.mydata.get(key);
    int size = values.size();
    values.add(value);
    return values.size() > size;
  }

  public boolean addAllData(IntentAnalysisInfo anotherData) {
    boolean success = false;
    for (Map.Entry<IntentField, Set<String>> entries: anotherData.mydata.entrySet()) {
      IntentField key = entries.getKey();
      if (!keySet.contains(key)) {
        Logger.err(getClass().getSimpleName(), "can not find key " + key + ", you can define new one if you want");
      }
      Set<String> values = entries.getValue();
      Set<String> thisValues = this.mydata.get(key);
      int size = thisValues.size();
      thisValues.addAll(values);
      success = success || (thisValues.size() > size);
    }
    return success;
  }

  public boolean match(IntentFilter filter) {
    return filter.match(this);
  }

  public Set<String> getData(IntentField key) {
    return this.mydata.get(key);
  }

  public Map<IntentField, Set<String>> getAllData() {
    return this.mydata;
  }

  public boolean hasData() {
    boolean isEmpty = getData(IntentField.MimeType).isEmpty() && getData(IntentField.Scheme).isEmpty()
    && getData(IntentField.Host).isEmpty() && getData(IntentField.Port).isEmpty()
    && getData(IntentField.Path).isEmpty();
    return !isEmpty;
  }

  public void clearAll() {
    mydata.clear();
  }

  public void clearData(IntentField key) {
    this.mydata.get(key).clear();
  }

  public void copyOf(IntentAnalysisInfo another) {
    mydata.clear();
    addAllData(another);
  }

  public String toString() {
    return this.mydata.toString();
  }
}
