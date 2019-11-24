/*
 * Util.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.testgen;

public class Util {
  public static String prepend(String input, String stuff) {
    String[] str = input.split("\n");
    StringBuffer sb = new StringBuffer();
    for (String s : str)
      sb.append(stuff + s + "\n");
    return sb.toString();
  }
}