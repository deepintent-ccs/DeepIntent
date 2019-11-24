/*
 * ActivityStackTests.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.ActivityStack;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.MethodSequence;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.TransitionPolicy;
import presto.android.gui.clients.ata.ActivityTransitionGraph.Node;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ActivityStackTests extends TestCase {
  public static void main(String[] args) {
    int k = 1;
    ActivityTransitionAnalysisInterface analysis =
        new KLimitActivityTransitionAnalysis(k);
    String mainActivity = "Main";
    ActivityStackTransitionGraph astg = new ActivityStackTransitionGraph();
    ActivityTransitionGraph atg = new ActivityTransitionGraph();
    Node mainNode = atg.getNode(mainActivity);
    Node a = atg.getNode("A");
    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());

    TransitionPolicy policy = DefaultTransitionPolicy.v();
    Set<ActivityStack> stacks = analysis.buildASTGAndGetPossibleStacks(mainActivity, atg, astg, policy);
    for (ActivityStack s : stacks) {
      System.out.println("  * " + s);
    }

    print(astg);
  }

  ActivityTransitionGraph atg;
  String mainActivity = "Main";
  Node mainNode;
  TransitionPolicy policy = DefaultTransitionPolicy.v();
  GatorDummyMainGenerator generator;

  Set<String> nodes;
  Set<String> edges;

//  int k = 2;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    atg = new ActivityTransitionGraph();
    mainNode = atg.getNode(mainActivity);
    nodes = Sets.newTreeSet();
    edges = Sets.newTreeSet();
    nodeStringAndIds = Maps.newHashMap();
    generator = new GatorDummyMainGenerator();
  }

  // Main,A
  public void testStandard() {
    Node a = atg.getNode("A");
    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 1, policy);

    serialize(astg, nodes, edges);

    assertTrue(nodes.contains("0: |<-top- Main -bot->|"));
    assertTrue(nodes.contains("1: |<-top- A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>"
    ));
    assertEquals(expectedEdges, edges);
    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A; A,A(singleTop)
  public void testSingleTop() {
    Node a = atg.getNode("A");
    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, a, new LaunchConfiguration(LaunchConfiguration.FLAG_SINGLE_TOP));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->1: <A::onPause, A::onNewIntent, A::onResume>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);
    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A; A,B(singleTop)
  public void testSingleTopNoReuse() {
    Node a = atg.getNode("A");
    Node b = atg.getNode("B");
    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, b, new LaunchConfiguration(LaunchConfiguration.FLAG_SINGLE_TOP));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|",
        "2: |<-top- B,A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->2: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "2-->1: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);
    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A,B,C,A(clearTop)
  public void testClearTopStandard() {
    Node a= atg.getNode("A");
    Node b = atg.getNode("B");
    Node c = atg.getNode("C");
    Node d = atg.getNode("D");
    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, b, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(b, c, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(c, d, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(d, a, new LaunchConfiguration(
        LaunchConfiguration.FLAG_CLEAR_TOP | LaunchConfiguration.FLAG_STANDARD));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|",
        "2: |<-top- B,A,Main -bot->|",
        "3: |<-top- C,B,A,Main -bot->|",
        "4: |<-top- D,C,B,A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->2: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "2-->1: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>",
        "2-->3: <B::onPause, C::<init>, C::onCreate, C::onStart, C::onResume, B::onStop>",
        "3-->2: <C::onPause, B::onRestart, B::onStart, B::onResume, C::onStop, C::onDestroy>",
        "3-->4: <C::onPause, D::<init>, D::onCreate, D::onStart, D::onResume, C::onStop>",
        "4-->3: <D::onPause, C::onRestart, C::onStart, C::onResume, D::onStop, D::onDestroy>",
        "4-->1: <B::onDestroy, C::onDestroy, D::onPause, A::onDestroy, A::<init>, A::onCreate, A::onStart, A::onResume, D::onStop, D::onDestroy>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);

    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A,B,C,A(clearTop,singleTop)
  public void testClearTopSingleTop() {
    Node a= atg.getNode("A");
    Node b = atg.getNode("B");
    Node c = atg.getNode("C");
    Node d = atg.getNode("D");
    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, b, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(b, c, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(c, d, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(d, a,
        new LaunchConfiguration(LaunchConfiguration.FLAG_CLEAR_TOP | LaunchConfiguration.FLAG_SINGLE_TOP));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|",
        "2: |<-top- B,A,Main -bot->|",
        "3: |<-top- C,B,A,Main -bot->|",
        "4: |<-top- D,C,B,A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->2: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "2-->1: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>",
        "2-->3: <B::onPause, C::<init>, C::onCreate, C::onStart, C::onResume, B::onStop>",
        "3-->2: <C::onPause, B::onRestart, B::onStart, B::onResume, C::onStop, C::onDestroy>",
        "3-->4: <C::onPause, D::<init>, D::onCreate, D::onStart, D::onResume, C::onStop>",
        "4-->3: <D::onPause, C::onRestart, C::onStart, C::onResume, D::onStop, D::onDestroy>",
        "4-->1: <B::onDestroy, C::onDestroy, D::onPause, A::onNewIntent, A::onRestart, A::onStart, A::onResume, D::onStop, D::onDestroy>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);

    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A,B,B(clearTop)
  public void testClearTopTargetIsTopStandard() {
    Node a= atg.getNode("A");
    Node b = atg.getNode("B");

    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, b, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(b, b, new LaunchConfiguration(
        LaunchConfiguration.FLAG_CLEAR_TOP | LaunchConfiguration.FLAG_STANDARD));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|",
        "2: |<-top- B,A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->2: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "2-->1: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>",
        "2-->2: <B::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, B::onStop, B::onDestroy>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);

    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A,B,B(clearTop,singleTop)
  public void testClearTopTargetIsTopSingleTop() {
    Node a= atg.getNode("A");
    Node b = atg.getNode("B");

    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, b, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(b, b, new LaunchConfiguration(
        LaunchConfiguration.FLAG_CLEAR_TOP | LaunchConfiguration.FLAG_SINGLE_TOP));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|",
        "2: |<-top- B,A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->2: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "2-->1: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>",
        "2-->2: <B::onPause, B::onNewIntent, B::onResume>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);

    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A,B(clearTop)
  public void testClearTopNoReuse() {
    Node a= atg.getNode("A");
    Node b = atg.getNode("B");

    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, b, new LaunchConfiguration(
        LaunchConfiguration.FLAG_CLEAR_TOP | LaunchConfiguration.FLAG_STANDARD));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|",
        "2: |<-top- B,A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->2: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "2-->1: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);

    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A,A(reorder)
  public void testReorderToFrontTargetIsTop() {
    Node a= atg.getNode("A");

    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, a, new LaunchConfiguration(
        LaunchConfiguration.FLAG_REORDER_TO_FRONT | LaunchConfiguration.FLAG_STANDARD));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->1: <A::onPause, A::onNewIntent, A::onResume>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);

    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A,B(reorder)
  public void testRorderToFrontNoReuse() {
    Node a= atg.getNode("A");
    Node b = atg.getNode("B");

    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, b, new LaunchConfiguration(
        LaunchConfiguration.FLAG_REORDER_TO_FRONT | LaunchConfiguration.FLAG_STANDARD));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|",
        "2: |<-top- B,A,Main -bot->|"));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->2: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "2-->1: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>"));

    assertEquals(expectedNodes, nodes);
    assertEquals(expectedEdges, edges);

    generator.generateDummyMain(mainActivity, atg, astg);
  }

  // Main,A,B,C,A(reorder)
  public void testReorderToFront() {
    Node a = atg.getNode("A");
    Node b = atg.getNode("B");
    Node c = atg.getNode("C");

    atg.findOrCreateEdgeWithConfig(mainNode, a, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(a, b, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(b, c, new LaunchConfiguration());
    atg.findOrCreateEdgeWithConfig(c, a, new LaunchConfiguration(
        LaunchConfiguration.FLAG_REORDER_TO_FRONT | LaunchConfiguration.FLAG_STANDARD));
    ActivityStackTransitionGraph astg = buildASTG(mainActivity, atg, 2, policy);

    serialize(astg, nodes, edges);

//    String s0 = "|<-top- Main -bot->|";
//    String s1 = "|<-top- A,Main -bot->|";
//    String s2 = "|<-top- B,A,Main -bot->|";
//    String s3 = "|<-top- C,B,A,Main -bot->|";
//    String s4 = "|<-top- A,C,B,Main -bot->|";
//        "5: |<-top- B,A,C,B,Main -bot->|",
//        "6: |<-top- C,B,Main -bot->|",
//        "7: |<-top- B,Main -bot->|",
//        "8: |<-top- C,B,A,C,B,Main -bot->|",
//        "9: |<-top- A,C,B,C,B,Main -bot->|"

    Set<String> expectedNodes = Sets.newTreeSet(Sets.newHashSet(
        "0: |<-top- Main -bot->|",
        "1: |<-top- A,Main -bot->|",
        "2: |<-top- B,A,Main -bot->|",
        "3: |<-top- C,B,A,Main -bot->|",
        "4: |<-top- A,C,B,Main -bot->|",
        "5: |<-top- B,A,C,B,Main -bot->|",
        "6: |<-top- C,B,Main -bot->|",
        "7: |<-top- C,B,A,C,B,Main -bot->|",
        "8: |<-top- B,Main -bot->|",
        "9: |<-top- A,C,B,C,B,Main -bot->|",
        "10: |<-top- C,B,C,B,Main -bot->|",
        "11: |<-top- B,C,B,Main -bot->|"
        ));
    Set<String> expectedEdges = Sets.newTreeSet(Sets.newHashSet(
        "0-->1: <Main::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, Main::onStop>",
        "1-->0: <A::onPause, Main::onRestart, Main::onStart, Main::onResume, A::onStop, A::onDestroy>",
        "1-->2: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "2-->1: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>",
        "2-->3: <B::onPause, C::<init>, C::onCreate, C::onStart, C::onResume, B::onStop>",
        "3-->2: <C::onPause, B::onRestart, B::onStart, B::onResume, C::onStop, C::onDestroy>",
        "3-->4: <C::onPause, A::onNewIntent, A::onRestart, A::onStart, A::onResume, C::onStop>",
        "4-->5: <A::onPause, B::<init>, B::onCreate, B::onStart, B::onResume, A::onStop>",
        "5-->4: <B::onPause, A::onRestart, A::onStart, A::onResume, B::onStop, B::onDestroy>",
        "4-->6: <A::onPause, C::onRestart, C::onStart, C::onResume, A::onStop, A::onDestroy>",
        "5-->7: <B::onPause, C::<init>, C::onCreate, C::onStart, C::onResume, B::onStop>",
        "6-->4: <C::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, C::onStop>",
        "6-->8: <C::onPause, B::onRestart, B::onStart, B::onResume, C::onStop, C::onDestroy>",
        "7-->9: <C::onPause, A::onNewIntent, A::onRestart, A::onStart, A::onResume, C::onStop>",
        "7-->5: <C::onPause, B::onRestart, B::onStart, B::onResume, C::onStop, C::onDestroy>",
        "8-->0: <B::onPause, Main::onRestart, Main::onStart, Main::onResume, B::onStop, B::onDestroy>",
        "8-->6: <B::onPause, C::<init>, C::onCreate, C::onStart, C::onResume, B::onStop>",
        "9-->10: <A::onPause, C::onRestart, C::onStart, C::onResume, A::onStop, A::onDestroy>",
        "10-->9: <C::onPause, A::<init>, A::onCreate, A::onStart, A::onResume, C::onStop>",
        "10-->11: <C::onPause, B::onRestart, B::onStart, B::onResume, C::onStop, C::onDestroy>",
        "11-->10: <B::onPause, C::<init>, C::onCreate, C::onStart, C::onResume, B::onStop>",
        "11-->6: <B::onPause, C::onRestart, C::onStart, C::onResume, B::onStop, B::onDestroy>"));

    assertEquals(expectedNodes, nodes);
//    assertEquals(expectedEdges, edges);
    assertEquals(expectedEdges.size(), edges.size());
    Iterator<String> expIter = expectedEdges.iterator();
    Iterator<String> actIter = edges.iterator();
    while (expIter.hasNext()) {
      String exp = expIter.next();
      String act = actIter.next();
      if (!exp.equals(act)) {
        System.out.println("exp=`" + exp + "'");
        System.out.println("act=`" + act + "'");
      }
      assertEquals(exp, act);
    }

    generator.generateDummyMain(mainActivity, atg, astg);
  }

  ActivityStackTransitionGraph buildASTG(String mainActivity,
      ActivityTransitionGraph atg, int k, TransitionPolicy policy) {
    ActivityTransitionAnalysisInterface analysis =
        new KLimitActivityTransitionAnalysis(k);

    ActivityStackTransitionGraph astg = new ActivityStackTransitionGraph();
    analysis.buildASTGAndGetPossibleStacks(mainActivity, atg, astg, policy);

    return astg;
  }

  static void print(ActivityStackTransitionGraph astg) {
    for (ActivityStackTransitionGraph.Node node : astg.getNodes()) {
      System.out.println(node.id + ": " + node);
    }
    for (ActivityStackTransitionGraph.Node source : astg.getNodes()) {
      for (ActivityStackTransitionGraph.Edge e : source.outgoingEdges) {
        ActivityStackTransitionGraph.Node target = e.target;
        for (MethodSequence methodSeq : e.sequences) {
          System.out.println(source.id + "-->" + target.id + ": " + methodSeq);
        }
      }
    }
  }

  Map<String, Integer> nodeStringAndIds;

  String constructStackString(String stack) {
    return nodeStringAndIds.get(stack) + ": " + stack;
  }

  void serialize(ActivityStackTransitionGraph astg, Set<String> nodes, Set<String> edges) {
    for (ActivityStackTransitionGraph.Node node : astg.getNodes()) {
      nodeStringAndIds.put(node.toString(), node.id);
      String n = node.id + ": " + node;
      System.out.println(n);
      nodes.add(n);
    }
    for (ActivityStackTransitionGraph.Node source : astg.getNodes()) {
      for (ActivityStackTransitionGraph.Edge e : source.outgoingEdges) {
        ActivityStackTransitionGraph.Node target = e.target;
        for (MethodSequence methodSeq : e.sequences) {
          String edgeString = source.id + "-->" + target.id + ": " + methodSeq;
          System.out.println(edgeString);
          edges.add(edgeString);
        }
      }
    }
  }
}
