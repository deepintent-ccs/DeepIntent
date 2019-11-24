/*
 * AlgorithmInput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.algo;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;

import presto.android.gui.graph.NActivityNode;
import presto.android.gui.wtg.analyzer.CFGAnalyzerInput;
import presto.android.gui.wtg.analyzer.CFGAnalyzerOutput;
import presto.android.gui.wtg.ds.WTG;
import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGNode;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;

public class AlgorithmInput {
  public final Map<CFGAnalyzerInput, CFGAnalyzerOutput> cfgOutput;
  public final Set<WTGEdgeSig> mustOwnerCloseEdges;
  public final Set<WTGEdgeSig> mayOwnerCloseEdges;
  public final Set<WTGEdgeSig> notCloseOwnerEdges;
  public final Set<WTGEdgeSig> mustSelfCloseEdges;
  public final Set<WTGEdgeSig> maySelfCloseEdges;
  public final Algorithm algo;
  public final WTGEdge edge;
  public final WTG wtg;
  public final Multimap<WTGNode, WTGEdge> inEdges;
  public final Multimap<WTGNode, NActivityNode> ownership;

  public AlgorithmInput(
      WTG wtg,
      WTGEdge edge,
      Algorithm algo) {
    this(wtg, edge, algo, null, null, null, null, null, null, null, null);
  }

  public AlgorithmInput(
      WTG wtg,
      WTGEdge edge,
      Algorithm algo,
      Multimap<WTGNode, NActivityNode> ownership) {
    this(wtg, edge, algo, null, ownership, null, null, null, null, null, null);
  }

  public AlgorithmInput(
      WTG wtg,
      WTGEdge edge,
      Algorithm algo,
      Multimap<WTGNode, WTGEdge> inEdges,
      Multimap<WTGNode, NActivityNode> ownership) {
    this(wtg, edge, algo, inEdges, ownership, null, null, null, null, null, null);
  }

  public AlgorithmInput(
      WTG wtg,
      WTGEdge edge,
      Algorithm algo,
      Map<CFGAnalyzerInput, CFGAnalyzerOutput> cfgOutput) {
    this(wtg, edge, algo, null, null, cfgOutput, null, null, null, null, null);
  }

  public AlgorithmInput(
      WTG wtg,
      WTGEdge edge,
      Algorithm algo,
      Multimap<WTGNode, WTGEdge> inEdges,
      Multimap<WTGNode, NActivityNode> ownership,
      Map<CFGAnalyzerInput, CFGAnalyzerOutput> cfgOutput) {
    this(wtg, edge, algo, inEdges, ownership, cfgOutput, null, null, null, null, null);
  }

  public AlgorithmInput(
      WTG wtg,
      WTGEdge edge,
      Algorithm algo,
      Multimap<WTGNode, NActivityNode> ownership,
      Set<WTGEdgeSig> mustOwnerCloseEdges,
      Set<WTGEdgeSig> mayOwnerCloseEdges,
      Set<WTGEdgeSig> notCloseOwnerEdges,
      Set<WTGEdgeSig> mustSelfCloseEdges,
      Set<WTGEdgeSig> maySelfCloseEdges) {
    this(wtg, edge, algo, null, ownership, null, mustOwnerCloseEdges, mayOwnerCloseEdges,
        notCloseOwnerEdges, mustSelfCloseEdges, maySelfCloseEdges);
  }

  private AlgorithmInput(
      WTG wtg,
      WTGEdge edge,
      Algorithm algo,
      Multimap<WTGNode, WTGEdge> inEdges,
      Multimap<WTGNode, NActivityNode> ownership,
      Map<CFGAnalyzerInput, CFGAnalyzerOutput> cfgOutput,
      Set<WTGEdgeSig> mustOwnerCloseEdges,
      Set<WTGEdgeSig> mayOwnerCloseEdges,
      Set<WTGEdgeSig> notCloseOwnerEdges,
      Set<WTGEdgeSig> mustSelfCloseEdges,
      Set<WTGEdgeSig> maySelfCloseEdges) {
    Preconditions.checkNotNull(edge);
    Preconditions.checkNotNull(algo);
    this.wtg = wtg;
    this.edge = edge;
    this.algo = algo;
    this.inEdges = inEdges;
    this.ownership = ownership;
    this.cfgOutput = cfgOutput;
    this.mustOwnerCloseEdges = mustOwnerCloseEdges;
    this.mayOwnerCloseEdges = mayOwnerCloseEdges;
    this.notCloseOwnerEdges = notCloseOwnerEdges;
    this.mustSelfCloseEdges = mustSelfCloseEdges;
    this.maySelfCloseEdges = maySelfCloseEdges;
  }

  @Override
  public int hashCode() {
    return algo.hashCode()
        + edge.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AlgorithmInput)) {
      return false;
    }
    AlgorithmInput another = (AlgorithmInput) o;
    return this.edge == another.edge && this.algo == another.algo;
  }
}
