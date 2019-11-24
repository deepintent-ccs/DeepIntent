/* 
 * AmplifyUtils.java - part of the LeakDroid project
 *
 * Copyright (c) 2013, The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the root
 * directory.
 */

package pai;

import android.content.res.Configuration;
import com.robotium.solo.Solo;

public class Util {
  public static final String REPLAY_TAG = "Xewr6chA";

  public static final String SLOW = "-slow";

  public static final String HOME_EVENT = "/data/presto/home_event";
  public static final int HOME_DELAY = 6000;

  public static final String POWER_EVENT = "/data/presto/power_event";
  public static final int POWER_DELAY = 6000;

  public static int rotateDelay = 1000;
  public static int homeDelay = 2000;
  public static int powerDelay = 1000;

  public static void replay(Solo solo, String event, int delay) {
    String cmd = CommandExecutor.REPLAY + " " + event;
    CommandExecutor.execute(solo, cmd, delay);
  }

  //rotate and back
  public static void rotateOnce(Solo solo) {
//    int CUR_ORIENTATION = solo.getCurrentActivity().getResources().getConfiguration().orientation;
//    if (CUR_ORIENTATION == Configuration.ORIENTATION_LANDSCAPE) {
//      solo.setActivityOrientation(Solo.PORTRAIT);
//      solo.sleep(rotateDelay);
//      solo.setActivityOrientation(Solo.LANDSCAPE);
//    } else if (CUR_ORIENTATION == Configuration.ORIENTATION_PORTRAIT) {
//      solo.setActivityOrientation(Solo.LANDSCAPE);
//      solo.sleep(rotateDelay);
//      solo.setActivityOrientation(Solo.PORTRAIT);
//    }
    solo.setActivityOrientation(Solo.LANDSCAPE);
    solo.sleep(rotateDelay);
    solo.setActivityOrientation(Solo.PORTRAIT);
  }

  public static void rotateTimes(Solo solo, int n) {
    for (int i = 0; i < n; i++) {
      int CUR_ORIENTATION = solo.getCurrentActivity().getResources().getConfiguration().orientation;
      solo.sleep(rotateDelay);
      if (CUR_ORIENTATION == Configuration.ORIENTATION_LANDSCAPE) {
        solo.setActivityOrientation(Solo.PORTRAIT);
      } else if (CUR_ORIENTATION == Configuration.ORIENTATION_PORTRAIT) {
        solo.setActivityOrientation(Solo.LANDSCAPE);
      }
      CommandExecutor.reportRep();
    }
  }

  // HOME { onPause()->onStop() }
  // SWITCH-BACK { onRestart()->onStart()->onResume() }
  public static void homeAndBack(Solo solo) {
    replay(solo, HOME_EVENT, HOME_DELAY);
  }

  public static void homeAndBackSlow(Solo solo) {
    replay(solo, HOME_EVENT + SLOW, HOME_DELAY);
  }

  public static void homeAndBack(Solo solo, int n) {
    for (int i = 0; i < n; i++) {
      replay(solo, HOME_EVENT, HOME_DELAY);
      if (homeDelay > 0) solo.sleep(homeDelay);
      CommandExecutor.reportRep();
    }
  }

  public static void homeAndBack(Solo solo, int n, int delay) {
    for (int i = 0; i < n; i++) {
      replay(solo, HOME_EVENT, delay);
    }
  }

  public static void powerAndBack(Solo solo) {
    replay(solo, POWER_EVENT, POWER_DELAY);
  }

  public static void powerAndBack(Solo solo, int n) {
    for (int i = 0; i < n; i++) {
      replay(solo, POWER_EVENT, POWER_DELAY);
      CommandExecutor.reportRep();
    }
  }
}
