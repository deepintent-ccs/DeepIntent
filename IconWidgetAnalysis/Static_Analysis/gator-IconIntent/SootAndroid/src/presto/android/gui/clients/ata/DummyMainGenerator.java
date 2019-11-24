/*
 * DummyMainGenerator.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import soot.SootMethod;

public interface DummyMainGenerator {
  public final String DUMMY_MAIN_CLASSNAME = "DummyMain80467581";

  public SootMethod generateDummyMain(String mainActivity, ActivityTransitionGraph atg);

  public SootMethod generateDummyMain(String mainActivity, ActivityTransitionGraph atg, ActivityStackTransitionGraph astg);
}
