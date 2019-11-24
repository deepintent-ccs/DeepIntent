/*
 * CFGAnalyzerOutput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.analyzer;

import presto.android.gui.graph.NObjectNode;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Multimap;

public class CFGAnalyzerOutput {
  public Multimap<NObjectNode, Pair<Stmt, SootMethod>> targets;
  public boolean avoid;
  public boolean exitSystem;
}
