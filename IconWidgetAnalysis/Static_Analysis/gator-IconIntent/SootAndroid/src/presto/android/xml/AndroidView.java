/*
 * AndroidView.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import presto.android.Configs;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AndroidView implements IAndroidView {
  private AndroidView parent;
  private final ArrayList<IAndroidView> children;

  private SootClass klass;
  private Integer id;
  private String text; // android:text

  // private ArrayList<String> includes;
  // private HashMap<String, Integer> includeeIdMap;

  // Absolute path or full class name where this view is declared.
  private String origin;

  public AndroidView() {
    this.children = Lists.newArrayList();
  }

  @Override
  public IAndroidView deepCopy() {
    AndroidView res = new AndroidView();
    // <src, tgt>
    LinkedList<Pair<AndroidView, AndroidView>> work = Lists.newLinkedList();
    work.add(new Pair<AndroidView, AndroidView>(this, res));

    while (!work.isEmpty()) {
      Pair<AndroidView, AndroidView> p = work.remove();
      AndroidView src = p.getO1();
      AndroidView tgt = p.getO2();
      tgt.klass = src.klass;
      tgt.id = src.id;
      tgt.text = src.text;
      tgt.origin = src.origin;

      int sz = src.getNumberOfChildren();
      for (int i = 0; i < sz; ++i) {
        IAndroidView newSrc = src.getChildInternal(i);
        if (newSrc instanceof IncludeAndroidView) {
          IAndroidView newTgt = newSrc.deepCopy();
          newTgt.setParent(tgt);
        } else {
          AndroidView newTgt = new AndroidView();
          newTgt.setParent(tgt);
          work.add(new Pair<AndroidView, AndroidView>((AndroidView) newSrc,
              newTgt));
        }
      }
    }

    return res;
  }

  @Override
  public void setParent(AndroidView parent) {
    setParent(parent, -1);
  }

  public void setParent(AndroidView parent, int i) {
    if (this.parent != null) {
      this.parent.removeChildInternal(this);
    }
    this.parent = parent;
    if (i == -1) {
      parent.addChildInternal(this);
    } else {
      parent.setChildInternal(i, this);
    }
  }

  public static void resolveGUINameSTR(String guiName) {
    if ("view".equals(guiName)) {
      throw new RuntimeException("It shouldn't happen!!!");
    }
    // TODO: read about mechanism of these tags,
    // and get the real thing in.
    if ("merge".equals(guiName) || "fragment".equals(guiName)) {
      guiName = "LinearLayout";
    }

    else if (guiName.equals("View")) {
      guiName = "android.view.View";
    }

    else if (guiName.equals("WebView")) {
      guiName = "android.webkit.WebView";
    }

    else if (guiName.equals("greendroid.widget.ActionBar")) {
      guiName = "greendroid.widget.GDActionBar";
    }

    // there's in fact a com.facebook.android.LoginButton, but
    // it requires build of some other code, which we may not
    // care. FIXME: change this if later we find it necessary.
    else if (guiName.equals("com.facebook.android.LoginButton")) {
      guiName = "com.facebook.widget.LoginButton";
    }

    // this class is marked @hidden in the platform, so we use its super
    // class instead
    else if (guiName.equals("android.widget.NumberPicker$CustomEditText")) {
      guiName = "android.widget.EditText";
    }
    // DONE with special handling
    if (!guiName.contains(".")) {

      String cls = Configs.widgetMap.get(guiName);
      if (cls == null) {
        System.out.println("[RESOLVELEVEL] GUI Widget not in the map: " + guiName);
      } else {
        Configs.onDemandClassSet.add(cls);
      }
      return;
    } else {
      Configs.onDemandClassSet.add(guiName);
    }

    // this seems safe, but we really need SootClass.BODIES (TODO)
    // Scene.v().tryLoadClass(guiName, SootClass.HIERARCHY);
  }

  public static SootClass resolveGUIName(String guiName) {
    if ("view".equals(guiName)) {
      throw new RuntimeException("It shouldn't happen!!!");
    }
    // TODO: read about mechanism of these tags,
    // and get the real thing in.
    if ("merge".equals(guiName) || "fragment".equals(guiName)) {
      guiName = "LinearLayout";
    }

    else if (guiName.equals("View")) {
      guiName = "android.view.View";
    }

    else if (guiName.equals("WebView")) {
      guiName = "android.webkit.WebView";
    }

    else if (guiName.equals("greendroid.widget.ActionBar")) {
      guiName = "greendroid.widget.GDActionBar";
    }

    // there's in fact a com.facebook.android.LoginButton, but
    // it requires build of some other code, which we may not
    // care. FIXME: change this if later we find it necessary.
    else if (guiName.equals("com.facebook.android.LoginButton")) {
      guiName = "com.facebook.widget.LoginButton";
    }

    // this class is marked @hidden in the platform, so we use its super
    // class instead
    else if (guiName.equals("android.widget.NumberPicker$CustomEditText")) {
      guiName = "android.widget.EditText";
    }

    // DONE with special handling

    SootClass res;
//    if (!guiName.contains(".")) {
//
//      String cls = "android.widget." + guiName;
//      if (!classExists(cls)) {
//        cls = "android.view." + guiName;
//      }
//      res = Scene.v().loadClassAndSupport(cls);
//    } else {
//      res = Scene.v().loadClassAndSupport(guiName);
//    }

    if (!guiName.contains(".")) {
      String cls = Configs.widgetMap.get(guiName);
      if (cls == null) {
        if (Configs.verbose) {
          System.out.println("GUI Widget not in the map: " + guiName);
        }
        cls = "android.widget." + guiName;
      }
      if (!classExists(cls)) {
        cls = "android.view." + guiName;
      }
      res = Scene.v().loadClassAndSupport(cls);
    } else {
      res = Scene.v().loadClassAndSupport(guiName);
    }

    // this seems safe, but we really need SootClass.BODIES (TODO)
    // Scene.v().tryLoadClass(guiName, SootClass.HIERARCHY);
    return res;
  }

  static boolean classExists(String className) {
    return SourceLocator.v().getClassSource(className) != null;
  }

  public void save(int guiId, String text, String guiName) {
    Integer i = null;
    if (guiId != -1) {
      i = new Integer(guiId);
    }
    this.id = i;

    this.text = text;
    if (!Configs.preRun) {
      try {
        klass = resolveGUIName(guiName);
      } catch (Exception ex) {
        ex.printStackTrace();
        System.err.println("Exception in expanding " + guiName + " in " + guiId);
      }
      // klass = Scene.v().tryLoadClass(guiName, SootClass.BODIES);
      // FIXME: turn this on when resolveGUIName() is done
      if (klass.isPhantom() && Configs.verbose) {
        System.err.println("[ERROR] Phantom GUI class `" + klass + "'!");
      }
    } else {
      //Pre-run mode
      resolveGUINameSTR(guiName);
    }
  }

  public AndroidView getParent() {
    return parent;
  }

  public void addChildInternal(IAndroidView node) {
    children.add(node);
  }

  public void removeChildInternal(IAndroidView node) {
    children.remove(node);
  }

  public void setChildInternal(int i, AndroidView child) {
    children.set(i, child);
  }

  public IAndroidView getChildInternal(int i) {
    return children.get(i);
  }

  ArrayList<AndroidView> childrenAfterResolve;

  public Iterator<AndroidView> getChildren() {
    if (childrenAfterResolve == null) {
      childrenAfterResolve = Lists.newArrayList();
      for (IAndroidView v : children) {
        if (!(v instanceof AndroidView)) {
          throw new RuntimeException("Include not fully resolved.");
        }
        childrenAfterResolve.add((AndroidView) v);
      }
    }
    return childrenAfterResolve.iterator();
  }

  // public Iterator<IAndroidView> getChildrenInternal() {
  // return children.iterator();
  // }

  public int getNumberOfChildren() {
    return children.size();
  }

  public SootClass getSootClass() {
    return klass;
  }

  public void setSootClass(SootClass klass) {
    this.klass = klass;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getOrigin() {
    return origin;
  }

  public void setOrigin(String origin) {
    this.origin = origin;
  }

  // CALLED after include-resolution
  public void dump() {
    LinkedList<Pair<AndroidView, String>> work = Lists.newLinkedList();
    work.addFirst(new Pair<AndroidView, String>(this, ""));
    while (!work.isEmpty()) {
      Pair<AndroidView, String> p = work.removeFirst();
      AndroidView node = p.getO1();
      String indent = p.getO2();
      System.out.printf("%s(%s, %s)\n", indent, node.getSootClass(),
          node.getId());
      ArrayList<IAndroidView> childrenList = node.children;
      int size = childrenList.size();
      for (int i = size - 1; i >= 0; --i) {
        AndroidView child = (AndroidView) childrenList.get(i);
        work.addFirst(new Pair<AndroidView, String>(child, indent + "  "));
      }
    }
  }
  
  /**
   * All attributes.
   */
  private HashMap<String, String> attributes = Maps.newHashMap();
  /**
   * Add an attribute.
   * @param attr
   * @param value
   */
  public void addAttr(String attr, String value) {
    attributes.put(attr, value);
  }
  /**
   * Get all attributes.
   * @return
   */
  public HashMap<String, String> getAttrs() {
    return attributes;
  }
}
