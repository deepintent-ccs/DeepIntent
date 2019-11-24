/*
 * DefaultTransitionPolicy.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.clients.ata;

import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.ActivityStack;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.MethodSequence;
import presto.android.gui.clients.ata.ActivityTransitionAnalysisInterface.TransitionPolicy;

public class DefaultTransitionPolicy implements TransitionPolicy {
  private static DefaultTransitionPolicy theInstance;
  public static DefaultTransitionPolicy v() {
    if (theInstance == null) {
      theInstance = new DefaultTransitionPolicy();
    }
    return theInstance;
  }

  @Override
  public ActivityStack constructNewStack(ActivityStack currentStack,
      LaunchConfiguration config, String targetActivity) {
    ActivityStack newStack = new ActivityStack(currentStack);
    int flags = config.getFlags();
    boolean clearTop = (flags & LaunchConfiguration.FLAG_CLEAR_TOP) != 0;
    boolean reorderToFront =
        (flags & LaunchConfiguration.FLAG_REORDER_TO_FRONT) != 0;
    boolean singleTop = (flags & LaunchConfiguration.FLAG_SINGLE_TOP) != 0;
    boolean standard = (flags & LaunchConfiguration.FLAG_STANDARD) != 0;

    if (clearTop) {
      // When clearTop, reorderToFront is ignored. And for this version,
      // we do not distinguish whether the instance is reused or a new
      // one is created. Therefore, SINGLE_TOP or STANDARD - doesn't
      // matter.
      boolean found = newStack.clearTop(targetActivity);
      if (!found) {
        newStack.push(targetActivity);
      }
    } else if (reorderToFront) {
      // When not clearTop, we reorder. SINGLE_TOP or STANDARD - doesn't
      // matter either.
      boolean found = newStack.reorderToFront(targetActivity);
      if (!found) {
        newStack.push(targetActivity);
      }
    } else if (singleTop) {
      if (!newStack.top().equals(targetActivity)) {
        newStack.push(targetActivity);
      }
    } else if (standard) {
      newStack.push(targetActivity);
    } else {
      throw new RuntimeException(
          "Invalid flags=0x" + Integer.toHexString(flags));
    }
    return newStack;
  }

  @Override
  public MethodSequence getMethodSequence(ActivityStack currentStack,
      LaunchConfiguration config, String targetActivity) {
    MethodSequence seq = new MethodSequence();
    int flags = config.getFlags();
    boolean clearTop = (flags & LaunchConfiguration.FLAG_CLEAR_TOP) != 0;
    boolean reorderToFront =
        (flags & LaunchConfiguration.FLAG_REORDER_TO_FRONT) != 0;
    boolean singleTop = (flags & LaunchConfiguration.FLAG_SINGLE_TOP) != 0;
    boolean standard = (flags & LaunchConfiguration.FLAG_STANDARD) != 0;
    if (!clearTop && !reorderToFront) {
      if (standard) {
        // a starts b
        String a = currentStack.top();
        String b = targetActivity;
        standard(a, b, seq);
      } else if (singleTop) {
        String a = currentStack.top();
        String b = targetActivity;
        if (a.equals(b)) {
          singleTop(a, seq);
        } else {
          standard(a, b, seq);
        }
      } else {
        throw new RuntimeException();
      }
      return seq;
    }

    if (clearTop) {
      String a = currentStack.top();
      String b = targetActivity;
      if (a.equals(b)) {
        if (standard) {
          standard(a, b, seq);
          seq.add(a, "onDestroy");
        } else {
          singleTop(a, seq);
        }
        return seq;
      }

      int found = currentStack.find(targetActivity);
      if (found == -1) {
        standard(a, b, seq);
      } else {
        // destroy all guys bottom to top starting from (found - 1), the one
        // right above matched activity
        for (int i = found - 1; i > 0; i--) {
          String c = currentStack.get(i);  // inefficient, but good enough
          seq.add(c, "onDestroy");
        }
        // pause top
        seq.add(a, "onPause");

        if (standard) {
          // destroy and create new
          seq.add(b, "onDestroy");
          seq.add(b, "<init>");
          seq.add(b, "onCreate");
          seq.add(b, "onStart");
          seq.add(b, "onResume");
        } else {
          // reuse
          seq.add(b, "onNewIntent");
          seq.add(b, "onRestart");
          seq.add(b, "onStart");
          seq.add(b, "onResume");
        }

        // stop & destroy top
        seq.add(a, "onStop");
        seq.add(a, "onDestroy");
      }
      return seq;
    }
    if (reorderToFront) {
      String a = currentStack.top();
      String b = targetActivity;
      if (a.equals(b)) {
        seq.add(a, "onPause");
        seq.add(a, "onNewIntent");
        seq.add(a, "onResume");
        return seq;
      }
      int found = currentStack.find(targetActivity);
      if (found == -1) {
        standard(a, b, seq);
      } else {
        seq.add(a, "onPause");
        seq.add(b, "onNewIntent");
        seq.add(b, "onRestart");
        seq.add(b, "onStart");
        seq.add(b, "onResume");
        seq.add(a, "onStop");
      }
      return seq;
    }

    throw new RuntimeException();
  }

  void standard(String a, String b, MethodSequence seq) {
    seq.add(a, "onPause");
    seq.add(b, "<init>");
    seq.add(b, "onCreate");
    seq.add(b, "onStart");
    seq.add(b, "onResume");
    seq.add(a, "onStop");
  }

  void singleTop(String a, MethodSequence seq) {
    seq.add(a, "onPause");
    seq.add(a, "onNewIntent");
    seq.add(a, "onResume");
  }
}
