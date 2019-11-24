/*
 * RootTag.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.wtg;

public enum RootTag {
  // show how this transition is triggered
  start_activity("start_activity"),
  show_dialog("show_dialog"),
  open_context_menu("open_context_menu"),
  open_options_menu("open_options_menu"),
  finish_activity("finish_activity"),
  dismiss_dialog("dismiss_dialog"),
  close_menu("close_menu"),
  implicit_rotate("implicit_rotate"),
  implicit_power("implicit_power"),
  implicit_home("implicit_home"),
  implicit_back("implicit_back"),
  implicit_launch("implicit_launch"),
  cyclic_edge("self_edge"),
  jump_edge("jump_edge"),
  fake_interim_edge("fake_interim_edge");
  private RootTag(String root) {
    this.root = root;
  }
  
  public String getTag() {
    return root;
  }

  private final String root;
}
