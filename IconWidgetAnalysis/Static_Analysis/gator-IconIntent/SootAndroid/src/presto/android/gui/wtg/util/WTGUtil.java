/*
 * WTGUtil.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.util;

import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import presto.android.Configs;
import presto.android.Configs.AsyncOpStrategy;
import presto.android.Hierarchy;
import presto.android.Logger;
import presto.android.MethodNames;
import presto.android.gui.graph.NAllocNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.wtg.intent.IntentAnalysisInfo;
import presto.android.gui.wtg.intent.IntentField;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Expr;
import soot.jimple.InvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class WTGUtil {
  private static WTGUtil wtgUtil;
  private static final String intentSpecFileName = Configs.wtgSpecFile;

  private final Hierarchy hier = Hierarchy.v();

  public final SootClass intentClass = Scene.v().getSootClass("android.content.Intent");

  public final SootClass activityClass = Scene.v().getSootClass("android.app.Activity");
  
  public final SootClass viewClass = Scene.v().getSootClass("android.view.View");

  public final SootClass dialogClass = Scene.v().getSootClass("android.app.Dialog");

  public final SootClass menuItemClass = Scene.v().getSootClass("android.view.MenuItem");
  
  public final SootClass handlerClass = Scene.v().getSootClass("android.os.Handler");

  public final SootClass preferenceActivityClass = Scene.v().getSootClass("android.preference.PreferenceActivity");

  public final String launcherAction = "android.intent.action.MAIN";

  public final String launcherCategory = "android.intent.category.LAUNCHER";

  // <class, <subsig, <ignore flow within method, <pos of activity, pos of
  // intent>>>>
  private final Map<SootClass, Map<String, Pair<Integer, Integer>>> startActivityMethods = Maps.newHashMap();
  // <class, <subsig, <pos, field>>>
  private final Map<SootClass, Map<String, Map<Integer, IntentField>>> setIntentContentMethods = Maps.newHashMap();
  // <class, <subsig, <pos, field>>>
  private final Map<SootClass, Map<String, Map<Integer, IntentField>>> createIntentMethods = Maps.newHashMap();
  // <class, <subsig, <pos, field>>>
  private final Map<SootClass, Map<String, Map<String, Integer>>> propagateIntentMethods = Maps.newHashMap();
  // <class, <subsig, <pos, field>>>
  private final Map<SootClass, Map<String, Map<String, Integer>>> propagateValueMethods = Maps.newHashMap();
  // <class, <subsig, pos of retrieved intent>>
  private final Map<SootClass, Map<String, Integer>> getIntentMethods = Maps.newHashMap();
  // <class, <subsig, <pos, (field,value)>>>
  private final Map<SootClass, Map<String, Map<Integer, Pair<IntentField, String>>>> defineIntentContentMethods = Maps.newHashMap();
  // <class, <subsig, pos of intent>>
  private final Map<SootClass, Map<String, Pair<Integer, Integer>>> menuItemSetIntentMethods = Maps.newHashMap();
  // <class, <subsig, pos of object>>
  private final Map<SootClass, Map<String, Integer>> getClassMethods = Maps.newHashMap();
  //<class, <subsig, pos of item>>
  private final Map<SootClass, Map<String, Integer>> getIdMethods = Maps.newHashMap();
  //<class, <subsig, pos of inserted element>>
  private final Map<SootClass, Map<String, Integer>> writeContainerMethods = Maps.newHashMap();
  //<class, <subsig, pos of retrieved element>>
  private final Map<SootClass, Map<String, Integer>> readContainerMethods = Maps.newHashMap();
  //<class, <subsig, pos of receiver>>
  private final Map<SootClass, Map<String, Integer>> acquireResourceMethods = Maps.newHashMap();
  //<class, <subsig, pos of receiver>>
  private final Map<SootClass, Map<String, Integer>> releaseResourceMethods = Maps.newHashMap();
  //<class, subsig>
  private final Map<SootClass, Set<String>> specialAllocationMethods = Maps.newHashMap();
  //<class, <subsig, pos of runnable>>
  private final Map<SootClass, Map<String, Integer>> asyncOperationMethods = Maps.newHashMap();

  private final Map<SootClass, Map<String, Integer>> serviceRelatedMethods = Maps.newHashMap();

  // specify the methods that should be ignored, specified in intents.xml
  // <class, subsig>
  private final Map<SootClass, Set<String>> ignoreMethods = Maps.newHashMap();

  public static synchronized WTGUtil v() {
    if (wtgUtil == null) {
      wtgUtil = new WTGUtil();
      wtgUtil.readFromSpecificationFile(intentSpecFileName);
    }
    return wtgUtil;
  }

  // to check bindObject set up runnable
  public final SootClass threadClass = Scene.v().getSootClass("java.lang.Thread");
  public final SootClass runnableClass = Scene.v().getSootClass("java.lang.Runnable");
  public final String[][] bindImplicitMethodSubSigs = new String[][] {
      /** Thread.start() and Thread.run() **/
      new String[] { threadClass.getName(), },
      new String[] {
          "void <init>(java.lang.Runnable)",
          "void <init>(java.lang.ThreadGroup,java.lang.Runnable)",
          "void <init>(java.lang.Runnable,java.lang.String)",
          "void <init>(java.lang.ThreadGroup,java.lang.Runnable,java.lang.String)",
      "void <init>(java.lang.ThreadGroup,java.lang.Runnable,java.lang.String,long)" },
      new String[] { "void run()", "void start()" }, };

  public boolean isBindImplicitMethodCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (int i = 0; i < bindImplicitMethodSubSigs.length; i += 3) {
      SootClass clz = Scene.v().getSootClass(bindImplicitMethodSubSigs[i][0]);
      if (!hier.isSubclassOf(declz, clz)) {
        continue;
      }
      for (int j = 0; j < bindImplicitMethodSubSigs[i + 1].length; j++) {
        String subsig = bindImplicitMethodSubSigs[i + 1][j];
        if (subsig.equals(mtdSig)) {
          return true;
        }
      }
    }
    return false;
  }

  public SootClass bindObjectType(SootMethod mtd) {
    SootClass declz = mtd.getDeclaringClass();
    for (int i = 0; i < bindImplicitMethodSubSigs.length; i += 3) {
      SootClass clz = Scene.v().getSootClass(bindImplicitMethodSubSigs[i][0]);
      if (!hier.isSubclassOf(declz, clz)) {
        continue;
      }
      return clz;
    }
    return null;
  }

  // to check thread.exec
  public final String runnableRunMethodSubSig = "void run()";

  public boolean isRunBindImplicitMethodCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (int i = 0; i < bindImplicitMethodSubSigs.length; i += 3) {
      SootClass clz = Scene.v().getSootClass(bindImplicitMethodSubSigs[i][0]);
      if (!hier.isSubclassOf(declz, clz)) {
        continue;
      }
      for (int j = 0; j < bindImplicitMethodSubSigs[i + 2].length; j++) {
        String subsig = bindImplicitMethodSubSigs[i + 2][j];
        if (subsig.equals(mtdSig)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isStartActivityCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : startActivityMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Pair<Integer, Integer>> subsigPair = startActivityMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  // MenuItem item = Menu.add(int...)
  public boolean isMenuItemAddCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    InvokeExpr ie = s.getInvokeExpr();
    SootMethod mtd = ie.getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    boolean is = wtgUtil.hier.isMenuClass(declz);
    if (!is) {
      return false;
    }
    return mtdSig.equals("android.view.MenuItem add(int)")
        || mtdSig.equals("android.view.MenuItem add(java.lang.CharSequence)")
        || mtdSig.equals("android.view.MenuItem add(int,int,int,int)")
        || mtdSig.equals("android.view.MenuItem add(int,int,int,java.lang.CharSequence)");
  }

  // void android.view.MenuInflater.inflate(int menuRes, Menu menu)
  public boolean isMenuInflateCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    InvokeExpr ie = s.getInvokeExpr();
    SootMethod mtd = ie.getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    boolean is = hier.isSubclassOf(declz, Scene.v().getSootClass("android.view.MenuInflater"));
    if (!is) {
      return false;
    }
    return mtdSig.equals("void inflate(int,android.view.Menu)");
  }

  public Pair<Integer, Integer> getStartActivityIntentField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : startActivityMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Pair<Integer, Integer>> subsigPair = startActivityMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    Logger.err(getClass().getSimpleName(), "we can not find the intent position for call: " + mtd);
    return null;
  }

  public boolean isIntentType(SootClass sc) {
    Set<SootClass> subClasses = hier.getSubtypes(intentClass);
    return subClasses.contains(sc);
  }

  public boolean isIntentAllocNode(NNode node) {
    if (!(node instanceof NAllocNode)) {
      return false;
    }
    Expr e = ((NAllocNode) node).e;
    if (!(e instanceof NewExpr)) {
      return false;
    }
    NewExpr newExpr = (NewExpr) e;
    SootClass initType = newExpr.getBaseType().getSootClass();
    return wtgUtil.isIntentType(initType);
  }

  public boolean isCreateIntentCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    return isCreateIntentCall(mtd);
  }

  private boolean isCreateIntentCall(SootMethod mtd) {
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : createIntentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<Integer, IntentField>> subsigPair = createIntentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  public Set<Pair<Integer, IntentField>> getCreateIntentFields(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : createIntentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<Integer, IntentField>> subsigPair = createIntentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          Map<Integer, IntentField> map = subsigPair.get(mtdSig);
          Set<Pair<Integer, IntentField>> fields = Sets.newHashSet();
          for (Map.Entry<Integer, IntentField> entry : map.entrySet()) {
            Integer pos = entry.getKey();
            IntentField field = entry.getValue();
            Pair<Integer, IntentField> pair = new Pair<Integer, IntentField>(
                pos, field);
            fields.add(pair);
          }
          return fields;
        }
      }
    }
    Logger.err(getClass().getSimpleName(), "we can not find the content fields for call: " + mtd);
    return null;
  }

  public boolean isCreateIntentAllocNode(NNode node) {
    if (!(node instanceof NAllocNode)) {
      return false;
    }
    Expr e = ((NAllocNode) node).e;
    if (!(e instanceof InvokeExpr)) {
      return false;
    }
    return isCreateIntentCall(((InvokeExpr) e).getMethod());
  }

  public boolean isSetIntentContentCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : setIntentContentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<Integer, IntentField>> subsigPair = setIntentContentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  public Set<Pair<Integer, IntentField>> getSetIntentContentFields(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : setIntentContentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<Integer, IntentField>> subsigPair = setIntentContentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          Map<Integer, IntentField> map = subsigPair.get(mtdSig);
          Set<Pair<Integer, IntentField>> fields = Sets.newHashSet();
          for (Map.Entry<Integer, IntentField> entry : map.entrySet()) {
            Integer pos = entry.getKey();
            IntentField field = entry.getValue();
            Pair<Integer, IntentField> pair = new Pair<Integer, IntentField>(
                pos, field);
            fields.add(pair);
          }
          return fields;
        }
      }
    }
    Logger.err(getClass().getSimpleName(), "we can not find the content fields for call: " + mtd);
    return null;
  }

  public boolean isIntentPropagationCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : propagateIntentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<String, Integer>> subsigPair = propagateIntentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  public Map<String, Integer> getIntentPropagationFields(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : propagateIntentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<String, Integer>> subsigPair = propagateIntentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          Map<String, Integer> map = subsigPair.get(mtdSig);
          Map<String, Integer> fields = Maps.newHashMap();
          for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer pos = entry.getValue();
            String field = entry.getKey();
            fields.put(field, pos);
          }
          return fields;
        }
      }
    }
    Logger.err(getClass().getSimpleName(), "we can not find the content fields for call: " + mtd);
    return null;
  }

  public boolean isValuePropagationCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : propagateValueMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<String, Integer>> subsigPair = propagateValueMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  public Map<String, Integer> getValuePropagationFields(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : propagateValueMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<String, Integer>> subsigPair = propagateValueMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          Map<String, Integer> map = subsigPair.get(mtdSig);
          Map<String, Integer> fields = Maps.newHashMap();
          for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer pos = entry.getValue();
            String field = entry.getKey();
            fields.put(field, pos);
          }
          return fields;
        }
      }
    }
    Logger.err(getClass().getSimpleName(), "we can not find the content fields for call: " + mtd);
    return null;
  }

  public boolean isDefineIntentContentCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : defineIntentContentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<Integer, Pair<IntentField, String>>> subsigPair = defineIntentContentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  public Set<Pair<IntentField, String>> getDefineIntentContentFields(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : defineIntentContentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Map<Integer, Pair<IntentField, String>>> subsigPair = defineIntentContentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          Set<Pair<IntentField, String>> contents = Sets.newHashSet();
          for (Pair<IntentField, String> pair : subsigPair.get(mtdSig).values()) {
            contents.add(pair);
          }
          return contents;
        }
      }
    }
    Logger.err(getClass().getSimpleName(), "we can not find the define intent for call: " + mtd);
    return null;
  }

  public boolean isGetIntentCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : getIntentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = getIntentMethods.get(clz);
        if (subsigPair.containsKey(mtdSig)) {
          return true;
        }
      }
    }
    return false;
  }

  public Integer getGetIntentField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : getIntentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = getIntentMethods.get(clz);
        if (subsigPair.containsKey(mtdSig)) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    Logger.err(getClass().getSimpleName(), "we can not find the intent position for call: " + mtd);
    return null;
  }

  public boolean isMenuItemSetIntentCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : menuItemSetIntentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Pair<Integer, Integer>> subsigPair = menuItemSetIntentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  public Pair<Integer, Integer> getMenuItemSetIntentField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : menuItemSetIntentMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Pair<Integer, Integer>> subsigPair = menuItemSetIntentMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    return null;
  }

  public SootClass getActivitySootClassByNameSig(String shortName) {
    for (SootClass sc : hier.applicationActivityClasses) {
      if (sc.getShortName().equals(shortName) || sc.getName().equals(shortName)) {
        return sc;
      }
    }
    return null;
  }

  public boolean isLauncherIntent(IntentAnalysisInfo intentInfo) {
    return intentInfo.getData(IntentField.Action).contains(launcherAction)
        && intentInfo.getData(IntentField.Category).contains(launcherCategory);
  }

  public boolean isExecutionExitCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    InvokeExpr ie = s.getInvokeExpr();
    if (!(ie instanceof StaticInvokeExpr)) {
      return false;
    }
    SootMethod mtd = ie.getMethod();
    if (mtd.getSubSignature().equals("void exit(int)")
        && mtd.getDeclaringClass() == Scene.v()
        .getSootClass("java.lang.System")) {
      return true;
    } else {
      return false;
    }
  }

  // should we ignore this method? depending on intents.xml
  public boolean isIgnoredMethod(SootMethod mtd) {
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : ignoreMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Set<String> subsigs = ignoreMethods.get(clz);
        is = subsigs.contains(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }
  public boolean isGetClassCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : getClassMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = getClassMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }
  public Integer getGetClassField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : getClassMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = getClassMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    return null;
  }
  public boolean isGetIdCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : getIdMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = getIdMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }
  public Integer getGetIdField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : getIdMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = getIdMethods
            .get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    return null;
  }
  public boolean isActivityFinishCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    InvokeExpr ie = s.getInvokeExpr();
    SootMethod mtd = ie.getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    boolean is = hier.isSubclassOf(declz, activityClass);
    if (!is) {
      return false;
    }
    return mtdSig.equals("void finish()");
  }
  public boolean isActivitySetResultCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    InvokeExpr ie = s.getInvokeExpr();
    SootMethod mtd = ie.getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    boolean is = hier.isSubclassOf(declz, activityClass);
    if (!is) {
      return false;
    }
    return mtdSig.equals("void setResult(int)")
        || mtdSig.equals("void setResult(int,android.content.Intent)");
  }

  public boolean isWriteContainerCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : writeContainerMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = writeContainerMethods.get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  public Integer getWriteContainerField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : writeContainerMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = writeContainerMethods.get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    return null;
  }

  public boolean isReadContainerCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : readContainerMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = readContainerMethods.get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return true;
        }
      }
    }
    return false;
  }

  public Integer getReadContainerField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : readContainerMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = readContainerMethods.get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    return null;
  }

  /** Activity.showDialog(int) or Activity.showDialog(Bundle,int) **/
  public boolean isActivityShowDialogCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    if (hier.isSubclassOf(declz, activityClass)) {
      return mtdSig.equals(MethodNames.activityShowDialogSubSig)
          || mtdSig.equals(MethodNames.activityShowDialogBundleSubSig);
    }
    return false;
  }

  private void readFromSpecificationFile(String fn) {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    Document doc = null;
    try {
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      doc = dBuilder.parse(fn);
    } catch (Exception ex) {
      Logger.err(getClass().getSimpleName(), ex.getMessage());
    }
    NodeList roles = doc.getElementsByTagName("role");
    for (int i = 0; i < roles.getLength(); i++) {
      Node role = roles.item(i);
      String type = role.getAttributes().getNamedItem("type").getNodeValue();
      if (type.equals("startActivity")) {
        readStartActivityRole(role);
      } else if (type.equals("setIntentContent")) {
        readSetIntentContentRole(role);
      } else if (type.equals("propagateIntent")) {
        readPropagateIntentRole(role);
      } else if (type.equals("propagateValue")) {
        readPropagateValueRole(role);
      } else if (type.equals("createIntent")) {
        readCreateIntentRole(role);
      } else if (type.equals("menuItemSetIntent")) {
        readMenuItemSetIntentRole(role);
      } else if (type.equals("getIntent")) {
        readGetIntentRole(role);
      } else if (type.equals("ignoreMethod")) {
        readIgnoreMethodRole(role);
      } else if (type.equals("getClass")) {
        readGetClassRole(role);
      } else if (type.equals("getId")) {
        readGetIdRole(role);
      } else if (type.equals("writeToContainer")) {
        readWriteToContainerRole(role);
      } else if (type.equals("readFromContainer")) {
        readReadFromContainerRole(role);
      } else if (type.equals("acquireResource")) {
        readAcquireResourceRole(role);
      } else if (type.equals("releaseResource")) {
        readReleaseResourceRole(role);
      } else if (type.equals("specialAllocation")) {
        readSpecialAllocationRole(role);
      } else if (type.equals("asyncOperation")) {
        readAsyncOpFromAsyncOperationRole(role);
      } else if (type.equals("serviceOperations")) {
        readServiceOpsRole(role);
      } else {
        Logger.err(getClass().getSimpleName(), "reading " + intentSpecFileName
            + " failed, since we can not find role=" + type);
      }
    }
  }

  public boolean isAcquireResourceCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : acquireResourceMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = acquireResourceMethods.get(clz);
        if (subsigPair.containsKey(mtdSig)) {
          return true;
        }
      }
    }
    return false;
  }

  public Integer getAcquireResourceField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : acquireResourceMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = acquireResourceMethods.get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    return null;
  }

  public boolean isReleaseResourceCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : releaseResourceMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = releaseResourceMethods.get(clz);
        if (subsigPair.containsKey(mtdSig)) {
          return true;
        }
      }
    }
    return false;
  }

  public Integer getReleaseResourceField(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return null;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : releaseResourceMethods.keySet()) {
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = releaseResourceMethods.get(clz);
        is = subsigPair.containsKey(mtdSig);
        if (is) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    return null;
  }
  
  public boolean isAsyncMethodCall(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return false;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : asyncOperationMethods.keySet()) {
      if ((Configs.asyncStrategy == AsyncOpStrategy.Default_EventHandler_Async
          || Configs.asyncStrategy == AsyncOpStrategy.Default_Special_Async)
          && clz != viewClass && clz != activityClass) {
        continue;
      }
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        if ((Configs.asyncStrategy == AsyncOpStrategy.Default_EventHandler_Async
            || Configs.asyncStrategy == AsyncOpStrategy.Default_Special_Async)
            && !mtdSig.equals("boolean post(java.lang.Runnable)")
            && !mtdSig.equals("void runOnUiThread(java.lang.Runnable)")) {
          continue;
        }
        Map<String, Integer> subsigPair = asyncOperationMethods.get(clz);
        if (subsigPair.containsKey(mtdSig)) {
          return true;
        }
      }
    }
    return false;
  }

  public Integer getAsyncMethodCallRunnableArg(Stmt s) {
    if (!s.containsInvokeExpr()) {
      return -1;
    }
    SootMethod mtd = s.getInvokeExpr().getMethod();
    SootClass declz = mtd.getDeclaringClass();
    String mtdSig = mtd.getSubSignature();
    for (SootClass clz : asyncOperationMethods.keySet()) {
      if ((Configs.asyncStrategy == AsyncOpStrategy.Default_EventHandler_Async
          || Configs.asyncStrategy == AsyncOpStrategy.Default_Special_Async)
          && clz != viewClass && clz != activityClass) {
        continue;
      }
      boolean is = hier.isSubclassOf(declz, clz);
      if (is) {
        Map<String, Integer> subsigPair = asyncOperationMethods.get(clz);
        if ((Configs.asyncStrategy == AsyncOpStrategy.Default_EventHandler_Async
            || Configs.asyncStrategy == AsyncOpStrategy.Default_Special_Async)
            && !mtdSig.equals("boolean post(java.lang.Runnable)")
            && !mtdSig.equals("void runOnUiThread(java.lang.Runnable)")) {
          continue;
        }
        if (subsigPair.containsKey(mtdSig)) {
          return subsigPair.get(mtdSig);
        }
      }
    }
    return -1;
  }
  
  public Map<SootClass, Map<String, Integer>> getAcquireAPISet(){
    return this.acquireResourceMethods;
  }
  
  public Map<SootClass, Map<String, Integer>> getReleaseAPISet(){
    return this.releaseResourceMethods;
  }

  private void readReleaseResourceRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) continue;
      String classType = classNode.getAttributes().getNamedItem("type").getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) Logger.err(getClass().getSimpleName(), "can not find soot class for " + classType);
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) continue;
        String subsig = invocation.getAttributes().getNamedItem("subsig").getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) continue;
          String posStr = arg.getAttributes().getNamedItem("pos").getNodeValue();
          Map<String, Integer> subsigPair = releaseResourceMethods.get(sc);
          if (subsigPair == null) {
            subsigPair = Maps.newHashMap();
            releaseResourceMethods.put(sc, subsigPair);
          }
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "we have already defined releaseResource method: " + subsig);
          }
          subsigPair.put(subsig, Integer.parseInt(posStr));
        }
      }
    }
  }

  private void readAcquireResourceRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) continue;
      String classType = classNode.getAttributes().getNamedItem("type").getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) Logger.err(getClass().getSimpleName(), "can not find soot class for " + classType);
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) continue;
        String subsig = invocation.getAttributes().getNamedItem("subsig").getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) continue;
          String posStr = arg.getAttributes().getNamedItem("pos").getNodeValue();
          Map<String, Integer> subsigPair = acquireResourceMethods.get(sc);
          if (subsigPair == null) {
            subsigPair = Maps.newHashMap();
            acquireResourceMethods.put(sc, subsigPair);
          }
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "we have already defined acquireResource method: " + subsig);
          }
          subsigPair.put(subsig, Integer.parseInt(posStr));
        }
      }
    }
  }


  private void readServiceOpsRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) continue;
      String classType = classNode.getAttributes().getNamedItem("type").getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) Logger.err(getClass().getSimpleName(), "can not find soot class for " + classType);
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) continue;
        String subsig = invocation.getAttributes().getNamedItem("subsig").getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) continue;
          String posStr = arg.getAttributes().getNamedItem("pos").getNodeValue();
          Map<String, Integer> subsigPair = serviceRelatedMethods.get(sc);
          if (subsigPair == null) {
            subsigPair = Maps.newHashMap();
            serviceRelatedMethods.put(sc, subsigPair);
          }
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "we have already defined acquireResource method: " + subsig);
          }
          subsigPair.put(subsig, Integer.parseInt(posStr));
        }
      }
    }
  }

  public boolean isServiceRelated(Stmt u){
    if (u.containsInvokeExpr()){
      InvokeExpr ie = u.getInvokeExpr();
      SootMethod mtd = ie.getMethod();
      SootClass declz = mtd.getDeclaringClass();
      String mtdSig = mtd.getSubSignature();
      for (SootClass clz : serviceRelatedMethods.keySet()) {
        boolean is = hier.isSubclassOf(declz, clz);
        if (is) {
          Map<String, Integer> subsigPair = serviceRelatedMethods.get(clz);
          if (subsigPair.containsKey(mtdSig)) {
            return true;
          }
        }
      }
    }
    return false;
  }


  private void readSpecialAllocationRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) continue;
      String classType = classNode.getAttributes().getNamedItem("type").getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) Logger.err(getClass().getSimpleName(), "can not find soot class for " + classType);
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) continue;
        String subsig = invocation.getAttributes().getNamedItem("subsig").getNodeValue();
        Set<String> subsigs = specialAllocationMethods.get(sc);
        if (subsigs == null) {
          subsigs = Sets.newHashSet();
          specialAllocationMethods.put(sc, subsigs);
        }
        if (subsigs.contains(subsig)) {
          Logger.err(getClass().getSimpleName(), "we have already defined special allocation method: "
                  + subsig);
        } else {
          subsigs.add(subsig);
        }
      }
    }
  }

  private void readStartActivityRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String intentPos = arg.getAttributes().getNamedItem("intentPos")
              .getNodeValue();
          String cxtPos = "0";
          Node cxtPosNode = arg.getAttributes().getNamedItem("cxtPos");
          if (cxtPosNode != null) {
            cxtPos = cxtPosNode.getNodeValue();
          }
          if (startActivityMethods.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "define duplicated startActivity for subsig: "
                    + subsig);
          }
          Map<String, Pair<Integer, Integer>> subsigPair = startActivityMethods
              .get(sc);
          if (subsigPair == null) {
            subsigPair = Maps.newHashMap();
            startActivityMethods.put(sc, subsigPair);
          }
          subsigPair.put(
              subsig,
              new Pair<Integer, Integer>(Integer.parseInt(cxtPos), Integer
                  .parseInt(intentPos)));
        }
      }
    }
  }

  private void readSetIntentContentRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        if (setIntentContentMethods.containsKey(subsig)) {
          Logger.err(getClass().getSimpleName(), "define duplicated setIntentContent for subsig: "
                  + subsig);
        }
        Map<String, Map<Integer, IntentField>> subsigPair = setIntentContentMethods
            .get(sc);
        if (subsigPair == null) {
          subsigPair = Maps.newHashMap();
          setIntentContentMethods.put(sc, subsigPair);
        }
        Map<Integer, IntentField> pos2FieldMap = Maps.newHashMap();
        subsigPair.put(subsig, pos2FieldMap);
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String pos = arg.getAttributes().getNamedItem("pos").getNodeValue();
          String fieldName = arg.getAttributes().getNamedItem("field")
              .getNodeValue();
          IntentField field = IntentField.valueOf(fieldName);
          int intPos = Integer.parseInt(pos);
          if (pos2FieldMap.containsKey(intPos)) {
            Logger.err(getClass().getSimpleName(), "define duplicated setIntentContent for subsig: "
                    + subsig + " on pos: " + pos);
          }
          pos2FieldMap.put(intPos, field);
        }
      }
    }
  }

  private void readPropagateIntentRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        if (propagateIntentMethods.containsKey(subsig)) {
          Logger.err(getClass().getSimpleName(), "define duplicated propagate intent method for subsig: "
                  + subsig);
        }
        Map<String, Map<String, Integer>> subsigPair = propagateIntentMethods
            .get(sc);
        if (subsigPair == null) {
          subsigPair = Maps.newHashMap();
          propagateIntentMethods.put(sc, subsigPair);
        }
        Map<String, Integer> fldToPosMap = Maps.newHashMap();
        subsigPair.put(subsig, fldToPosMap);
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          if (fldToPosMap.containsKey("srcPosition")
              || fldToPosMap.containsKey("tgtPosition")) {
            Logger.err(getClass().getSimpleName(), "define duplicated src and tgt for propagate intent method subsig: "
                    + subsig);
          }
          String src = arg.getAttributes().getNamedItem("srcPosition")
              .getNodeValue();
          String tgt = arg.getAttributes().getNamedItem("tgtPosition")
              .getNodeValue();
          int srcPos = Integer.parseInt(src);
          int tgtPos = Integer.parseInt(tgt);
          fldToPosMap.put("srcPosition", srcPos);
          fldToPosMap.put("tgtPosition", tgtPos);
        }
      }
    }
  }

  private void readPropagateValueRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        if (propagateValueMethods.containsKey(subsig)) {
          Logger.err(getClass().getSimpleName(), "define duplicated propagate value method for subsig: "
                  + subsig);
        }
        Map<String, Map<String, Integer>> subsigPair = propagateValueMethods
            .get(sc);
        if (subsigPair == null) {
          subsigPair = Maps.newHashMap();
          propagateValueMethods.put(sc, subsigPair);
        }
        Map<String, Integer> fldToPosMap = Maps.newHashMap();
        subsigPair.put(subsig, fldToPosMap);
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          if (fldToPosMap.containsKey("srcPosition")
              || fldToPosMap.containsKey("tgtPosition")) {
            Logger.err(getClass().getSimpleName(), "define duplicated src and tgt for propagate value method subsig: "
                    + subsig);
          }
          String src = arg.getAttributes().getNamedItem("srcPosition")
              .getNodeValue();
          String tgt = arg.getAttributes().getNamedItem("tgtPosition")
              .getNodeValue();
          int srcPos = Integer.parseInt(src);
          int tgtPos = Integer.parseInt(tgt);
          fldToPosMap.put("srcPosition", srcPos);
          fldToPosMap.put("tgtPosition", tgtPos);
        }
      }
    }
  }

  private void readCreateIntentRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        continue;
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        if (createIntentMethods.containsKey(subsig)) {
          Logger.err(getClass().getSimpleName(), "define duplicated createIntent method for subsig: "
                  + subsig);
        }
        Map<String, Map<Integer, IntentField>> subsigPair = createIntentMethods
            .get(sc);
        if (subsigPair == null) {
          subsigPair = Maps.newHashMap();
          createIntentMethods.put(sc, subsigPair);
        }
        Map<Integer, IntentField> pos2FieldMap = Maps.newHashMap();
        subsigPair.put(subsig, pos2FieldMap);
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String pos = arg.getAttributes().getNamedItem("pos").getNodeValue();
          String fieldName = arg.getAttributes().getNamedItem("field")
              .getNodeValue();
          IntentField field = IntentField.valueOf(fieldName);
          int intPos = Integer.parseInt(pos);
          if (pos2FieldMap.containsKey(intPos)) {
            Logger.err(getClass().getSimpleName(), "define duplicated createIntent for subsig: " + subsig
                + " on pos: " + pos);
          }
          pos2FieldMap.put(intPos, field);
        }
      }
    }
  }

  private void readMenuItemSetIntentRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      Map<String, Pair<Integer, Integer>> subsigPair = menuItemSetIntentMethods
          .get(sc);
      if (subsigPair == null) {
        subsigPair = Maps.newHashMap();
        menuItemSetIntentMethods.put(sc, subsigPair);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        Pair<Integer, Integer> itemAndIntent = subsigPair.get(subsig);
        if (itemAndIntent == null) {
          itemAndIntent = new Pair<Integer, Integer>();
          subsigPair.put(subsig, itemAndIntent);
        }
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String intentPos = arg.getAttributes().getNamedItem("intentPos")
              .getNodeValue();
          String itemPos = arg.getAttributes().getNamedItem("itemPos")
              .getNodeValue();
          itemAndIntent.setO1(Integer.parseInt(itemPos));
          itemAndIntent.setO2(Integer.parseInt(intentPos));
        }
      }
    }
  }

  private void readGetIntentRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String posStr = arg.getAttributes().getNamedItem("pos")
              .getNodeValue();
          Map<String, Integer> subsigPair = getIntentMethods.get(sc);
          if (subsigPair == null) {
            subsigPair = Maps.newHashMap();
            getIntentMethods.put(sc, subsigPair);
          }
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "we have already defined getIntent method: " + subsig);
          }
          subsigPair.put(subsig, Integer.parseInt(posStr));
        }
      }
    }
  }

  private void readIgnoreMethodRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        Set<String> subsigs = ignoreMethods.get(sc);
        if (subsigs == null) {
          subsigs = Sets.newHashSet();
          ignoreMethods.put(sc, subsigs);
        }
        subsigs.add(subsig);
      }
    }
  }

  private void readGetClassRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      Map<String, Integer> subsigPair = getClassMethods.get(sc);
      if (subsigPair == null) {
        subsigPair = Maps.newHashMap();
        getClassMethods.put(sc, subsigPair);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String srcPos = arg.getAttributes().getNamedItem("srcPos")
              .getNodeValue();
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "duplicated definition for role=getClass: " + sc);
          }
          subsigPair.put(subsig, Integer.parseInt(srcPos));
        }
      }
    }
  }
  private void readGetIdRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      Map<String, Integer> subsigPair = getIdMethods.get(sc);
      if (subsigPair == null) {
        subsigPair = Maps.newHashMap();
        getIdMethods.put(sc, subsigPair);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String srcPos = arg.getAttributes().getNamedItem("srcPos")
              .getNodeValue();
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "duplicated definition for role=getItemId: " + sc);
          }
          subsigPair.put(subsig, Integer.parseInt(srcPos));
        }
      }
    }
  }
  private void readWriteToContainerRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      Map<String, Integer> subsigPair = writeContainerMethods.get(sc);
      if (subsigPair == null) {
        subsigPair = Maps.newHashMap();
        writeContainerMethods.put(sc, subsigPair);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String srcPos = arg.getAttributes().getNamedItem("srcPos")
              .getNodeValue();
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "duplicated definition for role=getItemId: " + sc);
          }
          subsigPair.put(subsig, Integer.parseInt(srcPos));
        }
      }
    }
  }

  private void readReadFromContainerRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class")) {
        continue;
      }
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null) {
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      }
      Map<String, Integer> subsigPair = readContainerMethods.get(sc);
      if (subsigPair == null) {
        subsigPair = Maps.newHashMap();
        readContainerMethods.put(sc, subsigPair);
      }
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation")) {
          continue;
        }
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg")) {
            continue;
          }
          String srcPos = arg.getAttributes().getNamedItem("tgtPos")
              .getNodeValue();
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(), "duplicated definition for role=getItemId: " + sc);
          }
          subsigPair.put(subsig, Integer.parseInt(srcPos));
        }
      }
    }
  }
  
  private void readAsyncOpFromAsyncOperationRole(Node role) {
    NodeList classes = role.getChildNodes();
    for (int i = 0; i < classes.getLength(); i++) {
      Node classNode = classes.item(i);
      if (!classNode.getNodeName().equals("class"))
        continue;
      String classType = classNode.getAttributes().getNamedItem("type")
          .getNodeValue();
      SootClass sc = Scene.v().getSootClass(classType);
      if (sc == null)
        Logger.err(getClass().getSimpleName(), "can not find soot class for "
            + classType);
      NodeList invocations = classNode.getChildNodes();
      for (int j = 0; j < invocations.getLength(); j++) {
        Node invocation = invocations.item(j);
        if (!invocation.getNodeName().equals("invocation"))
          continue;
        String subsig = invocation.getAttributes().getNamedItem("subsig")
            .getNodeValue();
        NodeList args = invocation.getChildNodes();
        for (int k = 0; k < args.getLength(); k++) {
          Node arg = args.item(k);
          if (!arg.getNodeName().equals("arg"))
            continue;
          String posStr = arg.getAttributes().getNamedItem("pos")
              .getNodeValue();
          Map<String, Integer> subsigPair = asyncOperationMethods.get(sc);
          if (subsigPair == null) {
            subsigPair = Maps.newHashMap();
            asyncOperationMethods.put(sc, subsigPair);
          }
          if (subsigPair.containsKey(subsig)) {
            Logger.err(getClass().getSimpleName(),
                "we have already defined releaseResource method: " + subsig);
          }
          subsigPair.put(subsig, Integer.parseInt(posStr));
        }
      }
    }
  }
}
