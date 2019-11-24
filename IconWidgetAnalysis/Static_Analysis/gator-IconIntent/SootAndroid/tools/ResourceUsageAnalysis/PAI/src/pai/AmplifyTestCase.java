/* 
 * AmplifyTestCase.java - part of the LeakDroid project
 *
 * Copyright (c) 2013, The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the root
 * directory.
 */

package pai;

import android.os.Debug;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import com.robotium.solo.Solo;

@SuppressWarnings("rawtypes")
public class AmplifyTestCase extends ActivityInstrumentationTestCase2 {
  private static final String LOG_TAG = "AmplifyTestCase";
  protected Solo solo;
  private AmplifyAction amplifyAction;
  private Thread timerThread;

  private boolean mtdProfile = false;

  @SuppressWarnings("unchecked")
  public AmplifyTestCase(Class cls) {
    super(cls);
    amplifyAction = new AmplifyAction(500);
  }

  @SuppressWarnings({"unchecked", "deprecation"})
  public AmplifyTestCase(String pack, Class cls) {
    super(pack, cls);
    amplifyAction = new AmplifyAction(500);
  }

  public void setRepeatTimes(int times) {
    amplifyAction.setTimes(times);
  }

  protected void turnOnMtdProfile() {
    mtdProfile = true;
  }

  protected void setRotateDelay(int time) {
    if (time > Util.rotateDelay) Util.rotateDelay = time;
  }

  protected void setHomeDelay(int time) {
    if (time > Util.homeDelay) Util.homeDelay = time;
  }

  protected void setPowerDelay(int time) {
    if (time > Util.powerDelay) Util.powerDelay = time;
  }

  public void specifyAmplifyFunctor(GenericFunctor func) {
    amplifyAction.setFunc(func);
    solo.sleep(1000);
  }

  public void specifyAmplifySuffix(final GenericFunctor suffix) {
    GenericFunctor func = new GenericFunctor() {
      public void doIt(Object arg) {
        solo.goBack();
        suffix.doIt(arg);
      }
    };
    amplifyAction.setFunc(func);
  }

  public void setUp() throws Exception {
    Runtime runtime = Runtime.getRuntime();
    long max = runtime.maxMemory();
    Log.i("PRESTO", "Dalvik Heap Size: " + max);
    solo = new Solo(this.getInstrumentation(), this.getActivity());
    solo.unlockScreen();
    timerThread = new Thread() {
      public void run() {
        try {
          Thread.sleep(10 * 3600 * 1000);  // run at most 10 hours
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        Log.w(LOG_TAG, "Test case taking too long to finish.");
        throw new TimeoutException();
      }
    };
    timerThread.start();
  }

  public void tearDown() throws Exception {
    this.getInstrumentation().waitForIdleSync();
    String cmd = CommandExecutor.STAT + " BEGIN";
    CommandExecutor.execute(cmd);
    if (mtdProfile) Debug.startMethodTracing();
    amplifyAction.execute(solo);
    if (mtdProfile) Debug.stopMethodTracing();

    cmd = CommandExecutor.STAT + " END";
    CommandExecutor.execute(cmd);

    solo.finishOpenedActivities();
    super.tearDown();
  }

  // --- utility methods

  protected void printCurrent() {
    Log.i("PRESTO", solo.getCurrentActivity().toString());
  }

  protected void explore() {
    Thread t = new Thread() {
      public void run() {
        while (true) {
          printCurrent();
          solo.sleep(2000);
        }
      }
    };
    t.start();
    try {
      t.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
