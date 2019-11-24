/* 
 * CommandExecutor.java - part of the LeakDroid project
 *
 * Copyright (c) 2013, The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the root
 * directory.
 */

package pai;

import android.util.Log;
import com.robotium.solo.Solo;


public class CommandExecutor {
  public static final String EXE_TAG = "Xewr6chA";

  public static final String REPLAY = "REPLAY";
  public static final String STAT = "STAT";
  public static final String REP = "REP";
  public static final String THREAD = "THREAD";

  public static void execute(Solo solo, String cmd, int delay) {
    Log.i(EXE_TAG, cmd);
    solo.sleep(delay);
  }

  public static void execute(String cmd) {
    Log.i(EXE_TAG, cmd);
  }

  public static void reportRep() {
    Log.i(EXE_TAG, REP);
    Log.i(EXE_TAG, THREAD + " " + Thread.activeCount());
  }
}
