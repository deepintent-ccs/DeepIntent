/*
 * IAndroidView.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

public interface IAndroidView {
  public IAndroidView deepCopy();

  public void setParent(AndroidView parent);
}
