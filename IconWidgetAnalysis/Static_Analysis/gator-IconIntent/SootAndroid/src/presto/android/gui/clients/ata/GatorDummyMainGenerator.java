/*
 * GatorDummyMainGenerator.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;

import presto.android.gui.clients.ata.ActivityStackTransitionGraph.Edge;
import presto.android.gui.clients.ata.ActivityStackTransitionGraph.Node;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.ActivityStack;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.MethodSequence;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.TransitionPolicy;
import soot.ArrayType;
import soot.Local;
import soot.Modifier;
import soot.PatchingChain;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.VoidType;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import soot.util.Chain;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

// probably not gonna use this...
public class GatorDummyMainGenerator implements DummyMainGenerator {

  int k = 2;

  @Override
  public SootMethod generateDummyMain(String mainActivity, ActivityTransitionGraph atg) {
    // 1) Build ASTG
    ActivityTransitionAnalysisInterface analysis =
        new KLimitActivityTransitionAnalysis(k);
    ActivityStackTransitionGraph astg = new ActivityStackTransitionGraph();
    TransitionPolicy policy = DefaultTransitionPolicy.v();
    analysis.buildASTGAndGetPossibleStacks(mainActivity, atg, astg, policy);

    return generateDummyMain(mainActivity, atg, astg);
  }

  @Override
  public SootMethod generateDummyMain(String mainActivity, ActivityTransitionGraph atg, ActivityStackTransitionGraph astg) {
    // 2) Use ASTG for code generation
    SootClass dummyMainClass = new SootClass(DUMMY_MAIN_CLASSNAME);
    // NOTE: see Flowgraph if we need to save this dummy class into Scene and
    //       the Hierarchy class.
    Type stringArgsType = ArrayType.v(RefType.v("java.lang.String"), 1);
    SootMethod dummyMain = new SootMethod(
        "main",
        Collections.singletonList(stringArgsType),
        VoidType.v());
    dummyMain.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
    Jimple jimple = Jimple.v();
    JimpleBody body = jimple.newBody(dummyMain);
    dummyMain.setActiveBody(body);
    dummyMainClass.addMethod(dummyMain);

    Chain<Local> locals = body.getLocals();
    PatchingChain<Unit> units = body.getUnits();

    // java.lang.String[] r0;
    Local r0 = jimple.newLocal(nextLocalName(), stringArgsType);
    locals.add(r0);
    // r0 := @parameter0: java.lang.String[];
    Stmt defR0 = jimple.newIdentityStmt(r0, jimple.newParameterRef(stringArgsType, 0));
    units.add(defR0);

    // TODO: we are not calling this with real data yet, so print pseudocode
    //       for now.
    System.out.println("\n===");
    printPseudocodeForDummyMain(mainActivity, atg, astg);
    System.out.println("===\n");

    // return;
    units.add(jimple.newReturnVoidStmt());

    body.validate();
    return dummyMain;
  }

  Set<Edge> visitedEdges;
  LinkedList<Edge> dfsEdges;

  // TODO: we can probably print root---...---root paths instead
  // NOTE: this is similar to method inlining - empty stack, init stack, grow,
  //       shrink, init stack, empty stack.
  void printPseudocodeForDummyMain(String mainActivity, ActivityTransitionGraph atg, ActivityStackTransitionGraph astg) {
    // Starting from root, print the longest paths with no duplicate edges.
    ActivityStack root = astg.getInitStack();
    Node rootNode = astg.getNode(root);

    System.out.printf("<%s::<init>, %s::onCreate, %s::onStart, %s::onResume>\n",
        mainActivity, mainActivity, mainActivity, mainActivity);
    visitedEdges = Sets.newHashSet();
    dfsEdges = Lists.newLinkedList();
    dfsForPrintPseudocodeForDummyMain(rootNode);
  }

  void dfsForPrintPseudocodeForDummyMain(Node current) {
    boolean leaf = true;
    for (Edge edge : current.outgoingEdges) {
      Node target = edge.target;
      if (!visitedEdges.contains(edge)) {
        leaf = false;
        dfsEdges.add(edge);
        visitedEdges.add(edge);
        dfsForPrintPseudocodeForDummyMain(target);
        visitedEdges.remove(edge);
        dfsEdges.removeLast();
      }
    }
    if (leaf) {
      System.out.println("if (...) {");
      for (Edge e : dfsEdges) {
        System.out.println("  switch (...) {");
        for (MethodSequence seq : e.sequences) {
          System.out.println("    case ...:");
          System.out.println("      " + seq);
          System.out.println("      break;");
        }
        System.out.println("  }");
      }
      System.out.println("  return;");
      System.out.println("}");
      return;
    }
  }

  void printPseudocodeForDummyMainOLD(String mainActivity, ActivityTransitionGraph atg, ActivityStackTransitionGraph astg) {
    indent = 0;
    p("public class %s {", DUMMY_MAIN_CLASSNAME);
    indent += 2;
    p("public static void main(String[] args) {");
    indent += 2;
    // r1 = new Main; r1.onCreate; ...
    String m = nextMethodName();
    printStartActivityStandard(m, null, mainActivity);
    String r1 = nextLocalName();
    p("%s = %s();", r1, m);
    for (Node source : astg.getNodes(mainActivity)) {
      for (Edge e : source.outgoingEdges) {
        // Node target = e.target;
        for (MethodSequence seq : e.sequences) {
          p("if (...) {");
          indent += 2;
          p(seq.toString());
          indent -= 2;
          p("}");
        }
      }
    }
    indent -= 2;
    p("}");
    for (String s : pendingPrints) {
      System.out.print(s);
    }
    indent -= 2;
    p("}");
  }

  // a start b, standard (a==null to represent launcher start)
  void printStartActivityStandard(String methodName, String currentActivityReference,
      String targetActivityName) {
    int oldIndent = indent;
    indent = 0;
    pp("");
    indent = 2;
    pp("static void %s() {", methodName);
    indent += 2;
    // a.onPause
    if (currentActivityReference != null) {
      pp("%s.onPause();", currentActivityReference);
    }
    // new b; b.onCreate; b.onStart; b.onResume
    String targetActivityReference = nextLocalName();
    pp("%s %s = new %s();", targetActivityName, targetActivityReference, targetActivityName);
    pp("%s.onCreate(null);", targetActivityReference);
    pp("%s.onStart();", targetActivityReference);
    pp("%s.onResume();", targetActivityReference);
    // a.onStop
    if (currentActivityReference != null) {
      pp("%s.onStop();", currentActivityReference);
    }
    pp("return %s;", targetActivityReference);
    indent -= 2;
    pp("}");
    indent -= 2;
    pp("");
    indent = oldIndent;
  }

  // b is top, go back to a
  void printTerminateActivity(String methodName) {

  }

  int indent = 0;
  void p(String fmt, Object...args) {
    for (int i = 0; i < indent; ++i) {
      System.out.print(" ");
    }
    System.out.printf(fmt, args);
    System.out.println();
  }

  ArrayList<String> pendingPrints = Lists.newArrayList();
  void pp(String fmt, Object...args) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < indent; ++i) {
      sb.append(" ");
    }
    sb.append(String.format(fmt, args));
    sb.append("\n");
    pendingPrints.add(sb.toString());
  }

  int nextLocalIndex = 0;
  String nextLocalName() {
    return "r" + nextLocalIndex++;
  }
  int nextMethodIndex = 0;
  String nextMethodName() {
    return "m" + nextMethodIndex++;
  }
}
