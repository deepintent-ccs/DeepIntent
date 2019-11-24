/* 
 * ProgrammableRepeatAmplifyAction.java - part of the LeakDroid project
 *
 * Copyright (c) 2013, The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the root
 * directory.
 */

package pai;

import com.robotium.solo.Solo;

public class AmplifyAction {
  private GenericFunctor func;
  private int times;

  public AmplifyAction(int times) {
    this.times = times;
  }

  public void setFunc(GenericFunctor func) {
    this.func = func;
  }

  public void execute(Solo solo) {
    if (func == null) {
      throw new RuntimeException("Null functor!");
    }

    for (int i = 0; i < times; i++) {
      func.doIt(solo);
      CommandExecutor.reportRep();
    }
  }

  public void setTimes(int times) {
    this.times = times;
  }
}
