/*
 * Scene.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.mocks.soot;

import soot.SootClass;

public class Scene extends soot.Scene {
  private static Scene instance;
  
  private Scene() {
    super(null);
  }

  public static Scene v() {
    if (instance == null) {
      instance = new Scene();
    }
    return instance;
  }
  
  public SootClass getSootClass(String className) {
    return new SootClass(className) {
      public boolean isPhantom() {
        return false;
      }
    };
  }
}
