/*
 * JimpleUtil.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import presto.android.Hierarchy;
import presto.android.MethodNames;
import soot.ArrayType;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.Expr;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.tagkit.LineNumberTag;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class JimpleUtil implements MethodNames {
  Hierarchy hier;

  private static JimpleUtil instance;
  public static synchronized JimpleUtil v(Hierarchy hier) {
    if (instance == null) {
      instance = new JimpleUtil();
      instance.s2m = Maps.newHashMap();
      instance.exprToStmt = Maps.newHashMap();
      instance.hier = hier;
    }
    return instance;
  }

  public static synchronized JimpleUtil v() {
    return v(Hierarchy.v());
  }

  // /////////////////////////////////////////
  // General Jimple utils
  // Assume "l = ..."
  public Local lhsLocal(Stmt s) {
    return (Local) ((DefinitionStmt) s).getLeftOp();
  }

  // Assume "... = ..."
  public Value lhs(Stmt s) {
    return ((DefinitionStmt) s).getLeftOp();
  }

  public Local receiver(Stmt s) {
    return (Local) ((InstanceInvokeExpr) s.getInvokeExpr()).getBase();
  }

  public Local receiver(InvokeExpr ie) {
    if (ie instanceof InstanceInvokeExpr) {
      // It can be a static invoke.
      return (Local) ((InstanceInvokeExpr) ie).getBase();
    } else {
      return null;
    }
  }

  public Local thisLocal(SootMethod m) {
    IdentityStmt first = null;
    synchronized (m) {
        first = (IdentityStmt) m.retrieveActiveBody().getUnits().iterator().next();
    }
    if (!(first.getRightOp() instanceof ThisRef)) {
      throw new RuntimeException();
    }
    return lhsLocal(first);
  }

  /**
   * Returns the local variable corresponding to the n-th parameter in the
   * specified method. The counting starts from 0. For an instance method and
   * n=0, this method is equivalent to thisLocal().
   * @param method the specified method
   * @param index specifies the position of the parameter
   * @return
   */
  public Local localForNthParameter(SootMethod method, int index) {
    Iterator<Unit> stmts = null;
    synchronized(method) {
      stmts = method.retrieveActiveBody().getUnits().iterator();
    }
    for (int i = 0; i < index; i++) {
      stmts.next();
    }
    Stmt idStmt = (Stmt) stmts.next();
    if (!(idStmt instanceof DefinitionStmt)) {
      System.out.println("--- " + method);
      System.out.println(method.retrieveActiveBody());
    }
    return lhsLocal(idStmt);
  }

  // /////////////////////////////////////////
  // App-specific recording
  public Map<Stmt, SootMethod> s2m;

  public SootMethod lookup(Stmt s) {
    return s2m.get(s);
  }

  public void record(Stmt s, SootMethod m) {
    s2m.put(s, m);
  }

  public Map<Expr, Stmt> exprToStmt;

  public Stmt lookup(Expr e) {
    return exprToStmt.get(e);
  }

  public void record(Expr e, Stmt s) {
    exprToStmt.put(e, s);
  }

  public String toString(Expr e) {
    Stmt s = lookup(e);
    SootMethod m = lookup(s);
    return s + " @ " + m;
  }

  public Set<Value> getReturnValues(SootMethod method) {
    Preconditions.checkArgument(method.isConcrete());

    Set<Value> returnValues = Sets.newHashSet();
    Body body = method.retrieveActiveBody();
    Iterator<Unit> stmts = body.getUnits().iterator();
    while (stmts.hasNext()) {
      Stmt d = (Stmt) stmts.next();
      if (!(d instanceof ReturnStmt)) {
        continue;
      }
      Value retval = ((ReturnStmt) d).getOp();
      returnValues.add(retval);
    }
    return returnValues;
  }

  // Analysis-specific
  public boolean interesting(Type t) {
    if (t instanceof ArrayType) {
      return interesting(((ArrayType) t).baseType);
    }
    return t instanceof IntType || t instanceof RefType;
  }

  public int getLineNumber(Unit u) {
    int lineNumber = -1;
    LineNumberTag tag = (LineNumberTag) u.getTag("LineNumberTag");
    if (tag != null) {
      lineNumber = tag.getLineNumber();
    }
    return lineNumber;
  }

  // /////////////////////////////////////////
  // Android-specific
  public Value getLayoutId(Stmt s) {
    InvokeExpr ie = s.getInvokeExpr();
    SootMethod m = ie.getMethod();
    SootClass c = m.getDeclaringClass();
    String sig = m.getSignature();
    String subsig = m.getSubSignature();
    if (subsig.equals(setContentViewSubSig)) {
      if (hier.libActivityClasses.contains(c)
          || hier.applicationActivityClasses.contains(c)) {
        return ie.getArg(0);
      }
    }
    if (sig.equals(layoutInflaterInflate)
        || sig.equals(layoutInflaterInflateBool)) {
      return ie.getArg(0);
    }
    if (sig.equals(viewCtxInflate)) {
      return ie.getArg(1);
    }
    return null;
  }

  /**
   * Returns the set of methods in the specified interface. When the parameter
   * is not an interface (as expected), return an empty set.
   *
   * @param interfaceType the interface specified in SootClass
   * @return set of the methods in the specified interface
   */
  public Set<SootMethod> getMethodsInInterface(SootClass interfaceType) {
    if (interfaceType.isInterface()) {
      return Sets.newHashSet(interfaceType.getMethods());
    } else {
      return Collections.emptySet();
    }
  }

  public void writeAllJimples() {
    File tempDir = Files.createTempDir();
    String absPath = tempDir.getAbsolutePath();
    ExecutorService executor = Executors.newFixedThreadPool(4);
    for (final SootClass cls : Scene.v().getApplicationClasses()) {
      String clsName = cls.getName();
      String fileName = absPath + "/" + clsName + ".jimple";
      final File jimpleFile = new File(fileName);
      executor.submit(new Runnable() {
        @Override
        public void run() {
          PrintWriter out = null;
          try {
            out = new PrintWriter(new FileWriter(jimpleFile));
            for (SootField f : cls.getFields()) {
              out.println("!!! " + f.getSignature());
              out.println();
            }
            for (SootMethod m : cls.getMethods()) {
              out.println("--- " + m.getSignature());
              if (m.isConcrete()) {
                out.println(m.retrieveActiveBody());
              }
              out.println();
            }
            out.flush();
            System.out.print(".");
          } catch (IOException e) {
            e.printStackTrace();
          } finally {
            try {
              if (out != null) {
                out.close();
              }
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          }
        }
      });
    }
    try {
      executor.shutdown();
      executor.awaitTermination(30, TimeUnit.MINUTES);
      System.out.println("\nJimple code saved to " + absPath);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
