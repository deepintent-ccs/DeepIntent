/*
 * Hierarchy.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class Hierarchy {
  private final SootClass viewClass;
  private final SootClass menuClass;
  private final SootClass contextMenuClass;
  private final SootClass menuItemClass;
  private final SootClass collectionClass;
  private final SootClass iteratorClass;
  private final SootClass mapClass;
  private final SootClass dialogInterface;

  public boolean isSubclassOf(
      final String childClassName,final String parentClassName) {
    SootClass child = Scene.v().getSootClass(childClassName);
    SootClass parent = Scene.v().getSootClass(parentClassName);
    return isSubclassOf(child, parent);
  }

  public boolean isSubclassOf(final SootClass child, final SootClass parent) {
    Set<SootClass> superTypes = getSupertypes(child);
    if (superTypes != null) {
      return superTypes.contains(parent);
    }
    return isSubclassOfOnDemand(child, parent);
  }

  public boolean isSubclassOfOnDemand(
      final SootClass child, final SootClass parent) {
    if (parent.getName().equals("java.lang.Object")) {
      return true;
    }
    if (child.equals(parent)) {
      return true;
    }
    if (child.hasSuperclass()) {
      return isSubclassOfOnDemand(child.getSuperclass(), parent);
    }
    return false;
  }

  public boolean isGUIClass(final SootClass c) {
    return isSubclassOf(c, viewClass) || isSubclassOf(c, menuClass)
        || isSubclassOf(c, menuItemClass);
  }

  public boolean isViewClass(final SootClass c) {
    return isSubclassOf(c, viewClass);
  }

  public boolean isMenuClass(final SootClass c) {
    return isSubclassOf(c, menuClass);
  }

  public boolean isContextMenuClass(final SootClass c) {
    return isSubclassOf(c, contextMenuClass);
  }

  public boolean isMenuItemClass(final SootClass c) {
    return isSubclassOf(c, menuItemClass);
  }

  public boolean isCollectionClass(final SootClass c) {
    return isSubclassOf(c, collectionClass);
  }

  public boolean isIteratorClass(final SootClass c) {
    return isSubclassOf(c, iteratorClass);
  }

  public boolean isMapClass(final SootClass c) {
    return isSubclassOf(c, mapClass);
  }

  private boolean isDialog(final SootClass c) {
    return isSubclassOf(c, dialogInterface);
  }

  public boolean isDialogClass(final SootClass c) {
    return isDialog(c);
  }

  public boolean isActivityClass(final SootClass c) {
    return isSubclassOf(c, Scene.v().getSootClass("android.app.Activity"));
  }

  // -----------------------------------------
  // Returns a set of SootClasses: all transitive subtypes of c,
  // including c
  public Set<SootClass> getSubtypes(SootClass c) {
    return classAndItsSubTypes.get(c);
  }

  // -----------------------------------------
  // Returns a set of SootClasses: all transitive SUPERtypes of c,
  // including c
  public Set<SootClass> getSupertypes(SootClass c) {
    return classAndItsSuperTypes.get(c);
  }

  // ----------------------------------------------------------
  // Returns a set of SootClasses: all transitive subtypes of c
  // (including c) for which SootClass.isConcrete() is true
  public Set<SootClass> getConcreteSubtypes(SootClass c) {
    return classAndItsConcreteSubTypes.get(c);
  }

  // ---------------------------------------------------------
  // This method simulates the effects of the virtual dispatch
  // performed by the JVM at run time. Precondition:
  // receiver_class.isConcrete() == true
  public SootMethod virtualDispatch(SootMethod staticTarget,
      SootClass receiverClass) {
    // check the precondition, just in case
    if (!receiverClass.isConcrete()) {
      if (Configs.sanityCheck) {
        throw new RuntimeException(
                "Hierarchy.virtualDispatch called with non-concrete receiver class" + receiverClass.getName());
      } else {
        Logger.verb("WARNING", "Hierarchy.virtualDispatch called with non-concrete receiver class" + receiverClass.getName());
      }
    }
    // look up the method
    SootClass curr = receiverClass;
    while (curr != null) {
      if (curr.declaresMethod((staticTarget.getSubSignature()))) {
        return curr.getMethod(staticTarget.getSubSignature());
      }
      if (curr.hasSuperclass()) {
        curr = curr.getSuperclass();
      } else {
        curr = null; // for java.lang.Object
      }
    }

    if (Configs.verbose) {
      System.out.println(
          "[WARNING] No match in Hierarchy.virtualDispatch: \n\tmethod = "
              + staticTarget + "\n\ttype = " + receiverClass);
    }
    return null;
  }

  // ---------------------------------------------------------
  public SootMethod virtualDispatch(String staticTargetSubsig,
      SootClass receiverClass) {
    // check the precondition, just in case
    if (!receiverClass.isConcrete()) {
      if (Configs.sanityCheck) {
        throw new RuntimeException(
                "Hierarchy.virtualDispatch called with non-concrete receiver class: " + receiverClass
                        + ", subsig: " + staticTargetSubsig);
      } else {
        Logger.verb("WARNING", "Hierarchy.virtualDispatch called with non-concrete receiver class: " + receiverClass
                + ", subsig: " + staticTargetSubsig);
      }
    }
    // look up the method
    SootClass curr = receiverClass;
    while (curr != null) {
      if (curr.declaresMethod((staticTargetSubsig))) {
        return curr.getMethod(staticTargetSubsig);
      }
      if (curr.hasSuperclass()) {
        curr = curr.getSuperclass();
      } else {
        curr = null; // for java.lang.Object
      }
    }
    if (Configs.verbose) {
      System.out.println(
          "[WARNING] No match in Hierarchy.virtualDispatch: \n\tmethod subsig = "
              + staticTargetSubsig + "\n\ttype = " + receiverClass);
    }
    return null;
  }

  // ------------------
  public SootClass matchForVirtualDispatch(String staticTargetSubsig,
      SootClass receiverClass) {
    // look up the method
    SootClass currentClass = receiverClass;
    while (currentClass != null) {
      if (currentClass.declaresMethod(staticTargetSubsig)) {
        return currentClass;
      }
      if (currentClass.hasSuperclass()) {
        currentClass = currentClass.getSuperclass();
      } else {
        currentClass = null; // for java.lang.Object
      }
    }
    return null;
  }

  // ------------------
  public boolean isSibling(SootClass cls1, SootClass cls2, SootClass root) {
    int depth1 = 0, depth2 = 0;
    for (SootClass cls = cls1; cls.hasSuperclass(); cls = cls.getSuperclass()) {
      depth1++;
    }
    for (SootClass cls = cls2; cls.hasSuperclass(); cls = cls.getSuperclass()) {
      depth2++;
    }
    SootClass longer = cls1;
    SootClass shorter = cls2;
    if (depth1 < depth2) {
      longer = cls2;
      shorter = cls1;
    }
    for (int i = 0; i < Math.abs(depth1 - depth2); i++) {
      longer = longer.getSuperclass();
    }
    if (longer == shorter) {
      return false;
    }
    while (longer != shorter && longer.hasSuperclass() && shorter.hasSuperclass()) {
      longer = longer.getSuperclass();
      shorter = shorter.getSuperclass();
    }
    if (longer == shorter) {
      if (longer == root) {
        return true;
      }
      while (longer.hasSuperclass()) {
        longer = longer.getSuperclass();
        if (longer == root) {
          return true;
        }
      }
      return false;
    } else {
      return false;
    }
  }

  // ------------------
  private static Hierarchy instance;

  public static synchronized Hierarchy v() {
    if (instance == null) {
      instance = new Hierarchy();
    }
    return instance;
  }

  private Hierarchy() {
    Scene scene = Scene.v();

    viewClass = scene.getSootClass("android.view.View");
    menuClass = scene.getSootClass("android.view.Menu");
    contextMenuClass = scene.getSootClass("android.view.ContextMenu");
    menuItemClass = scene.getSootClass("android.view.MenuItem");
    collectionClass = scene.getSootClass("java.util.Collection");
    iteratorClass = scene.getSootClass("java.util.Iterator");
    mapClass = scene.getSootClass("java.util.Map");
    dialogInterface = scene.getSootClass("android.content.DialogInterface");

    simpleClassStatistics();

    // next, for each class/interface C, recursively traverse
    // all supertypes of C and add C to the appropriate sets
    for (SootClass c : scene.getClasses()) {
      traverse(c, c);
    }

    // Look at activities
    activityClasses();

    // figure out the view classes
    viewsAndMenus();

    // dialogs
    dialogs();

    recordFrameworkInvokedCallbacks();
  } // Hierarchy()

  void simpleClassStatistics() {
    Scene scene = Scene.v();
    int numClasses = 0;
    // first, create an empty set for each class/interface
    for (SootClass c : scene.getClasses()) {
      numClasses++;
      if (c.isApplicationClass()) {
        appClasses.add(c);
      }
      classAndItsConcreteSubTypes.put(c, new HashSet<SootClass>());
      classAndItsSubTypes.put(c, new HashSet<SootClass>());
      classAndItsSuperTypes.put(c, new HashSet<SootClass>());
    }
    System.out.print("[HIER] All classes: " + numClasses);
    System.out.print(" [App: " + appClasses.size());
    System.out.print(", Lib : " + scene.getLibraryClasses().size());
    System.out.println(", Phantom: " + scene.getPhantomClasses().size() + "]");
    if (numClasses != appClasses.size() + scene.getLibraryClasses().size()
        + scene.getPhantomClasses().size()) {
      throw new Error("[HIER] Numbers do not add up");
    }
  }

  void recordFrameworkInvokedCallbacks() {
    // (1) possible calbacks from the framework to app classes,
    // and (2) app classes that are instantiated by the framework
    for (SootClass c : appClasses) {
      boolean interesting = false;
      for (SootClass s : getSupertypes(c)) {
        if (s.isApplicationClass()) {
          continue;
        }
        if (s.getName().startsWith("java.")) {
          continue;
        }
        interesting = true;
        break;
      }
      if (!interesting) {
        continue;
      }

      // this application class is interesting - it has a
      // superclass/interface that is in the framework or in a
      // non-standard library. could be subjected to callbacks.

      // is it instantiated in the app code? if so, we will
      // assume that its creation and lifetime are not managed
      // by the framework
      boolean instantiated = instantiatedInApplicationCode(c);
      if (instantiated) {
        continue;
      }

      // Note: some View subclasses are instantiated via
      // inflation, and this will miss them (will erroneously
      // report them as not-instantiated). Later, after
      // inflating the XML files, we remove these from "todo"

      // TODO: for now, just deal with activities; later deal
      // with the other interesting application classes
      if (!applicationActivityClasses.contains(c)) {
        continue;
      }

      // here gather the callback methods (TODO: should be
      // generic to work for all classes, not just activities)
      Set<String> allSubsig = Sets.newHashSet();
      for (SootClass s : getSupertypes(c)) {
        if (s.isApplicationClass()) {
          continue;
        }
        if (s.getName().startsWith("java.")) {
          continue;
        }
        for (SootMethod m : s.getMethods()) {
          if (m.isStatic()) {
            continue;
          }
          if (m.isConstructor()) {
            continue;
          }
          // only public and protected methods can be overridden
          if (m.isPublic() || m.isProtected()) {
            allSubsig.add(m.getSubSignature());
          }
        }
      }
      Set<SootMethod> callbacks = Sets.newHashSet();
      // the no-parameters constructor can be called by the
      // framework as part of the instantiation procss
      if (c.declaresMethod("void <init>()")) {
        callbacks.add(c.getMethod("void <init>()"));
      }
      frameworkManaged.put(c, callbacks);
      for (String sub : allSubsig) {
        // do we have a match in the application code?
        for (SootClass t = c; t.isApplicationClass(); t = t.getSuperclass()) {
          if (t.declaresMethod(sub)) {
            SootMethod m = t.getMethod(sub);
            if (!m.isConcrete()) {
              Logger.verb("WARNING", "Callback method :" + m.getName() + " in Class "+ t.getName()
                      + " is not concrete");
              continue;
            }
            callbacks.add(m);
            // no need to look at more superclasses
            break;
          }
        }
      } // each subsignature
    } // each application class
  }

  void activityClasses() {
    // figure out the activity classes
    SootClass act = Scene.v().getSootClass("android.app.Activity");
    if (act == null) {
      throw new Error("[HIER] Did not find Activity");
    }
    for (SootClass c : appClasses) {
      if (getSupertypes(c).contains(act)) {
        applicationActivityClasses.add(c);
      }
    }
    for (SootClass c : Scene.v().getLibraryClasses()) {
      if (getSupertypes(c).contains(act)) {
        libActivityClasses.add(c);
      }
    }
    System.out.println("[HIER] Activities: " + applicationActivityClasses.size()
        + ", lib activities: " + libActivityClasses.size());
  }

  void viewsAndMenus() {
    Scene scene = Scene.v();
    SootClass view = scene.getSootClass("android.view.View");
    SootClass menuItem = scene.getSootClass("android.view.MenuItem");
    SootClass menu = scene.getSootClass("android.view.Menu");
    if (view == null) {
      throw new Error("[HIER] Did not find View");
    }

    int numAppViews = 0;
    for (SootClass c : appClasses) {
      Set<SootClass> superClzSet = getSupertypes(c);
      if (superClzSet.contains(view)) {
        numAppViews++;
        viewClasses.add(c);
      }
      if (superClzSet.contains(menuItem)) {
        menuItemClasses.add(c);
      }
      if (superClzSet.contains(menu)) {
        menuClasses.add(c);
      }
    }
    int numLibViews = 0;
    for (SootClass c : scene.getLibraryClasses()) {
      Set<SootClass> superClzSet = getSupertypes(c);
      if (superClzSet.contains(view)) {
        numLibViews++;
        viewClasses.add(c);
      }
      if (superClzSet.contains(menuItem)) {
        menuItemClasses.add(c);
      }
      if (superClzSet.contains(menu)) {
        menuClasses.add(c);
      }
    }
    System.out.println("[HIER] App views: " + numAppViews + ", Lib views: "
        + numLibViews);
  }

  void dialogs() {
    for (SootClass c : Scene.v().getClasses()) {
      if (isDialog(c)) {
        if (c.isApplicationClass()) {
          applicationDialogClasses.add(c);
        } else {
          libraryDialogClasses.add(c);
        }
      }
    }
    System.out.println("[HIER] App Dialogs: " + applicationDialogClasses.size()
        + ", Lib Dialogs: " + libraryDialogClasses.size());
  }

  //TODO: OPTIMIZATION
  //This method is run for every class that is in the Application Class
  //Can we change it so it only run once as it fairly expensive.
  boolean instantiatedInApplicationCode(SootClass c) {
    for (SootClass d : appClasses) {
      //There might be a bug in soot here, as the List returned by d.getMethods is reported to be
      //modified by soot during the execution.
      //Only few apps have this issue, e.g. it.greenaddress.cordova_72.apk
      for (SootMethod m : Lists.newArrayList(d.getMethods())) {
        if (!m.isConcrete()) {
          continue;
        }
        Body b = m.retrieveActiveBody();
        Iterator<Unit> stmts = b.getUnits().iterator();
        while (stmts.hasNext()) {
          Stmt stmt = (Stmt) stmts.next();
          if (!(stmt instanceof AssignStmt)) {
            continue;
          }
          Value rhs = ((AssignStmt) stmt).getRightOp();
          if (!(rhs instanceof NewExpr)) {
            continue;
          }
          if (((NewExpr) rhs).getBaseType().getSootClass() == c) {
            return true;
          }
        }
      }
    }
    return false;
  }

  // -------------------------------------------------------
  // recursive traversal of superclasses and superinterfaces
  private void traverse(SootClass sub, SootClass supr) {

    // sub is a subtype of supr (or possibly supr == sub)

    // first, add sub to the all_tbl set for supr
    classAndItsSubTypes.get(supr).add(sub);

    // also, add supr to the all_super_tbl set for sub
    classAndItsSuperTypes.get(sub).add(supr);

    // next, if sub is a non-interface non-abstract class, add it
    // to the tbl set for supr
    if (sub.isConcrete()) {
      classAndItsConcreteSubTypes.get(supr).add(sub);
    }

    // traverse parent classes/interfaces of supr

    if (supr.hasSuperclass()) {
      traverse(sub, supr.getSuperclass());
    }

    for (Iterator<SootClass> it = supr.getInterfaces().iterator(); it.hasNext();) {
      traverse(sub, it.next());
    }
  }

  public void addFakeListenerClass(SootClass listenerClass,
      SootClass listenerInterface) {
    classAndItsSuperTypes.put(
        listenerClass,
        Sets.newHashSet(listenerClass, listenerInterface));

    classAndItsSubTypes.put(listenerClass, Sets.newHashSet(listenerClass));
    classAndItsConcreteSubTypes.put(listenerClass, Sets.newHashSet(listenerClass));

    classAndItsSubTypes.get(listenerInterface).add(listenerClass);
    classAndItsConcreteSubTypes.get(listenerInterface).add(listenerClass);
  }

  // -------------------------------------------------------------
  // this hash table contains a pair (C,X) for each class C in the
  // program. Here by "class" we mean SootClass, which could be a
  // Java class or a Java interface. The set of possible values of C
  // covers all SootClasses that are application classes or library
  // classes. X is a HashSet that contains SootClasses for all
  // elements of the set { C } union { D | D is a direct or
  // transitive subtype of C }, **excluding** all D that are
  // interfaces or abstract classes.
  private final Map<SootClass, Set<SootClass>> classAndItsConcreteSubTypes = Maps.newHashMap();

  // -------------------------------------------------------------
  // this hash table contains a pair (C,X) for each class C in the
  // program. Here by "class" we mean SootClass, which could be a
  // Java class or a Java interface. The set of possible values of C
  // covers all SootClasses that are application classes or library
  // classes. X is a HashSet that contains SootClasses for all
  // elements of the set { C } union { D | D is a direct or
  // transitive subtype of C }.
  private final Map<SootClass, Set<SootClass>> classAndItsSubTypes = Maps.newHashMap();

  // -------------------------------------------------------------
  // this hash table contains a pair (C,X) for each class C in the
  // program. Here by "class" we mean SootClass, which could be a
  // Java class or a Java interface. The set of possible values of C
  // covers all SootClasses that are application classes or library
  // classes. X is a HashSet that contains SootClasses for all
  // elements of the set { C } union { D | D is a direct or
  // transitive SUPERtype of C }.
  private final Map<SootClass, Set<SootClass>> classAndItsSuperTypes = Maps.newHashMap();

  public Set<SootClass> applicationActivityClasses = Sets.newHashSet();
  public Set<SootClass> libActivityClasses = Sets.newHashSet(); // not in app

  public Set<SootClass> applicationDialogClasses = Sets.newHashSet();
  public Set<SootClass> libraryDialogClasses = Sets.newHashSet();

  // All application classes
  public Set<SootClass> appClasses = Sets.newHashSet();

  // Any subclass of android.view.View (could be either app or lib class)
  public Set<SootClass> viewClasses = Sets.newHashSet();

  // For now, we pretend Menu and MenuItem are View
  public Set<SootClass> menuClasses = Sets.newHashSet();
  public Set<SootClass> menuItemClasses = Sets.newHashSet();

  public Map<SootClass, Set<SootMethod>> frameworkManaged = Maps.newHashMap();
}
