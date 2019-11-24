/*
 * InstrumentationMain.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.dynamic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;

import presto.android.Configs;
import presto.android.gui.JimpleUtil;
import soot.Body;
import soot.CompilationDeathException;
import soot.G;
import soot.Local;
import soot.Modifier;
import soot.NullType;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SourceLocator;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.options.Options;
import soot.tagkit.SyntheticTag;
import soot.util.Chain;
import soot.util.JasminOutputStream;

import com.google.common.collect.Sets;
import com.google.common.io.Files;

// This client is meant to be invoked *only* by AndroidBench/instrument.sh
public class InstrumentationMain {
  final String TAG = "2HUzeste";
  final String TRACKER_CLASSNAME = "presto.android.runtime.TrackerRuntime";
  // util
  JimpleUtil jimpleUtil = JimpleUtil.v();

  public void run() {
    File instrumentedClasses = new File(Configs.bytecodes + "-ins");
    String outputDir = instrumentedClasses.getAbsolutePath();
    Options.v().set_output_dir(outputDir);
    for (SootClass c : Scene.v().getApplicationClasses()) {
      String className = c.getName();
      if (className.equals(TRACKER_CLASSNAME)) {
        writeClass(c);
        continue;
      }
      if (className.equals("de.mud.terminal.VDUBuffer")) {
        continue;
      }
      if (className.equals("org.vudroid.core.events.ZoomListener$CommitZoomEvent")) {
        continue;
      }
      if (className.equals("org.sipdroid.sipua.ui.OneShotAlarm2")) {
        continue;
      }
      if (className.equals("org.npr.android.util.Tracker")) {
        continue;
      }
      if (className.startsWith("FakeName_")) {
        continue;
      }
      for (SootMethod m : c.getMethods()) {
        if (!m.isConcrete()) {
          continue;
        }
        instrument(m);
        // Fix Synthetic tag (not enough...)
        if (Modifier.isSynthetic(m.getModifiers())
            && !m.hasTag("SyntheticTag")) {
          m.addTag(new SyntheticTag());
        }
      }
      writeClass(c);
    }
    fixVDUBuffer();
    fixCommitZoomEvent();
    fixNPRTracker();
    fixSipDroid();
//    try {
//      Files.move(instrumentedClasses, new File(Configs.bytecodes));
//    } catch (IOException e) {
//      // TODO Auto-generated catch block
//      e.printStackTrace();
//    }
    System.out.println(
        "\033[1;31m[InstrumentationClient]\033[0m Instrumented classes saved to " + outputDir);
  }

  void fixVDUBuffer() {
    if (Configs.benchmarkName.equals("ConnectBot")) {
      String src = Configs.bytecodes + "/de/mud/terminal/VDUBuffer.class";
      String tgt = Configs.bytecodes + "-ins/de/mud/terminal/VDUBuffer.class";
      try {
        Files.copy(new File(src), new File(tgt));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    }
  }

  void fixCommitZoomEvent() {
    if (Configs.benchmarkName.equals("VuDroid")) {
      String src = Configs.bytecodes + "/org/vudroid/core/events/ZoomListener$CommitZoomEvent.class";
      String tgt = Configs.bytecodes + "-ins/org/vudroid/core/events/ZoomListener$CommitZoomEvent.class";
      try {
        Files.copy(new File(src), new File(tgt));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    }
  }

  void fixNPRTracker() {
    if (Configs.benchmarkName.equals("NPR")) {
      String src = Configs.bytecodes + "/org/npr/android/util/Tracker.class";
      String tgt = Configs.bytecodes + "-ins/org/npr/android/util/Tracker.class";
      try {
        Files.copy(new File(src), new File(tgt));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    }
  }

  void fixSipDroid() {
    if (Configs.benchmarkName.equals("SipDroid")) {
      String src = Configs.bytecodes + "/org/sipdroid/sipua/ui/OneShotAlarm2.class";
      String tgt = Configs.bytecodes + "-ins/org/sipdroid/sipua/ui/OneShotAlarm2.class";
      try {
        Files.copy(new File(src), new File(tgt));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return;
    }
  }

  void instrument(SootMethod m) {
    System.out.println("  * Instrumenting " + m);
    Body b = m.retrieveActiveBody();
    int skip = m.getParameterCount();
    if (!m.isStatic()) {
      skip++;
    }
    PatchingChain<Unit> units = b.getUnits();
    Iterator<Unit> stmts = units.snapshotIterator();
    Stmt s = null;
    for (int i = 0; i < skip; i++) {
      s = (Stmt) stmts.next();
    }
    SootMethod toCall =
        Scene.v()
        .getSootClass(TRACKER_CLASSNAME)
        .getMethod("void p(java.lang.String,java.lang.String)");
    StaticInvokeExpr printCall =
        Jimple.v().newStaticInvokeExpr(toCall.makeRef(),
            StringConstant.v(TAG), StringConstant.v(m.getSignature()));
    InvokeStmt print = Jimple.v().newInvokeStmt(printCall);
    if (s == null) {
      units.addFirst(print);
    } else {
      units.insertAfter(print, s);
    }

    // Validate
    validateBody(b);
  }

  public void writeClass(SootClass c) {
    final int format = Options.output_format_class;

    OutputStream streamOut = null;
    PrintWriter writerOut = null;

    String fileName = SourceLocator.v().getFileNameFor(c, format);

    try {
      new File(fileName).getParentFile().mkdirs();
      streamOut = new JasminOutputStream(new FileOutputStream(fileName));
      writerOut = new PrintWriter(new OutputStreamWriter(streamOut));
      G.v().out.println( "Writing to "+fileName );
    } catch (IOException e) {
      throw new CompilationDeathException("Cannot output file " + fileName,e);
    }

    if (c.containsBafBody()) {
      new soot.baf.JasminClass(c).print(writerOut);
    } else {
      new soot.jimple.JasminClass(c).print(writerOut);
    }

    try {
      writerOut.flush();
      streamOut.close();
      writerOut.close();
    } catch (IOException e) {
      throw new CompilationDeathException("Cannot close output file " + fileName);
    }
  }

  void validateBody(Body body) {
    try {
      body.validate();
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg.contains("local type not allowed in final code")) {
        // Fix not allowed null_type
        fixNullType(body);
      } else {
        throw new RuntimeException(e);
      }
    }
  }

  int fakeLocalIndex = 0;

  String nextName() {
    return "fakeLocal_" + fakeLocalIndex++;
  }

  // For a stupid method in ConnectBot
  void fixNullType(Body body) {
    Set<Local> nullTypeLocals = Sets.newHashSet();
    Chain<Local> locals = body.getLocals();
    for (Local l : locals) {
      if (l.getType() instanceof NullType) {
        l.setType(RefType.v("java.lang.Object"));
        nullTypeLocals.add(l);
      }
    }
    PatchingChain<Unit> units = body.getUnits();
    for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
      Stmt s = (Stmt) iter.next();
      if (s instanceof InstanceInvokeExpr) {
        Local receiver = jimpleUtil.receiver(s);
        if (nullTypeLocals.contains(receiver)) {
          InstanceInvokeExpr ie = (InstanceInvokeExpr) s.getInvokeExpr();
          RefType newType = ie.getMethod().getDeclaringClass().getType();
          Local newLocal = Jimple.v().newLocal(nextName(), newType);
          locals.add(newLocal);
          AssignStmt cast = Jimple.v().newAssignStmt(
              newLocal,
              Jimple.v().newCastExpr(receiver, newType));
          System.out.println("[FIX] Add `" + cast + "' before `" + s + "'");
          units.insertBefore(cast, s);
          ie.setBase(newLocal);
          System.out.println("[FIX] Call changed to `" + s + "'");
        }
      }
    }
  }

  void runBodyPacks(SootClass c) {
    for (SootMethod m : c.getMethods()) {
      if (m.isConcrete()) {
        runBodyPacks(m);
      }
    }
    throw new RuntimeException("SHOULD NOT USE!");
  }

  private void runBodyPacks(SootMethod m) {
    JimpleBody body =(JimpleBody) m.retrieveActiveBody();
    PackManager.v().getPack("jtp").apply(body);
    PackManager.v().getPack("jop").apply(body);
    PackManager.v().getPack("jap").apply(body);
    throw new RuntimeException("SHOULD NOT USE!");
  }
}
