/*
 * IncludeAndroidView.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

class IncludeAndroidView implements IAndroidView {
  public AndroidView parent;
  public String layoutId;
  public Integer includeeId;

  public IncludeAndroidView(String layoutId) {
    this(layoutId, null);
  }

  public IncludeAndroidView(String layoutId, Integer includeeId) {
    this.layoutId = layoutId;
    this.includeeId = includeeId;
  }

  @Override
  public IAndroidView deepCopy() {
    IncludeAndroidView ret = new IncludeAndroidView(layoutId, includeeId);
    return ret;
  }

  @Override
  public void setParent(AndroidView parent) {
    if (this.parent != null) {
      this.parent.removeChildInternal(this);
    }
    this.parent = parent;
    this.parent.addChildInternal(this);
  }
}
