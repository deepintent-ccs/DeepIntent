/*
 * Path.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import java.util.ArrayList;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class Path {
  ArrayList<String> calls;

  public Path() {
    this(Lists.<String>newArrayList());
  }

  public Path(ArrayList<String> calls) {
    this.calls = calls;
  }

  public Path extend(String c) {
    Path p = new Path();
    p.calls.addAll(calls);
    p.calls.add(c);
    return p;
  }

  public Path extend(Iterable<String> extensionCalls) {
    Path p = new Path();
    p.calls.addAll(this.calls);
    for (String c : extensionCalls) {
      p.calls.add(c);
    }
    return p;
  }

  public void trim(int length) {
    int diff = calls.size() - length;
    for (int i = 0; i < diff; i++) {
      calls.remove(calls.size() - 1);
    }
  }

  public int length() {
    return calls.size();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Path)) {
      return false;
    }
    Path other = (Path) o;
    return this.calls.equals(other.calls);
  }

  @Override
  public int hashCode() {
    return calls.hashCode();
  }

  @Override
  public String toString() {
    return "<" + Joiner.on(',').join(calls) + ">";
  }
}
