/*
 * AlgorithmOutput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.algo;

import com.google.common.collect.Multimap;

import presto.android.gui.wtg.ds.WTGEdge;
import presto.android.gui.wtg.ds.WTGEdge.WTGEdgeSig;

public class AlgorithmOutput {
  // map from output edges back to input edges
  public Multimap<WTGEdgeSig, WTGEdge> newEdges;
}
