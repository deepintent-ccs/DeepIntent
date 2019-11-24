/*
 * CFGAnalyzerInput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg.analyzer;

import com.google.common.base.Preconditions;

import presto.android.gui.graph.NObjectNode;
import presto.android.gui.wtg.util.Filter;
import soot.SootMethod;
import soot.jimple.Stmt;

public class CFGAnalyzerInput {
  @SuppressWarnings("unused")
  private CFGAnalyzerInput() {
    widget = null;
    handler = null;
    stmtFilter = null;
  }

  public CFGAnalyzerInput(NObjectNode widget, SootMethod handler,
      Filter<Stmt, SootMethod> stmtFilter) {
    Preconditions.checkNotNull(widget);
    Preconditions.checkNotNull(handler);
    Preconditions.checkNotNull(stmtFilter);
    this.widget = widget;
    this.handler = handler;
    this.stmtFilter = stmtFilter;
  }

  @Override
  public int hashCode() {
    return (widget != null ? widget.hashCode() : 0)
        + (handler != null ? handler.hashCode() : 0)
        + (stmtFilter != null ? stmtFilter.hashCode() : 0);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CFGAnalyzerInput)) {
      return false;
    }
    CFGAnalyzerInput another = (CFGAnalyzerInput) o;
    return this.widget == another.widget
        && this.handler == another.handler
        && this.stmtFilter == another.stmtFilter;
  }

  public final NObjectNode widget;
  public final SootMethod handler;
  public final Filter<Stmt, SootMethod> stmtFilter;
}
