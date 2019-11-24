/*
 * StackOperation.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg;

import com.google.common.base.Preconditions;

import presto.android.gui.graph.NObjectNode;

public class StackOperation {
  @SuppressWarnings("unused")
  private StackOperation() {}

  public StackOperation(OpType type, NObjectNode window) {
    Preconditions.checkNotNull(type);
    Preconditions.checkNotNull(window);
    this.type = type;
    this.window = window;
  }

  public boolean isPushOp() {
    return type == OpType.push;
  }

  public NObjectNode getWindow() {
    return window;
  }

  @Override
  public int hashCode() {
    return window.hashCode() + type.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof StackOperation)) {
      return false;
    }
    StackOperation another = (StackOperation) o;
    return this.window == another.window
        && this.type == another.type;
  }

  @Override
  public String toString() {
    return "[" + type.name() + " " + window.toString() + "]"; 
  }

  NObjectNode window;
  OpType type;

  public enum OpType {
    push,
    pop
  }
}
