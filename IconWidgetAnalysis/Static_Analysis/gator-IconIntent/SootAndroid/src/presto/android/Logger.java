/*
 * Logger.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */

package presto.android;

public class Logger {
  public static void verb(String tag, String msg) {
    System.out.println("[" + tag + "]: " + msg);
  }

  public static void err(String tag, String msg) throws RuntimeException {
    throw new RuntimeException("[" + tag + "]: " + msg);
  }
}
